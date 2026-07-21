package com.tracker.quadrix.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tracker.quadrix.MainActivity
import com.tracker.quadrix.R
import com.tracker.quadrix.data.ConnectivityObserver
import com.tracker.quadrix.data.LocationRepository
import com.tracker.quadrix.data.SessionManager
import com.tracker.quadrix.data.UploadResult
import com.tracker.quadrix.update.UpdateManager
import com.tracker.quadrix.update.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Foreground service that records one location fix every 5 minutes and POSTs it to the REST API.
 *
 * A foreground service rather than WorkManager because WorkManager's periodic minimum is
 * 15 minutes and its work is deferred under Doze — neither is compatible with a hard 5-minute
 * cadence. Staying alive indefinitely is handled by three independent mechanisms, since no
 * single one is reliable across OEMs:
 *
 *  - START_STICKY, so the system recreates the service after a low-memory kill;
 *  - [TrackerWatchdog], an alarm that restarts the service if it is gone;
 *  - [BootCompletedReceiver], for reboots and app updates.
 */
class LocationTrackingService : Service() {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val repository by lazy { LocationRepository(this) }
    private val connectivity by lazy { ConnectivityObserver(this) }
    private val session by lazy { SessionManager(this) }
    private val updateManager by lazy { UpdateManager() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Throttles the background update check to [UPDATE_CHECK_INTERVAL_MS], not every fix. */
    private var lastUpdateCheckAt = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleLocation(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // A reconnect is the moment a backlog can finally go out; do not make it wait for the
        // next 5-minute tick.
        scope.launch {
            connectivity.observe().collect { online ->
                if (online) flush()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_text_waiting)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            },
        )

        if (!hasLocationPermission()) {
            TrackerStatus.onError("Location permission missing")
            session.trackingEnabled = false
            stopTracking()
            return START_NOT_STICKY
        }

        if (session.authToken == null) {
            session.trackingEnabled = false
            stopTracking()
            return START_NOT_STICKY
        }

        startLocationUpdates()
        session.trackingEnabled = true
        TrackerStatus.setRunning(true)
        TrackerStatus.setPending(repository.pendingCount())
        TrackerWatchdog.schedule(this)

        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .setMaxUpdateDelayMillis(INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission", e)
            TrackerStatus.onError("Location permission missing")
            stopTracking()
        }
    }

    private fun handleLocation(location: Location) {
        val now = System.currentTimeMillis()

        // Persist before attempting the network: a fix recorded is a fix kept, regardless of
        // what the upload does next.
        repository.record(location)
        TrackerStatus.onFix(location.latitude, location.longitude, location.accuracy, now)
        TrackerStatus.setPending(repository.pendingCount())
        session.lastKnownLocation = "%.5f, %.5f".format(location.latitude, location.longitude)

        // Notification is intentionally never updated per fix: no flashing, no coordinates on
        // the lock screen, nothing that would announce a fix was taken. Collection is silent
        // apart from the single static notification the platform requires a background location
        // service to display.
        scope.launch { flush() }

        maybeCheckForUpdate()
    }

    /**
     * Checks App Distribution for a newer build while the app is in the background and, if one
     * is found, posts the "update required" notification. Opening the app from there hits the
     * launch-time mandatory-update gate. Throttled so it runs at most once per interval.
     */
    private fun maybeCheckForUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheckAt < UPDATE_CHECK_INTERVAL_MS) return
        lastUpdateCheckAt = now

        updateManager.checkForUpdate { available, version ->
            if (available) {
                UpdateNotifier.notifyUpdateAvailable(this, version)
            } else {
                UpdateNotifier.clear(this)
            }
        }
    }

    private suspend fun flush() {
        when (val result = repository.flush()) {
            is UploadResult.Sent -> TrackerStatus.onUploadSucceeded(
                timestamp = System.currentTimeMillis(),
                sent = result.count,
                pending = repository.pendingCount(),
            )

            is UploadResult.Offline -> TrackerStatus.onUploadFailed(null)
            is UploadResult.Unauthorized -> TrackerStatus.onUploadFailed(result.message)
            is UploadResult.Failed -> TrackerStatus.onUploadFailed(result.message)
            UploadResult.NothingToSend -> Unit
        }
    }

    /**
     * Only ever called once [ServiceCompat.startForeground] has run, so tearing the service
     * down here cannot trip the "started but never went foreground" watchdog.
     */
    private fun stopTracking() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        scope.cancel()
        TrackerStatus.setRunning(false)
        // trackingEnabled is deliberately left alone: if the system killed us, the watchdog
        // and the boot receiver need it to still read true so they can bring tracking back.
        super.onDestroy()
    }

    /**
     * The one notification the platform obliges a background location service to show. Built to
     * be as unobtrusive as allowed: a MIN-importance channel (no status-bar icon on API 24–25
     * behaviour, collapsed to the bottom of the shade elsewhere), silent, no timestamp, no
     * detail about what is being collected.
     *
     * It cannot be omitted — a foreground service without a notification is torn down by the
     * system. Genuinely notification-free collection requires device-owner provisioning.
     */
    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "LocationTracking"
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 1001

        /** The 5-minute reporting cadence. */
        const val INTERVAL_MS = 5 * 60 * 1000L

        /** How often the background service polls App Distribution for a mandatory update. */
        private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context) {
            SessionManager(context).trackingEnabled = true
            val intent = Intent(context, LocationTrackingService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android 12+ forbids starting a foreground service from the background in
                // some states; the watchdog retries once the app is visible again.
                Log.w(TAG, "Could not start tracking service", e)
            }
            TrackerWatchdog.schedule(context)
        }

        /** Intentional stop (logout). Clears the flag so nothing restarts tracking later. */
        fun stop(context: Context) {
            SessionManager(context).trackingEnabled = false
            TrackerWatchdog.cancel(context)
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
