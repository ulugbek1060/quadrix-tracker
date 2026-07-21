package com.tracker.quadrix.data

/**
 * Temporary offline account for exercising the app before the REST API exists.
 *
 * Signing in with these credentials skips the network entirely and issues [TOKEN], and any
 * session holding that token has its uploads stubbed out — the queue drains as if the server
 * had accepted the batch, so tracking, the status screen, permissions and logout can all be
 * tested end to end with no backend.
 *
 * ⚠️  ENABLED IN RELEASE. [available] is true for every build, on purpose, so the release APK
 * can be used before the backend is ready. This is a hardcoded credential shipped inside the
 * distributed APK — anyone with the APK can sign in with it. DELETE this file (and the two
 * references in AuthRepository / LocationRepository) before the app goes live with real users
 * and a real API.
 */
object TestAccount {

    const val EMAIL = "test@tracker.local"
    const val PASSWORD = "test1234"

    /** Deliberately not a plausible real token, so it cannot be confused for one in logs. */
    const val TOKEN = "test-mode-token-not-a-real-credential"

    val available: Boolean get() = true

    fun matches(email: String, password: String): Boolean =
        email.trim().equals(EMAIL, ignoreCase = true) && password == PASSWORD

    /** True when the current session is the stub account rather than a real login. */
    fun isActiveSession(token: String?): Boolean = token == TOKEN
}
