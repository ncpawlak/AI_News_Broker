package com.ainewsbroker.ui.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainewsbroker.model.Article
import com.ainewsbroker.ui.DayBucket
import com.ainewsbroker.ui.components.ArticleCard
import com.ainewsbroker.ui.components.SkeletonCard
import com.ainewsbroker.ui.dayBucket
import com.ainewsbroker.ui.relativeTime
import com.ainewsbroker.ui.rememberUrlOpener
import com.ainewsbroker.ui.supportsPullToRefresh
import com.ainewsbroker.ui.theme.categoryAccent
import kotlinx.coroutines.delay
import kotlin.time.Clock

@Composable
fun FeedScreen(viewModel: FeedViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openUrl = rememberUrlOpener()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val now by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(30_000)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text("AI News") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CategoryChips(state, onSelect = viewModel::selectCategory)
            val content: @Composable () -> Unit = {
                AnimatedContent(
                    targetState = state.selectedCategoryId,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 24 })
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "category",
                ) { categoryId ->
                    val articles = if (categoryId == null) state.articles else state.articles.filter { it.categoryId == categoryId }
                    when {
                        articles.isEmpty() && state.isRefreshing -> SkeletonList()
                        articles.isEmpty() -> EmptyState(state.hasEverRefreshed, viewModel::refresh)
                        else -> ArticleGrid(
                            articles = articles,
                            state = state,
                            now = now,
                            onClick = { a ->
                                viewModel.markRead(a.id)
                                openUrl(a.link)
                            },
                        )
                    }
                }
            }
            if (supportsPullToRefresh) {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) { content() }
            } else {
                Box(Modifier.fillMaxSize()) {
                    content()
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.isRefreshing && state.articles.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(state: FeedUiState, onSelect: (String?) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                CategoryChip("All", state.countsByCategory[null] ?: 0, state.selectedCategoryId == null, null) { onSelect(null) }
            }
            items(state.categories, key = { it.id }) { c ->
                CategoryChip(c.name, state.countsByCategory[c.id] ?: 0, state.selectedCategoryId == c.id, c.id) { onSelect(c.id) }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, count: Int, selected: Boolean, categoryId: String?, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(if (count > 0) "$label  $count" else label) },
        leadingIcon = categoryId?.let {
            { Box(Modifier.size(8.dp).clip(CircleShape).background(categoryAccent(it))) }
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.height(40.dp),
    )
}

@Composable
private fun ArticleGrid(articles: List<Article>, state: FeedUiState, now: Long, onClick: (Article) -> Unit) {
    val categoryNames = remember(state.categories) { state.categories.associate { it.id to it.name } }
    val grouped = remember(articles, now / 3_600_000) { articles.groupBy { dayBucket(it.publishedAtMillis, now) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalItemSpacing = 14.dp,
        ) {
            state.lastRefreshMillis?.let { last ->
                item(key = "updated", span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        "Updated ${relativeTime(last, now).lowercase()}  ·  ${articles.size} articles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            DayBucket.entries.forEach { bucket ->
                val items = grouped[bucket].orEmpty()
                if (items.isEmpty()) return@forEach
                item(key = "header-${bucket.name}", span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        bucket.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp).animateItem(),
                    )
                }
                items(items, key = { it.id }) { article ->
                    ArticleCard(
                        article = article,
                        categoryName = categoryNames[article.categoryId] ?: article.categoryId,
                        isRead = article.id in state.readIds,
                        now = now,
                        onClick = { onClick(article) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonList() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalItemSpacing = 14.dp,
            userScrollEnabled = false,
        ) {
            items(8) { SkeletonCard() }
        }
    }
}

@Composable
private fun EmptyState(hasEverRefreshed: Boolean, onRefresh: () -> Unit) {
    // Scrollable container keeps pull-to-refresh working on an empty screen.
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(32.dp),
    ) {
        item {
            Spacer(Modifier.height(64.dp))
            Text(
                if (hasEverRefreshed) "Nothing here yet" else "Your AI briefing awaits",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (hasEverRefreshed) "No recent articles match these searches. Try another category or tweak queries in Settings."
                else "Pull down or tap refresh to fetch the latest on Claude, Copilot and new AI patterns.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FilledTonalButton(onClick = onRefresh) { Text("Refresh now") }
            }
        }
    }
}
