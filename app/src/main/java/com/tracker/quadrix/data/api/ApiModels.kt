package com.tracker.quadrix.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * >>> PLACEHOLDER SHAPES <<<
 *
 * Field names here are what gets serialised to JSON. When the real API arrives, rename with
 * @SerialName rather than renaming the Kotlin properties, so the rest of the app is untouched.
 */

@Serializable
data class DeviceInfo(
    /**
     * ANDROID_ID: stable across reboots and app updates, unique per device + app signing key,
     * resets only on factory reset. This is the device identity the backend should key on.
     */
    @SerialName("device_id") val deviceId: String,
    /**
     * Read from the platform on device-owner / platform-signed / carrier-privileged devices
     * (Android 9 and below too), otherwise whatever the operator typed in at login. Null when
     * neither is available.
     */
    @SerialName("imei") val imei: String? = null,
    /** "platform", "manual" or "unavailable" — so the server knows how far to trust [imei]. */
    @SerialName("imei_source") val imeiSource: String = "unavailable",
    @SerialName("manufacturer") val manufacturer: String,
    @SerialName("model") val model: String,
    @SerialName("os_version") val osVersion: String,
    @SerialName("sdk_int") val sdkInt: Int,
    @SerialName("app_version") val appVersion: String,
    @SerialName("app_version_code") val appVersionCode: Int,
)

@Serializable
data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("device") val device: DeviceInfo,
)

@Serializable
data class LoginResponse(
    @SerialName("token") val token: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("email") val email: String? = null,
)

@Serializable
data class LocationPayload(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("accuracy") val accuracy: Float,
    @SerialName("altitude") val altitude: Double,
    @SerialName("speed") val speed: Float,
    @SerialName("provider") val provider: String? = null,
    /** Epoch millis of the fix itself, which may be older than when it was sent. */
    @SerialName("recorded_at") val recordedAt: Long,
    @SerialName("battery_percent") val batteryPercent: Int? = null,
)

@Serializable
data class LocationBatchRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("locations") val locations: List<LocationPayload>,
)
