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

class AtomAndChangelogTest {
    private val atom = """
        <feed xmlns="http://www.w3.org/2005/Atom"><title>Release notes from claude-code</title>
        <entry>
          <updated>2026-11-06T17:04:01Z</updated>
          <link rel="alternate" type="text/html" href="https://github.com/anthropics/claude-code/releases/tag/v2.1.319"/>
          <title>v2.1.319</title>
          <content type="html">&lt;h2&gt;What&#39;s changed&lt;/h2&gt;
          &lt;ul&gt;&lt;li&gt;Added support for MCP tasks in the terminal&lt;/li&gt;&lt;li&gt;Fixed &lt;code&gt;/resume&lt;/code&gt; crash&lt;/li&gt;&lt;/ul&gt;</content>
        </entry>
        </feed>
    """.trimIndent()

    @Test
    fun parsesAtomReleaseWithPrefixAndBullets() {
        val item = RssParser.parse(atom, titlePrefix = "Claude Code").single()
        assertEquals("Claude Code v2.1.319", item.title)
        assertEquals("https://github.com/anthropics/claude-code/releases/tag/v2.1.319", item.link)
        assertEquals("Added support for MCP tasks in the terminal • Fixed /resume crash", item.summary)
        assertEquals(DateParser.parseIso8601("2026-11-06T12:04:01-05:00"), item.publishedAtMillis)
    }

    @Test
    fun stripsWordpressFooterAndCapsLength() {
        val desc = "<p>Copilot usage metrics now report code review outcomes for every repository&#8230;</p>" +
            "<p>The post <a href=\"x\">Usage metrics</a> appeared first on <a href=\"y\">The GitHub Blog</a>.</p>"
        assertEquals("Copilot usage metrics now report code review outcomes for every repository…", RssParser.cleanSummary(desc))
        val long = RssParser.cleanSummary("word ".repeat(200))!!
        assertTrue(long.length <= 321 && long.endsWith("…"), long)
    }

    @Test
    fun parsesIsoDates() {
        assertEquals(0L, DateParser.parseIso8601("1970-01-01T00:00:00Z"))
        assertEquals(3_600_000L, DateParser.parseIso8601("1970-01-01T00:00:00-01:00"))
        assertNull(DateParser.parseIso8601("yesterday"))
    }
}

class HackerNewsTest {
    @Test
    fun splitsOrTerms() {
        assertEquals(listOf("Claude Code", "MCP", "multi-agent"), QueryNormalizer.hackerNewsTerms(" \"Claude Code\"  OR MCP OR multi-agent OR MCP "))
    }

    @Test
    fun buildsAlgoliaUrl() {
        val url = QueryNormalizer.buildHackerNewsUrl("Claude Code", showHn = true, minPoints = 5, sinceEpochSeconds = 100)
        assertTrue(url.startsWith("https://hn.algolia.com/api/v1/search_by_date?query=Claude%20Code&tags=show_hn"), url)
        assertTrue("numericFilters=points%3E%3D5%2Ccreated_at_i%3E100" in url, url)
    }

    @Test
    fun parsesHits() {
        val body = """{"hits":[
            {"objectID":"1","title":"Show HN: Paseo &amp; friends","url":"https://www.github.com/getpaseo/paseo","points":89,"num_comments":12,"created_at_i":1000,"story_text":"<p>Run many coding agents in parallel, each in its own worktree.</p>"},
            {"objectID":"2","title":"Ask HN: Agent setups?","url":null,"points":500,"num_comments":300,"created_at_i":2000},
            {"objectID":"3","title":null}
        ],"nbHits":3}"""
        val items = HackerNewsParser.parse(body)
        assertEquals(2, items.size)
        assertEquals("Paseo & friends", items[0].title)
        assertEquals("https://www.github.com/getpaseo/paseo", items[0].link)
        assertEquals(1_000_000L, items[0].publishedAtMillis)
        assertEquals("▲ 89 points · 12 comments · github.com — Run many coding agents in parallel, each in its own worktree.", items[0].summary)
        assertEquals("https://news.ycombinator.com/item?id=2", items[1].link)
        assertEquals("Hacker News", items[1].source)
    }
}

class QueryMigrationTest {
    private class MemoryStore(val map: MutableMap<String, String> = mutableMapOf()) : com.ainewsbroker.platform.KeyValueStore {
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }

    @Test
    fun upgradesOldDefaultsAndKeepsCustomSearches() {
        val store = MemoryStore()
        store.put(
            "queries",
            """[{"id":"paradigms-rag","categoryId":"paradigms","label":"RAG","rawQuery":"RAG"},
                {"id":"custom-1","categoryId":"paradigms","label":"Mine","rawQuery":"\"Paseo\""}]""",
        )
        val repo = NewsRepository(store, io.ktor.client.HttpClient())
        val ids = repo.queries.value.map { it.id }
        assertTrue("paradigms-rag" !in ids)
        assertEquals(DefaultQueries.queries.map { it.id } + "custom-1", ids)
        assertEquals(DefaultQueries.WORKFLOWS, repo.queries.value.last().categoryId)
        assertEquals(DefaultQueries.VERSION.toString(), store.get("queries_version"))
        assertNull(repo.lastRefreshMillis.value)

        // Second launch keeps the user's state instead of re-migrating.
        store.put("queries", store.get("queries")!!.replace("\"enabled\":true", "\"enabled\":false"))
        assertTrue(NewsRepository(store, io.ktor.client.HttpClient()).queries.value.none { it.enabled })
    }
}
