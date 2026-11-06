package com.ainewsbroker.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: String,
    val name: String,
)

/** Where a [SearchQuery] pulls articles from. */
@Serializable
enum class QuerySource(val displayName: String) {
    /** Bing News RSS search; rawQuery uses Google-Alerts syntax. */
    BingNews("Bing News"),

    /** Hacker News stories; rawQuery is title terms separated by OR. */
    HackerNews("Hacker News"),

    /** "Show HN" launches; rawQuery is title terms separated by OR. */
    ShowHN("Show HN"),

    /** A specific RSS/Atom feed (changelogs, release notes); rawQuery is the feed URL. */
    Feed("RSS feed"),
}

@Serializable
data class SearchQuery(
    val id: String,
    val categoryId: String,
    val label: String,
    val rawQuery: String,
    val enabled: Boolean = true,
    val source: QuerySource = QuerySource.BingNews,
    /** Hacker News only: minimum points a story needs. */
    val minPoints: Int = 0,
    /** Feed only: prepended to bare version titles such as "v2.1.3". */
    val titlePrefix: String? = null,
)

@Serializable
data class Article(
    val id: String,
    val title: String,
    val source: String,
    val link: String,
    val publishedAtMillis: Long,
    val categoryId: String,
    val queryId: String,
    val queryLabel: String,
    val fetchedAtMillis: Long,
    val summary: String? = null,
)

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
data class AppSettings(
    val refreshHours: Int = 3,
    val themeMode: ThemeMode = ThemeMode.System,
)

val RefreshIntervalOptions = listOf(1, 3, 6, 12)
