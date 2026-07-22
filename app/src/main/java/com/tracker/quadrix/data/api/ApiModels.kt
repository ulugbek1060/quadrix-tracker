package com.tracker.quadrix.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs for the backend contract in doc/api-doc.md.
 *
 * The auth/location endpoints answer with the envelope `{ status, data, message, errors }`, so
 * [ApiEnvelope] wraps their per-endpoint `data` payloads. Updates are handled separately by the
 * flat [AppVersionData] from `GET api/tablet/app/version/` — see UpdateManager.
 */

/** The uniform response envelope. `data` is the only endpoint-specific part. */
@Serializable
data class ApiEnvelope<T>(
    @SerialName("status") val status: String? = null,
    @SerialName("data") val data: T? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("errors") val errors: JsonElement? = null,
) {
    val isSuccess: Boolean get() = status.equals("success", ignoreCase = true)
}

// ---- app/version (dedicated update endpoint, not enveloped) ----

@Serializable
data class AppVersionData(
    @SerialName("version") val version: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
)

// ---- auth/request-otp ----

@Serializable
data class RequestOtpBody(
    @SerialName("email") val email: String,
)

@Serializable
data class RequestOtpData(
    @SerialName("email") val email: String? = null,
    /** Seconds until the emailed code stops being accepted. */
    @SerialName("expires_in") val expiresIn: Int = 0,
    /** Seconds the user must wait before a new code can be requested. */
    @SerialName("resend_after") val resendAfter: Int = 0,
)

// ---- auth/verify-otp ----

@Serializable
data class VerifyOtpBody(
    @SerialName("email") val email: String,
    @SerialName("code") val code: String,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class Tokens(
    @SerialName("access") val access: String,
    @SerialName("refresh") val refresh: String,
)

@Serializable
data class TabletUser(
    @SerialName("id") val id: Int? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("company_id") val companyId: Int? = null,
)

@Serializable
data class VerifyOtpData(
    @SerialName("tokens") val tokens: Tokens,
    @SerialName("user") val user: TabletUser? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("client_type") val clientType: String? = null,
)

// ---- auth/me ----

@Serializable
data class MeData(
    @SerialName("id") val id: Int? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("company_id") val companyId: Int? = null,
    @SerialName("client_type") val clientType: String? = null,
)

// ---- tablet/location ----

@Serializable
data class LocationUploadBody(
    @SerialName("device_id") val deviceId: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("heading") val heading: Float,
    @SerialName("speed") val speed: Float,
)

@Serializable
data class LocationUploadData(
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

// ---- token/refresh ----

@Serializable
data class TokenRefreshBody(
    @SerialName("refresh") val refresh: String,
)

/**
 * A single recorded fix, persisted in the offline queue. Sent one-per-request as
 * [LocationUploadBody]; the extra fields are kept locally so a fix survives an offline stretch
 * with everything the upload needs.
 */
@Serializable
data class LocationPayload(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("accuracy") val accuracy: Float,
    @SerialName("altitude") val altitude: Double,
    @SerialName("speed") val speed: Float,
    /** Compass bearing of travel in degrees; sent to the backend as `heading`. */
    @SerialName("heading") val heading: Float = 0f,
    @SerialName("provider") val provider: String? = null,
    /** Epoch millis of the fix itself, which may be older than when it was sent. */
    @SerialName("recorded_at") val recordedAt: Long,
    @SerialName("battery_percent") val batteryPercent: Int? = null,
)
