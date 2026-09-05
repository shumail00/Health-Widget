package com.shumail.healthwidget

import com.shumail.healthwidget.model.TimerModel
import com.shumail.healthwidget.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerModelTest {

    @Test
    fun testReadyStateDefaults() {
        val timer = TimerModel()
        assertEquals(TimerStatus.READY, timer.status)
        assertEquals(TimerStatus.READY, timer.getEffectiveStatus())
        assertEquals(TimerModel.DEFAULT_DURATION_MILLIS, timer.remainingMillis())
        assertEquals("10:00", timer.formattedRemaining())
        assertEquals(0f, timer.progressFraction(), 0.001f)
    }

    @Test
    fun testRunningStateCalculations() {
        val start = 1_000_000L
        val duration = 600_000L // 10 minutes
        val finish = start + duration

        val timer = TimerModel(
            status = TimerStatus.RUNNING,
            startTimeMillis = start,
            finishTimeMillis = finish,
            durationMillis = duration
        )

        // 3 minutes elapsed (7 minutes remaining = 420,000 ms)
        val now = start + 180_000L
        assertEquals(TimerStatus.RUNNING, timer.getEffectiveStatus(now))
        assertEquals(420_000L, timer.remainingMillis(now))
        assertEquals("07:00", timer.formattedRemaining(now))
        assertEquals(0.3f, timer.progressFraction(now), 0.01f)
    }

    @Test
    fun testAutoTransitionToFinishedWhenTimeExpires() {
        val start = 1_000_000L
        val duration = 600_000L
        val finish = start + duration

        val timer = TimerModel(
            status = TimerStatus.RUNNING,
            startTimeMillis = start,
            finishTimeMillis = finish,
            durationMillis = duration
        )

        // Time has reached or passed finish time
        val now = finish + 5000L
        assertEquals(TimerStatus.FINISHED, timer.getEffectiveStatus(now))
        assertEquals(0L, timer.remainingMillis(now))
        assertEquals("00:00", timer.formattedRemaining(now))
        assertEquals(1.0f, timer.progressFraction(now), 0.001f)
    }

    @Test
    fun testFinishedStateExplicit() {
        val timer = TimerModel(
            status = TimerStatus.FINISHED,
            startTimeMillis = 1_000L,
            finishTimeMillis = 601_000L,
            durationMillis = 600_000L
        )

        assertEquals(TimerStatus.FINISHED, timer.getEffectiveStatus())
        assertEquals(0L, timer.remainingMillis())
        assertEquals("00:00", timer.formattedRemaining())
        assertEquals(1.0f, timer.progressFraction(), 0.001f)
    }
}
