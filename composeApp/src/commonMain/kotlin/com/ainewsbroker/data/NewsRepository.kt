package com.ainewsbroker.data

import com.ainewsbroker.model.AppSettings
import com.ainewsbroker.model.Article
import com.ainewsbroker.model.SearchQuery
import com.ainewsbroker.platform.KeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

data class RefreshResult(val newArticles: Int, val failedQueries: Int, val totalQueries: Int)

class NewsRepository(
    private val store: KeyValueStore,
    private val client: HttpClient,
    private val baseUrl: String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val refreshLock = Mutex()

    private val _queries = MutableStateFlow(
        load(KEY_QUERIES, ListSerializer(SearchQuery.serializer())) ?: DefaultQueries.queries,
    )
    val queries: StateFlow<List<SearchQuery>> = _queries.asStateFlow()

    private val _articles = MutableStateFlow(
        // Drop articles cached from the old Google News source; they have redirect links and no summaries.
        load(KEY_ARTICLES, ListSerializer(Article.serializer()))?.filterNot { it.link.contains("news.google.com") } ?: emptyList(),
    )
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()

    private val _readIds = MutableStateFlow(
        load(KEY_READ, SetSerializer(String.serializer())) ?: emptySet(),
    )
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    private val _settings = MutableStateFlow(load(KEY_SETTINGS, AppSettings.serializer()) ?: AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _lastRefreshMillis = MutableStateFlow(store.get(KEY_LAST_REFRESH)?.toLongOrNull())
    val lastRefreshMillis: StateFlow<Long?> = _lastRefreshMillis.asStateFlow()

    val categories = DefaultQueries.categories

    suspend fun refreshAll(): RefreshResult = refreshLock.withLock {
        val active = _queries.value.filter { it.enabled }
        val gate = Semaphore(MAX_PARALLEL_REQUESTS)
        val fetchedAt = now()
        val results = coroutineScope {
            active.map { q -> async { gate.withPermit { runCatching { fetch(q, fetchedAt) } } } }.awaitAll()
        }
        val fetched = results.mapNotNull { it.getOrNull() }.flatten()
        val failed = results.count { it.isFailure }
        if (active.isNotEmpty() && failed == active.size) {
            throw results.first { it.isFailure }.exceptionOrNull()!!
        }

        val before = _articles.value.map { it.id }.toSet()
        val merged = merge(_articles.value, fetched, fetchedAt)
        _articles.value = merged
        save(KEY_ARTICLES, ListSerializer(Article.serializer()), merged)
        _readIds.update { ids -> ids.intersect(merged.map { it.id }.toSet()) }
        save(KEY_READ, SetSerializer(String.serializer()), _readIds.value)
        _lastRefreshMillis.value = fetchedAt
        store.put(KEY_LAST_REFRESH, fetchedAt.toString())
        RefreshResult(merged.count { it.id !in before }, failed, active.size)
    }

    private suspend fun fetch(query: SearchQuery, fetchedAt: Long): List<Article> {
        val response = client.get(QueryNormalizer.buildSearchUrl(baseUrl, query.rawQuery))
        check(response.status.isSuccess()) { "HTTP ${response.status.value} for ${query.label}" }
        return RssParser.parse(response.bodyAsText()).map { item ->
            Article(
                id = stableId(item.link),
                title = item.title,
                source = item.source ?: "Unknown source",
                link = item.link,
                publishedAtMillis = item.publishedAtMillis ?: fetchedAt,
                categoryId = query.categoryId,
                queryId = query.id,
                queryLabel = query.label,
                fetchedAtMillis = fetchedAt,
                summary = item.summary,
            )
        }
    }

    private fun merge(existing: List<Article>, fetched: List<Article>, nowMillis: Long): List<Article> {
        val cutoff = nowMillis - RETENTION_MILLIS
        val byId = LinkedHashMap<String, Article>()
        val seenTitles = HashSet<String>()
        (existing + fetched)
            .filter { it.publishedAtMillis >= cutoff }
            .forEach { a ->
                val titleKey = a.title.lowercase().filter { it.isLetterOrDigit() }
                if (a.id !in byId && seenTitles.add(titleKey)) byId[a.id] = a
            }
        return byId.values.sortedByDescending { it.publishedAtMillis }.take(MAX_ARTICLES)
    }

    fun markRead(articleId: String) {
        if (articleId in _readIds.value) return
        _readIds.update { it + articleId }
        save(KEY_READ, SetSerializer(String.serializer()), _readIds.value)
    }

    fun upsertQuery(query: SearchQuery) {
        _queries.update { list ->
            if (list.any { it.id == query.id }) list.map { if (it.id == query.id) query else it } else list + query
        }
        // Keep cached article labels in sync with renamed/re-categorised queries.
        _articles.update { list ->
            list.map { if (it.queryId == query.id) it.copy(queryLabel = query.label, categoryId = query.categoryId) else it }
        }
        persistQueriesAndArticles()
    }

    fun setQueryEnabled(id: String, enabled: Boolean) {
        _queries.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
        save(KEY_QUERIES, ListSerializer(SearchQuery.serializer()), _queries.value)
    }

    fun deleteQuery(id: String) {
        _queries.update { list -> list.filterNot { it.id == id } }
        _articles.update { list -> list.filterNot { it.queryId == id } }
        persistQueriesAndArticles()
    }

    fun resetQueriesToDefaults() {
        _queries.value = DefaultQueries.queries
        val ids = DefaultQueries.queries.map { it.id }.toSet()
        _articles.update { list -> list.filter { it.queryId in ids } }
        persistQueriesAndArticles()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        save(KEY_SETTINGS, AppSettings.serializer(), _settings.value)
    }

    fun newQueryId(): String = "custom-${now()}"

    private fun persistQueriesAndArticles() {
        save(KEY_QUERIES, ListSerializer(SearchQuery.serializer()), _queries.value)
        save(KEY_ARTICLES, ListSerializer(Article.serializer()), _articles.value)
    }

    private fun <T> load(key: String, serializer: KSerializer<T>): T? =
        store.get(key)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private fun <T> save(key: String, serializer: KSerializer<T>, value: T) =
        store.put(key, json.encodeToString(serializer, value))

    companion object {
        private const val KEY_QUERIES = "queries"
        private const val KEY_ARTICLES = "articles"
        private const val KEY_READ = "read_ids"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_LAST_REFRESH = "last_refresh"
        private const val MAX_PARALLEL_REQUESTS = 3
        private const val MAX_ARTICLES = 600
        private const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

        /** FNV-1a 64-bit hash of the link: stable across platforms and app restarts. */
        fun stableId(link: String): String {
            var hash = -0x340d631b7bdddcdbL
            for (c in link) {
                hash = hash xor c.code.toLong()
                hash *= 0x100000001b3L
            }
            return hash.toULong().toString(16)
        }
    }
}
