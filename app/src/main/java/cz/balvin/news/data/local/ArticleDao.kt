package cz.balvin.news.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    /**
     * One query backs every timeline variant; a null or empty parameter disables
     * the matching clause rather than selecting nothing.
     *
     * [perSourceLimit] keeps a prolific source from burying the rest: an article
     * is shown only when fewer than that many newer ones from the same feed also
     * pass the current filters. The rank is counted with a correlated subquery
     * rather than a window function, which SQLite only gained in a version newer
     * than this app's minimum. Zero or less means no cap.
     *
     * [since] scopes the result to one edition by the time articles were fetched
     * rather than published, so a source that backdates its items cannot slip
     * them past the boundary. Zero lifts it.
     */
    @Query(
        """
        SELECT a.*, f.title AS feedTitle, f.category AS feedCategory
        FROM articles a
        JOIN feeds f ON f.id = a.feedId
        WHERE (:since <= 0 OR a.fetchedAt >= :since)
          AND (:onlyUnread = 0 OR a.isRead = 0)
          AND (:onlyBookmarked = 0 OR a.isBookmarked = 1)
          AND (:category IS NULL OR f.category = :category)
          AND (:feedId IS NULL OR a.feedId = :feedId)
          AND (
                :query = ''
                OR a.title LIKE '%' || :query || '%'
                OR a.summary LIKE '%' || :query || '%'
              )
          AND (
                :perSourceLimit <= 0
                OR (
                    SELECT COUNT(*) FROM articles n
                    WHERE n.feedId = a.feedId
                      AND (:since <= 0 OR n.fetchedAt >= :since)
                      AND (
                            n.publishedAt > a.publishedAt
                            OR (n.publishedAt = a.publishedAt AND n.id > a.id)
                          )
                      AND (:onlyUnread = 0 OR n.isRead = 0)
                      AND (:onlyBookmarked = 0 OR n.isBookmarked = 1)
                      AND (
                            :query = ''
                            OR n.title LIKE '%' || :query || '%'
                            OR n.summary LIKE '%' || :query || '%'
                          )
                ) < :perSourceLimit
              )
        ORDER BY a.publishedAt DESC
        LIMIT :limit
        """
    )
    fun observeTimeline(
        onlyUnread: Boolean,
        onlyBookmarked: Boolean,
        category: String?,
        feedId: Long?,
        query: String,
        since: Long,
        perSourceLimit: Int,
        limit: Int,
    ): Flow<List<ArticleListItem>>

    @Query(
        """
        SELECT a.*, f.title AS feedTitle, f.category AS feedCategory
        FROM articles a
        JOIN feeds f ON f.id = a.feedId
        WHERE a.id = :id
        """
    )
    fun observeById(id: Long): Flow<ArticleListItem?>

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<Article>): List<Long>

    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    @Query("UPDATE articles SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarked: Boolean)

    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    /** Bookmarked articles survive retention pruning. */
    @Query("DELETE FROM articles WHERE isBookmarked = 0 AND publishedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
