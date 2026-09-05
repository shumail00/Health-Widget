package com.shumail.healthwidget.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shumail.healthwidget.model.MedicationId
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object TimerAlarmManager {
    private const val REQ_AQUAWELL_TIMER = 1001
    private const val REQ_AQUAWELL_REPEAT = 1002
    private const val BASE_REQ_DAILY_DOSE = 2000

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    fun scheduleAquawellTimerAlarm(context: Context, finishTimeMillis: Long, aquawellDoseIndex: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_TIMER_FINISHED
            putExtra(TimerAlarmReceiver.EXTRA_DOSE_INDEX, aquawellDoseIndex)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_AQUAWELL_TIMER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(alarmManager, finishTimeMillis, pendingIntent, context)
    }

    fun cancelAquawellTimerAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_TIMER_FINISHED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_AQUAWELL_TIMER,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleAquawellRepeatAlarm(context: Context, triggerTimeMillis: Long, aquawellDoseIndex: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_AQUAWELL_REPEAT_REMINDER
            putExtra(TimerAlarmReceiver.EXTRA_DOSE_INDEX, aquawellDoseIndex)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_AQUAWELL_REPEAT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(alarmManager, triggerTimeMillis, pendingIntent, context)
    }

    fun cancelAquawellRepeatAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_AQUAWELL_REPEAT_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_AQUAWELL_REPEAT,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleDailyDoseAlarm(
        context: Context,
        date: LocalDate,
        time: LocalTime,
        medicationId: MedicationId,
        doseIndex: Int
    ) {
        // Never schedule for MULMIN
        if (medicationId == MedicationId.MULMIN) return

        val zonedDateTime = date.atTime(time).atZone(ZoneId.systemDefault())
        val triggerTimeMillis = zonedDateTime.toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (triggerTimeMillis <= now) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = BASE_REQ_DAILY_DOSE + (medicationId.ordinal * 10) + doseIndex

        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_DOSE_ALARM
            putExtra(TimerAlarmReceiver.EXTRA_MEDICATION, medicationId.name)
            putExtra(TimerAlarmReceiver.EXTRA_DOSE_INDEX, doseIndex)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(alarmManager, triggerTimeMillis, pendingIntent, context)
    }

    fun cancelAllDoseAlarms(context: Context) {
        cancelAquawellTimerAlarm(context)
        cancelAquawellRepeatAlarm(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        for (medId in listOf(MedicationId.KETOGATE, MedicationId.AQUAWELL)) {
            for (doseIdx in 0..4) {
                val requestCode = BASE_REQ_DAILY_DOSE + (medId.ordinal * 10) + doseIdx
                val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
                    action = TimerAlarmReceiver.ACTION_DOSE_ALARM
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }
    }

    private fun setAlarm(
        alarmManager: AlarmManager,
        triggerTimeMillis: Long,
        pendingIntent: PendingIntent,
        context: Context
    ) {
        try {
            if (canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } catch (_: Exception) {
            }
        }
    }
}
