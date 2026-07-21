package com.tracker.quadrix.data.api

import com.tracker.quadrix.BuildConfig

/**
 * >>> PLACEHOLDER <<<
 *
 * Everything about the backend contract is collected here so that swapping in the real API is
 * a matter of editing this file plus the DTOs in [ApiModels]. The base URL itself comes from
 * `API_BASE_URL` in app/build.gradle.kts.
 *
 * Expected contract:
 *
 *   POST {base}auth/login
 *     body:  { "email": "…", "password": "…", "device": { … } }
 *     200:   { "token": "…", "userId": "…", "email": "…" }
 *     401:   invalid credentials
 *
 *   POST {base}locations            (header: Authorization: Bearer <token>)
 *     body:  { "deviceId": "…", "locations": [ { … }, … ] }
 *     200/2xx: batch accepted; anything else is retried on the next cycle
 */
object ApiConfig {

    val baseUrl: String = BuildConfig.API_BASE_URL

    const val LOGIN_PATH = "auth/login"
    const val LOCATIONS_PATH = "locations"

    /** Whether the base URL still points at the placeholder host. */
    val isPlaceholder: Boolean get() = baseUrl.contains("api.example.com")
}
