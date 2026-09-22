package cz.balvin.news.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** Opens the original article; falls back to any browser if Custom Tabs are absent. */
fun Context.openInBrowser(url: String) {
    if (url.isBlank()) return
    val uri = runCatching { url.toUri() }.getOrNull() ?: return
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, uri)
    } catch (_: ActivityNotFoundException) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

fun Context.shareArticle(title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, if (url.isBlank()) title else "$title\n$url")
    }
    runCatching { startActivity(Intent.createChooser(intent, title)) }
}

fun String.host(): String =
    runCatching { Uri.parse(this).host?.removePrefix("www.") }.getOrNull().orEmpty()
