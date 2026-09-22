package cz.balvin.news.util

/**
 * Feed descriptions arrive as HTML fragments. The timeline needs plain text and
 * a lead image; the reader keeps the markup. Both jobs are small enough that a
 * full HTML parser would cost more than it returns.
 */
object Html {

    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private val IMG_SRC = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "hellip" to "…", "mdash" to "—", "ndash" to "–",
        "laquo" to "«", "raquo" to "»", "bdquo" to "„", "ldquo" to "“",
        "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’", "eacute" to "é",
        "egrave" to "è", "uuml" to "ü", "ouml" to "ö", "auml" to "ä",
        "szlig" to "ß", "middot" to "·", "bull" to "•", "euro" to "€",
        "pound" to "£", "deg" to "°", "copy" to "©", "reg" to "®", "trade" to "™",
    )

    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    fun toPlainText(html: String?): String {
        val source = html.orEmpty()
        if (source.isEmpty()) return ""
        val withBreaks = source
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), " ")
        return unescape(TAG.replace(withBreaks, ""))
            .replace(WHITESPACE, " ")
            .trim()
    }

    fun firstImageUrl(html: String?): String? =
        html?.let { IMG_SRC.find(it)?.groupValues?.get(1)?.takeIf(String::isNotBlank) }

    fun unescape(text: String): String = ENTITY.replace(text) { match ->
        val token = match.groupValues[1]
        when {
            token.startsWith("#x", ignoreCase = true) ->
                token.drop(2).toIntOrNull(16)?.let { codePointToString(it) } ?: match.value
            token.startsWith("#") ->
                token.drop(1).toIntOrNull()?.let { codePointToString(it) } ?: match.value
            else -> NAMED_ENTITIES[token.lowercase()] ?: match.value
        }
    }

    private fun codePointToString(codePoint: Int): String? =
        if (codePoint in 1..0x10FFFF) String(Character.toChars(codePoint)) else null
}
