package cz.balvin.news.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {

    @Test
    fun `parses RSS 2 channel and items`() {
        val feed = RssParser.parse(RSS_2.toByteArray())

        assertEquals("Denní zprávy", feed.title)
        assertEquals("https://example.cz", feed.siteUrl)
        assertEquals(2, feed.items.size)

        val first = feed.items[0]
        assertEquals("https://example.cz/a/1", first.guid)
        assertEquals("Vláda schválila rozpočet", first.title)
        assertEquals("https://example.cz/a/1", first.link)
        assertEquals("Krátký popis s diakritikou.", first.summary)
        assertEquals("https://cdn.example.cz/1.jpg", first.imageUrl)
        assertEquals("Jana Nováková", first.author)
        assertNotNull(first.publishedAt)
    }

    @Test
    fun `prefers content encoded over description for the article body`() {
        val feed = RssParser.parse(RSS_2.toByteArray())
        val body = feed.items[0].content

        assertNotNull(body)
        assertTrue(body!!.contains("<p>"))
    }

    @Test
    fun `falls back to the first inline image when no enclosure is present`() {
        val feed = RssParser.parse(RSS_2.toByteArray())

        assertEquals("https://cdn.example.cz/inline.png", feed.items[1].imageUrl)
    }

    @Test
    fun `parses Atom entries`() {
        val feed = RssParser.parse(ATOM.toByteArray())

        assertEquals("Example Atom", feed.title)
        assertEquals("https://example.org/", feed.siteUrl)
        assertEquals(1, feed.items.size)

        val item = feed.items.single()
        assertEquals("tag:example.org,2026:1", item.guid)
        assertEquals("Atom headline", item.title)
        assertEquals("https://example.org/post/1", item.link)
        assertEquals("Alex Doe", item.author)
        assertNotNull(item.publishedAt)
    }

    @Test
    fun `parses RSS 1 RDF items that sit outside the channel element`() {
        val feed = RssParser.parse(RDF.toByteArray())

        assertEquals("RDF feed", feed.title)
        assertEquals(1, feed.items.size)
        assertEquals("https://example.net/story", feed.items.single().link)
    }

    @Test
    fun `strips markup and entities from the summary`() {
        val feed = RssParser.parse(RSS_2.toByteArray())

        assertTrue(feed.items[1].summary.none { it == '<' })
        assertTrue(feed.items[1].summary.contains("„"))
    }

    @Test
    fun `returns no items for a document that is not a feed`() {
        val feed = RssParser.parse("<html><body>nope</body></html>".toByteArray())

        assertEquals(0, feed.items.size)
        assertNull(feed.title)
    }

    @Test
    fun `honours a non-UTF8 encoding declared in the prolog`() {
        val xml = """
            <?xml version="1.0" encoding="windows-1250"?>
            <rss version="2.0"><channel>
              <title>Příliš žluťoučký kůň</title>
              <link>https://example.cz</link>
              <item><title>Šíleně žlutá zpráva</title><link>https://example.cz/x</link></item>
            </channel></rss>
        """.trimIndent()

        val feed = RssParser.parse(xml.toByteArray(charset("windows-1250")))

        assertEquals("Příliš žluťoučký kůň", feed.title)
        assertEquals("Šíleně žlutá zpráva", feed.items.single().title)
    }

    @Test
    fun `takes the body from an Atom content element`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>T</title>
              <entry>
                <title>E</title>
                <id>urn:1</id>
                <link href="https://example.org/e"/>
                <summary>Short</summary>
                <content type="html">&lt;p&gt;Long body&lt;/p&gt;</content>
              </entry>
            </feed>
        """.trimIndent()

        val item = RssParser.parse(xml.toByteArray()).items.single()

        assertEquals("<p>Long body</p>", item.content)
        assertEquals("Short", item.summary)
        assertEquals("https://example.org/e", item.link)
    }

    @Test
    fun `reads media namespace images`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel>
              <item>
                <title>A</title><link>https://e.cz/a</link>
                <media:content url="https://cdn.e.cz/big" medium="image"/>
              </item>
              <item>
                <title>B</title><link>https://e.cz/b</link>
                <media:thumbnail url="https://cdn.e.cz/thumb"/>
              </item>
            </channel></rss>
        """.trimIndent()

        val items = RssParser.parse(xml.toByteArray()).items

        assertEquals("https://cdn.e.cz/big", items[0].imageUrl)
        assertEquals("https://cdn.e.cz/thumb", items[1].imageUrl)
    }

    @Test
    fun `leaves the date null when the item carries none`() {
        val xml = """
            <rss version="2.0"><channel>
              <item><title>Undated</title><link>https://e.cz/u</link></item>
            </channel></rss>
        """.trimIndent()

        assertNull(RssParser.parse(xml.toByteArray()).items.single().publishedAt)
    }

    @Test
    fun `unwraps CDATA titles and decodes their entities`() {
        val xml = """
            <rss version="2.0"><channel>
              <item>
                <title><![CDATA[Ministr &amp; premiér <b>jednali</b>]]></title>
                <link>https://e.cz/c</link>
              </item>
            </channel></rss>
        """.trimIndent()

        assertEquals(
            "Ministr & premiér jednali",
            RssParser.parse(xml.toByteArray()).items.single().title,
        )
    }

    @Test
    fun `falls back to the guid when the item has no link`() {
        val xml = """
            <rss version="2.0"><channel>
              <item>
                <title>No link</title>
                <guid isPermaLink="true">https://e.cz/only-guid</guid>
              </item>
            </channel></rss>
        """.trimIndent()

        val item = RssParser.parse(xml.toByteArray()).items.single()

        assertEquals("https://e.cz/only-guid", item.link)
        assertEquals("https://e.cz/only-guid", item.guid)
    }

    @Test
    fun `refuses external entities instead of resolving them`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss version="2.0"><channel>
              <item><title>&xxe;</title><link>https://e.cz/x</link></item>
            </channel></rss>
        """.trimIndent()

        // Either the document is rejected outright or the entity stays unexpanded;
        // what must never happen is the file's contents reaching an article.
        val titles = runCatching { RssParser.parse(xml.toByteArray()) }
            .map { feed -> feed.items.map(ParsedItem::title) }
            .getOrElse { emptyList() }

        assertTrue(titles.none { it.contains("root:") })
    }

    private companion object {
        val RSS_2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"
                 xmlns:content="http://purl.org/rss/1.0/modules/content/"
                 xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>Denní zprávy</title>
                <link>https://example.cz</link>
                <item>
                  <title>Vláda schválila rozpočet</title>
                  <link>https://example.cz/a/1</link>
                  <guid isPermaLink="true">https://example.cz/a/1</guid>
                  <description>Krátký popis s diakritikou.</description>
                  <content:encoded><![CDATA[<p>Plný text článku.</p>]]></content:encoded>
                  <enclosure url="https://cdn.example.cz/1.jpg" type="image/jpeg" length="1234"/>
                  <dc:creator>Jana Nováková</dc:creator>
                  <pubDate>Tue, 22 Sep 2026 08:30:00 +0200</pubDate>
                </item>
                <item>
                  <title>Druhá zpráva</title>
                  <link>https://example.cz/a/2</link>
                  <description>&lt;p&gt;Text s &amp;bdquo;uvozovkami&amp;ldquo; a &lt;img src="https://cdn.example.cz/inline.png"/&gt;&lt;/p&gt;</description>
                  <pubDate>Mon, 21 Sep 2026 19:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val ATOM = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Example Atom</title>
              <link rel="alternate" href="https://example.org/"/>
              <entry>
                <title>Atom headline</title>
                <link rel="alternate" href="https://example.org/post/1"/>
                <id>tag:example.org,2026:1</id>
                <published>2026-09-20T10:15:00Z</published>
                <summary>Short summary.</summary>
                <author><name>Alex Doe</name></author>
              </entry>
            </feed>
        """.trimIndent()

        val RDF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns="http://purl.org/rss/1.0/">
              <channel rdf:about="https://example.net">
                <title>RDF feed</title>
                <link>https://example.net</link>
              </channel>
              <item rdf:about="https://example.net/story">
                <title>Story</title>
                <link>https://example.net/story</link>
                <description>Desc</description>
              </item>
            </rdf:RDF>
        """.trimIndent()
    }
}
