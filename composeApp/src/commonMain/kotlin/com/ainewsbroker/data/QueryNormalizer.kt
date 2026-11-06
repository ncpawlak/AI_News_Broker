package com.ainewsbroker.data

import io.ktor.http.encodeURLParameter

/**
 * Builds Bing News RSS search URLs from Google-Alerts-style queries.
 * Bing honors `AND`, `OR`, quotes and parentheses directly, so queries only need whitespace cleanup.
 */
object QueryNormalizer {
    private val whitespace = Regex("""\s+""")

    fun normalize(raw: String): String = raw.replace(whitespace, " ").trim()

    // qft=interval="8" limits results to the past week; count=50 asks for the largest page Bing serves.
    fun buildSearchUrl(baseUrl: String, raw: String): String =
        "$baseUrl/news/search?q=${normalize(raw).encodeURLParameter()}" +
            "&format=rss&qft=interval%3d%228%22&count=50&setlang=en-US&mkt=en-US"
}
