package com.tracker.quadrix.data.api

import com.tracker.quadrix.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Raised when the server answers with a non-2xx status. */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Hand-rolled OkHttp client rather than Retrofit: there are two endpoints, and keeping the
 * request construction visible makes it cheaper to adapt to whatever the real API turns out
 * to expect.
 */
class TrackerApi(private val tokenProvider: () -> String?) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Retry the request when a pooled connection turns out to be stale — common on mobile
        // where the radio sleeps between our 5-minute uploads.
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }
        .build()

    suspend fun login(request: LoginRequest): LoginResponse = withContext(Dispatchers.IO) {
        val response = post(ApiConfig.LOGIN_PATH, json.encodeToString(request), authenticated = false)
        json.decodeFromString(LoginResponse.serializer(), response)
    }

    suspend fun uploadLocations(request: LocationBatchRequest) = withContext(Dispatchers.IO) {
        post(ApiConfig.LOCATIONS_PATH, json.encodeToString(request), authenticated = true)
        Unit
    }

    private fun post(path: String, body: String, authenticated: Boolean): String {
        val builder = Request.Builder()
            .url(ApiConfig.baseUrl.trimEnd('/') + "/" + path)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")

        if (authenticated) {
            val token = tokenProvider()
                ?: throw ApiException(401, "Not authenticated")
            builder.header("Authorization", "Bearer $token")
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, "HTTP ${response.code}: $responseBody")
            }
            return responseBody
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
