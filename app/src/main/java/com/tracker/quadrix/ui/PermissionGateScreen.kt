package com.tracker.quadrix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Full-screen block shown until location is set to "Allow all the time". The app deliberately
 * offers nothing else here — background location is not optional for a tracker, so the only way
 * forward is to grant it. [step] decides what the primary action does.
 */
enum class PermissionStep {
    /** Foreground location not yet granted — the normal runtime dialog can still be shown. */
    REQUEST_FOREGROUND,

    /** Foreground granted, background not — needs the "Allow all the time" grant. */
    REQUEST_BACKGROUND,

    /** A permission was permanently denied; only the system settings screen can fix it. */
    OPEN_SETTINGS,
}

@Composable
fun PermissionGateScreen(
    step: PermissionStep,
    onPrimaryAction: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Location access required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = when (step) {
                PermissionStep.REQUEST_FOREGROUND ->
                    "Tracker records this device's location. Grant location access to continue."

                PermissionStep.REQUEST_BACKGROUND ->
                    "Tracker must keep recording location while the app is closed. On the next " +
                        "screen choose “Allow all the time” — anything less stops " +
                        "tracking the moment the app leaves the screen."

                PermissionStep.OPEN_SETTINGS ->
                    "Location is not set to “Allow all the time”. Open Settings → " +
                        "Permissions → Location and choose “Allow all the time” to " +
                        "continue."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onPrimaryAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (step) {
                    PermissionStep.REQUEST_FOREGROUND -> "Grant location access"
                    PermissionStep.REQUEST_BACKGROUND -> "Allow all the time"
                    PermissionStep.OPEN_SETTINGS -> "Open Settings"
                }
            )
        }

        // Escape hatch that always lands on the system screen, in case the OEM's dialog does
        // not appear (some ROMs suppress the background-location prompt entirely).
        if (step != PermissionStep.OPEN_SETTINGS) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Open Settings instead")
            }
        }
    }
}
