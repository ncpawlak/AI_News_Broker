package com.ainewsbroker.data

data class RssItem(
    val title: String,
    val link: String,
    val publishedAtMillis: Long?,
    val source: String?,
    val summary: String? = null,
)

/**
 * Minimal parser for RSS 2.0 (Bing News, blog changelogs) and Atom (GitHub releases).
 * Regex-based so it runs identically on Android and Wasm.
 */
object RssParser {
    private const val MAX_SUMMARY_CHARS = 320

    private val itemRegex = Regex("""<item>([\s\S]*?)</item>""")
    private val entryRegex = Regex("""<entry>([\s\S]*?)</entry>""")
    private val atomLinkRegex = Regex("""<link\b[^>]*\bhref="([^"]+)"[^>]*/?>""")
    private val cdataRegex = Regex("""^<!\[CDATA\[([\s\S]*?)\]\]>$""")
    private val entityRegex = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);""")
    private val listItemRegex = Regex("""<li\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val headingRegex = Regex("""<h[1-6]\b[^>]*>[\s\S]*?</h[1-6]>""", RegexOption.IGNORE_CASE)
    private val htmlTagRegex = Regex("""<[^>]+>""")
    private val whitespaceRegex = Regex("""\s+""")
    private val trailingEllipsisRegex = Regex("""\s*(\.\.\.|…)$""")
    private val wordpressFooterRegex = Regex("""\s*The post .{0,300}? appeared first on .{0,100}$""")
    private val bingUrlParam = Regex("""[?&]url=([^&]+)""")
    private val versionTitleRegex = Regex("""^v?\d+(\.\d+)+\S*$""")

    /** Parses RSS `<item>`s, or Atom `<entry>`s if the document has no items. */
    fun parse(xml: String, titlePrefix: String? = null): List<RssItem> {
        val rss = itemRegex.findAll(xml).mapNotNull { parseRssItem(it.groupValues[1]) }.toList()
        val items = rss.ifEmpty { entryRegex.findAll(xml).mapNotNull { parseAtomEntry(it.groupValues[1]) }.toList() }
        if (titlePrefix.isNullOrBlank()) return items
        return items.map { if (versionTitleRegex.matches(it.title)) it.copy(title = "$titlePrefix ${it.title}") else it }
    }

    private fun parseRssItem(block: String): RssItem? {
        val rawTitle = tag(block, "title") ?: return null
        val link = tag(block, "link") ?: return null
        val source = tag(block, "News:Source") ?: tag(block, "source")
        return RssItem(
            title = stripSourceSuffix(rawTitle, source),
            link = publisherUrl(link),
            publishedAtMillis = tag(block, "pubDate")?.let(DateParser::parseRfc822),
            source = source,
            summary = tag(block, "description")?.let(::cleanSummary),
        )
    }

    private fun parseAtomEntry(block: String): RssItem? {
        val title = tag(block, "title") ?: return null
        val link = atomLinkRegex.find(block)?.groupValues?.get(1)?.let(::decodeEntities) ?: return null
        val date = tag(block, "updated") ?: tag(block, "published")
        return RssItem(
            title = title,
            link = link,
            publishedAtMillis = date?.let(DateParser::parseIso8601),
            source = null,
            summary = (tag(block, "content") ?: tag(block, "summary"))?.let(::cleanSummary),
        )
    }

    /** Bing wraps links in an apiclick redirect; the real article URL is in its `url` parameter. */
    fun publisherUrl(link: String): String {
        if (!link.contains("bing.com/news/apiclick")) return link
        val encoded = bingUrlParam.find(link)?.groupValues?.get(1) ?: return link
        val decoded = percentDecode(encoded)
        return if (decoded.startsWith("http://") || decoded.startsWith("https://")) decoded else link
    }

    /** Turns HTML/plain text into a short single-paragraph summary; release-note bullets become "•". */
    fun cleanSummary(text: String): String? {
        val html = text.replace(headingRegex, " ").replace(listItemRegex, " • ")
        var plain = decodeEntities(html.replace(htmlTagRegex, " ")).replace(whitespaceRegex, " ").trim()
        plain = plain.replace(wordpressFooterRegex, "").removePrefix("•").trim()
        if (plain.length < 20) return null
        if (plain.length > MAX_SUMMARY_CHARS) {
            val cut = plain.take(MAX_SUMMARY_CHARS)
            plain = cut.substring(0, cut.lastIndexOf(' ').takeIf { it > MAX_SUMMARY_CHARS / 2 } ?: cut.length).trimEnd(' ', ',', '•') + "…"
        }
        return plain.replace(trailingEllipsisRegex, "…")
    }

    private fun percentDecode(s: String): String {
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    bytes.add(v.toByte()); i += 3; continue
                }
            }
            if (c == '+') bytes.add(' '.code.toByte()) else c.toString().encodeToByteArray().forEach(bytes::add)
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun tag(block: String, name: String): String? {
        val m = Regex("""<$name(?:\s[^>]*)?>([\s\S]*?)</$name>""").find(block) ?: return null
        val inner = m.groupValues[1].trim()
        val content = cdataRegex.find(inner)?.groupValues?.get(1) ?: inner
        return decodeEntities(content).trim().ifEmpty { null }
    }

    /** Google News titles look like "Headline - Source"; drop the redundant suffix. */
    private fun stripSourceSuffix(title: String, source: String?): String {
        if (source == null) return title
        val suffix = " - $source"
        return if (title.endsWith(suffix)) title.removeSuffix(suffix).trim() else title
    }

    fun decodeEntities(text: String): String = entityRegex.replace(text) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            else -> {
                val code = if (e.startsWith("#x")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                code?.let { codePointToString(it) } ?: m.value
            }
        }
    }

    private fun codePointToString(cp: Int): String = if (cp < 0x10000) {
        cp.toChar().toString()
    } else {
        val v = cp - 0x10000
        charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }
}

