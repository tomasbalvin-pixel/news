package cz.balvin.news.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cz.balvin.news.data.ai.Digest
import cz.balvin.news.data.ai.DigestService
import cz.balvin.news.data.ai.DigestTopic
import cz.balvin.news.data.local.AppDatabase
import cz.balvin.news.data.local.ArticleListItem
import cz.balvin.news.data.local.Feed
import cz.balvin.news.data.local.FeedDao
import cz.balvin.news.data.remote.FeedService
import cz.balvin.news.data.remote.ParsedFeed
import cz.balvin.news.data.remote.ParsedItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NewsRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var feedDao: FeedDao
    private lateinit var service: FakeFeedService
    private lateinit var digestService: FakeDigestService
    private lateinit var repository: NewsRepository

    private var clock = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        feedDao = database.feedDao()
        service = FakeFeedService()
        digestService = FakeDigestService()
        repository = NewsRepository(
            feedDao = feedDao,
            articleDao = database.articleDao(),
            digestDao = database.digestDao(),
            feedService = service,
            digestService = digestService,
            now = { clock },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `refresh stores new items and counts only what it added`() = runTest {
        val id = feedDao.insert(feed())
        service.stub(FEED_URL, success(item("a"), item("b")))

        val first = repository.refreshAll()
        val second = repository.refreshAll()

        assertEquals(2, first.newArticles)
        assertEquals(0, second.newArticles)
        assertEquals(2, timeline().size)
        assertNull(feedDao.byId(id)!!.lastError)
    }

    @Test
    fun `a re-fetched article keeps the read state it already had`() = runTest {
        feedDao.insert(feed())
        service.stub(FEED_URL, success(item("a")))
        repository.refreshAll()

        val stored = timeline().single().article
        repository.setRead(stored.id, true)
        repository.refreshAll()

        assertTrue(timeline().single().article.isRead)
    }

    @Test
    fun `validators from one response are sent with the next request`() = runTest {
        feedDao.insert(feed())
        service.stub(FEED_URL, success(item("a"), etag = "W/\"v1\"", lastModified = "Mon, 21 Sep 2026 19:00:00 GMT"))
        repository.refreshAll()

        service.stub(FEED_URL, FeedService.Result.NotModified)
        repository.refreshAll()

        val secondRequest = service.requests.last()
        assertEquals("W/\"v1\"", secondRequest.etag)
        assertEquals("Mon, 21 Sep 2026 19:00:00 GMT", secondRequest.lastModified)
    }

    @Test
    fun `a not-modified response keeps the validators and adds nothing`() = runTest {
        val id = feedDao.insert(feed(etag = "W/\"v1\""))
        service.stub(FEED_URL, FeedService.Result.NotModified)

        val outcome = repository.refreshAll()

        assertEquals(0, outcome.newArticles)
        assertTrue(outcome.failures.isEmpty())
        assertEquals("W/\"v1\"", feedDao.byId(id)!!.etag)
    }

    @Test
    fun `a failing source is reported by name and recorded against the feed`() = runTest {
        val id = feedDao.insert(feed())
        service.stub(FEED_URL, FeedService.Result.Failure("HTTP 503"))

        val outcome = repository.refreshAll()

        assertEquals(listOf("Deník"), outcome.failures.map { it.title })
        assertEquals(listOf("HTTP 503"), outcome.failures.map { it.message })
        assertEquals("HTTP 503", feedDao.byId(id)!!.lastError)
    }

    @Test
    fun `one broken source does not stop the others`() = runTest {
        feedDao.insert(feed())
        feedDao.insert(feed(title = "Technika", url = "https://b.cz/rss"))
        service.stub(FEED_URL, FeedService.Result.Failure("boom"))
        service.stub("https://b.cz/rss", success(item("a"), item("b")))

        val outcome = repository.refreshAll()

        assertEquals(2, outcome.newArticles)
        assertEquals(listOf("Deník"), outcome.failures.map { it.title })
    }

    @Test
    fun `a disabled source is not fetched`() = runTest {
        feedDao.insert(feed(enabled = false))
        service.stub(FEED_URL, success(item("a")))

        val outcome = repository.refreshAll()

        assertEquals(0, outcome.newArticles)
        assertTrue(service.requests.isEmpty())
    }

    @Test
    fun `an item without a date sorts as just fetched`() = runTest {
        feedDao.insert(feed())
        clock = 5_000
        service.stub(FEED_URL, success(item("a", publishedAt = null)))

        repository.refreshAll()

        assertEquals(5_000L, timeline().single().article.publishedAt)
    }

    @Test
    fun `adding a source validates it, names it from the channel and stores its items`() = runTest {
        service.stub("https://c.cz/rss", success(item("a"), title = "Kanál C"))

        val result = repository.addFeed("c.cz/rss", title = null, category = "Svět")

        assertTrue(result is NewsRepository.AddFeedResult.Added)
        val added = (result as NewsRepository.AddFeedResult.Added).feed
        assertEquals("Kanál C", added.title)
        assertEquals("https://c.cz/rss", added.url)
        assertEquals(1, timeline().size)
    }

    @Test
    fun `adding a source that does not respond is rejected and stores nothing`() = runTest {
        service.stub("https://c.cz/rss", FeedService.Result.Failure("HTTP 404"))

        val result = repository.addFeed("https://c.cz/rss", title = null, category = "")

        assertTrue(result is NewsRepository.AddFeedResult.Invalid)
        assertEquals(0, feedDao.observeAll().first().size)
    }

    @Test
    fun `a URL with no host is rejected before any request`() = runTest {
        val result = repository.addFeed("nonsense", title = null, category = "")

        assertTrue(result is NewsRepository.AddFeedResult.Invalid)
        assertTrue(service.requests.isEmpty())
    }

    @Test
    fun `adding a source twice is reported as a duplicate`() = runTest {
        service.stub(FEED_URL, success(item("a")))
        repository.addFeed(FEED_URL, title = null, category = "")

        val result = repository.addFeed(FEED_URL, title = null, category = "")

        assertEquals(NewsRepository.AddFeedResult.Duplicate, result)
        assertEquals(1, feedDao.observeAll().first().size)
    }

    @Test
    fun `pruning uses the retention window against the clock`() = runTest {
        feedDao.insert(feed())
        clock = DAY_MILLIS * 40
        service.stub(FEED_URL, success(item("old", publishedAt = DAY_MILLIS), item("new", publishedAt = clock)))
        repository.refreshAll()

        val deleted = repository.prune(retentionDays = 30)

        assertEquals(1, deleted)
        assertEquals(listOf("new"), timeline().map { it.article.guid })
    }

    @Test
    fun `default sources seed once and are not re-added`() = runTest {
        repository.seedDefaultFeedsIfEmpty()
        val afterFirst = feedDao.observeAll().first().size

        feedDao.delete(feedDao.observeAll().first().first())
        repository.seedDefaultFeedsIfEmpty()

        assertTrue(afterFirst > 0)
        assertEquals(afterFirst - 1, feedDao.observeAll().first().size)
    }

    @Test
    fun `a digest is stored against the edition it summarises`() = runTest {
        feedDao.insert(feed())
        service.stub(FEED_URL, success(item("a"), item("b")))
        clock = 5_000
        repository.refreshAll()
        digestService.stub(
            Digest(
                topics = listOf(DigestTopic("Rozpočet", "Vláda ho schválila.", listOf("Deník"))),
                generatedAt = 0,
            )
        )

        val result = repository.refreshDigest(
            editionAt = 1_000, apiKey = "k", perSourceLimit = 0, editionSize = 0,
        )

        assertTrue(result is DigestService.Result.Success)
        val stored = repository.observeDigest(1_000).first()
        assertEquals(listOf("Rozpočet"), stored?.topics?.map { it.title })
        assertEquals(listOf("Deník"), stored?.topics?.single()?.sources)
    }

    @Test
    fun `regenerating replaces the previous digest instead of stacking`() = runTest {
        feedDao.insert(feed())
        service.stub(FEED_URL, success(item("a")))
        repository.refreshAll()

        digestService.stub(Digest(listOf(DigestTopic("První", "x", emptyList())), 0))
        repository.refreshDigest(1_000, "k", 0, 0)
        digestService.stub(Digest(listOf(DigestTopic("Druhé", "y", emptyList())), 0))
        repository.refreshDigest(1_000, "k", 0, 0)

        assertEquals(
            listOf("Druhé"),
            repository.observeDigest(1_000).first()?.topics?.map { it.title },
        )
    }

    @Test
    fun `a failed digest leaves the previous one in place`() = runTest {
        feedDao.insert(feed())
        service.stub(FEED_URL, success(item("a")))
        repository.refreshAll()
        digestService.stub(Digest(listOf(DigestTopic("Původní", "x", emptyList())), 0))
        repository.refreshDigest(1_000, "k", 0, 0)

        digestService.fail("HTTP 401")
        val result = repository.refreshDigest(1_000, "k", 0, 0)

        assertTrue(result is DigestService.Result.Failure)
        assertEquals(
            listOf("Původní"),
            repository.observeDigest(1_000).first()?.topics?.map { it.title },
        )
    }

    @Test
    fun `without a key nothing is summarised and nothing is stored`() = runTest {
        feedDao.insert(feed())
        service.stub(FEED_URL, success(item("a")))
        repository.refreshAll()

        val result = repository.refreshDigest(1_000, apiKey = "", perSourceLimit = 0, editionSize = 0)

        assertEquals(DigestService.Result.NoApiKey, result)
        assertNull(repository.observeDigest(1_000).first())
    }

    private suspend fun timeline() = repository.observeTimeline(TimelineFilter()).first()

    private fun feed(
        title: String = "Deník",
        url: String = FEED_URL,
        enabled: Boolean = true,
        etag: String? = null,
    ) = Feed(title = title, url = url, category = "Domácí", enabled = enabled, etag = etag)

    private fun item(guid: String, publishedAt: Long? = 1_000) = ParsedItem(
        guid = guid,
        title = "Titulek $guid",
        link = "https://example.cz/$guid",
        summary = "Perex $guid",
        content = null,
        imageUrl = null,
        author = null,
        publishedAt = publishedAt,
    )

    private fun success(
        vararg items: ParsedItem,
        title: String = "Deník",
        etag: String? = null,
        lastModified: String? = null,
    ) = FeedService.Result.Success(
        feed = ParsedFeed(title = title, siteUrl = null, items = items.toList()),
        etag = etag,
        lastModified = lastModified,
    )

    private class FakeDigestService : DigestService {

        private var result: DigestService.Result = DigestService.Result.Failure("no stub")

        fun stub(digest: Digest) {
            result = DigestService.Result.Success(digest)
        }

        fun fail(message: String) {
            result = DigestService.Result.Failure(message)
        }

        override suspend fun summarise(
            articles: List<ArticleListItem>,
            apiKey: String,
        ): DigestService.Result =
            if (apiKey.isBlank()) DigestService.Result.NoApiKey else result
    }

    private class FakeFeedService : FeedService {

        data class Request(val url: String, val etag: String?, val lastModified: String?)

        private val responses = mutableMapOf<String, FeedService.Result>()
        val requests = mutableListOf<Request>()

        fun stub(url: String, result: FeedService.Result) {
            responses[url] = result
        }

        override suspend fun fetch(
            url: String,
            etag: String?,
            lastModified: String?,
        ): FeedService.Result {
            requests += Request(url, etag, lastModified)
            return responses[url] ?: FeedService.Result.Failure("no stub for $url")
        }
    }

    private companion object {
        const val FEED_URL = "https://a.cz/rss"
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
