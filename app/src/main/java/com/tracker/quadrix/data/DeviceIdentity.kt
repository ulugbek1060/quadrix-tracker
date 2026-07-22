package com.tracker.quadrix.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/**
 * Identifies the device to the backend.
 *
 * ANDROID_ID is the identifier the backend keys on (sent as `device_id` at verify-otp and on
 * every location upload): no permission required, stable across reboots and app updates, unique
 * per device + app signing key, reset only by a factory reset. Note the signing-key scoping — a
 * debug build and a release build report different IDs.
 */
class DeviceIdentity(context: Context) {

    private val appContext = context.applicationContext

    @SuppressLint("HardwareIds")
    val deviceId: String =
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
}
