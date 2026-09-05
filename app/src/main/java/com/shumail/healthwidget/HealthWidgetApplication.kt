package com.shumail.healthwidget

import android.app.Application
import com.shumail.healthwidget.notification.NotificationHelper

class HealthWidgetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}
