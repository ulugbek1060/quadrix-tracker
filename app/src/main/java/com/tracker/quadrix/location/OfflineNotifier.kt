package com.tracker.quadrix.location

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
 * Posts the "internet is off" notification while the background service is running without a
 * connection.
 *
 * Distinct from the silent tracking notification: this one is meant to be seen, so it uses a
 * DEFAULT-importance channel. The service throttles how often it is (re)posted — see
 * [LocationTrackingService] — so the user is reminded at most every 30 minutes while offline, and
 * it is [clear]ed the moment connectivity returns.
 */
object OfflineNotifier {

    private const val CHANNEL_ID = "connectivity"
    private const val NOTIFICATION_ID = 1003

    fun notifyOffline(context: Context) {
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
            .setContentTitle(context.getString(R.string.offline_notification_title))
            .setContentText(context.getString(R.string.offline_notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.offline_notification_text))
            )
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
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
            context.getString(R.string.offline_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.offline_channel_description)
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
