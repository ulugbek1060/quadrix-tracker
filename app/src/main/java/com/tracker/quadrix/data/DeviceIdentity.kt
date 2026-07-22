package com.tracker.quadrix.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.tracker.quadrix.BuildConfig
import com.tracker.quadrix.data.api.DeviceInfo

/**
 * Identifies the device to the backend.
 *
 * The IMEI is reported when the platform allows it. Since Android 10 (API 29) reading it
 * requires READ_PRIVILEGED_PHONE_STATE, which is only held by apps that are the **device owner**
 * (MDM-provisioned), platform-signed, or carrier-privileged — the intended setup for a
 * company-owned fleet tablet. On an ordinary install [autoImei] is null and
 * [imeiUnavailableReason] explains why; a manually entered IMEI is used as a fallback (see
 * [toDeviceInfo]).
 *
 * ANDROID_ID ([deviceId]) is always available and is the identifier the backend can rely on:
 * no permission, stable across reboots and app updates, unique per device + app signing key,
 * reset only by a factory reset.
 */
class DeviceIdentity(context: Context) {

    private val appContext = context.applicationContext

    @SuppressLint("HardwareIds")
    val deviceId: String =
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"

    /** IMEI read from the platform, or null when the platform refuses (the usual case). */
    val autoImei: String? get() = readImei()

    /** Why [autoImei] is null, phrased for display. Null when an IMEI was obtained. */
    val imeiUnavailableReason: String?
        get() {
            if (autoImei != null) return null
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    "Android ${Build.VERSION.RELEASE} restricts IMEI to device-owner (MDM), " +
                        "carrier and system apps"

                !hasPhonePermission() -> "Phone permission not granted"
                else -> "Device did not report an IMEI"
            }
        }

    fun toDeviceInfo(manualImei: String? = null): DeviceInfo = DeviceInfo(
        deviceId = deviceId,
        imei = autoImei ?: manualImei?.takeIf { it.isNotBlank() },
        imeiSource = when {
            autoImei != null -> "platform"
            !manualImei.isNullOrBlank() -> "manual"
            else -> "unavailable"
        },
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        osVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        appVersion = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
    )

    private fun hasPhonePermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Attempts the read on every API level rather than short-circuiting on 29+.
     *
     * On an ordinary Android 10+ install this always throws SecurityException and returns null —
     * that is the platform's decision, not something the app can opt out of. It is still worth
     * attempting, because the same call succeeds unchanged on a device where the app is the
     * device owner, is platform-signed, or holds carrier privileges — the fleet-tablet case.
     */
    @SuppressLint("HardwareIds", "MissingPermission")
    private fun readImei(): String? {
        if (!hasPhonePermission()) return null

        return runCatching {
            val telephony = appContext.getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephony?.imei
            } else {
                @Suppress("DEPRECATION")
                telephony?.deviceId
            }
        }.getOrElse { error ->
            Log.i(TAG, "IMEI unavailable: ${error.javaClass.simpleName}")
            null
        }?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "DeviceIdentity"
    }
}
