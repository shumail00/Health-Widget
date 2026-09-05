package com.shumail.healthwidget.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shumail.healthwidget.data.TimerRepository
import com.shumail.healthwidget.model.TimerStatus
import com.shumail.healthwidget.notification.NotificationHelper

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = TimerRepository.getInstance(context)
            val timer = repository.getTimerModel()
            val now = System.currentTimeMillis()

            if (timer.status == TimerStatus.RUNNING) {
                if (now >= timer.finishTimeMillis) {
                    repository.finishTimer()
                    NotificationHelper.showTimerFinishedNotification(context)
                } else {
                    // Reschedule alarm for remaining duration
                    TimerAlarmManager.scheduleTimerAlarm(context, timer.finishTimeMillis)
                    repository.updateWidgets()
                }
            }
        }
    }
}
