package cz.balvin.news.data.local

/**
 * Seeded once, on first launch, when the feed table is still empty.
 * Everything here is editable from the Sources screen — this is a starting
 * point, not a fixed list.
 */
object DefaultFeeds {

    const val CATEGORY_DOMESTIC = "Domácí"
    const val CATEGORY_WORLD = "Svět"
    const val CATEGORY_TECH = "Technologie"
    const val CATEGORY_ECONOMY = "Ekonomika"

    val ALL: List<Feed> = listOf(
        Feed(
            title = "ČT24",
            url = "https://ct24.ceskatelevize.cz/rss/hlavni-zpravy",
            siteUrl = "https://ct24.ceskatelevize.cz",
            category = CATEGORY_DOMESTIC,
        ),
        Feed(
            title = "iROZHLAS",
            url = "https://www.irozhlas.cz/rss/irozhlas",
            siteUrl = "https://www.irozhlas.cz",
            category = CATEGORY_DOMESTIC,
        ),
        Feed(
            title = "Seznam Zprávy",
            url = "https://www.seznamzpravy.cz/rss",
            siteUrl = "https://www.seznamzpravy.cz",
            category = CATEGORY_DOMESTIC,
        ),
        Feed(
            title = "Aktuálně.cz",
            url = "https://www.aktualne.cz/rss/",
            siteUrl = "https://www.aktualne.cz",
            category = CATEGORY_DOMESTIC,
        ),
        Feed(
            title = "Novinky.cz",
            url = "https://www.novinky.cz/rss",
            siteUrl = "https://www.novinky.cz",
            category = CATEGORY_DOMESTIC,
        ),
        Feed(
            title = "iDNES.cz — Zprávy",
            url = "https://servis.idnes.cz/rss.aspx?c=zpravodaj",
            siteUrl = "https://zpravy.idnes.cz",
            category = CATEGORY_DOMESTIC,
        ),
        Feed(
            title = "BBC World",
            url = "https://feeds.bbci.co.uk/news/world/rss.xml",
            siteUrl = "https://www.bbc.com/news/world",
            category = CATEGORY_WORLD,
        ),
        // Reuters withdrew its public RSS; the feed 404s. The Guardian's world
        // feed has been stable for years and needs no key.
        Feed(
            title = "The Guardian — World",
            url = "https://www.theguardian.com/world/rss",
            siteUrl = "https://www.theguardian.com/world",
            category = CATEGORY_WORLD,
        ),
        Feed(
            title = "E15",
            url = "https://www.e15.cz/rss",
            siteUrl = "https://www.e15.cz",
            category = CATEGORY_ECONOMY,
        ),
        Feed(
            title = "Root.cz",
            url = "https://www.root.cz/rss/clanky/",
            siteUrl = "https://www.root.cz",
            category = CATEGORY_TECH,
        ),
        Feed(
            title = "Lupa.cz",
            url = "https://www.lupa.cz/rss/clanky/",
            siteUrl = "https://www.lupa.cz",
            category = CATEGORY_TECH,
        ),
        Feed(
            title = "Ars Technica",
            url = "https://feeds.arstechnica.com/arstechnica/index",
            siteUrl = "https://arstechnica.com",
            category = CATEGORY_TECH,
        ),
    )
}
