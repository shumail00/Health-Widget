package com.shumail.healthwidget.data

import android.content.Context
import android.content.SharedPreferences
import com.shumail.healthwidget.alarm.TimerAlarmManager
import com.shumail.healthwidget.model.TimerModel
import com.shumail.healthwidget.model.TimerStatus
import com.shumail.healthwidget.notification.NotificationHelper
import com.shumail.healthwidget.widget.EyeCareWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _timerFlow = MutableStateFlow(loadTimerModel())
    val timerFlow: StateFlow<TimerModel> = _timerFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Synchronize on startup
        refresh()
    }

    @Synchronized
    fun getTimerModel(): TimerModel {
        val model = loadTimerModel()
        val effectiveStatus = model.getEffectiveStatus()
        if (model.status == TimerStatus.RUNNING && effectiveStatus == TimerStatus.FINISHED) {
            // Auto-transition to FINISHED state
            val finishedModel = model.copy(status = TimerStatus.FINISHED)
            saveTimerModel(finishedModel)
            _timerFlow.value = finishedModel
            return finishedModel
        }
        return model
    }

    @Synchronized
    fun refresh() {
        _timerFlow.value = getTimerModel()
    }

    @Synchronized
    fun startTimer(durationMillis: Long = TimerModel.DEFAULT_DURATION_MILLIS): TimerModel {
        val current = getTimerModel()
        // Prevent accidental restart if already running with remaining time
        if (current.getEffectiveStatus() == TimerStatus.RUNNING && current.remainingMillis() > 0L) {
            return current
        }

        val now = System.currentTimeMillis()
        val finishTime = now + durationMillis
        val newModel = TimerModel(
            status = TimerStatus.RUNNING,
            startTimeMillis = now,
            finishTimeMillis = finishTime,
            durationMillis = durationMillis
        )
        saveTimerModel(newModel)
        _timerFlow.value = newModel

        // Schedule exact/inexact background alarm
        TimerAlarmManager.scheduleTimerAlarm(context, finishTime)

        // Update widget
        updateWidgets()

        return newModel
    }

    @Synchronized
    fun cancelTimer(): TimerModel {
        TimerAlarmManager.cancelTimerAlarm(context)
        NotificationHelper.dismissNotification(context)

        val newModel = TimerModel(
            status = TimerStatus.READY,
            startTimeMillis = 0L,
            finishTimeMillis = 0L,
            durationMillis = TimerModel.DEFAULT_DURATION_MILLIS
        )
        saveTimerModel(newModel)
        _timerFlow.value = newModel

        updateWidgets()
        return newModel
    }

    @Synchronized
    fun finishTimer(): TimerModel {
        TimerAlarmManager.cancelTimerAlarm(context)

        val current = loadTimerModel()
        val newModel = current.copy(status = TimerStatus.FINISHED)
        saveTimerModel(newModel)
        _timerFlow.value = newModel

        updateWidgets()
        return newModel
    }

    @Synchronized
    fun resetTimer(): TimerModel {
        return cancelTimer()
    }

    fun updateWidgets() {
        scope.launch {
            try {
                EyeCareWidget().updateAll(context)
            } catch (_: Exception) {
                // Widget might not be placed yet or glance update failed
            }
        }
    }

    private fun loadTimerModel(): TimerModel {
        val statusString = prefs.getString(KEY_STATUS, TimerStatus.READY.name) ?: TimerStatus.READY.name
        val status = try {
            TimerStatus.valueOf(statusString)
        } catch (_: Exception) {
            TimerStatus.READY
        }
        val startTime = prefs.getLong(KEY_START_TIME, 0L)
        val finishTime = prefs.getLong(KEY_FINISH_TIME, 0L)
        val duration = prefs.getLong(KEY_DURATION, TimerModel.DEFAULT_DURATION_MILLIS)

        return TimerModel(
            status = status,
            startTimeMillis = startTime,
            finishTimeMillis = finishTime,
            durationMillis = duration
        )
    }

    private fun saveTimerModel(model: TimerModel) {
        prefs.edit()
            .putString(KEY_STATUS, model.status.name)
            .putLong(KEY_START_TIME, model.startTimeMillis)
            .putLong(KEY_FINISH_TIME, model.finishTimeMillis)
            .putLong(KEY_DURATION, model.durationMillis)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "eye_care_timer_prefs"
        private const val KEY_STATUS = "key_timer_status"
        private const val KEY_START_TIME = "key_start_time"
        private const val KEY_FINISH_TIME = "key_finish_time"
        private const val KEY_DURATION = "key_duration"

        @Volatile
        private var instance: TimerRepository? = null

        fun getInstance(context: Context): TimerRepository {
            return instance ?: synchronized(this) {
                instance ?: TimerRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
