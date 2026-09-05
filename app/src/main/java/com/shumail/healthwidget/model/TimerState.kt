package com.shumail.healthwidget.model

import java.util.Locale

enum class TimerStatus {
    READY,
    RUNNING,
    FINISHED
}

data class TimerModel(
    val status: TimerStatus = TimerStatus.READY,
    val startTimeMillis: Long = 0L,
    val finishTimeMillis: Long = 0L,
    val durationMillis: Long = DEFAULT_DURATION_MILLIS
) {
    companion object {
        const val DEFAULT_DURATION_MILLIS = 10 * 60 * 1000L // 10 minutes
    }

    /**
     * Resolves the actual current status taking current timestamp into account.
     * If status was RUNNING but now >= finishTimeMillis, it is effectively FINISHED.
     */
    fun getEffectiveStatus(now: Long = System.currentTimeMillis()): TimerStatus {
        return when (status) {
            TimerStatus.RUNNING -> {
                if (now >= finishTimeMillis && finishTimeMillis > 0L) {
                    TimerStatus.FINISHED
                } else {
                    TimerStatus.RUNNING
                }
            }
            else -> status
        }
    }

    /**
     * Calculates the remaining milliseconds until completion.
     */
    fun remainingMillis(now: Long = System.currentTimeMillis()): Long {
        return when (getEffectiveStatus(now)) {
            TimerStatus.READY -> durationMillis
            TimerStatus.RUNNING -> {
                val remaining = finishTimeMillis - now
                if (remaining > 0L) remaining else 0L
            }
            TimerStatus.FINISHED -> 0L
        }
    }

    /**
     * Returns remaining time formatted as MM:SS (e.g. "10:00", "08:14").
     */
    fun formattedRemaining(now: Long = System.currentTimeMillis()): String {
        val rem = remainingMillis(now)
        val totalSeconds = (rem + 999L) / 1000L // round up seconds for user expectation
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /**
     * Progress between 0.0f (just started) and 1.0f (completed).
     */
    fun progressFraction(now: Long = System.currentTimeMillis()): Float {
        return when (getEffectiveStatus(now)) {
            TimerStatus.READY -> 0f
            TimerStatus.FINISHED -> 1f
            TimerStatus.RUNNING -> {
                if (durationMillis <= 0L) return 1f
                val elapsed = (now - startTimeMillis).coerceAtLeast(0L)
                (elapsed.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
}
