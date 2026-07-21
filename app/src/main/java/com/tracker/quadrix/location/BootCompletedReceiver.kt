package com.tracker.quadrix.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tracker.quadrix.data.SessionManager

/**
 * Restarts tracking after a reboot or an app update, but only if the user was tracking when
 * the device went down and is still signed in. After a logout the session is empty, so
 * nothing restarts.
 *
 * BOOT_COMPLETED is one of the exemptions that still permits starting a foreground service
 * from the background on Android 12+.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isRestartTrigger = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_QUICKBOOT_POWERON ||
            action == ACTION_HTC_QUICKBOOT_POWERON
        if (!isRestartTrigger) return

        val session = SessionManager(context)
        if (!session.trackingEnabled || session.authToken == null) return

        LocationTrackingService.start(context)
        TrackerWatchdog.schedule(context)
    }

    private companion object {
        // Some OEM ROMs (Xiaomi, HTC) send these instead of BOOT_COMPLETED on a fast boot.
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
