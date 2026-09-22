package cz.balvin.news.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Feeds date their items in whatever their CMS emits: RFC 822 with a named zone,
 * RFC 822 with a numeric offset, ISO 8601 with or without fractions, and the
 * occasional bare date. Each pattern is tried in turn; failure returns null so
 * the caller can fall back to the fetch time.
 */
object DateParsing {

    private val PATTERNS: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT,
        pattern("EEE, d MMM yyyy HH:mm:ss zzz"),
        pattern("EEE, d MMM yyyy HH:mm:ss Z"),
        pattern("EEE, d MMM yyyy HH:mm zzz"),
        pattern("EEE, d MMM yyyy HH:mm Z"),
        pattern("d MMM yyyy HH:mm:ss zzz"),
        pattern("d MMM yyyy HH:mm:ss Z"),
        pattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
        pattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
        pattern("yyyy-MM-dd'T'HH:mm:ss"),
        pattern("yyyy-MM-dd HH:mm:ss"),
    )

    private val DATE_ONLY: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        pattern("dd.MM.yyyy"),
    )

    private fun pattern(value: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(value, Locale.ENGLISH)

    /** Epoch milliseconds, or null when nothing in [raw] looks like a timestamp. */
    fun toEpochMillis(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        for (formatter in PATTERNS) {
            val parsed = runCatching {
                OffsetDateTime.parse(text, formatter).toInstant()
            }.recoverCatching {
                Instant.from(formatter.parse(text))
            }.recoverCatching {
                LocalDateTime.parse(text, formatter).toInstant(ZoneOffset.UTC)
            }.getOrNull()
            if (parsed != null) return parsed.toEpochMilli()
        }

        for (formatter in DATE_ONLY) {
            val parsed = runCatching {
                LocalDate.parse(text, formatter).atStartOfDay(ZoneOffset.UTC).toInstant()
            }.getOrNull()
            if (parsed != null) return parsed.toEpochMilli()
        }

        return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
    }
}
