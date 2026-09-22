package cz.balvin.news.data.remote

import cz.balvin.news.util.DateParsing
import cz.balvin.news.util.Html
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads RSS 2.0, RSS 1.0/RDF and Atom 1.0 into one shape.
 *
 * Parsing runs namespace-unaware and matches on the local part of each tag, so
 * a feed that writes `content:encoded`, `media:content` or `dc:date` is handled
 * without knowing which prefix it bound to which namespace. DOM is used rather
 * than a pull parser because it runs under plain JVM unit tests.
 *
 * The two platforms are not interchangeable, though: their factory
 * implementations disagree about which optional settings exist, so a green test
 * run here does not prove the configuration below works on a device. See
 * [documentBuilder].
 */
object RssParser {

    private const val MAX_SUMMARY_CHARS = 400

    fun parse(bytes: ByteArray): ParsedFeed {
        val document = documentBuilder().parse(ByteArrayInputStream(bytes))
        document.documentElement.normalize()
        val root = document.documentElement ?: return ParsedFeed(null, null, emptyList())

        return when (localName(root)) {
            "feed" -> parseAtom(root)
            "rdf" -> parseRdf(root)
            else -> parseRss(root)
        }
    }

    /**
     * A feed is untrusted input, so doctype declarations and external entities
     * are refused.
     *
     * Every optional setting goes through [harden] because implementations
     * disagree about which of them exist: Android's DocumentBuilderFactory does
     * not override setXIncludeAware, so the base class throws
     * UnsupportedOperationException unconditionally, while the JVM's Xerces
     * implements it and does nothing. An unguarded call there fails every parse
     * on the device and none on a test machine.
     *
     * The entity resolver is what actually guarantees the XXE defence: unlike
     * the feature flags, it is honoured by every implementation.
     */
    private fun documentBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        harden { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        harden { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        harden { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        harden { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        harden { factory.isXIncludeAware = false }
        harden { factory.isExpandEntityReferences = false }

        return factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
    }

    /** Applies one optional setting, ignoring implementations that reject it. */
    private inline fun harden(apply: () -> Unit) {
        runCatching(apply)
    }

    // ---- RSS 2.0 -----------------------------------------------------------

    private fun parseRss(root: Element): ParsedFeed {
        val channel = root.child("channel") ?: root
        val items = channel.children("item").ifEmpty { root.children("item") }
        return ParsedFeed(
            title = channel.text("title"),
            siteUrl = channel.text("link"),
            items = items.mapNotNull { toItem(it, atom = false) },
        )
    }

    private fun parseRdf(root: Element): ParsedFeed {
        val channel = root.child("channel")
        return ParsedFeed(
            title = channel?.text("title"),
            siteUrl = channel?.text("link"),
            items = root.children("item").mapNotNull { toItem(it, atom = false) },
        )
    }

    // ---- Atom --------------------------------------------------------------

    private fun parseAtom(root: Element): ParsedFeed = ParsedFeed(
        title = root.text("title"),
        siteUrl = root.children("link").firstOrNull { link ->
            val rel = link.getAttribute("rel")
            rel.isEmpty() || rel == "alternate"
        }?.getAttribute("href")?.takeIf(String::isNotBlank),
        items = root.children("entry").mapNotNull { toItem(it, atom = true) },
    )

    // ---- Shared item mapping ----------------------------------------------

    private fun toItem(element: Element, atom: Boolean): ParsedItem? {
        val link = if (atom) atomLink(element) else element.text("link") ?: element.text("guid")
        val title = element.text("title")?.let(Html::toPlainText).orEmpty()
        val guid = element.text("guid")
            ?: element.text("id")
            ?: link
            ?: title.takeIf(String::isNotBlank)
            ?: return null

        val content = element.text("encoded") ?: element.text("content")
        val rawSummary = element.text("description")
            ?: element.text("summary")
            ?: element.text("subtitle")
            ?: content

        val published = firstNonNull(
            element.text("pubDate"),
            element.text("published"),
            element.text("updated"),
            element.text("date"),
            element.text("modified"),
        )?.let(DateParsing::toEpochMillis)

        return ParsedItem(
            guid = guid.trim(),
            title = title.ifBlank { Html.toPlainText(rawSummary).take(80) },
            link = link?.trim().orEmpty(),
            summary = Html.toPlainText(rawSummary).take(MAX_SUMMARY_CHARS),
            content = content?.takeIf(String::isNotBlank),
            imageUrl = imageUrl(element, content, rawSummary),
            author = author(element),
            publishedAt = published,
        )
    }

    private fun atomLink(entry: Element): String? {
        val links = entry.children("link")
        val alternate = links.firstOrNull { it.getAttribute("rel") == "alternate" }
            ?: links.firstOrNull { it.getAttribute("rel").isEmpty() }
            ?: links.firstOrNull()
        return alternate?.getAttribute("href")?.takeIf(String::isNotBlank)
            ?: entry.text("link")
    }

    private fun author(element: Element): String? {
        element.text("author")?.takeIf(String::isNotBlank)?.let { return it.trim() }
        element.child("author")?.text("name")?.let { return it.trim() }
        element.text("creator")?.takeIf(String::isNotBlank)?.let { return it.trim() }
        return null
    }

    private fun imageUrl(element: Element, content: String?, summary: String?): String? {
        element.children("enclosure")
            .firstOrNull { it.getAttribute("type").startsWith("image", ignoreCase = true) }
            ?.getAttribute("url")
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        val mediaCandidates = element.children("content") + element.children("thumbnail")
        mediaCandidates
            .firstOrNull { candidate ->
                val url = candidate.getAttribute("url")
                url.isNotBlank() && (
                    candidate.getAttribute("medium").equals("image", ignoreCase = true) ||
                        candidate.getAttribute("type").startsWith("image", ignoreCase = true) ||
                        localName(candidate).contains("thumbnail") ||
                        looksLikeImage(url)
                    )
            }
            ?.getAttribute("url")
            ?.let { return it }

        element.child("image")?.text("url")?.takeIf(String::isNotBlank)?.let { return it }
        element.children("image").firstOrNull()?.getAttribute("href")
            ?.takeIf(String::isNotBlank)?.let { return it }

        return Html.firstImageUrl(content) ?: Html.firstImageUrl(summary)
    }

    private fun looksLikeImage(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return listOf(".jpg", ".jpeg", ".png", ".webp", ".gif").any(path::endsWith)
    }

    // ---- DOM helpers -------------------------------------------------------

    private fun localName(node: Node): String =
        node.nodeName.substringAfter(':').lowercase()

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        val result = ArrayList<Element>(nodes.length)
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element) result.add(node)
        }
        return result
    }

    /** Matches on the local name, so `dc:date` is found by `date` and by `dc:date`. */
    private fun Element.children(name: String): List<Element> {
        val wanted = name.substringAfter(':').lowercase()
        return childElements().filter { localName(it) == wanted }
    }

    private fun Element.child(name: String): Element? = children(name).firstOrNull()

    private fun Element.text(name: String): String? =
        child(name)?.textContent?.trim()?.takeIf(String::isNotEmpty)

    private fun firstNonNull(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }
}
