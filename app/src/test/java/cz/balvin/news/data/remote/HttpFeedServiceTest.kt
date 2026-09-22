package cz.balvin.news.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpFeedServiceTest {

    private lateinit var server: MockWebServer
    private val service = HttpFeedService()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }

    @After fun tearDown() { server.shutdown() }

    private fun url() = server.url("/rss").toString()

    @Test
    fun `parses a served feed and captures the validators`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/rss+xml")
                .setHeader("ETag", "W/\"v1\"")
                .setHeader("Last-Modified", "Mon, 21 Sep 2026 19:00:00 GMT")
                .setBody(RSS)
        )

        val result = service.fetch(url())

        assertTrue("got $result", result is FeedService.Result.Success)
        result as FeedService.Result.Success
        assertEquals("Deník", result.feed.title)
        assertEquals(1, result.feed.items.size)
        assertEquals("W/\"v1\"", result.etag)
    }

    @Test
    fun `sends the expected headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(RSS))

        service.fetch(url(), etag = "W/\"v1\"", lastModified = "Mon, 21 Sep 2026 19:00:00 GMT")

        val request = server.takeRequest()
        assertTrue(request.getHeader("User-Agent").orEmpty().isNotBlank())
        assertTrue(request.getHeader("Accept").orEmpty().contains("xml"))
        assertEquals("W/\"v1\"", request.getHeader("If-None-Match"))
        assertEquals("Mon, 21 Sep 2026 19:00:00 GMT", request.getHeader("If-Modified-Since"))
    }

    @Test
    fun `304 is not modified`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))
        assertEquals(FeedService.Result.NotModified, service.fetch(url(), etag = "x"))
    }

    @Test
    fun `error statuses are reported with the code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(FeedService.Result.Failure("HTTP 404"), service.fetch(url()))

        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(FeedService.Result.Failure("HTTP 500"), service.fetch(url()))
    }

    @Test
    fun `an empty body is a failure, not a crash`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        assertTrue(service.fetch(url()) is FeedService.Result.Failure)
    }

    @Test
    fun `malformed xml fails the one source instead of throwing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<rss><channel><item>"))
        assertTrue(service.fetch(url()) is FeedService.Result.Failure)
    }

    @Test
    fun `redirects are followed`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(301).setHeader("Location", server.url("/moved").toString())
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(RSS))

        assertTrue(service.fetch(url()) is FeedService.Result.Success)
    }

    @Test
    fun `a gzipped body is decoded`() = runTest {
        val gzipped = Buffer().also { sink ->
            GzipSink(sink).buffer().use { it.writeUtf8(RSS) }
        }
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Encoding", "gzip").setBody(gzipped)
        )

        val result = service.fetch(url())

        assertTrue("got $result", result is FeedService.Result.Success)
        assertEquals(1, (result as FeedService.Result.Success).feed.items.size)
    }

    @Test
    fun `a non-UTF8 body declared in the prolog survives the round trip`() = runTest {
        val body = Buffer().write(RSS_CP1250.toByteArray(charset("windows-1250")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = service.fetch(url())

        assertTrue("got $result", result is FeedService.Result.Success)
        assertEquals(
            "Šíleně žlutá zpráva",
            (result as FeedService.Result.Success).feed.items.single().title,
        )
    }

    @Test
    fun `an unreachable host is a failure`() = runTest {
        val dead = server.url("/rss").toString()
        server.shutdown()
        val result = service.fetch(dead)
        assertTrue("got $result", result is FeedService.Result.Failure)
        server = MockWebServer().also { it.start() }
    }

    private companion object {
        val RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
              <title>Deník</title><link>https://example.cz</link>
              <item><title>Zpráva</title><link>https://example.cz/1</link>
                <pubDate>Tue, 22 Sep 2026 08:30:00 +0200</pubDate></item>
            </channel></rss>
        """.trimIndent()

        val RSS_CP1250 = """
            <?xml version="1.0" encoding="windows-1250"?>
            <rss version="2.0"><channel>
              <title>Deník</title>
              <item><title>Šíleně žlutá zpráva</title><link>https://e.cz/x</link></item>
            </channel></rss>
        """.trimIndent()
    }
}
