package cz.balvin.news.data.repository

data class TimelineFilter(
    val onlyUnread: Boolean = false,
    val onlyBookmarked: Boolean = false,
    val category: String? = null,
    val feedId: Long? = null,
    val query: String = "",
    /** At most this many articles per source; zero or less lifts the cap. */
    val perSourceLimit: Int = 0,
)
