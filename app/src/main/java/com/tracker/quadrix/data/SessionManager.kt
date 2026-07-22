package com.tracker.quadrix.data

import android.content.Context
import androidx.core.content.edit

/**
 * Everything the app persists about the signed-in user. Deliberately tiny, so that
 * [clear] is a complete wipe rather than a best-effort one.
 */
class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit { putString(KEY_EMAIL, value) }

    /** Bearer token from the login endpoint. Its presence is what "logged in" means. */
    var authToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit { putString(KEY_USER_ID, value) }

    /** IMEI typed in at login, used when the platform will not report one. */
    var manualImei: String?
        get() = prefs.getString(KEY_MANUAL_IMEI, null)
        set(value) = prefs.edit { putString(KEY_MANUAL_IMEI, value) }

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
        const val KEY_EMAIL = "user_email"
        const val KEY_TOKEN = "auth_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_MANUAL_IMEI = "manual_imei"
        const val KEY_TRACKING = "tracking_enabled"
        const val KEY_LAST_UPLOAD = "last_upload_at"
        const val KEY_LAST_LOCATION = "last_known_location"
        const val KEY_UPLOAD_COUNT = "upload_count"
    }
}
