package cz.balvin.news.data.remote

data class ParsedFeed(
    val title: String?,
    val siteUrl: String?,
    val items: List<ParsedItem>,
)

data class ParsedItem(
    val guid: String,
    val title: String,
    val link: String,
    val summary: String,
    val content: String?,
    val imageUrl: String?,
    val author: String?,
    val publishedAt: Long?,
)
