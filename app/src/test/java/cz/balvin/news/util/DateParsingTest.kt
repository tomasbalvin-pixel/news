package cz.balvin.news.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateParsingTest {

    @Test
    fun `reads RFC 822 with a numeric offset`() {
        assertEquals(
            1790058600000L,
            DateParsing.toEpochMillis("Tue, 22 Sep 2026 08:30:00 +0200"),
        )
    }

    @Test
    fun `reads RFC 822 with a named zone`() {
        assertEquals(
            1790017200000L,
            DateParsing.toEpochMillis("Mon, 21 Sep 2026 19:00:00 GMT"),
        )
    }

    @Test
    fun `reads ISO 8601 with and without fractions`() {
        val withZ = DateParsing.toEpochMillis("2026-09-20T10:15:00Z")
        val withFraction = DateParsing.toEpochMillis("2026-09-20T10:15:00.000Z")
        val withOffset = DateParsing.toEpochMillis("2026-09-20T12:15:00+02:00")

        assertEquals(withZ, withFraction)
        assertEquals(withZ, withOffset)
    }

    @Test
    fun `reads a bare date`() {
        assertEquals(1789862400000L, DateParsing.toEpochMillis("2026-09-20"))
    }

    @Test
    fun `returns null for junk and for blanks`() {
        assertNull(DateParsing.toEpochMillis(null))
        assertNull(DateParsing.toEpochMillis("   "))
        assertNull(DateParsing.toEpochMillis("yesterday"))
    }
}
