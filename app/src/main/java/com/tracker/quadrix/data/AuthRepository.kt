package com.tracker.quadrix.data

import android.content.Context
import com.tracker.quadrix.data.api.ApiException
import com.tracker.quadrix.data.api.RequestOtpData
import com.tracker.quadrix.data.api.SessionExpiredException
import com.tracker.quadrix.data.api.TrackerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Owns the OTP sign-in (per doc/api-doc.md) and the logout wipe.
 *
 * The [TrackerApi] it builds is wired so that expired access tokens are refreshed and persisted
 * transparently, and a dead session clears the stored tokens.
 */
class AuthRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
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

    val isLoggedIn: Boolean get() = session.accessToken != null
    val currentUserEmail: String? get() = session.userEmail
    val currentUserName: String? get() = session.userName
    val deviceId: String get() = identity.deviceId

    /** Platform-reported IMEI where obtainable, else null. Shown on the status screen only. */
    val imei: String? get() = identity.autoImei
    val imeiIsFromPlatform: Boolean get() = identity.autoImei != null
    val imeiUnavailableReason: String? get() = identity.imeiUnavailableReason

    /** Asks the backend to email a verification code. */
    suspend fun requestOtp(email: String): Result<RequestOtpData> = withContext(Dispatchers.IO) {
        runCatching { api.requestOtp(email.trim()) }
            .recoverCatching { throw mapError(it) }
    }

    /**
     * Verifies the emailed code, registering this device by its [DeviceIdentity.deviceId]. On
     * success the access/refresh tokens and the user details are persisted and the session is
     * live. Returns the display name (or email) to show once signed in.
     */
    suspend fun verifyOtp(email: String, code: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val data = api.verifyOtp(email.trim(), code.trim(), identity.deviceId)
                session.accessToken = data.tokens.access
                session.refreshToken = data.tokens.refresh
                session.userEmail = data.user?.email ?: email.trim()
                session.userName = data.user?.name
                session.userId = data.user?.id?.toString()
                session.userName ?: session.userEmail!!
            }.recoverCatching { throw mapError(it) }
        }

    /**
     * Wipes every trace of the session from the device: tokens, user details, any location fixes
     * still queued for upload, and the app caches. Nothing survives that could identify the
     * previous user to the next one.
     */
    suspend fun signOutAndWipe() = withContext(Dispatchers.IO) {
        LocationQueue(appContext).clear()
        session.clear()

        runCatching {
            appContext.cacheDir.deleteRecursively()
            appContext.externalCacheDir?.deleteRecursively()
        }
        Unit
    }

    private fun mapError(error: Throwable): Throwable = when {
        error is ApiException && error.code in 400..499 ->
            IllegalArgumentException(error.message ?: "Invalid request.")
        error is ApiException && error.code >= 500 ->
            IOException("Server error — try again shortly.")
        error is SessionExpiredException ->
            IllegalStateException("Session expired — sign in again.")
        error is IOException ->
            IOException("Could not reach the server.")
        else -> error
    }
}
