package com.shumail.healthwidget.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shumail.healthwidget.data.MedicationRepository
import com.shumail.healthwidget.model.ActiveTimerStatus
import com.shumail.healthwidget.notification.NotificationHelper

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = MedicationRepository.getInstance(context)
            val timer = repository.getActiveTimer()
            val now = System.currentTimeMillis()

            if (timer.status == ActiveTimerStatus.RUNNING) {
                if (now >= timer.finishTimeMillis) {
                    repository.reconcileActiveTimer()
                    NotificationHelper.showAquawellDueNotification(context, timer.aquawellDoseIndex, isFromTimer = true)
                    TimerAlarmManager.scheduleAquawellRepeatAlarm(
                        context,
                        System.currentTimeMillis() + 5 * 60 * 1000L,
                        timer.aquawellDoseIndex
                    )
                } else {
                    TimerAlarmManager.scheduleAquawellTimerAlarm(
                        context,
                        timer.finishTimeMillis,
                        timer.aquawellDoseIndex
                    )
                }
            }

            // Reschedule today's remaining alarms
            repository.scheduleUpcomingAlarms()
            repository.updateWidgets()
        }
    }
}
