package com.tracker.quadrix.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tracker.quadrix.data.ConnectivityObserver
import com.tracker.quadrix.location.LocationPermissionStatus
import com.tracker.quadrix.location.LocationTrackingService
import com.tracker.quadrix.location.TrackerState
import com.tracker.quadrix.location.TrackerStatus
import com.tracker.quadrix.update.UpdateManager
import com.tracker.quadrix.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val connectivity = ConnectivityObserver(application)

    val online: StateFlow<Boolean> = connectivity.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), connectivity.isOnline())

    val trackerState: StateFlow<TrackerState> = TrackerStatus.state

    val updateState: StateFlow<UpdateState> = UpdateManager.state

    private val _permissions = MutableStateFlow(
        LocationPermissionStatus.read(application)
    )
    val permissions: StateFlow<LocationPermissionStatus> = _permissions.asStateFlow()

    /**
     * Re-reads the live permission grants and starts or stops tracking to match. Called on
     * every resume so that a change made in system Settings takes effect immediately — the app
     * only runs once "Allow all the time" is in place, and stops the instant it is revoked.
     */
    fun refreshPermissions() {
        val status = LocationPermissionStatus.read(getApplication())
        _permissions.value = status
        if (status.allGranted) {
            startTracking()
        }
    }

    fun startTracking() {
        LocationTrackingService.start(getApplication())
    }

    fun stopTracking() {
        LocationTrackingService.stop(getApplication())
    }

    /**
     * Launch-time mandatory-update check. Polls the version endpoint (unauthenticated, so it runs
     * on the login screen too); a newer advertised version flips [updateState].required and
     * blocks the app.
     */
    fun enforceUpdate() {
        viewModelScope.launch { UpdateManager.refreshVersion() }
    }

    fun startForcedUpdate() {
        viewModelScope.launch { UpdateManager.downloadAndInstall(getApplication()) }
    }

    /**
     * Battery optimisation is the single biggest cause of tracking dying overnight: while the
     * app is optimised, Doze can freeze it and Android 12+ blocks the watchdog from restarting
     * the service from the background.
     */
    fun isBatteryOptimised(): Boolean {
        val context = getApplication<Application>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
