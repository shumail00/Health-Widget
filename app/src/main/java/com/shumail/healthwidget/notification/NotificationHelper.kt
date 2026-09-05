package com.shumail.healthwidget.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shumail.healthwidget.R
import com.shumail.healthwidget.alarm.TimerAlarmReceiver
import com.shumail.healthwidget.data.MedicationRepository
import com.shumail.healthwidget.model.MedicationId
import com.shumail.healthwidget.ui.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "eye_medication_drops_channel"
    const val NOTIFICATION_ID_KETOGATE = 2001
    const val NOTIFICATION_ID_AQUAWELL = 2002
    const val NOTIFICATION_ID_GENERIC = 2003

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Eye Drop Medication Reminders"
            val descriptionText = "Reminders for scheduled eye drops and 10-minute interval completion"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val soundUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.eye_chime}")
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 200, 100, 250)
                setSound(soundUri, audioAttributes)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showKetogateDueNotification(context: Context, doseIndex: Int) {
        if (!hasNotificationPermission(context)) return

        val repo = MedicationRepository.getInstance(context)
        if (!repo.isNotificationsEnabled()) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mark KETOGATE Taken -> starts 10m timer
        val markTakenIntent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_MARK_TAKEN
            putExtra(TimerAlarmReceiver.EXTRA_MEDICATION, MedicationId.KETOGATE.name)
            putExtra(TimerAlarmReceiver.EXTRA_DOSE_INDEX, doseIndex)
        }
        val markTakenPendingIntent = PendingIntent.getBroadcast(
            context,
            101 + doseIndex,
            markTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.eye_chime}")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eye_care)
            .setContentTitle("👁 KETOGATE is due")
            .setContentText("Your eye-drop dose is ready. 10m timer starts after taking.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Your KETOGATE eye-drop dose is ready. Marking it taken will start the 10-minute absorption timer before AQUAWELL.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_check_circle,
                "✓ Mark as Taken",
                markTakenPendingIntent
            )

        if (repo.isSoundEnabled()) {
            builder.setSound(soundUri)
        } else {
            builder.setSilent(true)
        }

        if (repo.isVibrationEnabled()) {
            builder.setVibrate(longArrayOf(0, 150, 100, 200, 100, 250))
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_KETOGATE, builder.build())
    }

    fun showAquawellDueNotification(context: Context, doseIndex: Int, isFromTimer: Boolean = true) {
        if (!hasNotificationPermission(context)) return

        val repo = MedicationRepository.getInstance(context)
        if (!repo.isNotificationsEnabled()) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            200,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markTakenIntent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_MARK_TAKEN
            putExtra(TimerAlarmReceiver.EXTRA_MEDICATION, MedicationId.AQUAWELL.name)
            putExtra(TimerAlarmReceiver.EXTRA_DOSE_INDEX, doseIndex)
        }
        val markTakenPendingIntent = PendingIntent.getBroadcast(
            context,
            201 + doseIndex,
            markTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.eye_chime}")

        val contentText = if (isFromTimer) {
            "10-minute interval complete. Ready for AQUAWELL eye drops."
        } else {
            "Scheduled AQUAWELL eye drop is ready."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eye_care)
            .setContentTitle("💧 AQUAWELL is due")
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText Please mark taken once drops have been administered.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_check_circle,
                "✓ Mark as Taken",
                markTakenPendingIntent
            )

        if (repo.isSoundEnabled()) {
            builder.setSound(soundUri)
        } else {
            builder.setSilent(true)
        }

        if (repo.isVibrationEnabled()) {
            builder.setVibrate(longArrayOf(0, 150, 100, 200, 100, 250))
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_AQUAWELL, builder.build())
    }

    fun dismissNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
