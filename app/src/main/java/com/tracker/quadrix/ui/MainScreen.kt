package com.tracker.quadrix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tracker.quadrix.BuildConfig
import com.tracker.quadrix.data.api.ApiConfig
import com.tracker.quadrix.location.LocationTrackingService
import com.tracker.quadrix.location.TrackerState
import com.tracker.quadrix.update.UpdateState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    email: String?,
    deviceId: String,
    imei: String?,
    imeiUnavailableReason: String?,
    online: Boolean,
    tracker: TrackerState,
    update: UpdateState,
    permissionsGranted: Boolean,
    batteryOptimised: Boolean,
    loggingOut: Boolean,
    onGrantPermissions: () -> Unit,
    onDisableBatteryOptimisation: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmLogout by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Tracker") },
                actions = {
                    TextButton(onClick = { confirmLogout = true }, enabled = !loggingOut) {
                        Text("Log out")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            StatusCard(
                email = email,
                deviceId = deviceId,
                imei = imei,
                imeiUnavailableReason = imeiUnavailableReason,
                online = online,
                tracker = tracker,
                permissionsGranted = permissionsGranted,
            )

            if (!permissionsGranted) {
                Spacer(Modifier.height(16.dp))
                PermissionCard(onGrantPermissions = onGrantPermissions)
            }

            if (batteryOptimised) {
                Spacer(Modifier.height(16.dp))
                BatteryCard(onDisableBatteryOptimisation = onDisableBatteryOptimisation)
            }

            Spacer(Modifier.height(16.dp))
            LastFixCard(tracker = tracker)

            Spacer(Modifier.height(16.dp))
            UpdateCard(update = update, online = online, onCheckForUpdates = onCheckForUpdates)
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = {
                Text(
                    "Tracking stops and all data stored on this device — your session, cached " +
                        "locations and app caches — is erased."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    }
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatusCard(
    email: String?,
    deviceId: String,
    imei: String?,
    imeiUnavailableReason: String?,
    online: Boolean,
    tracker: TrackerState,
    permissionsGranted: Boolean,
) {
    val healthy = online && tracker.running && permissionsGranted

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(healthy = healthy)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = when {
                            !permissionsGranted -> "Permission needed"
                            !tracker.running -> "Tracking stopped"
                            !online -> "Tracking — offline"
                            else -> "Tracking active"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = email ?: "Signed in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            InfoRow("Interval", "Every $trackingIntervalMinutes minutes")
            InfoRow("Internet", if (online) "Connected" else "No connection")
            InfoRow("Device ID", deviceId)
            InfoRow("IMEI", imei ?: "Not available")
            InfoRow(
                "Queued uploads",
                if (tracker.pendingUploads > 0) "${tracker.pendingUploads} waiting" else "None",
            )
            InfoRow("Uploaded", "${tracker.uploadCount} this session")

            if (!online) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Offline — fixes are stored on the device and sent automatically " +
                        "once the connection is back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (imei == null && imeiUnavailableReason != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "IMEI: $imeiUnavailableReason. Sign in again to enter it by hand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (ApiConfig.isPlaceholder) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Backend not configured — API_BASE_URL still points at the " +
                        "placeholder host, so uploads will fail.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (tracker.lastError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tracker.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun LastFixCard(tracker: TrackerState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last location", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (tracker.lastLatitude == null || tracker.lastLongitude == null) {
                Text(
                    text = "Waiting for the first fix…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                InfoRow(
                    "Coordinates",
                    "%.5f, %.5f".format(tracker.lastLatitude, tracker.lastLongitude),
                )
                InfoRow("Accuracy", tracker.lastAccuracy?.let { "±%.0f m".format(it) } ?: "—")
                InfoRow("Recorded", formatTime(tracker.lastFixAt))
                InfoRow("Last synced", formatTime(tracker.lastSyncedAt))
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrantPermissions: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Location access required", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tracker needs location access — set to \"Allow all the time\" — to " +
                    "record your position while the app is in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onGrantPermissions) { Text("Grant permission") }
        }
    }
}

@Composable
private fun BatteryCard(onDisableBatteryOptimisation: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Battery optimisation is on", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Android may freeze tracking overnight while the app is battery " +
                    "optimised. Allow it to run unrestricted so location keeps reporting " +
                    "around the clock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDisableBatteryOptimisation) { Text("Allow") }
        }
    }
}

@Composable
private fun UpdateCard(
    update: UpdateState,
    online: Boolean,
    onCheckForUpdates: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("App version", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            InfoRow("Installed", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

            if (update.availableVersion != null) {
                InfoRow("Available", update.availableVersion)
            }

            if (update.downloadPercent != null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { update.downloadPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Downloading ${update.downloadPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (update.message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = update.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCheckForUpdates, enabled = online && !update.checking) {
                if (update.checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Checking…")
                } else {
                    Text("Check for updates")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusDot(healthy: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (healthy) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(14.dp),
    ) { Box(Modifier) }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTime(timestamp: Long): String =
    if (timestamp <= 0L) "—" else timeFormat.format(Date(timestamp))

/** Kept next to the UI so the displayed cadence and the service cannot drift apart. */
internal val trackingIntervalMinutes = LocationTrackingService.INTERVAL_MS / 60_000
