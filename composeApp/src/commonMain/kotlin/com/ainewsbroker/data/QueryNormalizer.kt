package com.ainewsbroker.data

import io.ktor.http.encodeURLParameter

/**
 * Converts Google-Alerts-style queries into Google News search syntax.
 * Google News treats whitespace as AND and does not reliably honor the literal `AND` keyword,
 * while `OR`, quotes and parentheses are supported.
 */
object QueryNormalizer {
    private val andKeyword = Regex("""\s+AND\s+""")
    private val whitespace = Regex("""\s+""")

    fun normalize(raw: String, window: String = "7d"): String {
        var q = raw.replace(andKeyword, " ").replace(whitespace, " ").trim()
        if (q.isNotEmpty() && !q.contains("when:")) q = "$q when:$window"
        return q
    }

    fun buildSearchUrl(baseUrl: String, raw: String): String =
        "$baseUrl/rss/search?q=${normalize(raw).encodeURLParameter()}&hl=en-US&gl=US&ceid=US:en"
}
