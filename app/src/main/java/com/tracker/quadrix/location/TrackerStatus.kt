package com.tracker.quadrix.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Snapshot of what the tracking service is doing, surfaced on the main screen. */
data class TrackerState(
    val running: Boolean = false,
    val lastFixAt: Long = 0L,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastAccuracy: Float? = null,
    val lastSyncedAt: Long = 0L,
    val pendingUploads: Int = 0,
    val uploadCount: Int = 0,
    val lastError: String? = null,
)

/**
 * Process-wide bridge between the service and the UI. A StateFlow rather than a bound
 * service: the UI only ever reads, and it must survive the activity being recreated while
 * the service keeps running.
 */
object TrackerStatus {

    private val _state = MutableStateFlow(TrackerState())
    val state: StateFlow<TrackerState> = _state.asStateFlow()

    fun setRunning(running: Boolean) = _state.update {
        if (running) it.copy(running = true, lastError = null) else it.copy(running = false)
    }

    fun onFix(latitude: Double, longitude: Double, accuracy: Float, timestamp: Long) = _state.update {
        it.copy(
            lastFixAt = timestamp,
            lastLatitude = latitude,
            lastLongitude = longitude,
            lastAccuracy = accuracy,
            lastError = null,
        )
    }

    /** Pending is read back from the on-disk queue, not tracked incrementally. */
    fun setPending(pending: Int) = _state.update { it.copy(pendingUploads = pending) }

    fun onUploadSucceeded(timestamp: Long, sent: Int, pending: Int) = _state.update {
        it.copy(
            lastSyncedAt = timestamp,
            pendingUploads = pending,
            uploadCount = it.uploadCount + sent,
            lastError = null,
        )
    }

    /** A null message means "offline", which the UI already shows and should not repeat. */
    fun onUploadFailed(message: String?) = _state.update { it.copy(lastError = message) }

    fun onError(message: String) = _state.update { it.copy(lastError = message) }

    fun reset() {
        _state.value = TrackerState()
    }
}
