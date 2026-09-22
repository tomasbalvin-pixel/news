package cz.balvin.news.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object RefreshScheduler {

    /**
     * One edition a day at the chosen hour. The first run is delayed to the next
     * occurrence of that hour rather than firing on the spot, so enabling this at
     * midnight does not produce an edition at midnight.
     */
    fun scheduleDailyEdition(context: Context, hour: Int, now: ZonedDateTime = ZonedDateTime.now()) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNext(hour, now).toMinutes(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Time from [now] to the next occurrence of [hour] o'clock, local time. */
    fun delayUntilNext(hour: Int, now: ZonedDateTime): Duration {
        val target = now.with(LocalTime.of(hour.coerceIn(0, 23), 0))
        val next = if (target.isAfter(now)) target else target.plusDays(1)
        return Duration.between(now, next)
    }
}
