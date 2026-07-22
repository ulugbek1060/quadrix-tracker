package com.tracker.quadrix.data

import android.content.Context
import android.location.Location
import android.util.Log
import com.tracker.quadrix.data.api.ApiException
import com.tracker.quadrix.data.api.LocationPayload
import com.tracker.quadrix.data.api.LocationUploadBody
import com.tracker.quadrix.data.api.SessionExpiredException
import com.tracker.quadrix.data.api.TrackerApi
import com.tracker.quadrix.update.UpdateManager
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
 * Records fixes locally, then drains the queue to `POST /api/tablet/location/` one fix per
 * request (the endpoint takes a single position, not a batch).
 *
 * Recording and sending are separated on purpose: a fix is never lost because the network
 * happened to be down at that instant, and a reconnect flushes the whole backlog.
 */
class LocationRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val queue = LocationQueue(appContext)
    private val connectivity = ConnectivityObserver(appContext)
    private val identity = DeviceIdentity(appContext)
    private val api = TrackerApi(
        accessTokenProvider = { session.accessToken },
        refreshTokenProvider = { session.refreshToken },
        onTokensRefreshed = { access -> session.accessToken = access },
        onSessionExpired = {
            session.accessToken = null
            session.refreshToken = null
        },
    )

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
                heading = location.bearing,
                provider = location.provider,
                recordedAt = location.time,
                batteryPercent = BatteryLevel.read(appContext),
            )
        )
    }

    fun pendingCount(): Int = queue.size()

    fun clear() = queue.clear()

    suspend fun flush(): UploadResult = flushLock.withLock {
        session.accessToken ?: return@withLock UploadResult.Unauthorized("Not signed in")

        if (!connectivity.isOnline()) {
            return@withLock UploadResult.Offline
        }

        var sent = 0
        while (true) {
            val fix = queue.peek(1).firstOrNull() ?: break

            val response = try {
                api.uploadLocation(
                    LocationUploadBody(
                        deviceId = identity.deviceId,
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        heading = fix.heading,
                        speed = fix.speed,
                    )
                )
            } catch (e: SessionExpiredException) {
                Log.w(TAG, "Upload unauthorized", e)
                return@withLock UploadResult.Unauthorized("Session expired — sign in again")
            } catch (e: ApiException) {
                Log.w(TAG, "Upload rejected", e)
                // 403 keeps the fixes; they go out after the next successful login rather than
                // being dropped. (401 surfaces as SessionExpiredException above.)
                return@withLock if (e.code == 403) {
                    UploadResult.Unauthorized("Session expired — sign in again")
                } else {
                    UploadResult.Failed("HTTP ${e.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upload failed", e)
                return@withLock UploadResult.Failed(e.message ?: "Network error")
            }

            // The upload response carries the current app version. Feed it to the update gate so
            // a new release is caught on every upload (foreground → force-update screen,
            // background → notification), without a separate poll.
            response.appVersion?.let { UpdateManager.onServerVersion(it, apkUrl = null) }

            queue.removeFirst(1)
            sent++
        }

        if (sent > 0) {
            session.lastUploadAt = System.currentTimeMillis()
            session.uploadCount += sent
            UploadResult.Sent(sent)
        } else {
            UploadResult.NothingToSend
        }
    }

    private companion object {
        const val TAG = "LocationRepository"
    }
}
