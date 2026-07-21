package com.tracker.quadrix.data

import android.content.Context
import android.location.Location
import android.util.Log
import com.tracker.quadrix.data.api.ApiException
import com.tracker.quadrix.data.api.LocationBatchRequest
import com.tracker.quadrix.data.api.LocationPayload
import com.tracker.quadrix.data.api.TrackerApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface UploadResult {
    data class Sent(val count: Int) : UploadResult
    data object NothingToSend : UploadResult
    data object Offline : UploadResult
    data class Unauthorized(val message: String) : UploadResult
    data class Failed(val message: String) : UploadResult
}

/**
 * Records fixes locally, then drains the queue to the REST API.
 *
 * Recording and sending are separated on purpose: a fix is never lost because the network
 * happened to be down at that instant, and a reconnect flushes the whole backlog in batches.
 */
class LocationRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val queue = LocationQueue(appContext)
    private val connectivity = ConnectivityObserver(appContext)
    private val identity = DeviceIdentity(appContext)
    private val api = TrackerApi { session.authToken }

    /** Guards against a flush triggered by reconnect overlapping the 5-minute flush. */
    private val flushLock = Mutex()

    fun record(location: Location) {
        queue.add(
            LocationPayload(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                altitude = location.altitude,
                speed = location.speed,
                provider = location.provider,
                recordedAt = location.time,
                batteryPercent = BatteryLevel.read(appContext),
            )
        )
    }

    fun pendingCount(): Int = queue.size()

    fun clear() = queue.clear()

    suspend fun flush(): UploadResult = flushLock.withLock {
        val token = session.authToken
            ?: return@withLock UploadResult.Unauthorized("Not signed in")

        // Debug test account: pretend the server accepted the batch, so the whole flow can be
        // exercised without a backend.
        if (TestAccount.isActiveSession(token)) {
            return@withLock drainAsTestSession()
        }

        if (!connectivity.isOnline()) {
            return@withLock UploadResult.Offline
        }

        var sent = 0
        while (true) {
            val batch = queue.peek(BATCH_SIZE)
            if (batch.isEmpty()) break

            try {
                api.uploadLocations(
                    LocationBatchRequest(deviceId = identity.deviceId, locations = batch)
                )
            } catch (e: ApiException) {
                Log.w(TAG, "Upload rejected", e)
                // 401/403 means the token is dead — keep the fixes, they will go out after
                // the next successful login rather than being dropped.
                return@withLock if (e.code == 401 || e.code == 403) {
                    UploadResult.Unauthorized("Session expired — sign in again")
                } else {
                    UploadResult.Failed("HTTP ${e.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upload failed", e)
                return@withLock UploadResult.Failed(e.message ?: "Network error")
            }

            queue.removeFirst(batch.size)
            sent += batch.size
        }

        if (sent > 0) {
            session.lastUploadAt = System.currentTimeMillis()
            session.uploadCount += sent
            UploadResult.Sent(sent)
        } else {
            UploadResult.NothingToSend
        }
    }

    private fun drainAsTestSession(): UploadResult {
        val pending = queue.peek(Int.MAX_VALUE)
        if (pending.isEmpty()) return UploadResult.NothingToSend

        pending.forEach { fix ->
            Log.i(TAG, "TEST MODE — would POST ${fix.latitude}, ${fix.longitude} @ ${fix.recordedAt}")
        }
        queue.removeFirst(pending.size)

        session.lastUploadAt = System.currentTimeMillis()
        session.uploadCount += pending.size
        return UploadResult.Sent(pending.size)
    }

    private companion object {
        const val TAG = "LocationRepository"

        /** Small enough that a rejected batch costs little, big enough to drain a backlog. */
        const val BATCH_SIZE = 50
    }
}
