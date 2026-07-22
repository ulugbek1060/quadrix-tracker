package com.tracker.quadrix.ui

import androidx.activity.compose.BackHandler
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
 * Full-screen, non-dismissible block shown after login while the app is still subject to
 * battery optimisation. Doze can freeze the tracking service overnight, and on Android 12+ it
 * can also block the watchdog from restarting it from the background — the only real fix is the
 * "ignore battery optimisations" exemption, so unlike the old dismissible card, this gate does
 * not let the user past it without granting it (mirrors [PermissionGateScreen]).
 */
@Composable
fun BatteryGateScreen(
    onDisableBatteryOptimisation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Consume back so the gate cannot be dismissed by navigating away.
    BackHandler(enabled = true) { /* intentionally blocked */ }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Disable battery optimisation",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Tracker must run unrestricted in the background to keep reporting location. " +
                "Android may otherwise freeze it overnight or block it from restarting. Choose " +
                "“Allow” (or “Don’t optimise”) on the next screen to continue.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDisableBatteryOptimisation,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Disable battery optimisation") }

        // [onDisableBatteryOptimisation] already falls back to the battery-settings list if the
        // direct request intent is unavailable; this is the last resort for the rare OEM ROM
        // where neither works, landing on the app's own settings page instead.
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open app settings")
        }
    }
}
