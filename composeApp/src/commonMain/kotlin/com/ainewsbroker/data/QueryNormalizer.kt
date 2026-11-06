package com.ainewsbroker.data

import io.ktor.http.encodeURLParameter

/** Builds request URLs for each [com.ainewsbroker.model.QuerySource]. */
object QueryNormalizer {
    const val BING_BASE = "https://www.bing.com"
    const val HN_BASE = "https://hn.algolia.com"

    private val whitespace = Regex("""\s+""")
    private val orSplit = Regex("""\s+OR\s+""")

    fun normalize(raw: String): String = raw.replace(whitespace, " ").trim()

    /**
     * Bing News RSS search. Bing honors `AND`, `OR`, quotes and parentheses directly.
     * qft=interval="8" limits results to the past week; count=50 asks for the largest page Bing serves.
     */
    fun buildSearchUrl(baseUrl: String, raw: String): String =
        "$baseUrl/news/search?q=${normalize(raw).encodeURLParameter()}" +
            "&format=rss&qft=interval%3d%228%22&count=50&setlang=en-US&mkt=en-US"

    /** Splits `a OR "b c" OR d` into title terms; the HN search API has no OR operator, so each term is its own request. */
    fun hackerNewsTerms(raw: String): List<String> =
        normalize(raw).split(orSplit).map { it.trim().trim('"').trim() }.filter { it.isNotEmpty() }.distinct()

    /** Hacker News (Algolia) title search for stories newer than [sinceEpochSeconds] with at least [minPoints]. */
    fun buildHackerNewsUrl(term: String, showHn: Boolean, minPoints: Int, sinceEpochSeconds: Long): String =
        "$HN_BASE/api/v1/search_by_date?query=${term.encodeURLParameter()}" +
            "&tags=${if (showHn) "show_hn" else "story"}" +
            "&restrictSearchableAttributes=title&typoTolerance=false" +
            "&numericFilters=${"points>=$minPoints,created_at_i>$sinceEpochSeconds".encodeURLParameter()}" +
            "&hitsPerPage=30"
}
