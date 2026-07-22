package com.tracker.quadrix.data

import android.content.Context
import com.tracker.quadrix.data.api.ApiException
import com.tracker.quadrix.data.api.LoginRequest
import com.tracker.quadrix.data.api.TrackerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class AuthRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val identity = DeviceIdentity(appContext)
    private val api = TrackerApi { session.authToken }

    val isLoggedIn: Boolean get() = session.authToken != null
    val currentUserEmail: String? get() = session.userEmail
    val deviceId: String get() = identity.deviceId

    /**
     * Signs in and registers this device in the same call — the backend gets the device
     * identity (ANDROID_ID, model, OS, app version) so it can tell which device the session
     * belongs to.
     */
    suspend fun signIn(
        email: String,
        password: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.login(
                LoginRequest(
                    email = email.trim(),
                    password = password,
                    device = identity.toDeviceInfo(),
                )
            )

            session.authToken = response.token
            session.userId = response.userId
            session.userEmail = response.email ?: email.trim()
            session.userEmail!!
        }.recoverCatching { error ->
            throw when {
                error is ApiException && error.code == 401 ->
                    IllegalArgumentException("Incorrect email or password.")

                error is ApiException && error.code >= 500 ->
                    IOException("Server error — try again shortly.")

                error is IOException ->
                    IOException("Could not reach the server.")

                else -> error
            }
        }
    }

    /**
     * Wipes every trace of the session from the device: the token, the user details, any
     * location fixes still queued for upload, and the app caches. Nothing survives that could
     * identify the previous user to the next one.
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
}
