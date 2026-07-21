package com.tracker.quadrix.update

import android.util.Log
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.FirebaseAppDistributionException
import com.google.firebase.appdistribution.UpdateProgress
import com.google.firebase.appdistribution.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UpdateState(
    val checking: Boolean = false,
    val availableVersion: String? = null,
    /** When true, a newer build exists and the app blocks until it is installed. */
    val required: Boolean = false,
    val downloadPercent: Int? = null,
    val message: String? = null,
)

/**
 * In-app updates through Firebase App Distribution — this app is not on Play, so this is the
 * only upgrade path for testers.
 *
 * `updateIfNewReleaseAvailable()` handles the whole flow itself: it checks the backend,
 * shows the "new version available" dialog, downloads the APK and launches the installer.
 * The SDK only sees releases for the same signing key and a higher versionCode, so bump
 * `versionCode` in app/build.gradle.kts for every upload.
 */
class UpdateManager {

    /**
     * Null when the app was built without google-services.json — the SDK cannot initialise
     * without a Firebase app id, and asking it to would throw on every call.
     */
    private val distribution: FirebaseAppDistribution?
        get() = runCatching { FirebaseAppDistribution.getInstance() }.getOrNull()

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Runs the full check → prompt → download → install flow. Safe to call on every launch. */
    fun checkAndUpdate() {
        val distribution = distribution ?: run {
            _state.value = UpdateState(message = NOT_CONFIGURED)
            return
        }
        _state.value = UpdateState(checking = true)

        distribution.updateIfNewReleaseAvailable()
            .addOnProgressListener { progress -> onProgress(progress) }
            .addOnCompleteListener {
                _state.value = _state.value.copy(checking = false)
            }
            .addOnFailureListener { error ->
                val message = when ((error as? FirebaseAppDistributionException)?.errorCode) {
                    FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED,
                    FirebaseAppDistributionException.Status.INSTALLATION_CANCELED,
                    FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE,
                        -> null

                    FirebaseAppDistributionException.Status.NETWORK_FAILURE ->
                        "Could not check for updates — no connection."

                    else -> error.message
                }
                Log.w(TAG, "Update check failed", error)
                _state.value = UpdateState(checking = false, message = message)
            }
    }

    /**
     * Mandatory-update check. Every App Distribution release is treated as required: if a
     * newer build exists, [UpdateState.required] flips to true and the UI blocks the entire app
     * behind [ForceUpdateScreen] until [startUpdate] installs it.
     *
     * "Newer" is the SDK's own rule — same signing key, higher versionCode — so bump
     * versionCode on every upload. If the app was built without Firebase config, nothing can be
     * enforced and the app proceeds normally.
     */
    fun enforceUpdate() {
        val distribution = distribution ?: return
        _state.value = _state.value.copy(checking = true)

        distribution.checkForNewRelease()
            .addOnSuccessListener { release ->
                _state.value = if (release != null) {
                    _state.value.copy(
                        checking = false,
                        required = true,
                        availableVersion = "${release.displayVersion} (${release.versionCode})",
                    )
                } else {
                    _state.value.copy(checking = false, required = false)
                }
            }
            .addOnFailureListener { error ->
                // A failed check must not lock users out — if we cannot confirm an update is
                // required, let them through rather than block on a network hiccup.
                Log.w(TAG, "Required-update check failed", error)
                _state.value = _state.value.copy(checking = false, required = false)
            }
    }

    /**
     * Downloads and installs the release already found by [enforceUpdate]. The SDK caches the
     * result of the preceding checkForNewRelease(), so this needs no argument.
     */
    fun startUpdate() {
        val distribution = distribution ?: return
        _state.value = _state.value.copy(downloadPercent = 0, message = null)

        distribution.updateApp()
            .addOnProgressListener { progress -> onProgress(progress) }
            .addOnFailureListener { error ->
                Log.w(TAG, "Forced update failed", error)
                _state.value = _state.value.copy(
                    downloadPercent = null,
                    message = "Update failed — check your connection and try again.",
                )
            }
    }

    /** Checks without prompting, so the main screen can show that a version is waiting. */
    fun checkOnly() {
        val distribution = distribution ?: run {
            _state.value = UpdateState(message = NOT_CONFIGURED)
            return
        }
        _state.value = _state.value.copy(checking = true)

        distribution.checkForNewRelease()
            .addOnSuccessListener { release ->
                _state.value = UpdateState(
                    checking = false,
                    availableVersion = release?.let { "${it.displayVersion} (${it.versionCode})" },
                )
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Release check failed", error)
                _state.value = UpdateState(checking = false, message = error.message)
            }
    }

    private fun onProgress(progress: UpdateProgress) {
        when (progress.updateStatus) {
            UpdateStatus.DOWNLOADING -> {
                val total = progress.apkFileTotalBytes
                val percent = if (total > 0) {
                    (progress.apkBytesDownloaded * 100 / total).toInt()
                } else {
                    0
                }
                _state.value = _state.value.copy(downloadPercent = percent, message = null)
            }

            UpdateStatus.DOWNLOADED ->
                _state.value = _state.value.copy(downloadPercent = 100)

            UpdateStatus.DOWNLOAD_FAILED ->
                _state.value = _state.value.copy(
                    downloadPercent = null,
                    message = "Download failed.",
                )

            else -> Unit
        }
    }

    /**
     * One-shot check for use from the background service. Reports whether a newer release
     * exists, without touching [state] — the service turns a positive result into a
     * notification. Requires the tester to have signed in previously; if not, or if Firebase is
     * absent, it simply reports "no update".
     */
    fun checkForUpdate(onResult: (available: Boolean, version: String?) -> Unit) {
        val distribution = distribution ?: return onResult(false, null)
        distribution.checkForNewRelease()
            .addOnSuccessListener { release ->
                onResult(
                    release != null,
                    release?.let { "${it.displayVersion} (${it.versionCode})" },
                )
            }
            .addOnFailureListener { error ->
                Log.i(TAG, "Background update check failed: ${error.message}")
                onResult(false, null)
            }
    }

    fun signOutTester() {
        runCatching { distribution?.signOutTester() }
    }

    private companion object {
        const val TAG = "UpdateManager"
        const val NOT_CONFIGURED = "Updates unavailable — this build has no Firebase config."
    }
}
