package com.ainewsbroker.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryNormalizerTest {
    @Test
    fun keepsBooleanSyntaxAndCollapsesWhitespace() {
        val q = QueryNormalizer.normalize("  \"Claude\"   AND (\"Anthropic\" OR \"API\") ")
        assertEquals("\"Claude\" AND (\"Anthropic\" OR \"API\")", q)
    }

    @Test
    fun buildsEncodedBingUrl() {
        val url = QueryNormalizer.buildSearchUrl("https://www.bing.com", "\"a b\" OR c")
        assertTrue(url.startsWith("https://www.bing.com/news/search?q=%22a%20b%22%20OR%20c&format=rss"), url)
        assertTrue("qft=interval%3d%228%22" in url, url)
    }
}

class RssParserTest {
    private val xml = """
        <?xml version="1.0" encoding="utf-8" ?>
        <rss version="2.0" xmlns:News="https://www.bing.com/news/search?q=x&amp;format=rss"><channel><title>Search</title>
        <item>
          <title>Claude adds context caching &amp; more</title>
          <link>http://www.bing.com/news/apiclick.aspx?ref=FexRss&amp;aid=&amp;tid=1&amp;url=https%3a%2f%2fwww.theverge.com%2fai%2f123%3fa%3d1%26b%3d2&amp;c=9&amp;mkt=en-us</link>
          <description>Anthropic shipped &lt;b&gt;context caching&lt;/b&gt; for   Claude today, cutting costs ...</description>
          <pubDate>Thu, 05 Nov 2026 18:30:00 GMT</pubDate>
          <News:Source>The Verge</News:Source>
        </item>
        <item>
          <title><![CDATA[Copilot agents “ship” today]]></title>
          <link>https://example.com/copilot</link>
          <description>short</description>
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
        assertEquals("https://www.theverge.com/ai/123?a=1&b=2", first.link)
        assertEquals("Anthropic shipped context caching for Claude today, cutting costs…", first.summary)
        assertEquals(1_793_903_400_000L, first.publishedAtMillis)

        val second = items[1]
        assertEquals("Copilot agents “ship” today", second.title)
        assertEquals("https://example.com/copilot", second.link)
        assertNull(second.source)
        assertNull(second.summary)
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
