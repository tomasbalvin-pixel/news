package cz.balvin.news.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DigestDao {

    @Query("SELECT * FROM digest_topics WHERE editionAt = :editionAt ORDER BY position")
    fun observeForEdition(editionAt: Long): Flow<List<DigestTopicEntity>>

    @Query("DELETE FROM digest_topics WHERE editionAt = :editionAt")
    suspend fun deleteForEdition(editionAt: Long)

    @Insert
    suspend fun insertAll(topics: List<DigestTopicEntity>)

    /** A regenerated digest replaces the old one rather than stacking beside it. */
    @Transaction
    suspend fun replaceForEdition(editionAt: Long, topics: List<DigestTopicEntity>) {
        deleteForEdition(editionAt)
        if (topics.isNotEmpty()) insertAll(topics)
    }

    @Query("DELETE FROM digest_topics WHERE editionAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
