package cz.balvin.news.ui.common

import android.text.format.DateUtils

/** "před 5 min" / "5 min ago" — the platform already localises this. */
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
