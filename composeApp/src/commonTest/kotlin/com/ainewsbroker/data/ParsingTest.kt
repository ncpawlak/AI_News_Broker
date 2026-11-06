package com.ainewsbroker.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryNormalizerTest {
    @Test
    fun dropsAndKeywordAndAppendsWindow() {
        val q = QueryNormalizer.normalize("\"Claude\" AND (\"Anthropic\" OR \"API\")")
        assertEquals("\"Claude\" (\"Anthropic\" OR \"API\") when:7d", q)
    }

    @Test
    fun keepsExistingWhenClause() {
        assertEquals("foo when:1d", QueryNormalizer.normalize("foo   when:1d"))
    }

    @Test
    fun buildsEncodedUrl() {
        val url = QueryNormalizer.buildSearchUrl("https://news.google.com", "\"a b\" OR c")
        assertTrue(url.startsWith("https://news.google.com/rss/search?q="))
        assertTrue("%22a%20b%22%20OR%20c%20when%3A7d" in url, url)
        assertTrue(url.endsWith("&hl=en-US&gl=US&ceid=US:en"))
    }
}

class RssParserTest {
    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Search</title>
        <item>
          <title>Claude adds context caching &amp; more - The Verge</title>
          <link>https://news.google.com/rss/articles/abc?oc=5</link>
          <guid isPermaLink="false">abc</guid>
          <pubDate>Thu, 05 Nov 2026 18:30:00 GMT</pubDate>
          <description>&lt;a href="x"&gt;ignored&lt;/a&gt;</description>
          <source url="https://www.theverge.com">The Verge</source>
        </item>
        <item>
          <title><![CDATA[Copilot agents “ship” today]]></title>
          <link>https://news.google.com/rss/articles/def</link>
          <pubDate>Wed, 04 Nov 2026 09:00:00 +0100</pubDate>
        </item>
        <item><title>No link here</title></item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun parsesItems() {
        val items = RssParser.parse(xml)
        assertEquals(2, items.size)

        val first = items[0]
        assertEquals("Claude adds context caching & more", first.title)
        assertEquals("The Verge", first.source)
        assertEquals("https://news.google.com/rss/articles/abc?oc=5", first.link)
        assertEquals(1_793_903_400_000L, first.publishedAtMillis)

        val second = items[1]
        assertEquals("Copilot agents “ship” today", second.title)
        assertNull(second.source)
        assertEquals(1_793_779_200_000L, second.publishedAtMillis)
    }

    @Test
    fun decodesNumericEntities() {
        assertEquals("It’s 🚀", RssParser.decodeEntities("It&#8217;s &#x1F680;"))
    }
}

class DateParserTest {
    @Test
    fun parsesEpochAndLeapYear() {
        assertEquals(0L, DateParser.parseRfc822("Thu, 01 Jan 1970 00:00:00 GMT"))
        assertEquals(951_782_400_000L, DateParser.parseRfc822("Tue, 29 Feb 2000 00:00:00 GMT"))
        assertNull(DateParser.parseRfc822("not a date"))
    }
}
