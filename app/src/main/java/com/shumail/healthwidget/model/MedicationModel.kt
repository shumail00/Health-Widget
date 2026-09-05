package com.shumail.healthwidget.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class MedicationId {
    MULMIN,
    KETOGATE,
    AQUAWELL
}

enum class MedicationType {
    TABLET,
    EYE_DROPS
}

data class MedicationDef(
    val id: MedicationId,
    val name: String,
    val type: MedicationType,
    val durationDays: Int,
    val dailyDoseCount: Int,
    val hasNotifications: Boolean,
    val subtitle: String,
    val instructions: String,
    val primaryColorHex: Long,
    val containerColorHex: Long
)

object MedicationCatalog {
    val MULMIN = MedicationDef(
        id = MedicationId.MULMIN,
        name = "MULMIN",
        type = MedicationType.TABLET,
        durationDays = 30,
        dailyDoseCount = 1,
        hasNotifications = false,
        subtitle = "Tablet • Once daily",
        instructions = "Take 1 tablet daily after morning breakfast. No alarm reminder.",
        primaryColorHex = 0xFFD97706,      // Warm Amber
        containerColorHex = 0xFFFEF3C7
    )

    val KETOGATE = MedicationDef(
        id = MedicationId.KETOGATE,
        name = "KETOGATE",
        type = MedicationType.EYE_DROPS,
        durationDays = 14,
        dailyDoseCount = 3,
        hasNotifications = true,
        subtitle = "Eye Drops • 3 times daily",
        instructions = "Apply 1 drop in eye. Marking taken starts a 10-min countdown before AQUAWELL.",
        primaryColorHex = 0xFFE11D48,      // Rose / Coral
        containerColorHex = 0xFFFFE4E6
    )

    val AQUAWELL = MedicationDef(
        id = MedicationId.AQUAWELL,
        name = "AQUAWELL",
        type = MedicationType.EYE_DROPS,
        durationDays = 60,
        dailyDoseCount = 4,
        hasNotifications = true,
        subtitle = "Eye Drops • 4 times daily",
        instructions = "Follows KETOGATE after 10-minute interval for doses 1-3. Dose 4 at bedtime.",
        primaryColorHex = 0xFF0284C7,      // Sky / Cyan
        containerColorHex = 0xFFE0F2FE
    )

    val ALL = listOf(MULMIN, KETOGATE, AQUAWELL)

    fun get(id: MedicationId): MedicationDef = when (id) {
        MedicationId.MULMIN -> MULMIN
        MedicationId.KETOGATE -> KETOGATE
        MedicationId.AQUAWELL -> AQUAWELL
    }

    /**
     * Calculates the course day number (1-indexed) from the start date.
     */
    fun calculateDayOfCourse(courseStartDate: LocalDate, targetDate: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(courseStartDate, targetDate)
        return (days + 1).toInt()
    }

    /**
     * Checks if a medication is currently active on the given day of the course.
     * MULMIN: days 1..30
     * KETOGATE: days 1..14
     * AQUAWELL: days 1..60
     */
    fun isMedicationActive(medicationId: MedicationId, dayOfCourse: Int): Boolean {
        if (dayOfCourse < 1) return false
        val def = get(medicationId)
        return dayOfCourse <= def.durationDays
    }
}

/**
 * Standard daily slot specification for a medication dose.
 */
data class DoseSlot(
    val medicationId: MedicationId,
    val doseIndex: Int,
    val scheduledTime: LocalTime,
    val label: String,
    val slotPeriod: String, // e.g. "Morning", "Afternoon", "Evening", "Bedtime"
    val chainedKetogateDoseIndex: Int? = null
)

