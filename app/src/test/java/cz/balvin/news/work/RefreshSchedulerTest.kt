package cz.balvin.news.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RefreshSchedulerTest {

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 9, 22, hour, minute, 0, 0, ZoneId.of("Europe/Prague"))

    @Test
    fun `waits until later today when the hour is still ahead`() {
        val delay = RefreshScheduler.delayUntilNext(hour = 7, now = at(5, 30))

        assertEquals(90, delay.toMinutes())
    }

    @Test
    fun `rolls over to tomorrow once the hour has passed`() {
        val delay = RefreshScheduler.delayUntilNext(hour = 7, now = at(9))

        assertEquals(22 * 60, delay.toMinutes())
    }

    @Test
    fun `an hour that has just struck waits a full day rather than firing twice`() {
        val delay = RefreshScheduler.delayUntilNext(hour = 7, now = at(7))

        assertEquals(24 * 60, delay.toMinutes())
    }

    @Test
    fun `an out of range hour is clamped instead of throwing`() {
        assertEquals(
            RefreshScheduler.delayUntilNext(hour = 23, now = at(5)),
            RefreshScheduler.delayUntilNext(hour = 99, now = at(5)),
        )
    }
}
