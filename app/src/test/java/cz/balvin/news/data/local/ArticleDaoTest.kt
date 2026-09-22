package cz.balvin.news.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One query backs every timeline variant, so each filter is exercised against a
 * real SQLite database rather than trusted to read correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArticleDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var feedDao: FeedDao
    private lateinit var articleDao: ArticleDao

    private var domesticFeedId = 0L
    private var techFeedId = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        feedDao = database.feedDao()
        articleDao = database.articleDao()

        domesticFeedId = feedDao.insert(
            Feed(title = "Deník", url = "https://a.cz/rss", category = "Domácí")
        )
        techFeedId = feedDao.insert(
            Feed(title = "Technika", url = "https://b.cz/rss", category = "Technologie")
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `timeline returns newest first with the feed title joined in`() = runTest {
        articleDao.insertAll(
            listOf(
                article(domesticFeedId, "older", publishedAt = 1_000),
                article(domesticFeedId, "newer", publishedAt = 9_000),
            )
        )

        val timeline = timeline()

        assertEquals(listOf("newer", "older"), timeline.map { it.article.guid })
        assertEquals("Deník", timeline.first().feedTitle)
        assertEquals("Domácí", timeline.first().feedCategory)
    }

    @Test
    fun `unread and bookmarked filters narrow the timeline`() = runTest {
        articleDao.insertAll(
            listOf(
                article(domesticFeedId, "read", isRead = true),
                article(domesticFeedId, "unread"),
                article(domesticFeedId, "saved", isRead = true, isBookmarked = true),
            )
        )

        assertEquals(
            setOf("unread"),
            timeline(onlyUnread = true).map { it.article.guid }.toSet(),
        )
        assertEquals(
            setOf("saved"),
            timeline(onlyBookmarked = true).map { it.article.guid }.toSet(),
        )
    }

    @Test
    fun `category and feed filters select one source or one rubric`() = runTest {
        articleDao.insertAll(
            listOf(
                article(domesticFeedId, "domestic"),
                article(techFeedId, "tech"),
            )
        )

        assertEquals(
            listOf("tech"),
            timeline(category = "Technologie").map { it.article.guid },
        )
        assertEquals(
            listOf("domestic"),
            timeline(feedId = domesticFeedId).map { it.article.guid },
        )
    }

    @Test
    fun `search matches the title and the summary`() = runTest {
        articleDao.insertAll(
            listOf(
                article(domesticFeedId, "a", title = "Rozpočet schválen", summary = "nic"),
                article(domesticFeedId, "b", title = "Počasí", summary = "Mluví se o rozpočtu"),
                article(domesticFeedId, "c", title = "Sport", summary = "nic"),
            )
        )

        assertEquals(setOf("a"), timeline(query = "Rozpočet").map { it.article.guid }.toSet())
        assertEquals(setOf("b"), timeline(query = "rozpočtu").map { it.article.guid }.toSet())
    }

    @Test
    fun `an empty query disables the search clause instead of matching nothing`() = runTest {
        articleDao.insertAll(listOf(article(domesticFeedId, "a")))

        assertEquals(1, timeline(query = "").size)
    }

    @Test
    fun `the limit caps how much of the timeline is loaded`() = runTest {
        articleDao.insertAll((1..10).map { article(domesticFeedId, "g$it", publishedAt = it.toLong()) })

        assertEquals(3, timeline(limit = 3).size)
    }

    @Test
    fun `re-inserting the same guid keeps the stored read state`() = runTest {
        val ids = articleDao.insertAll(listOf(article(domesticFeedId, "dup")))
        articleDao.setRead(ids.single(), true)
        articleDao.setBookmarked(ids.single(), true)

        val second = articleDao.insertAll(listOf(article(domesticFeedId, "dup", title = "Rewritten")))

        assertEquals(listOf(-1L), second)
        val stored = timeline().single().article
        assertTrue(stored.isRead)
        assertTrue(stored.isBookmarked)
    }

    @Test
    fun `the same guid from a different feed is a different article`() = runTest {
        val inserted = articleDao.insertAll(
            listOf(
                article(domesticFeedId, "shared"),
                article(techFeedId, "shared"),
            )
        )

        assertTrue(inserted.none { it == -1L })
        assertEquals(2, timeline().size)
    }

    @Test
    fun `pruning drops old articles but spares bookmarked ones`() = runTest {
        articleDao.insertAll(
            listOf(
                article(domesticFeedId, "old", publishedAt = 100),
                article(domesticFeedId, "old-saved", publishedAt = 100, isBookmarked = true),
                article(domesticFeedId, "fresh", publishedAt = 10_000),
            )
        )

        val deleted = articleDao.deleteOlderThan(before = 1_000)

        assertEquals(1, deleted)
        assertEquals(
            setOf("old-saved", "fresh"),
            timeline().map { it.article.guid }.toSet(),
        )
    }

    @Test
    fun `deleting a feed takes its articles with it`() = runTest {
        articleDao.insertAll(
            listOf(
                article(domesticFeedId, "a"),
                article(techFeedId, "b"),
            )
        )

        feedDao.delete(feedDao.byId(domesticFeedId)!!)

        assertEquals(listOf("b"), timeline().map { it.article.guid })
    }

    @Test
    fun `the unread count tracks reads`() = runTest {
        val ids = articleDao.insertAll(
            listOf(article(domesticFeedId, "a"), article(domesticFeedId, "b"))
        )

        assertEquals(2, articleDao.observeUnreadCount().first())

        articleDao.setRead(ids.first(), true)
        assertEquals(1, articleDao.observeUnreadCount().first())

        articleDao.markAllRead()
        assertEquals(0, articleDao.observeUnreadCount().first())
    }

    @Test
    fun `the per-source cap keeps the newest few from each feed`() = runTest {
        articleDao.insertAll(
            (1..5).map { article(domesticFeedId, "d$it", publishedAt = it.toLong()) } +
                (1..5).map { article(techFeedId, "t$it", publishedAt = it.toLong()) }
        )

        val capped = timeline(perSourceLimit = 2)

        assertEquals(4, capped.size)
        assertEquals(
            setOf("d5", "d4", "t5", "t4"),
            capped.map { it.article.guid }.toSet(),
        )
    }

    @Test
    fun `a cap of zero lifts the limit`() = runTest {
        articleDao.insertAll((1..5).map { article(domesticFeedId, "d$it", publishedAt = it.toLong()) })

        assertEquals(5, timeline(perSourceLimit = 0).size)
    }

    @Test
    fun `the cap counts within the active filter, not the whole feed`() = runTest {
        // The three newest are read; an unread view must still show the older ones
        // rather than spending the cap on articles it is not displaying.
        articleDao.insertAll(
            listOf(
                article(domesticFeedId, "new1", publishedAt = 50, isRead = true),
                article(domesticFeedId, "new2", publishedAt = 40, isRead = true),
                article(domesticFeedId, "new3", publishedAt = 30, isRead = true),
                article(domesticFeedId, "old1", publishedAt = 20),
                article(domesticFeedId, "old2", publishedAt = 10),
            )
        )

        val unread = timeline(onlyUnread = true, perSourceLimit = 2)

        assertEquals(listOf("old1", "old2"), unread.map { it.article.guid })
    }

    @Test
    fun `articles sharing a timestamp do not both escape the cap`() = runTest {
        articleDao.insertAll(
            (1..4).map { article(domesticFeedId, "same$it", publishedAt = 1_000) }
        )

        assertEquals(1, timeline(perSourceLimit = 1).size)
    }

    private suspend fun timeline(
        onlyUnread: Boolean = false,
        onlyBookmarked: Boolean = false,
        category: String? = null,
        feedId: Long? = null,
        query: String = "",
        perSourceLimit: Int = 0,
        limit: Int = 500,
    ): List<ArticleListItem> = articleDao
        .observeTimeline(onlyUnread, onlyBookmarked, category, feedId, query, perSourceLimit, limit)
        .first()

    private fun article(
        feedId: Long,
        guid: String,
        title: String = "Titulek $guid",
        summary: String = "Perex $guid",
        publishedAt: Long = 1_000,
        isRead: Boolean = false,
        isBookmarked: Boolean = false,
    ) = Article(
        feedId = feedId,
        guid = guid,
        title = title,
        link = "https://example.cz/$guid",
        summary = summary,
        content = null,
        imageUrl = null,
        author = null,
        publishedAt = publishedAt,
        fetchedAt = publishedAt,
        isRead = isRead,
        isBookmarked = isBookmarked,
    )
}
