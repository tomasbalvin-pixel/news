package cz.balvin.news.data.local

/**
 * Seeded once, on first launch, when the feed table is still empty.
 *
 * The list is every outlet rated **A** by the Nadační fond nezávislé
 * žurnalistiky (nfnz.cz/rating-medii), as the reader asked. Outlets rated A−
 * and below are deliberately absent.
 *
 * The feed addresses are not part of that rating and are not verified — a
 * source that does not answer shows its error in the Sources tab.
 */
object DefaultFeeds {

    const val CATEGORY_NEWS = "Zpravodajství"
    const val CATEGORY_ECONOMY = "Ekonomika"

    val ALL: List<Feed> = listOf(
        Feed(
            title = "Aktuálně.cz",
            url = "https://www.aktualne.cz/rss/",
            siteUrl = "https://www.aktualne.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "České noviny",
            url = "https://www.ceskenoviny.cz/sluzby/rss/zpravy.php",
            siteUrl = "https://www.ceskenoviny.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "ČT24",
            url = "https://ct24.ceskatelevize.cz/rss/hlavni-zpravy",
            siteUrl = "https://ct24.ceskatelevize.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Deník",
            url = "https://www.denik.cz/rss/vse.html",
            siteUrl = "https://www.denik.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Deník Alarm",
            url = "https://a2larm.cz/feed/",
            siteUrl = "https://a2larm.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Deník N",
            url = "https://denikn.cz/feed/",
            siteUrl = "https://denikn.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Deník Referendum",
            url = "https://denikreferendum.cz/rss",
            siteUrl = "https://denikreferendum.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "E15",
            url = "https://www.e15.cz/rss",
            siteUrl = "https://www.e15.cz",
            category = CATEGORY_ECONOMY,
        ),
        Feed(
            title = "Echo24",
            url = "https://echo24.cz/rss",
            siteUrl = "https://echo24.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Euro",
            url = "https://www.euro.cz/rss",
            siteUrl = "https://www.euro.cz",
            category = CATEGORY_ECONOMY,
        ),
        Feed(
            title = "Forum 24",
            url = "https://www.forum24.cz/feed/",
            siteUrl = "https://www.forum24.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Hospodářské noviny",
            url = "https://hn.cz/?m=rss",
            siteUrl = "https://hn.cz",
            category = CATEGORY_ECONOMY,
        ),
        Feed(
            title = "iROZHLAS",
            url = "https://www.irozhlas.cz/rss/irozhlas",
            siteUrl = "https://www.irozhlas.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Refresher",
            url = "https://refresher.cz/rss",
            siteUrl = "https://refresher.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Respekt",
            url = "https://www.respekt.cz/rss",
            siteUrl = "https://www.respekt.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Seznam Zprávy",
            url = "https://www.seznamzpravy.cz/rss",
            siteUrl = "https://www.seznamzpravy.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Voxpot",
            url = "https://www.voxpot.cz/feed/",
            siteUrl = "https://www.voxpot.cz",
            category = CATEGORY_NEWS,
        ),
        Feed(
            title = "Život v Česku",
            url = "https://zivotvcesku.cz/feed/",
            siteUrl = "https://zivotvcesku.cz",
            category = CATEGORY_NEWS,
        ),
    )
}
