package com.tracker.quadrix.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tracker.quadrix.MainActivity
import com.tracker.quadrix.R

/**
 * Posts the "update required" notification when the background service finds a newer build.
 *
 * Distinct from the silent tracking notification: this one is meant to be seen, so it uses a
 * DEFAULT-importance channel. Tapping it opens the app, where the launch-time mandatory-update
 * check raises the blocking ForceUpdateScreen — so the notification and the in-app gate are two
 * doors into the same forced-update flow.
 */
object UpdateNotifier {

    private const val CHANNEL_ID = "app_update"
    private const val NOTIFICATION_ID = 1002

    fun notifyUpdateAvailable(context: Context, version: String?) {
        if (!canPostNotifications(context)) return
        createChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(
                version?.let { context.getString(R.string.update_notification_text_versioned, it) }
                    ?: context.getString(R.string.update_notification_text)
            )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOngoing(true) // Mandatory update — keep it in the shade until acted on.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
    }

    fun clear(context: Context) {
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.update_channel_description)
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
