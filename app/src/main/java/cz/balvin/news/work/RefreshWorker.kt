package cz.balvin.news.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.balvin.news.NewsApplication
import cz.balvin.news.data.repository.TimelineFilter
import kotlinx.coroutines.flow.first

/**
 * Assembles one edition: open a new boundary, pull every enabled source, drop
 * what fell out of the retention window, and say once that it is ready.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NewsApplication).container
        val settings = container.settingsStore.settings.first()

        // The boundary opens before the fetch, so everything this run brings in
        // belongs to the edition it is building.
        val startedAt = System.currentTimeMillis()
        container.settingsStore.startEdition(startedAt)

        val outcome = runCatching { container.repository.refreshAll() }
            .getOrElse { return Result.retry() }

        runCatching { container.repository.prune(settings.retentionDays) }

        if (outcome.newArticles > 0 && settings.notificationsEnabled) {
            val edition = container.repository
                .observeTimeline(
                    TimelineFilter(
                        since = startedAt,
                        perSourceLimit = settings.perSourceLimit,
                        limit = settings.editionSize,
                    )
                )
                .first()

            Notifier.notifyEditionReady(
                context = applicationContext,
                count = edition.size,
                headline = edition.firstOrNull()?.article?.title,
            )
        }

        // A source that could not be reached is not a reason to run again.
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "daily-edition"
    }
}
