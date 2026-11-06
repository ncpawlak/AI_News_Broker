package com.ainewsbroker.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainewsbroker.data.NewsRepository
import com.ainewsbroker.model.Article
import com.ainewsbroker.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class FeedUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val articles: List<Article> = emptyList(),
    val countsByCategory: Map<String?, Int> = emptyMap(),
    val readIds: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val hasEverRefreshed: Boolean = false,
    val lastRefreshMillis: Long? = null,
    val message: String? = null,
)

private data class Transient(
    val selected: String? = null,
    val refreshing: Boolean = false,
    val message: String? = null,
)

class FeedViewModel(private val repo: NewsRepository) : ViewModel() {
    private val transient = MutableStateFlow(Transient())

    val state: StateFlow<FeedUiState> = combine(
        repo.articles, repo.queries, repo.readIds, repo.lastRefreshMillis, transient,
    ) { articles, queries, readIds, lastRefresh, t ->
        val enabledIds = queries.filter { it.enabled }.map { it.id }.toSet()
        val visible = articles.filter { it.queryId in enabledIds }
        val counts = buildMap<String?, Int> {
            put(null, visible.size)
            repo.categories.forEach { c -> put(c.id, visible.count { it.categoryId == c.id }) }
        }
        FeedUiState(
            categories = repo.categories,
            selectedCategoryId = t.selected,
            articles = visible,
            countsByCategory = counts,
            readIds = readIds,
            isRefreshing = t.refreshing,
            hasEverRefreshed = lastRefresh != null,
            lastRefreshMillis = lastRefresh,
            message = t.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState(categories = repo.categories))

    init {
        val last = repo.lastRefreshMillis.value
        if (last == null || Clock.System.now().toEpochMilliseconds() - last > STALE_AFTER_MILLIS) refresh()
    }

    fun selectCategory(id: String?) = transient.update { it.copy(selected = id) }

    fun refresh() {
        if (transient.value.refreshing) return
        transient.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val msg = runCatching { repo.refreshAll() }.fold(
                onSuccess = { r ->
                    when {
                        r.failedQueries > 0 -> "Updated, but ${r.failedQueries} of ${r.totalQueries} searches failed"
                        r.newArticles > 0 -> "${r.newArticles} new article${if (r.newArticles == 1) "" else "s"}"
                        else -> "You're all caught up"
                    }
                },
                onFailure = { "Couldn't refresh. Check your connection and try again." },
            )
            transient.update { it.copy(refreshing = false, message = msg) }
        }
    }

    fun consumeMessage() = transient.update { it.copy(message = null) }

    fun markRead(id: String) = repo.markRead(id)

    private companion object {
        const val STALE_AFTER_MILLIS = 15 * 60 * 1000L
    }
}
