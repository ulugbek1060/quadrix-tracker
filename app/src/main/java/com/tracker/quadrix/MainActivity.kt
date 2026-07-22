package com.tracker.quadrix

import android.Manifest
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
import com.tracker.quadrix.ui.LoginScreen
import com.tracker.quadrix.ui.MainScreen
import com.tracker.quadrix.ui.MainViewModel
import com.tracker.quadrix.ui.PermissionGateScreen
import com.tracker.quadrix.ui.PermissionStep
import com.tracker.quadrix.ui.theme.TrackerTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TrackerTheme {
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
                            imeiReadOnly = authViewModel.imeiReadOnly,
                            imeiUnavailableReason = authViewModel.imeiUnavailableReason,
                            onEmailChange = authViewModel::onEmailChange,
                            onPasswordChange = authViewModel::onPasswordChange,
                            onImeiChange = authViewModel::onImeiChange,
                            onSignIn = authViewModel::signIn,
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
        // refreshPermissions().
        MainScreen(
            email = authViewModel.userEmail,
            deviceId = authViewModel.deviceId,
            imei = authViewModel.imei,
            imeiUnavailableReason = authViewModel.imeiUnavailableReason,
            online = online,
            tracker = tracker,
            loggingOut = loggingOut,
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

    private fun foregroundPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        // Only worth prompting where it can actually yield an IMEI (Android 9-). On 10+ the
        // permission grants nothing to a non-device-owner app, so asking would gain nothing.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}
