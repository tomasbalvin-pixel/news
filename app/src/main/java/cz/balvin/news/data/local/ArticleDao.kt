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
     */
    @Query(
        """
        SELECT a.*, f.title AS feedTitle, f.category AS feedCategory
        FROM articles a
        JOIN feeds f ON f.id = a.feedId
        WHERE (:onlyUnread = 0 OR a.isRead = 0)
          AND (:onlyBookmarked = 0 OR a.isBookmarked = 1)
          AND (:category IS NULL OR f.category = :category)
          AND (:feedId IS NULL OR a.feedId = :feedId)
          AND (
                :query = ''
                OR a.title LIKE '%' || :query || '%'
                OR a.summary LIKE '%' || :query || '%'
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
