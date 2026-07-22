package com.tracker.quadrix.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tracker.quadrix.data.SessionManager
import java.util.concurrent.TimeUnit

/**
 * Periodic fallback update check for when the foreground [com.tracker.quadrix.location
 * .LocationTrackingService] is not running — i.e. tracking is off. While tracking is on, the
 * service already polls the version endpoint every 30 minutes, so this worker steps aside to
 * avoid duplicate checks.
 *
 * WorkManager's minimum periodic interval is 15 minutes and its work is deferred under Doze,
 * which is fine here: this is a best-effort background probe, not the 5-minute location cadence.
 * The periodic request survives reboots on its own, so scheduling it once with [KEEP] is enough.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The tracking service owns update checks while it runs; only act as the fallback.
        if (SessionManager(applicationContext).trackingEnabled) return Result.success()

        UpdateManager.refreshVersion()

        val update = UpdateManager.state.value
        if (update.required) {
            UpdateNotifier.notifyUpdateAvailable(applicationContext, update.availableVersion)
        } else {
            UpdateNotifier.clear(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "update-version-check"

        /** Idempotent: [ExistingPeriodicWorkPolicy.KEEP] leaves an already-scheduled job alone. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
