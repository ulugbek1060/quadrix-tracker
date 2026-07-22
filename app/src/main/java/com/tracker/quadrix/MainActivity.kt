package com.tracker.quadrix

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracker.quadrix.ui.AuthViewModel
import com.tracker.quadrix.ui.BatteryGateScreen
import com.tracker.quadrix.ui.LoginScreen
import com.tracker.quadrix.ui.MainScreen
import com.tracker.quadrix.ui.ForceUpdateScreen
import com.tracker.quadrix.ui.MainViewModel
import com.tracker.quadrix.ui.PermissionGateScreen
import com.tracker.quadrix.ui.PermissionStep
import com.tracker.quadrix.ui.theme.TrackerTheme
import com.tracker.quadrix.update.UpdateCheckWorker

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Mandatory-update check on every launch. If the backend advertises a newer app_version,
        // the whole app is blocked behind the force-update gate until the APK is installed.
        mainViewModel.enforceUpdate()

        // Background fallback that polls for updates while tracking is off (the tracking service
        // covers the tracking-on case). KEEP makes this a no-op once scheduled.
        UpdateCheckWorker.schedule(this)

        setContent {
            TrackerTheme {
                val update by mainViewModel.updateState.collectAsStateWithLifecycle()
                if (update.required) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ForceUpdateScreen(
                            update = update,
                            onUpdate = mainViewModel::startForcedUpdate,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    return@TrackerTheme
                }

                val loggedIn by authViewModel.loggedIn.collectAsStateWithLifecycle()
                val online by mainViewModel.online.collectAsStateWithLifecycle()

                if (loggedIn) {
                    MainRoute(online = online)
                } else {
                    val loginState by authViewModel.loginState.collectAsStateWithLifecycle()
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        LoginScreen(
                            state = loginState,
                            online = online,
                            onEmailChange = authViewModel::onEmailChange,
                            onCodeChange = authViewModel::onCodeChange,
                            onRequestOtp = authViewModel::requestOtp,
                            onVerifyOtp = authViewModel::verifyOtp,
                            onResendOtp = authViewModel::resendOtp,
                            onChangeEmail = authViewModel::changeEmail,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MainRoute(online: Boolean) {
        val tracker by mainViewModel.trackerState.collectAsStateWithLifecycle()
        val permissions by mainViewModel.permissions.collectAsStateWithLifecycle()
        val loggingOut by authViewModel.loggingOut.collectAsStateWithLifecycle()

        // Once a request has been made and the grant still isn't there, the only remaining route
        // is the system settings screen — Android will not surface the dialog again.
        var foregroundBlocked by remember { mutableStateOf(false) }
        var attemptedBackground by remember { mutableStateOf(false) }

        // Background location is requested on its own, after foreground is granted — Android 10+
        // rejects a combined request, and on 11+ this call routes the user to Settings.
        val backgroundLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { mainViewModel.refreshPermissions() }

        val foregroundLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (!granted &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                foregroundBlocked = true
            }
            mainViewModel.refreshPermissions()
        }

        // Re-read grants and reconcile tracking on every resume: covers the user coming back
        // from Settings, and revoking access while the app was away.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) mainViewModel.refreshPermissions()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val step: PermissionStep? = when {
            !permissions.foreground && foregroundBlocked -> PermissionStep.OPEN_SETTINGS
            !permissions.foreground -> PermissionStep.REQUEST_FOREGROUND
            !permissions.background && attemptedBackground -> PermissionStep.OPEN_SETTINGS
            !permissions.background -> PermissionStep.REQUEST_BACKGROUND
            else -> null
        }

        // Kick off the first foreground prompt automatically, so an already-permissioned device
        // sails straight through without a tap.
        LaunchedEffect(step) {
            if (step == PermissionStep.REQUEST_FOREGROUND && !foregroundBlocked) {
                foregroundLauncher.launch(foregroundPermissions())
            }
        }

        if (step != null) {
            PermissionGateScreen(
                step = step,
                onPrimaryAction = {
                    when (step) {
                        PermissionStep.REQUEST_FOREGROUND ->
                            foregroundLauncher.launch(foregroundPermissions())

                        PermissionStep.REQUEST_BACKGROUND -> {
                            attemptedBackground = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                backgroundLauncher.launch(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                )
                            }
                        }

                        PermissionStep.OPEN_SETTINGS -> openAppSettings()
                    }
                },
                onOpenSettings = ::openAppSettings,
            )
            return
        }

        // All-time location is in place: tracking is guaranteed to have been started by
        // refreshPermissions(). What remains is the battery-optimisation exemption, which is
        // mandatory — see BatteryGateScreen — not an optional nudge.
        var batteryOptimised by remember { mutableStateOf(mainViewModel.isBatteryOptimised()) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    batteryOptimised = mainViewModel.isBatteryOptimised()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        if (batteryOptimised) {
            BatteryGateScreen(
                onDisableBatteryOptimisation = ::requestIgnoreBatteryOptimisations,
                onOpenSettings = ::openAppSettings,
            )
            return
        }

        MainScreen(
            email = authViewModel.userEmail,
            deviceId = authViewModel.deviceId,
            online = online,
            tracker = tracker,
            permissionsGranted = true,
            loggingOut = loggingOut,
            onGrantPermissions = { foregroundLauncher.launch(foregroundPermissions()) },
            onLogout = authViewModel::logout,
        )
    }

    /** Opens this app's system settings page — the last resort for a permanently denied grant. */
    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        runCatching { startActivity(intent) }
            .onFailure { Log.w("MainActivity", "Could not open app settings", it) }
    }

    /**
     * Opens the system prompt to exempt the app from battery optimisation. Falls back to the
     * settings list, since some OEM ROMs do not implement the direct-request intent.
     */
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimisations() {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        runCatching { startActivity(direct) }
            .recoverCatching { startActivity(fallback) }
            .onFailure { Log.w("MainActivity", "No battery optimisation settings screen", it) }
    }

    private fun foregroundPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}
