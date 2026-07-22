package com.tracker.quadrix.data.api

import com.tracker.quadrix.BuildConfig

/**
 * The backend contract, per doc/api-doc.md. The base URL comes from `API_BASE_URL` in
 * app/build.gradle.kts and must end with a trailing slash; the paths below are appended to it.
 *
 * Most responses are wrapped in the envelope `{ status, data, message, errors }` (see
 * [ApiEnvelope]). Updates are driven by the dedicated, unenveloped version endpoint below —
 * polled in the background — not by the other responses; see UpdateManager.
 *
 *   POST {base}api/tablet/auth/request-otp/   { email }
 *   POST {base}api/tablet/auth/verify-otp/    { email, code, device_id } -> { tokens, user, … }
 *   GET  {base}api/tablet/auth/me/            (Bearer) -> current user
 *   POST {base}api/tablet/location/           (Bearer) { device_id, latitude, longitude, … }
 *   POST {base}api/token/refresh/             { refresh } -> { access }
 *   GET  {base}api/tablet/app/version/        -> { version, download_url }   (flat, no envelope)
 */
object ApiConfig {

    val baseUrl: String = BuildConfig.API_BASE_URL

    const val REQUEST_OTP_PATH = "api/tablet/auth/request-otp/"
    const val VERIFY_OTP_PATH = "api/tablet/auth/verify-otp/"
    const val ME_PATH = "api/tablet/auth/me/"
    const val LOCATION_PATH = "api/tablet/location/"
    const val TOKEN_REFRESH_PATH = "api/token/refresh/"
    const val APP_VERSION_PATH = "api/tablet/app/version/"

    /** Whether the base URL still points at the placeholder host. */
    val isPlaceholder: Boolean get() = baseUrl.contains("api.example.com")
}
