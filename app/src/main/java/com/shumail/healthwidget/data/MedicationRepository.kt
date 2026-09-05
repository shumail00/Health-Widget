package com.shumail.healthwidget.data

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.appwidget.updateAll
import com.shumail.healthwidget.alarm.TimerAlarmManager
import com.shumail.healthwidget.model.ActiveTimerState
import com.shumail.healthwidget.model.ActiveTimerStatus
import com.shumail.healthwidget.model.DoseItem
import com.shumail.healthwidget.model.MedicationCatalog
import com.shumail.healthwidget.model.MedicationDef
import com.shumail.healthwidget.model.MedicationId
import com.shumail.healthwidget.model.ScheduleSlots
import com.shumail.healthwidget.notification.NotificationHelper
import com.shumail.healthwidget.widget.EyeCareWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class TodayAdherence(
    val totalDoses: Int,
    val completedDoses: Int,
    val isCourseActive: Boolean,
    val dayOfCourse: Int,
    val activeTimer: ActiveTimerState,
    val nextDoseItem: DoseItem?
) {
    val progressFraction: Float
        get() = if (totalDoses > 0) (completedDoses.toFloat() / totalDoses.toFloat()).coerceIn(0f, 1f) else 0f

    val percentString: String
        get() = "${(progressFraction * 100).toInt()}%"
}

data class DaySummary(
    val date: LocalDate,
    val dayOfCourse: Int,
    val totalScheduled: Int,
    val totalCompleted: Int,
    val isFuture: Boolean,
    val isToday: Boolean,
    val isBeforeCourse: Boolean,
    val isAfterCourse: Boolean
) {
    val isFullyCompleted: Boolean get() = totalScheduled > 0 && totalCompleted >= totalScheduled
    val isPartiallyCompleted: Boolean get() = totalCompleted in 1 until totalScheduled
    val isMissed: Boolean get() = !isFuture && !isToday && totalScheduled > 0 && totalCompleted < totalScheduled
}

class MedicationRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dbHelper = MedicationDbHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _courseStartDateFlow = MutableStateFlow(loadCourseStartDate())
    val courseStartDateFlow: StateFlow<LocalDate> = _courseStartDateFlow.asStateFlow()

    private val _activeTimerFlow = MutableStateFlow(loadActiveTimer())
    val activeTimerFlow: StateFlow<ActiveTimerState> = _activeTimerFlow.asStateFlow()

    // Trigger state changes
    private val _refreshTrigger = MutableStateFlow(0L)
    val refreshTrigger: StateFlow<Long> = _refreshTrigger.asStateFlow()

    init {
        // Initialize default start date if missing
        if (!prefs.contains(KEY_COURSE_START_DATE)) {
            saveCourseStartDate(LocalDate.now())
        }
        reconcileActiveTimer()
    }

    private fun notifyChanged() {
        _refreshTrigger.value = System.currentTimeMillis()
        updateWidgets()
    }

    // ----------------------------------------------------
    // COURSE CONFIGURATION & DATES
    // ----------------------------------------------------

    fun getCourseStartDate(): LocalDate = _courseStartDateFlow.value

    fun setCourseStartDate(date: LocalDate) {
        saveCourseStartDate(date)
        _courseStartDateFlow.value = date
        // Reschedule alarms for upcoming doses
        scheduleUpcomingAlarms()
        notifyChanged()
    }

    fun restartCourse(startDate: LocalDate = LocalDate.now()) {
        cancelActiveTimer()
        dbHelper.clearAll()
        setCourseStartDate(startDate)
    }

    fun getDayOfCourse(date: LocalDate = LocalDate.now()): Int {
        return MedicationCatalog.calculateDayOfCourse(getCourseStartDate(), date)
    }

    // ----------------------------------------------------
    // ACTIVE 10-MINUTE TIMER STATE MACHINE
    // ----------------------------------------------------

    @Synchronized
    fun getActiveTimer(): ActiveTimerState {
        val current = loadActiveTimer()
        val effective = current.getEffectiveStatus()
        if (current.status == ActiveTimerStatus.RUNNING && effective == ActiveTimerStatus.FINISHED_AQUAWELL_DUE) {
            val due = current.copy(status = ActiveTimerStatus.FINISHED_AQUAWELL_DUE)
            saveActiveTimer(due)
            _activeTimerFlow.value = due
            return due
        }
        return current
    }

    @Synchronized
    fun startTimerForAquawell(
        aquawellDoseIndex: Int,
        ketogateDoseIndex: Int,
        dateIso: String = LocalDate.now().toString(),
        durationMillis: Long = 10 * 60 * 1000L
    ): ActiveTimerState {
        val now = System.currentTimeMillis()
        val finishTime = now + durationMillis

        val timer = ActiveTimerState(
            status = ActiveTimerStatus.RUNNING,
            aquawellDoseIndex = aquawellDoseIndex,
            ketogateDoseIndex = ketogateDoseIndex,
            dateIso = dateIso,
            startTimeMillis = now,
            finishTimeMillis = finishTime,
            durationMillis = durationMillis
        )
        saveActiveTimer(timer)
        _activeTimerFlow.value = timer

        // Schedule exact alarm for when 10 minutes finish
        TimerAlarmManager.scheduleAquawellTimerAlarm(context, finishTime, aquawellDoseIndex)

        notifyChanged()
        return timer
    }

    @Synchronized
    fun cancelActiveTimer() {
        TimerAlarmManager.cancelAquawellTimerAlarm(context)
        TimerAlarmManager.cancelAquawellRepeatAlarm(context)
        NotificationHelper.dismissNotification(context, NotificationHelper.NOTIFICATION_ID_AQUAWELL)

        val idle = ActiveTimerState(status = ActiveTimerStatus.IDLE)
        saveActiveTimer(idle)
        _activeTimerFlow.value = idle
        notifyChanged()
    }

    @Synchronized
    fun reconcileActiveTimer() {
        val current = getActiveTimer()
        _activeTimerFlow.value = current
    }

    // ----------------------------------------------------
    // DOSE RETRIEVAL & MANIPULATION
    // ----------------------------------------------------

    fun getDosesForDate(date: LocalDate): List<DoseItem> {
        val dayOfCourse = MedicationCatalog.calculateDayOfCourse(getCourseStartDate(), date)
        val mulminActive = MedicationCatalog.isMedicationActive(MedicationId.MULMIN, dayOfCourse)
        val ketogateActive = MedicationCatalog.isMedicationActive(MedicationId.KETOGATE, dayOfCourse)
        val aquawellActive = MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, dayOfCourse)

        val takenMap = dbHelper.getTakenDosesForDate(date.toString())
        val currentTimer = getActiveTimer()
        val isToday = date == LocalDate.now()

        val list = mutableListOf<DoseItem>()

        for (slot in ScheduleSlots.ALL_SLOTS) {
            val isActive = when (slot.medicationId) {
                MedicationId.MULMIN -> mulminActive
                MedicationId.KETOGATE -> ketogateActive
                MedicationId.AQUAWELL -> aquawellActive
            }
            if (!isActive) continue

            val key = Pair(slot.medicationId.name, slot.doseIndex)
            val isTaken = takenMap.containsKey(key)
            val takenTime = takenMap[key]

            val isTimerRunningForThis = isToday &&
                    slot.medicationId == MedicationId.AQUAWELL &&
                    currentTimer.aquawellDoseIndex == slot.doseIndex &&
                    currentTimer.getEffectiveStatus() == ActiveTimerStatus.RUNNING

            val isTimerDueForThis = isToday &&
                    slot.medicationId == MedicationId.AQUAWELL &&
                    currentTimer.aquawellDoseIndex == slot.doseIndex &&
                    currentTimer.getEffectiveStatus() == ActiveTimerStatus.FINISHED_AQUAWELL_DUE

            list.add(
                DoseItem(
                    medicationId = slot.medicationId,
                    doseIndex = slot.doseIndex,
                    date = date,
                    scheduledTime = slot.scheduledTime,
                    label = slot.label,
                    slotPeriod = slot.slotPeriod,
                    isTaken = isTaken,
                    takenTimeMillis = takenTime,
                    isTimerActive = isTimerRunningForThis,
                    isTimerExpiredDue = isTimerDueForThis,
                    chainedKetogateDoseIndex = slot.chainedKetogateDoseIndex
                )
            )
        }

        return list
    }

    fun markDoseTaken(
        medicationId: MedicationId,
        doseIndex: Int,
        date: LocalDate = LocalDate.now(),
        takenTimeMillis: Long = System.currentTimeMillis()
    ) {
        dbHelper.markDoseTaken(date.toString(), medicationId.name, doseIndex, takenTimeMillis)

        val dayOfCourse = getDayOfCourse(date)

        // If KETOGATE is marked taken today, check if not expired and start 10-minute timer for AQUAWELL
        if (date == LocalDate.now() && medicationId == MedicationId.KETOGATE) {
            // Dismiss KETOGATE notification
            NotificationHelper.dismissNotification(context, NotificationHelper.NOTIFICATION_ID_KETOGATE)

            // Check if AQUAWELL is active on this day
            if (MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, dayOfCourse)) {
                // doseIndex 0 -> AQUAWELL 0, doseIndex 1 -> AQUAWELL 1, doseIndex 2 -> AQUAWELL 2
                startTimerForAquawell(aquawellDoseIndex = doseIndex, ketogateDoseIndex = doseIndex)
            }
        } else if (date == LocalDate.now() && medicationId == MedicationId.AQUAWELL) {
            // If this AQUAWELL dose matches the active timer, finish the timer and cancel reminders
            val currentTimer = getActiveTimer()
            if (currentTimer.aquawellDoseIndex == doseIndex) {
                cancelActiveTimer()
            }
        }

        notifyChanged()
    }

    fun markDoseUntaken(
        medicationId: MedicationId,
        doseIndex: Int,
        date: LocalDate = LocalDate.now()
    ) {
        dbHelper.markDoseUntaken(date.toString(), medicationId.name, doseIndex)

        // If KETOGATE was untaken and a timer was running for its paired AQUAWELL, cancel timer
        if (medicationId == MedicationId.KETOGATE) {
            val timer = getActiveTimer()
            if (timer.ketogateDoseIndex == doseIndex) {
                cancelActiveTimer()
            }
        }

        notifyChanged()
    }

    fun resetTodayDoses() {
        val today = LocalDate.now().toString()
        cancelActiveTimer()
        dbHelper.clearDate(today)
        notifyChanged()
    }

    // ----------------------------------------------------
    // PROGRESS & ADHERENCE
    // ----------------------------------------------------

    fun getTodayAdherence(): TodayAdherence {
        val today = LocalDate.now()
        val doses = getDosesForDate(today)
        val dayOfCourse = getDayOfCourse(today)
        val isCourseActive = dayOfCourse in 1..60
        val completed = doses.count { it.isTaken }
        val activeTimer = getActiveTimer()

        // Find the next recommended action / dose
        val nextDose = when {
            activeTimer.getEffectiveStatus() == ActiveTimerStatus.FINISHED_AQUAWELL_DUE -> {
                doses.find { it.medicationId == MedicationId.AQUAWELL && it.doseIndex == activeTimer.aquawellDoseIndex }
            }
            activeTimer.getEffectiveStatus() == ActiveTimerStatus.RUNNING -> {
                doses.find { it.medicationId == MedicationId.AQUAWELL && it.doseIndex == activeTimer.aquawellDoseIndex }
            }
            else -> {
                doses.firstOrNull { !it.isTaken }
            }
        }

        return TodayAdherence(
            totalDoses = doses.size,
            completedDoses = completed,
            isCourseActive = isCourseActive,
            dayOfCourse = dayOfCourse,
            activeTimer = activeTimer,
            nextDoseItem = nextDose
        )
    }

    fun getDaySummary(date: LocalDate): DaySummary {
        val dayOfCourse = getDayOfCourse(date)
        val today = LocalDate.now()
        val doses = getDosesForDate(date)
        val completed = doses.count { it.isTaken }

        return DaySummary(
            date = date,
            dayOfCourse = dayOfCourse,
            totalScheduled = doses.size,
            totalCompleted = completed,
            isFuture = date.isAfter(today),
            isToday = date == today,
            isBeforeCourse = dayOfCourse < 1,
            isAfterCourse = dayOfCourse > 60
        )
    }

    fun getCourseOverallStats(): CourseStats {
        val startDate = getCourseStartDate()
        val today = LocalDate.now()
        val dayOfCourse = getDayOfCourse(today)

        val mulminTaken = dbHelper.getTotalTakenCountForMedication(MedicationId.MULMIN.name)
        val ketogateTaken = dbHelper.getTotalTakenCountForMedication(MedicationId.KETOGATE.name)
        val aquawellTaken = dbHelper.getTotalTakenCountForMedication(MedicationId.AQUAWELL.name)
        val totalTaken = mulminTaken + ketogateTaken + aquawellTaken

        // Total scheduled up to today
        var totalScheduledSoFar = 0
        var daysCounted = 0
        var cur = startDate
        while (!cur.isAfter(today) && daysCounted < 60) {
            val d = MedicationCatalog.calculateDayOfCourse(startDate, cur)
            if (d in 1..60) {
                var dailySlots = 0
                if (MedicationCatalog.isMedicationActive(MedicationId.MULMIN, d)) dailySlots += 1
                if (MedicationCatalog.isMedicationActive(MedicationId.KETOGATE, d)) dailySlots += 3
                if (MedicationCatalog.isMedicationActive(MedicationId.AQUAWELL, d)) dailySlots += 4
                totalScheduledSoFar += dailySlots
            }
            cur = cur.plusDays(1)
            daysCounted++
        }

        val totalCourseDoses = (30 * 1) + (14 * 3) + (60 * 4) // 30 + 42 + 240 = 312 doses

        return CourseStats(
            currentDayOfCourse = dayOfCourse,
            totalDosesTaken = totalTaken,
            totalDosesScheduledToDate = totalScheduledSoFar.coerceAtLeast(1),
            totalCourseDoses = totalCourseDoses,
            mulminTaken = mulminTaken,
            ketogateTaken = ketogateTaken,
            aquawellTaken = aquawellTaken
        )
    }

    // ----------------------------------------------------
    // SETTINGS / PREFERENCES
    // ----------------------------------------------------

    fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        if (!enabled) {
            TimerAlarmManager.cancelAllDoseAlarms(context)
        } else {
            scheduleUpcomingAlarms()
        }
        notifyChanged()
    }

    fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, true)
    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        notifyChanged()
    }

    // ----------------------------------------------------
    // ALARM SCHEDULING
    // ----------------------------------------------------

    fun scheduleUpcomingAlarms() {
        if (!isNotificationsEnabled()) return
        val today = LocalDate.now()
        val doses = getDosesForDate(today)
        val now = LocalTime.now()

        for (dose in doses) {
            // Never schedule alarm for MULMIN
            if (dose.medicationId == MedicationId.MULMIN) continue

            // Only schedule future untaken doses
            if (!dose.isTaken && dose.scheduledTime.isAfter(now)) {
                // If this is AQUAWELL with a chained ketogate, it will be triggered by timer
                // However if Ketogate is expired, AQUAWELL should trigger directly at its scheduled time
                val dayOfCourse = getDayOfCourse(today)
                val ketogateActive = MedicationCatalog.isMedicationActive(MedicationId.KETOGATE, dayOfCourse)

                if (dose.medicationId == MedicationId.KETOGATE || (!ketogateActive && dose.medicationId == MedicationId.AQUAWELL) || dose.doseIndex == 3) {
                    TimerAlarmManager.scheduleDailyDoseAlarm(
                        context = context,
                        date = today,
                        time = dose.scheduledTime,
                        medicationId = dose.medicationId,
                        doseIndex = dose.doseIndex
                    )
                }
            }
        }
    }

    // ----------------------------------------------------
    // WIDGET UPDATE
    // ----------------------------------------------------

    fun updateWidgets() {
        scope.launch {
            try {
                EyeCareWidget().updateAll(context)
            } catch (_: Exception) {
            }
        }
    }

    // ----------------------------------------------------
    // INTERNAL STORAGE HELPERS
    // ----------------------------------------------------

    private fun loadCourseStartDate(): LocalDate {
        val dateStr = prefs.getString(KEY_COURSE_START_DATE, null)
        return if (dateStr != null) {
            try {
                LocalDate.parse(dateStr)
            } catch (_: Exception) {
                LocalDate.now()
            }
        } else {
            LocalDate.now()
        }
    }

    private fun saveCourseStartDate(date: LocalDate) {
        prefs.edit().putString(KEY_COURSE_START_DATE, date.toString()).apply()
    }

    private fun loadActiveTimer(): ActiveTimerState {
        val statusStr = prefs.getString(KEY_TIMER_STATUS, ActiveTimerStatus.IDLE.name)
        val status = try {
            ActiveTimerStatus.valueOf(statusStr ?: ActiveTimerStatus.IDLE.name)
        } catch (_: Exception) {
            ActiveTimerStatus.IDLE
        }
        val aquaIdx = prefs.getInt(KEY_TIMER_AQUA_IDX, -1)
        val ketoIdx = prefs.getInt(KEY_TIMER_KETO_IDX, -1)
        val dateIso = prefs.getString(KEY_TIMER_DATE, "") ?: ""
        val start = prefs.getLong(KEY_TIMER_START, 0L)
        val finish = prefs.getLong(KEY_TIMER_FINISH, 0L)
        val dur = prefs.getLong(KEY_TIMER_DURATION, 10 * 60 * 1000L)

        return ActiveTimerState(
            status = status,
            aquawellDoseIndex = aquaIdx,
            ketogateDoseIndex = ketoIdx,
            dateIso = dateIso,
            startTimeMillis = start,
            finishTimeMillis = finish,
            durationMillis = dur
        )
    }

    private fun saveActiveTimer(state: ActiveTimerState) {
        prefs.edit()
            .putString(KEY_TIMER_STATUS, state.status.name)
            .putInt(KEY_TIMER_AQUA_IDX, state.aquawellDoseIndex)
            .putInt(KEY_TIMER_KETO_IDX, state.ketogateDoseIndex)
            .putString(KEY_TIMER_DATE, state.dateIso)
            .putLong(KEY_TIMER_START, state.startTimeMillis)
            .putLong(KEY_TIMER_FINISH, state.finishTimeMillis)
            .putLong(KEY_TIMER_DURATION, state.durationMillis)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "eye_medication_prefs"
        private const val KEY_COURSE_START_DATE = "key_course_start_date"

        private const val KEY_TIMER_STATUS = "key_timer_status"
        private const val KEY_TIMER_AQUA_IDX = "key_timer_aqua_idx"
        private const val KEY_TIMER_KETO_IDX = "key_timer_keto_idx"
        private const val KEY_TIMER_DATE = "key_timer_date"
        private const val KEY_TIMER_START = "key_timer_start"
        private const val KEY_TIMER_FINISH = "key_timer_finish"
        private const val KEY_TIMER_DURATION = "key_timer_duration"

        private const val KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "key_vibration_enabled"
        private const val KEY_THEME_MODE = "key_theme_mode"

        @Volatile
        private var instance: MedicationRepository? = null

        fun getInstance(context: Context): MedicationRepository {
            return instance ?: synchronized(this) {
                instance ?: MedicationRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

data class CourseStats(
    val currentDayOfCourse: Int,
    val totalDosesTaken: Int,
    val totalDosesScheduledToDate: Int,
    val totalCourseDoses: Int,
    val mulminTaken: Int,
    val ketogateTaken: Int,
    val aquawellTaken: Int
) {
    val overallAdherenceFraction: Float
        get() = if (totalDosesScheduledToDate > 0) {
            (totalDosesTaken.toFloat() / totalDosesScheduledToDate.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val overallAdherencePercent: String
        get() = "${(overallAdherenceFraction * 100).toInt()}%"
}
