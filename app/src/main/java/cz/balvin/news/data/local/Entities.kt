package cz.balvin.news.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A subscribed RSS/Atom source. */
@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true)]
)
data class Feed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val siteUrl: String? = null,
    val category: String = "",
    val enabled: Boolean = true,
    val lastFetchedAt: Long? = null,
    val lastError: String? = null,
    /** Validators kept for conditional GET, so unchanged feeds cost one 304. */
    val etag: String? = null,
    val lastModified: String? = null,
)

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index(value = ["publishedAt"]),
        Index(value = ["feedId", "publishedAt"]),
        Index(value = ["isBookmarked"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Feed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    /** Feed-provided guid, or the link when the feed omits one. */
    val guid: String,
    val title: String,
    val link: String,
    val summary: String,
    val content: String?,
    val imageUrl: String?,
    val author: String?,
    val publishedAt: Long,
    val fetchedAt: Long,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
)
