package cz.balvin.news.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlTest {

    @Test
    fun `strips tags and collapses whitespace`() {
        assertEquals(
            "První odstavec Druhý odstavec",
            Html.toPlainText("<p>První odstavec</p>\n  <p>Druhý  odstavec</p>"),
        )
    }

    @Test
    fun `decodes named and numeric entities`() {
        assertEquals("a & b < c „d“ é", Html.unescape("a &amp; b &lt; c &bdquo;d&ldquo; &#233;"))
        assertEquals("→", Html.unescape("&#x2192;"))
    }

    @Test
    fun `leaves unknown entities untouched`() {
        assertEquals("&notarealentity;", Html.unescape("&notarealentity;"))
    }

    @Test
    fun `finds the first image source`() {
        assertEquals(
            "https://example.cz/a.jpg",
            Html.firstImageUrl("""<p>x</p><img class="lead" src="https://example.cz/a.jpg" />"""),
        )
        assertNull(Html.firstImageUrl("<p>no image</p>"))
    }
}
