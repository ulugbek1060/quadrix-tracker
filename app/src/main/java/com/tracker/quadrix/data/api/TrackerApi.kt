package com.tracker.quadrix.data.api

import android.util.Log
import com.tracker.quadrix.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Raised when the server answers with a non-2xx status. */
class ApiException(val code: Int, message: String) : IOException(message)

/** Raised when the session can no longer be recovered — refresh failed or is missing. */
class SessionExpiredException(message: String = "Session expired") : IOException(message)

/**
 * OkHttp client for the contract in [ApiConfig]. Hand-rolled rather than Retrofit: a handful of
 * endpoints, all sharing the `{ status, data, … }` envelope, so keeping the request/response
 * plumbing visible is cheaper than a code-gen layer.
 *
 * Two cross-cutting concerns are handled centrally:
 *
 *  - **Token refresh.** Authenticated calls carry the access token; on a 401 the [Authenticator]
 *    transparently exchanges the refresh token for a new access token and replays the request.
 *    When refresh itself fails, [onSessionExpired] fires and the call surfaces
 *    [SessionExpiredException].
 *
 * Update checks are not piggybacked here — they use the dedicated `app/version/` endpoint that
 * UpdateManager polls directly.
 */
class TrackerApi(
    private val accessTokenProvider: () -> String?,
    private val refreshTokenProvider: () -> String? = { null },
    private val onTokensRefreshed: (access: String) -> Unit = {},
    private val onSessionExpired: () -> Unit = {},
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Bare client used only by the refresh exchange, so it never triggers the authenticator. */
    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Retry the request when a pooled connection turns out to be stale — common on mobile
        // where the radio sleeps between our 5-minute uploads.
        .retryOnConnectionFailure(true)
        .authenticator { route, response -> refreshAndRetry(route, response) }
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

    // ---- endpoints ----

    suspend fun requestOtp(email: String): RequestOtpData = withContext(Dispatchers.IO) {
        val body = json.encodeToString(RequestOtpBody(email))
        decode(post(ApiConfig.REQUEST_OTP_PATH, body, authenticated = false), RequestOtpData.serializer())
    }

    suspend fun verifyOtp(email: String, code: String, deviceId: String): VerifyOtpData =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(VerifyOtpBody(email, code, deviceId))
            decode(
                post(ApiConfig.VERIFY_OTP_PATH, body, authenticated = false),
                VerifyOtpData.serializer(),
            )
        }

    suspend fun me(): MeData = withContext(Dispatchers.IO) {
        decode(get(ApiConfig.ME_PATH, authenticated = true), MeData.serializer())
    }

    suspend fun uploadLocation(body: LocationUploadBody): LocationUploadData =
        withContext(Dispatchers.IO) {
            decode(
                post(ApiConfig.LOCATION_PATH, json.encodeToString(body), authenticated = true),
                LocationUploadData.serializer(),
            )
        }

    // ---- envelope handling ----

    /** Unwraps the envelope and returns the `data`, or fails if the server sent none. */
    private fun <T> decode(responseBody: String, serializer: KSerializer<T>): T {
        val envelope = json.decodeFromString(ApiEnvelope.serializer(serializer), responseBody)
        return envelope.data
            ?: throw ApiException(-1, envelope.message ?: "Empty response from server")
    }

    private fun post(path: String, body: String, authenticated: Boolean): String {
        val builder = requestBuilder(path, authenticated)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
        return execute(builder.build())
    }

    private fun get(path: String, authenticated: Boolean): String {
        val builder = requestBuilder(path, authenticated).get()
        return execute(builder.build())
    }

    private fun requestBuilder(path: String, authenticated: Boolean): Request.Builder {
        val builder = Request.Builder()
            .url(ApiConfig.baseUrl.trimEnd('/') + "/" + path)
            .header("Accept", "application/json")
        if (authenticated) {
            val token = accessTokenProvider()
                ?: throw SessionExpiredException("Not authenticated")
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // A 401 the authenticator could not recover from means the session is gone.
                if (response.code == 401) {
                    onSessionExpired()
                    throw SessionExpiredException(errorMessage(response.code, responseBody))
                }
                throw ApiException(response.code, errorMessage(response.code, responseBody))
            }
            return responseBody
        }
    }

    // ---- token refresh ----

    /**
     * OkHttp authenticator: fired on a 401. Exchanges the refresh token for a fresh access
     * token and replays the original request with it. Returns null (giving up) when there is no
     * refresh token, the refresh call fails, or we have already retried once — [execute] then
     * turns the surfaced 401 into a [SessionExpiredException].
     */
    private fun refreshAndRetry(route: Route?, response: Response): Request? {
        // Only retry once: if the replay is itself a 401, responseCount is 2 and we bail.
        if (responseCount(response) >= 2) return null

        val refresh = refreshTokenProvider() ?: return null
        val newAccess = runCatching { performRefresh(refresh) }.getOrNull()
        if (newAccess == null) {
            onSessionExpired()
            return null
        }
        onTokensRefreshed(newAccess)
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    private fun performRefresh(refresh: String): String? {
        val request = Request.Builder()
            .url(ApiConfig.baseUrl.trimEnd('/') + "/" + ApiConfig.TOKEN_REFRESH_PATH)
            .header("Accept", "application/json")
            .post(json.encodeToString(TokenRefreshBody(refresh)).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        refreshClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Token refresh rejected: HTTP ${response.code}")
                return null
            }
            val body = response.body?.string().orEmpty()
            return extractAccessToken(body)
        }
    }

    /** The refresh response shape is not pinned down; accept `{access}` at the root or in `data`. */
    private fun extractAccessToken(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val direct = root["access"]?.jsonPrimitive?.contentOrNull()
        if (direct != null) return@runCatching direct
        root["data"]?.jsonObject?.get("access")?.jsonPrimitive?.contentOrNull()
    }.getOrNull()

    private fun errorMessage(code: Int, body: String): String = runCatching {
        json.decodeFromString(ApiEnvelope.serializer(JsonElement.serializer()), body).message
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val TAG = "TrackerApi"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (isString) content else content.takeIf { it.isNotBlank() }
