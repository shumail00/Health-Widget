package com.shumail.healthwidget.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shumail.healthwidget.data.TimerRepository
import com.shumail.healthwidget.model.TimerStatus
import com.shumail.healthwidget.notification.NotificationHelper

class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val repository = TimerRepository.getInstance(context)

        when (intent?.action) {
            ACTION_TIMER_FINISHED -> {
                val current = repository.getTimerModel()
                // Only notify if timer was running
                if (current.status == TimerStatus.RUNNING || current.getEffectiveStatus() == TimerStatus.FINISHED) {
                    repository.finishTimer()
                    NotificationHelper.showTimerFinishedNotification(context)
                }
            }
            ACTION_RESET_FROM_NOTIFICATION -> {
                repository.resetTimer()
                NotificationHelper.dismissNotification(context)
            }
        }
    }

    companion object {
        const val ACTION_TIMER_FINISHED = "com.shumail.healthwidget.ACTION_TIMER_FINISHED"
        const val ACTION_RESET_FROM_NOTIFICATION = "com.shumail.healthwidget.ACTION_RESET_FROM_NOTIFICATION"
    }
}
