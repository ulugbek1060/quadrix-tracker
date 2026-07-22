package com.tracker.quadrix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tracker.quadrix.data.ConnectivityObserver
import com.tracker.quadrix.location.LocationPermissionStatus
import com.tracker.quadrix.location.LocationTrackingService
import com.tracker.quadrix.location.TrackerState
import com.tracker.quadrix.location.TrackerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val connectivity = ConnectivityObserver(application)

    val online: StateFlow<Boolean> = connectivity.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), connectivity.isOnline())

    val trackerState: StateFlow<TrackerState> = TrackerStatus.state

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
}
