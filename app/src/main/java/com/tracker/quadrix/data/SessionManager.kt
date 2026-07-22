package com.tracker.quadrix.data

import android.content.Context
import androidx.core.content.edit

/**
 * Everything the app persists about the signed-in user. Deliberately tiny, so that
 * [clear] is a complete wipe rather than a best-effort one.
 *
 * Auth is a pair of JWTs: the short-lived [accessToken] sent on every request, and the
 * [refreshToken] exchanged for a new access token when it expires (see TrackerApi). The
 * presence of [accessToken] is what "logged in" means.
 */
class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Bearer token from verify-otp; refreshed in place when it expires. */
    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit { putString(KEY_ACCESS, value) }

    /** Long-lived token used to mint a new [accessToken] via /api/token/refresh/. */
    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit { putString(KEY_REFRESH, value) }

    var userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit { putString(KEY_EMAIL, value) }

    var userName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit { putString(KEY_NAME, value) }

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit { putString(KEY_USER_ID, value) }

    var trackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRACKING, false)
        set(value) = prefs.edit { putBoolean(KEY_TRACKING, value) }

    var lastUploadAt: Long
        get() = prefs.getLong(KEY_LAST_UPLOAD, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_UPLOAD, value) }

    var lastKnownLocation: String?
        get() = prefs.getString(KEY_LAST_LOCATION, null)
        set(value) = prefs.edit { putString(KEY_LAST_LOCATION, value) }

    var uploadCount: Int
        get() = prefs.getInt(KEY_UPLOAD_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_UPLOAD_COUNT, value) }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val PREFS_NAME = "tracker_session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EMAIL = "user_email"
        const val KEY_NAME = "user_name"
        const val KEY_USER_ID = "user_id"
        const val KEY_TRACKING = "tracking_enabled"
        const val KEY_LAST_UPLOAD = "last_upload_at"
        const val KEY_LAST_LOCATION = "last_known_location"
        const val KEY_UPLOAD_COUNT = "upload_count"
    }
}
