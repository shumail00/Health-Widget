package com.shumail.healthwidget.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shumail.healthwidget.data.MedicationRepository
import com.shumail.healthwidget.model.ActiveTimerStatus
import com.shumail.healthwidget.model.MedicationCatalog
import com.shumail.healthwidget.model.MedicationId
import com.shumail.healthwidget.notification.NotificationHelper
import java.time.LocalDate

class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val repository = MedicationRepository.getInstance(context)
        val action = intent?.action ?: return

        when (action) {
            ACTION_TIMER_FINISHED -> {
                val timer = repository.getActiveTimer()
                val doseIndex = intent.getIntExtra(EXTRA_DOSE_INDEX, timer.aquawellDoseIndex)

                // Only alert if timer was running or effective status is FINISHED_AQUAWELL_DUE
                if (timer.status == ActiveTimerStatus.RUNNING || timer.getEffectiveStatus() == ActiveTimerStatus.FINISHED_AQUAWELL_DUE) {
                    repository.reconcileActiveTimer()
                    NotificationHelper.showAquawellDueNotification(context, doseIndex, isFromTimer = true)

                    // Schedule repeat reminder in 5 minutes until user marks taken
                    TimerAlarmManager.scheduleAquawellRepeatAlarm(
                        context,
                        System.currentTimeMillis() + 5 * 60 * 1000L,
                        doseIndex
                    )

                    repository.updateWidgets()
                }
            }

            ACTION_AQUAWELL_REPEAT_REMINDER -> {
                val timer = repository.getActiveTimer()
                val doseIndex = intent.getIntExtra(EXTRA_DOSE_INDEX, timer.aquawellDoseIndex)
                val todayDoses = repository.getDosesForDate(LocalDate.now())
                val aquawellDose = todayDoses.find { it.medicationId == MedicationId.AQUAWELL && it.doseIndex == doseIndex }

                // Check if still untaken and not expired
                if (aquawellDose != null && !aquawellDose.isTaken) {
                    NotificationHelper.showAquawellDueNotification(context, doseIndex, isFromTimer = true)

                    // Reschedule next repeat reminder in 5 minutes
                    TimerAlarmManager.scheduleAquawellRepeatAlarm(
                        context,
                        System.currentTimeMillis() + 5 * 60 * 1000L,
                        doseIndex
                    )
                } else {
                    // Already taken or invalid, cancel repeat
                    TimerAlarmManager.cancelAquawellRepeatAlarm(context)
                }
            }

            ACTION_DOSE_ALARM -> {
                val medName = intent.getStringExtra(EXTRA_MEDICATION) ?: return
                val doseIndex = intent.getIntExtra(EXTRA_DOSE_INDEX, 0)
                val medId = try {
                    MedicationId.valueOf(medName)
                } catch (_: Exception) {
                    return
                }

                // Never alert for MULMIN
                if (medId == MedicationId.MULMIN) return

                val today = LocalDate.now()
                val dayOfCourse = repository.getDayOfCourse(today)

                // Critical: Check expiration!
                if (!MedicationCatalog.isMedicationActive(medId, dayOfCourse)) {
                    return
                }

                val doses = repository.getDosesForDate(today)
                val dose = doses.find { it.medicationId == medId && it.doseIndex == doseIndex }

                if (dose != null && !dose.isTaken) {
                    if (medId == MedicationId.KETOGATE) {
                        NotificationHelper.showKetogateDueNotification(context, doseIndex)
                    } else if (medId == MedicationId.AQUAWELL) {
                        NotificationHelper.showAquawellDueNotification(context, doseIndex, isFromTimer = false)
                    }
                }
            }

            ACTION_MARK_TAKEN -> {
                val medName = intent.getStringExtra(EXTRA_MEDICATION) ?: return
                val doseIndex = intent.getIntExtra(EXTRA_DOSE_INDEX, 0)
                val medId = try {
                    MedicationId.valueOf(medName)
                } catch (_: Exception) {
                    return
                }

                repository.markDoseTaken(medId, doseIndex)

                if (medId == MedicationId.AQUAWELL) {
                    TimerAlarmManager.cancelAquawellRepeatAlarm(context)
                    NotificationHelper.dismissNotification(context, NotificationHelper.NOTIFICATION_ID_AQUAWELL)
                } else if (medId == MedicationId.KETOGATE) {
                    NotificationHelper.dismissNotification(context, NotificationHelper.NOTIFICATION_ID_KETOGATE)
                }
            }
        }
    }

    companion object {
        const val ACTION_TIMER_FINISHED = "com.shumail.healthwidget.ACTION_TIMER_FINISHED"
        const val ACTION_AQUAWELL_REPEAT_REMINDER = "com.shumail.healthwidget.ACTION_AQUAWELL_REPEAT_REMINDER"
        const val ACTION_DOSE_ALARM = "com.shumail.healthwidget.ACTION_DOSE_ALARM"
        const val ACTION_MARK_TAKEN = "com.shumail.healthwidget.ACTION_MARK_TAKEN"

        const val EXTRA_MEDICATION = "extra_medication"
        const val EXTRA_DOSE_INDEX = "extra_dose_index"
    }
}
