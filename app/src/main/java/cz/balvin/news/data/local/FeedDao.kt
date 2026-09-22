package cz.balvin.news.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("SELECT * FROM feeds ORDER BY category, title COLLATE NOCASE")
    fun observeAll(): Flow<List<Feed>>

    @Query("SELECT DISTINCT category FROM feeds WHERE category != '' ORDER BY category COLLATE NOCASE")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM feeds WHERE enabled = 1")
    suspend fun enabled(): List<Feed>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun byId(id: Long): Feed?

    @Query("SELECT COUNT(*) FROM feeds")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: Feed): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(feeds: List<Feed>)

    @Update
    suspend fun update(feed: Feed)

    @Delete
    suspend fun delete(feed: Feed)

    @Query("UPDATE feeds SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query(
        "UPDATE feeds SET lastFetchedAt = :at, lastError = :error, etag = :etag, lastModified = :lastModified WHERE id = :id"
    )
    suspend fun recordFetch(id: Long, at: Long, error: String?, etag: String?, lastModified: String?)
}
