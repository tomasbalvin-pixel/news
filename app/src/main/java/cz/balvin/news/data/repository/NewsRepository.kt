package cz.balvin.news.data.repository

import android.util.Log
import cz.balvin.news.data.local.Article
import cz.balvin.news.data.local.ArticleDao
import cz.balvin.news.data.local.ArticleListItem
import cz.balvin.news.data.local.DefaultFeeds
import cz.balvin.news.data.local.Feed
import cz.balvin.news.data.local.FeedDao
import cz.balvin.news.data.remote.FeedService
import cz.balvin.news.data.remote.ParsedItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit

class NewsRepository(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val feedService: FeedService,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** A failure carries its reason, not just the fact that it happened. */
    data class FeedFailure(val title: String, val message: String)

    data class RefreshOutcome(val newArticles: Int, val failures: List<FeedFailure>) {
        val hasFailures: Boolean get() = failures.isNotEmpty()
    }

    sealed interface AddFeedResult {
        data class Added(val feed: Feed) : AddFeedResult
        data object Duplicate : AddFeedResult
        data class Invalid(val message: String) : AddFeedResult
    }

    // ---- Reads -------------------------------------------------------------

    fun observeFeeds(): Flow<List<Feed>> = feedDao.observeAll()

    fun observeCategories(): Flow<List<String>> = feedDao.observeCategories()

    fun observeUnreadCount(): Flow<Int> = articleDao.observeUnreadCount()

    fun observeArticle(id: Long): Flow<ArticleListItem?> = articleDao.observeById(id)

    fun observeTimeline(filter: TimelineFilter): Flow<List<ArticleListItem>> =
        articleDao.observeTimeline(
            onlyUnread = filter.onlyUnread,
            onlyBookmarked = filter.onlyBookmarked,
            category = filter.category,
            feedId = filter.feedId,
            query = filter.query.trim(),
            perSourceLimit = filter.perSourceLimit,
            limit = TIMELINE_LIMIT,
        )

    // ---- Feed management ---------------------------------------------------

    /** Populates the source list on first launch only; later edits are the user's. */
    suspend fun seedDefaultFeedsIfEmpty() {
        if (feedDao.count() == 0) feedDao.insertAll(DefaultFeeds.ALL)
    }

    suspend fun addFeed(url: String, title: String?, category: String): AddFeedResult {
        val normalized = normalizeUrl(url)
            ?: return AddFeedResult.Invalid("Invalid URL")

        return when (val result = feedService.fetch(normalized)) {
            is FeedService.Result.Failure -> AddFeedResult.Invalid(result.message)
            FeedService.Result.NotModified -> AddFeedResult.Invalid("Empty feed")
            is FeedService.Result.Success -> {
                val resolvedTitle = title?.takeIf(String::isNotBlank)
                    ?: result.feed.title?.takeIf(String::isNotBlank)
                    ?: normalized
                val feed = Feed(
                    title = resolvedTitle.trim(),
                    url = normalized,
                    siteUrl = result.feed.siteUrl,
                    category = category.trim(),
                )
                val id = feedDao.insert(feed)
                if (id == -1L) {
                    AddFeedResult.Duplicate
                } else {
                    val stored = feed.copy(id = id)
                    storeItems(stored, result.feed.items)
                    feedDao.recordFetch(id, now(), null, result.etag, result.lastModified)
                    AddFeedResult.Added(stored)
                }
            }
        }
    }

    suspend fun deleteFeed(feed: Feed) = feedDao.delete(feed)

    suspend fun setFeedEnabled(id: Long, enabled: Boolean) = feedDao.setEnabled(id, enabled)

    suspend fun updateFeed(feed: Feed) = feedDao.update(feed)

    // ---- Article state -----------------------------------------------------

    suspend fun setRead(id: Long, read: Boolean) = articleDao.setRead(id, read)

    suspend fun setBookmarked(id: Long, bookmarked: Boolean) = articleDao.setBookmarked(id, bookmarked)

    suspend fun markAllRead() = articleDao.markAllRead()

    /** Drops read, unsaved articles past the retention window. */
    suspend fun prune(retentionDays: Int): Int {
        val cutoff = now() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        return articleDao.deleteOlderThan(cutoff)
    }

    // ---- Refresh -----------------------------------------------------------

    suspend fun refreshAll(): RefreshOutcome = coroutineScope {
        val feeds = feedDao.enabled()
        if (feeds.isEmpty()) return@coroutineScope RefreshOutcome(0, emptyList())

        val gate = Semaphore(MAX_CONCURRENT_FETCHES)
        val results = feeds
            .map { feed -> async { gate.withPermit { refreshFeed(feed) } } }
            .awaitAll()

        RefreshOutcome(
            newArticles = results.sumOf { it.first },
            failures = results.mapNotNull { it.second },
        )
    }

    /** Returns the number of newly stored articles and the failure, if there was one. */
    private suspend fun refreshFeed(feed: Feed): Pair<Int, FeedFailure?> {
        val timestamp = now()
        return when (val result = feedService.fetch(feed.url, feed.etag, feed.lastModified)) {
            FeedService.Result.NotModified -> {
                feedDao.recordFetch(feed.id, timestamp, null, feed.etag, feed.lastModified)
                0 to null
            }

            is FeedService.Result.Failure -> {
                // Also to logcat, so a cable is enough to read what went wrong.
                Log.w(TAG, "Feed failed: ${feed.title} <${feed.url}> — ${result.message}")
                feedDao.recordFetch(feed.id, timestamp, result.message, feed.etag, feed.lastModified)
                0 to FeedFailure(feed.title, result.message)
            }

            is FeedService.Result.Success -> {
                val inserted = storeItems(feed, result.feed.items)
                feedDao.recordFetch(feed.id, timestamp, null, result.etag, result.lastModified)
                inserted to null
            }
        }
    }

    private suspend fun storeItems(feed: Feed, items: List<ParsedItem>): Int {
        if (items.isEmpty()) return 0
        val timestamp = now()
        val articles = items
            .filter { it.link.isNotBlank() || it.title.isNotBlank() }
            .distinctBy { it.guid }
            .map { item ->
                Article(
                    feedId = feed.id,
                    guid = item.guid,
                    title = item.title,
                    link = item.link,
                    summary = item.summary,
                    content = item.content,
                    imageUrl = item.imageUrl,
                    author = item.author,
                    // A feed without a usable date sorts as "just fetched".
                    publishedAt = item.publishedAt ?: timestamp,
                    fetchedAt = timestamp,
                )
            }
        return articleDao.insertAll(articles).count { it != -1L }
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("feed://", ignoreCase = true) -> "https://" + trimmed.removePrefix("feed://")
            else -> "https://$trimmed"
        }
        return withScheme.takeIf { it.contains('.') }
    }

    private companion object {
        const val TAG = "NewsRepository"
        const val TIMELINE_LIMIT = 500
        const val MAX_CONCURRENT_FETCHES = 4
    }
}
