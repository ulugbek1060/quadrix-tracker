package com.tracker.quadrix.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tracker.quadrix.data.SessionManager

/**
 * Periodic alarm that puts the tracking service back if something removed it.
 *
 * START_STICKY covers low-memory kills, but not an OEM battery manager or a user "force stop"
 * followed by a launch. The alarm is cheap — it fires every 15 minutes and does nothing at all
 * unless the user is signed in and tracking was on.
 */
object TrackerWatchdog {

    private const val TAG = "TrackerWatchdog"
    private const val REQUEST_CODE = 2001
    private const val INTERVAL_MS = 15 * 60 * 1000L

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + INTERVAL_MS
        val pendingIntent = pendingIntent(context)

        try {
            // An exact alarm grants a short allowlist window that permits starting a
            // foreground service from the background — the inexact variant does not, so it is
            // only a fallback for when exact alarms are not permitted.
            if (canScheduleExact(context, alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not schedule watchdog alarm", e)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun canScheduleExact(context: Context, alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WatchdogReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    class WatchdogReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = SessionManager(context)
            if (!session.trackingEnabled || session.authToken == null) {
                cancel(context)
                return
            }

            // Starting an already-running service is harmless: onStartCommand simply
            // re-requests location updates with the same callback, which replaces the old
            // request rather than adding a second one.
            LocationTrackingService.start(context)
            schedule(context)
        }
    }
}
