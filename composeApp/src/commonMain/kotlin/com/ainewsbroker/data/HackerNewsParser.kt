package com.ainewsbroker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parses Hacker News search results from the Algolia API into feed items. */
object HackerNewsParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val showHnPrefix = Regex("""^Show HN:\s*""")
    private val hostRegex = Regex("""^https?://(?:www\.)?([^/:?#]+)""")

    @Serializable
    private data class Response(val hits: List<Hit> = emptyList())

    @Serializable
    private data class Hit(
        val objectID: String,
        val title: String? = null,
        val url: String? = null,
        val points: Int? = null,
        @SerialName("num_comments") val numComments: Int? = null,
        @SerialName("created_at_i") val createdAtSeconds: Long? = null,
        @SerialName("story_text") val storyText: String? = null,
    )

    fun parse(body: String): List<RssItem> = json.decodeFromString(Response.serializer(), body).hits.mapNotNull { hit ->
        val title = hit.title?.let(RssParser::decodeEntities)?.replace(showHnPrefix, "")?.trim()?.ifEmpty { null }
            ?: return@mapNotNull null
        val discussion = "https://news.ycombinator.com/item?id=${hit.objectID}"
        val link = hit.url?.takeIf { it.startsWith("http") } ?: discussion
        val stats = buildList {
            hit.points?.let { add("▲ $it points") }
            hit.numComments?.let { add("$it comments") }
            hostRegex.find(link)?.groupValues?.get(1)?.takeIf { it != "news.ycombinator.com" }?.let(::add)
        }.joinToString(" · ")
        val text = hit.storyText?.let(RssParser::cleanSummary)
        RssItem(
            title = title,
            link = link,
            publishedAtMillis = hit.createdAtSeconds?.times(1000),
            source = "Hacker News",
            summary = listOfNotNull(stats.ifEmpty { null }, text).joinToString(" — ").ifEmpty { null },
        )
    }
}
