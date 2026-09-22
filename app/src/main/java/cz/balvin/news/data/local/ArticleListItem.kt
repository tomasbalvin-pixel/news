package cz.balvin.news.data.local

import androidx.room.Embedded

/** An article joined with the display fields of the feed it came from. */
data class ArticleListItem(
    @Embedded val article: Article,
    val feedTitle: String,
    val feedCategory: String,
)
