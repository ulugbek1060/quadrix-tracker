package com.tracker.quadrix.ui

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tracker.quadrix.data.AuthRepository
import com.tracker.quadrix.data.ConnectivityObserver
import com.tracker.quadrix.data.SessionManager
import com.tracker.quadrix.location.LocationTrackingService
import com.tracker.quadrix.location.TrackerStatus
import com.tracker.quadrix.update.UpdateNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which half of the OTP sign-in the user is on. */
enum class LoginStep { EMAIL, OTP }

data class LoginUiState(
    val step: LoginStep = LoginStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Confirmation text, e.g. "Code sent to …". */
    val info: String? = null,
    /** Seconds until another code may be requested; 0 once resend is allowed. */
    val resendAfter: Int = 0,
)

/** Owns the session: OTP sign-in and the logout wipe. */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application)
    private val connectivity = ConnectivityObserver(application)
    private val session = SessionManager(application)

    private val _loggedIn = MutableStateFlow(authRepository.isLoggedIn)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    val userEmail: String? get() = authRepository.currentUserEmail ?: session.userEmail
    val userName: String? get() = authRepository.currentUserName

    /** Shown on the main screen so an operator can read off the ID the backend registered. */
    val deviceId: String get() = authRepository.deviceId

    /** Full hardware/OS dump shown on the main screen for support and diagnostics. */
    val deviceInfo: String get() = authRepository.deviceInfo

    fun onEmailChange(value: String) =
        _loginState.update { it.copy(email = value, error = null) }

    /** Digits only — the codes are numeric and users paste them with stray spaces. */
    fun onCodeChange(value: String) = _loginState.update {
        it.copy(code = value.filter(Char::isDigit).take(OTP_LENGTH), error = null)
    }

    /** Back to the email step to correct a typo'd address. */
    fun changeEmail() = _loginState.update {
        it.copy(step = LoginStep.EMAIL, code = "", error = null, info = null)
    }

    /** Step 1: ask the backend to email a verification code. */
    fun requestOtp() {
        val state = _loginState.value
        if (state.loading) return

        if (!isValidEmail(state.email)) {
            _loginState.update { it.copy(error = "Enter a valid email address.") }
            return
        }
        if (!connectivity.isOnline()) {
            _loginState.update { it.copy(error = "No internet connection.") }
            return
        }

        _loginState.update { it.copy(loading = true, error = null, info = null) }
        viewModelScope.launch {
            authRepository.requestOtp(state.email)
                .onSuccess { data ->
                    _loginState.update {
                        it.copy(
                            step = LoginStep.OTP,
                            loading = false,
                            info = "Code sent to ${data.email ?: state.email.trim()}.",
                        )
                    }
                    startResendCountdown(data.resendAfter)
                }
                .onFailure { error ->
                    _loginState.update {
                        it.copy(loading = false, error = error.message ?: "Could not send code.")
                    }
                }
        }
    }

    fun resendOtp() {
        if (_loginState.value.resendAfter > 0) return
        requestOtp()
    }

    /** Step 2: verify the code and open the session. */
    fun verifyOtp() {
        val state = _loginState.value
        if (state.loading) return

        if (state.code.length < OTP_LENGTH) {
            _loginState.update { it.copy(error = "Enter the $OTP_LENGTH-digit code.") }
            return
        }
        if (!connectivity.isOnline()) {
            _loginState.update { it.copy(error = "No internet connection.") }
            return
        }

        _loginState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            authRepository.verifyOtp(state.email, state.code)
                .onSuccess {
                    _loginState.value = LoginUiState()
                    _loggedIn.value = true
                }
                .onFailure { error ->
                    _loginState.update {
                        it.copy(loading = false, error = error.message ?: "Sign-in failed.")
                    }
                }
        }
    }

    private fun startResendCountdown(seconds: Int) {
        if (seconds <= 0) {
            _loginState.update { it.copy(resendAfter = 0) }
            return
        }
        _loginState.update { it.copy(resendAfter = seconds) }
        viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _loginState.update { it.copy(resendAfter = remaining) }
            }
        }
    }

    /**
     * Stops tracking first, then wipes. Order matters: a running service would otherwise
     * write another location fix into the cache we just cleared.
     */
    fun logout() {
        if (_loggingOut.value) return
        _loggingOut.value = true

        val context = getApplication<Application>()
        viewModelScope.launch {
            LocationTrackingService.stop(context)
            UpdateNotifier.clear(context)
            authRepository.signOutAndWipe()
            TrackerStatus.reset()
            _loginState.value = LoginUiState()
            _loggedIn.value = false
            _loggingOut.value = false
        }
    }

    private fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    private companion object {
        const val OTP_LENGTH = 6
    }
}
