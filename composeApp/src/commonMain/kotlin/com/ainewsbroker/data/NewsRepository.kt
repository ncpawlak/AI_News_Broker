package com.ainewsbroker.data

import com.ainewsbroker.model.AppSettings
import com.ainewsbroker.model.Article
import com.ainewsbroker.model.QuerySource
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
    /** Rewrites absolute source URLs; the web build routes them through the dev-server proxy. */
    private val rewriteUrl: (String) -> String = { it },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val refreshLock = Mutex()

    private val _queries = MutableStateFlow(loadQueries())
    val queries: StateFlow<List<SearchQuery>> = _queries.asStateFlow()

    private val _articles = MutableStateFlow(
        load(KEY_ARTICLES, ListSerializer(Article.serializer()))
            // Drop articles cached from the old Google News source and from retired default searches.
            ?.filterNot { it.link.contains("news.google.com") }
            ?.filter { a -> _queries.value.any { it.id == a.queryId } }
            ?: emptyList(),
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
        val items = when (query.source) {
            QuerySource.BingNews -> RssParser.parse(get(QueryNormalizer.buildSearchUrl(QueryNormalizer.BING_BASE, query.rawQuery), query))
            QuerySource.Feed -> RssParser.parse(get(query.rawQuery.trim(), query), query.titlePrefix)
            QuerySource.HackerNews, QuerySource.ShowHN -> fetchHackerNews(query, fetchedAt)
        }
        val feedSource = if (query.source == QuerySource.Feed) feedSourceName(query.rawQuery) else null
        return items.map { item ->
            Article(
                id = stableId(item.link),
                title = item.title,
                source = item.source ?: feedSource ?: "Unknown source",
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

    private suspend fun fetchHackerNews(query: SearchQuery, fetchedAt: Long): List<RssItem> {
        val since = (fetchedAt - HN_WINDOW_MILLIS) / 1000
        val terms = QueryNormalizer.hackerNewsTerms(query.rawQuery).take(MAX_HN_TERMS)
        check(terms.isNotEmpty()) { "No search terms for ${query.label}" }
        val results = coroutineScope {
            terms.map { term ->
                async {
                    runCatching {
                        val url = QueryNormalizer.buildHackerNewsUrl(term, query.source == QuerySource.ShowHN, query.minPoints, since)
                        HackerNewsParser.parse(get(url, query))
                    }
                }
            }.awaitAll()
        }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        return results.mapNotNull { it.getOrNull() }.flatten().distinctBy { it.link }
    }

    private suspend fun get(url: String, query: SearchQuery): String {
        check(url.startsWith("https://")) { "${query.label}: only https:// URLs are supported" }
        val response = client.get(rewriteUrl(url))
        check(response.status.isSuccess()) { "HTTP ${response.status.value} for ${query.label}" }
        return response.bodyAsText()
    }

    private fun loadQueries(): List<SearchQuery> {
        val stored = load(KEY_QUERIES, ListSerializer(SearchQuery.serializer()))
        val version = store.get(KEY_QUERIES_VERSION)?.toIntOrNull() ?: 1
        if (stored == null || version >= DefaultQueries.VERSION) {
            store.put(KEY_QUERIES_VERSION, DefaultQueries.VERSION.toString())
            return stored ?: DefaultQueries.queries
        }
        // Older install: adopt the new defaults but keep the user's own searches.
        val validCategories = DefaultQueries.categories.map { it.id }.toSet()
        val custom = stored.filter { it.id.startsWith(CUSTOM_PREFIX) }.map {
            if (it.categoryId in validCategories) it else it.copy(categoryId = DefaultQueries.WORKFLOWS)
        }
        val migrated = DefaultQueries.queries + custom
        save(KEY_QUERIES, ListSerializer(SearchQuery.serializer()), migrated)
        store.put(KEY_QUERIES_VERSION, DefaultQueries.VERSION.toString())
        store.put(KEY_LAST_REFRESH, "") // Forces a refresh on launch so the new searches populate the feed.
        return migrated
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

    fun newQueryId(): String = "$CUSTOM_PREFIX${now()}"

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
        private const val KEY_QUERIES_VERSION = "queries_version"
        private const val KEY_ARTICLES = "articles"
        private const val KEY_READ = "read_ids"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_LAST_REFRESH = "last_refresh"
        private const val CUSTOM_PREFIX = "custom-"
        private const val MAX_PARALLEL_REQUESTS = 3
        private const val MAX_HN_TERMS = 8
        private const val MAX_ARTICLES = 600
        private const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val HN_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000
        private val hostRegex = Regex("""^https?://(?:www\.)?([^/:?#]+)""")

        /** Friendly publisher name for feed sources that don't name themselves per item. */
        fun feedSourceName(feedUrl: String): String = when (val host = hostRegex.find(feedUrl.trim())?.groupValues?.get(1)) {
            "github.blog" -> "GitHub Changelog"
            "github.com" -> "GitHub Releases"
            null -> "Feed"
            else -> host
        }

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
