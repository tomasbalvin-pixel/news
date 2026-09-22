package cz.balvin.news.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.balvin.news.NewsApplication
import cz.balvin.news.data.repository.TimelineFilter
import kotlinx.coroutines.flow.first

/**
 * Periodic background refresh: pull every enabled feed, prune what fell out of
 * the retention window, and raise one notification when anything new landed.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NewsApplication).container
        val settings = container.settingsStore.settings.first()

        val outcome = runCatching { container.repository.refreshAll() }
            .getOrElse { return Result.retry() }

        runCatching { container.repository.prune(settings.retentionDays) }

        if (outcome.newArticles > 0 && settings.notificationsEnabled) {
            val headline = container.repository
                .observeTimeline(TimelineFilter(onlyUnread = true))
                .first()
                .firstOrNull()
                ?.article
                ?.title
            Notifier.notifyNewArticles(applicationContext, outcome.newArticles, headline)
        }

        // A partial failure is normal — one unreachable source is not a retry.
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "periodic-feed-refresh"
    }
}
