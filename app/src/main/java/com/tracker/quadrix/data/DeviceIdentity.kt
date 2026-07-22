package com.tracker.quadrix.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.tracker.quadrix.BuildConfig
import com.tracker.quadrix.data.api.DeviceInfo

/**
 * Identifies the device to the backend.
 *
 * Uses ANDROID_ID only. IMEI and other non-resettable hardware identifiers are intentionally
 * not read — Google Play restricts them, and ANDROID_ID is the supported identifier: no
 * permission, stable across reboots and app updates, unique per device + app signing key, reset
 * only by a factory reset. (Note the signing-key scoping — a debug build and a release build
 * report different IDs.)
 */
class DeviceIdentity(context: Context) {

    private val appContext = context.applicationContext

    @SuppressLint("HardwareIds")
    val deviceId: String =
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"

    fun toDeviceInfo(): DeviceInfo = DeviceInfo(
        deviceId = deviceId,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        osVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        appVersion = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
    )
}
