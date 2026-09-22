package cz.balvin.news.data.repository

data class TimelineFilter(
    val onlyUnread: Boolean = false,
    val onlyBookmarked: Boolean = false,
    val category: String? = null,
    val feedId: Long? = null,
    val query: String = "",
)
