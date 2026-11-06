package com.ainewsbroker.data

data class RssItem(
    val title: String,
    val link: String,
    val publishedAtMillis: Long?,
    val source: String?,
)

/** Minimal parser for Google News RSS. Regex-based so it runs identically on Android and Wasm. */
object RssParser {
    private val itemRegex = Regex("""<item>([\s\S]*?)</item>""")
    private val cdataRegex = Regex("""^<!\[CDATA\[([\s\S]*?)\]\]>$""")
    private val entityRegex = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);""")

    fun parse(xml: String): List<RssItem> = itemRegex.findAll(xml).mapNotNull { match ->
        val block = match.groupValues[1]
        val rawTitle = tag(block, "title") ?: return@mapNotNull null
        val link = tag(block, "link") ?: return@mapNotNull null
        val source = tag(block, "source")
        RssItem(
            title = stripSourceSuffix(rawTitle, source),
            link = link,
            publishedAtMillis = tag(block, "pubDate")?.let(DateParser::parseRfc822),
            source = source,
        )
    }.toList()

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
