package com.tracker.quadrix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tracker.quadrix.data.AuthRepository
import com.tracker.quadrix.data.ConnectivityObserver
import com.tracker.quadrix.data.SessionManager
import com.tracker.quadrix.data.TestAccount
import com.tracker.quadrix.location.LocationTrackingService
import com.tracker.quadrix.location.TrackerStatus
import com.tracker.quadrix.update.UpdateManager
import com.tracker.quadrix.update.UpdateNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    /** Typed in only when the platform will not give us one; see [AuthViewModel.imeiReadOnly]. */
    val imei: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

/** Owns the session: who is signed in, signing in, and the logout wipe. */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application)
    private val connectivity = ConnectivityObserver(application)
    private val session = SessionManager(application)

    private val _loggedIn = MutableStateFlow(authRepository.isLoggedIn)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _loginState = MutableStateFlow(
        LoginUiState(imei = authRepository.imei.orEmpty())
    )
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    val imei: String? get() = authRepository.imei

    /** True when the platform gave us a real IMEI, so the field must not be edited. */
    val imeiReadOnly: Boolean get() = authRepository.imeiIsFromPlatform

    val imeiUnavailableReason: String? get() = authRepository.imeiUnavailableReason

    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    val userEmail: String? get() = authRepository.currentUserEmail ?: session.userEmail

    /** Shown on the main screen so a tester can read off the ID the backend registered. */
    val deviceId: String get() = authRepository.deviceId

    fun onEmailChange(value: String) = _loginState.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) =
        _loginState.update { it.copy(password = value, error = null) }

    /** Digits only — an IMEI is 15 digits, and operators habitually paste in spaces. */
    fun onImeiChange(value: String) = _loginState.update {
        it.copy(imei = value.filter(Char::isDigit).take(17), error = null)
    }

    /** Debug convenience: fills the form with the stub credentials. */
    fun useTestAccount() = _loginState.update {
        it.copy(email = TestAccount.EMAIL, password = TestAccount.PASSWORD, error = null)
    }

    fun signIn() {
        val state = _loginState.value
        if (state.loading) return

        when {
            state.email.isBlank() -> {
                _loginState.update { it.copy(error = "Enter your email.") }
                return
            }

            state.password.isBlank() -> {
                _loginState.update { it.copy(error = "Enter your password.") }
                return
            }

            // The test account never touches the network, so being offline is no obstacle.
            !connectivity.isOnline() && !TestAccount.matches(state.email, state.password) -> {
                _loginState.update { it.copy(error = "No internet connection.") }
                return
            }
        }

        _loginState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.signIn(state.email, state.password, state.imei)
            result
                .onSuccess { email ->
                    session.userEmail = email
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
            UpdateManager().signOutTester()
            // Wipes the token, the user details and any location fixes still queued, so
            // nothing recorded under this session can be uploaded by the next one.
            authRepository.signOutAndWipe()
            TrackerStatus.reset()
            _loginState.value = LoginUiState()
            _loggedIn.value = false
            _loggingOut.value = false
        }
    }
}
