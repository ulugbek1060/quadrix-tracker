package com.tracker.quadrix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tracker.quadrix.update.UpdateState

/**
 * Non-dismissible mandatory-update gate. Rendered over everything — login included — whenever
 * [UpdateState.required] is set, and it swallows the back button so the app cannot be used on
 * an outdated version.
 */
@Composable
fun ForceUpdateScreen(
    update: UpdateState,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Consume back so the gate cannot be dismissed by navigating away.
    BackHandler(enabled = true) { /* intentionally blocked */ }

    val downloading = update.downloadPercent != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Update required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = "A newer version of Tracker is required to continue." +
                (update.availableVersion?.let { "\n\nLatest: $it" } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        when {
            downloading -> {
                val percent = update.downloadPercent ?: 0
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (percent >= 100) "Installing…" else "Downloading $percent%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {
                Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text("Update now")
                }
            }
        }

        if (update.checking) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        if (update.message != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = update.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}
