package cz.balvin.news.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * One topic of one edition's digest. A digest is simply the rows carrying the
 * same [editionAt], ordered by [position] — no second table for a header that
 * would hold nothing but a timestamp.
 */
@Entity(
    tableName = "digest_topics",
    indices = [Index(value = ["editionAt", "position"], unique = true)],
)
data class DigestTopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val editionAt: Long,
    val position: Int,
    val title: String,
    val summary: String,
    val sources: List<String>,
    val generatedAt: Long,
)

/** Source names never contain a unit separator, so joining on one is lossless. */
class StringListConverter {

    @TypeConverter
    fun fromList(values: List<String>): String = values.joinToString(SEPARATOR)

    @TypeConverter
    fun toList(stored: String): List<String> =
        if (stored.isEmpty()) emptyList() else stored.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\u001f"
    }
}