object DateParser {
    private val rfc822 = Regex("""(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([A-Za-z]+|[+-]\d{4})?""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Parses e.g. "Thu, 05 Nov 2026 18:30:00 GMT" into epoch millis (UTC). */
    fun parseRfc822(text: String): Long? {
        val m = rfc822.find(text) ?: return null
        val (d, mon, y, h, min) = m.destructured
        val sec = m.groupValues[6].ifEmpty { "0" }.toInt()
        val month = months.indexOf(mon.lowercase()) + 1
        if (month == 0) return null
        val days = daysFromCivil(y.toInt(), month, d.toInt())
        var seconds = days * 86_400L + h.toInt() * 3600L + min.toInt() * 60L + sec
        val zone = m.groupValues[7]
        if (zone.length == 5 && (zone[0] == '+' || zone[0] == '-')) {
            val offset = zone.substring(1, 3).toInt() * 3600 + zone.substring(3, 5).toInt() * 60
            seconds -= if (zone[0] == '+') offset else -offset
        }
        return seconds * 1000
    }

    private val iso8601 = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?""")

    /** Parses e.g. "2026-11-06T17:04:01Z" or "2026-11-06T12:04:01-05:00" into epoch millis (UTC). */
    fun parseIso8601(text: String): Long? {
        val m = iso8601.find(text) ?: return null
        val (y, mo, d, h, min) = m.destructured
        val sec = m.groupValues[6].ifEmpty { "0" }.toInt()
        var seconds = daysFromCivil(y.toInt(), mo.toInt(), d.toInt()) * 86_400L + h.toInt() * 3600L + min.toInt() * 60L + sec
        val zone = m.groupValues[7].replace(":", "")
        if (zone.length == 5) {
            val offset = zone.substring(1, 3).toInt() * 3600 + zone.substring(3, 5).toInt() * 60
            seconds -= if (zone[0] == '+') offset else -offset
        }
        return seconds * 1000
    }

    // Howard Hinnant's days_from_civil algorithm.
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }
}
