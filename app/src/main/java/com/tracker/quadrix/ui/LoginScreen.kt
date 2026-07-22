package com.tracker.quadrix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    state: LoginUiState,
    online: Boolean,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onRequestOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onChangeEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Tracker", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (state.step) {
                LoginStep.EMAIL -> "Enter your email to receive a sign-in code"
                LoginStep.OTP -> "Enter the code we emailed you"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        when (state.step) {
            LoginStep.EMAIL -> EmailStep(
                state = state,
                online = online,
                onEmailChange = onEmailChange,
                onRequestOtp = onRequestOtp,
            )

            LoginStep.OTP -> OtpStep(
                state = state,
                online = online,
                onCodeChange = onCodeChange,
                onVerifyOtp = onVerifyOtp,
                onResendOtp = onResendOtp,
                onChangeEmail = onChangeEmail,
            )
        }

        if (state.info != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.info,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!online) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "You are offline. Signing in needs an internet connection.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmailStep(
    state: LoginUiState,
    online: Boolean,
    onEmailChange: (String) -> Unit,
    onRequestOtp: () -> Unit,
) {
    OutlinedTextField(
        value = state.email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        enabled = !state.loading,
        isError = state.error != null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onRequestOtp() }),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onRequestOtp,
        enabled = !state.loading && online,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.loading) {
            LoadingSpinner()
        } else {
            Text("Send code")
        }
    }
}

@Composable
private fun OtpStep(
    state: LoginUiState,
    online: Boolean,
    onCodeChange: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    OutlinedTextField(
        value = state.code,
        onValueChange = onCodeChange,
        label = { Text("Verification code") },
        singleLine = true,
        enabled = !state.loading,
        isError = state.error != null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onVerifyOtp() }),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onVerifyOtp,
        enabled = !state.loading && online,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.loading) {
            LoadingSpinner()
        } else {
            Text("Verify & sign in")
        }
    }

    Spacer(Modifier.height(8.dp))

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(
            onClick = onResendOtp,
            enabled = !state.loading && online && state.resendAfter == 0,
        ) {
            Text(
                if (state.resendAfter > 0) {
                    "Resend code in ${state.resendAfter}s"
                } else {
                    "Resend code"
                }
            )
        }
        TextButton(onClick = onChangeEmail, enabled = !state.loading) {
            Text("Change email")
        }
    }
}

@Composable
private fun LoadingSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.height(20.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onPrimary,
    )
}