object ScheduleSlots {
    val ALL_SLOTS = listOf(
        // Morning: Mulmin tablet & Ketogate dose 1
        DoseSlot(MedicationId.MULMIN, 0, LocalTime.of(8, 0), "Morning Tablet", "Morning"),
        DoseSlot(MedicationId.KETOGATE, 0, LocalTime.of(8, 0), "Dose 1 (Morning)", "Morning"),
        DoseSlot(MedicationId.AQUAWELL, 0, LocalTime.of(8, 10), "Dose 1 (10m after Ketogate)", "Morning", chainedKetogateDoseIndex = 0),

        // Afternoon: Ketogate dose 2 & Aquawell dose 2
        DoseSlot(MedicationId.KETOGATE, 1, LocalTime.of(14, 0), "Dose 2 (Afternoon)", "Afternoon"),
        DoseSlot(MedicationId.AQUAWELL, 1, LocalTime.of(14, 10), "Dose 2 (10m after Ketogate)", "Afternoon", chainedKetogateDoseIndex = 1),

        // Evening: Ketogate dose 3 & Aquawell dose 3
        DoseSlot(MedicationId.KETOGATE, 2, LocalTime.of(20, 0), "Dose 3 (Evening)", "Evening"),
        DoseSlot(MedicationId.AQUAWELL, 2, LocalTime.of(20, 10), "Dose 3 (10m after Ketogate)", "Evening", chainedKetogateDoseIndex = 2),

        // Bedtime: Aquawell dose 4
        DoseSlot(MedicationId.AQUAWELL, 3, LocalTime.of(22, 0), "Dose 4 (Bedtime)", "Bedtime", chainedKetogateDoseIndex = null)
    )
}

enum class ActiveTimerStatus {
    IDLE,
    RUNNING,
    FINISHED_AQUAWELL_DUE
}

data class ActiveTimerState(
    val status: ActiveTimerStatus = ActiveTimerStatus.IDLE,
    val aquawellDoseIndex: Int = -1,
    val ketogateDoseIndex: Int = -1,
    val dateIso: String = "",
    val startTimeMillis: Long = 0L,
    val finishTimeMillis: Long = 0L,
    val durationMillis: Long = 10 * 60 * 1000L // 10 minutes
) {
    fun getEffectiveStatus(now: Long = System.currentTimeMillis()): ActiveTimerStatus {
        return when (status) {
            ActiveTimerStatus.RUNNING -> {
                if (now >= finishTimeMillis && finishTimeMillis > 0L) {
                    ActiveTimerStatus.FINISHED_AQUAWELL_DUE
                } else {
                    ActiveTimerStatus.RUNNING
                }
            }
            else -> status
        }
    }

    fun remainingMillis(now: Long = System.currentTimeMillis()): Long {
        return when (getEffectiveStatus(now)) {
            ActiveTimerStatus.IDLE -> 0L
            ActiveTimerStatus.RUNNING -> {
                val rem = finishTimeMillis - now
                if (rem > 0L) rem else 0L
            }
            ActiveTimerStatus.FINISHED_AQUAWELL_DUE -> 0L
        }
    }

    fun formattedRemaining(now: Long = System.currentTimeMillis()): String {
        val rem = remainingMillis(now)
        val totalSec = (rem + 999L) / 1000L
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }

    fun progressFraction(now: Long = System.currentTimeMillis()): Float {
        return when (getEffectiveStatus(now)) {
            ActiveTimerStatus.IDLE -> 0f
            ActiveTimerStatus.FINISHED_AQUAWELL_DUE -> 1f
            ActiveTimerStatus.RUNNING -> {
                if (durationMillis <= 0L) return 1f
                val elapsed = (now - startTimeMillis).coerceAtLeast(0L)
                (elapsed.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
}

data class DoseItem(
    val medicationId: MedicationId,
    val doseIndex: Int,
    val date: LocalDate,
    val scheduledTime: LocalTime,
    val label: String,
    val slotPeriod: String,
    val isTaken: Boolean,
    val takenTimeMillis: Long?,
    val isTimerActive: Boolean,
    val isTimerExpiredDue: Boolean,
    val chainedKetogateDoseIndex: Int?
) {
    val doseKey: String = "${date}_${medicationId.name}_$doseIndex"

    val timeFormatted: String
        get() = scheduledTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

    val takenTimeFormatted: String?
        get() {
            if (takenTimeMillis == null || takenTimeMillis <= 0L) return null
            val instant = java.time.Instant.ofEpochMilli(takenTimeMillis)
            val zdt = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
            return zdt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        }
}
