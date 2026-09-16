package com.apkstudio.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.apkstudio.app.MainActivity
import com.apkstudio.app.R

object BuildNotifications {
    private const val CHANNEL_ID = "apkstudio_builds"
    private const val NOTIF_ID = 1001

    fun notifyFinished(context: Context, success: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = context.getString(R.string.notif_channel_desc)
        manager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(
                context.getString(
                    if (success) R.string.notif_success_title else R.string.notif_fail_title
                )
            )
            .setContentText(
                context.getString(
                    if (success) R.string.notif_success_text else R.string.notif_fail_text
                )
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // Notifications permission not granted — ignore.
        }
    }
}
