package com.tracker.quadrix.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.tracker.quadrix.BuildConfig
import com.tracker.quadrix.data.api.ApiConfig
import com.tracker.quadrix.data.api.AppVersionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateState(
    val checking: Boolean = false,
    /** The server's `app_version` when it is newer than the installed one; else null. */
    val availableVersion: String? = null,
    /** When true, a newer build exists and the app blocks until it is installed. */
    val required: Boolean = false,
    val downloadPercent: Int? = null,
    val message: String? = null,
)

/**
 * Firebase-free, self-hosted updater.
 *
 * [refreshVersion] polls `GET api/tablet/app/version/` for `{ version, download_url }`; whenever
 * the advertised `version` is newer than the installed [BuildConfig.VERSION_NAME] the update
 * becomes [UpdateState.required] and the UI blocks the whole app behind ForceUpdateScreen until
 * [downloadAndInstall] fetches the APK from `download_url` and hands it to the system installer.
 *
 * A singleton because the same gate is driven from several places — the launch check, the manual
 * "check for updates" button, and the background service's periodic poll.
 */
object UpdateManager {

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The download URL advertised alongside the newest known version. */
    @Volatile
    private var pendingApkUrl: String? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor { message -> Log.d(TAG, message) }.apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .build()

    /** The version this build reports; the yardstick every server version is compared against. */
    val installedVersion: String get() = BuildConfig.VERSION_NAME

    /**
     * Polls the dedicated version endpoint and updates the gate. Unauthenticated, so it works on
     * the login screen and from the background alike. Failures are returned, not thrown, so a
     * network hiccup never locks anyone out.
     */
    suspend fun refreshVersion(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = ApiConfig.baseUrl.trimEnd('/') + "/" + ApiConfig.APP_VERSION_PATH
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val data = json.decodeFromString(AppVersionData.serializer(), body)
                onServerVersion(data.version, data.downloadUrl)
            }
        }
    }

    /**
     * Records the version/URL from an API response. Flips the gate to required when [appVersion]
     * is strictly newer than what is installed; leaves it alone otherwise so a stale-but-equal
     * response never clears an already-raised gate.
     */
    private fun onServerVersion(appVersion: String?, apkUrl: String?) {
        val server = appVersion?.trim().orEmpty()
        val newer = server.isNotEmpty() && isNewer(server, installedVersion)

        _state.value = if (newer) {
            pendingApkUrl = apkUrl?.takeIf { it.isNotBlank() }
            _state.value.copy(
                checking = false,
                required = true,
                availableVersion = server,
            )
        } else {
            _state.value.copy(checking = false)
        }
    }

    /**
     * Downloads the pending APK to internal storage and launches the system installer. Reports
     * progress through [UpdateState.downloadPercent]. On Android 8+ the user must allow this app
     * to install unknown apps first; when that is not yet granted we send them to the right
     * settings screen rather than failing silently.
     */
    suspend fun downloadAndInstall(context: Context) {
        val url = pendingApkUrl
        if (url.isNullOrBlank()) {
            _state.value = _state.value.copy(message = "No download URL was provided by the server.")
            return
        }

        if (!canInstallPackages(context)) {
            requestInstallPermission(context)
            _state.value = _state.value.copy(
                message = "Allow Tracker to install apps, then tap Update again.",
            )
            return
        }

        _state.value = _state.value.copy(downloadPercent = 0, message = null)

        val apk = runCatching { withContext(Dispatchers.IO) { download(context, url) } }
            .getOrElse { error ->
                Log.w(TAG, "APK download failed", error)
                _state.value = _state.value.copy(
                    downloadPercent = null,
                    message = "Download failed — check your connection and try again.",
                )
                return
            }

        _state.value = _state.value.copy(downloadPercent = 100)
        launchInstaller(context, apk)
    }

    private fun download(context: Context, url: String): File {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        // A fixed name (rather than per-version) means a re-download overwrites the last attempt
        // instead of piling up half-finished APKs in the cache.
        val target = File(dir, "tracker-update.apk")

        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty body")
            val total = body.contentLength()
            var downloaded = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = (downloaded * 100 / total).toInt().coerceIn(0, 99)
                            _state.value = _state.value.copy(downloadPercent = percent)
                        }
                    }
                }
            }
        }
        return target
    }

    private fun launchInstaller(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                Log.w(TAG, "Could not launch installer", error)
                _state.value = _state.value.copy(
                    downloadPercent = null,
                    message = "Could not open the installer.",
                )
            }
    }

    private fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    private fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "No unknown-sources settings screen", it) }
    }

    /** True when dotted-numeric [server] ("2.3.4") is strictly greater than [installed]. */
    private fun isNewer(server: String, installed: String): Boolean {
        val a = server.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val b = installed.split(".").map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private const val TAG = "UpdateManager"
    private const val UPDATE_DIR = "updates"
}
