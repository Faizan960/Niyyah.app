package com.salahlock.app.auth

import android.util.Log
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.errorMessage
import com.clerk.api.network.serialization.onFailure
import com.clerk.api.network.serialization.onSuccess
import com.clerk.api.signin.SignIn
import com.clerk.api.signup.SignUp
import com.clerk.api.sso.OAuthProvider
import com.clerk.api.sso.ResultType
import com.clerk.api.user.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** App-level identity, mapped from Clerk's [User]. Decouples UI from the SDK types. */
data class AuthUser(
    val id: String,
    val name: String,
    val email: String,
    val imageUrl: String?,
)

/**
 * BM-AUTH-001 — the single authentication state for the whole app.
 *
 * - [Loading]  Clerk is still initializing / restoring a session.
 * - [SignedOut] initialized, no active session.
 * - [SignedIn]  active Clerk session with the resolved user.
 * - [Error]     a sign-in/out attempt failed (transient; UI surfaces the message).
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
    data class Error(val message: String) : AuthState
}

/**
 * Wraps the Clerk Android SDK as NIYYAH's sole authentication authority.
 *
 * [state] is derived from Clerk's own [Clerk.isInitialized] + [Clerk.userFlow], so
 * session persistence and restoration are handled entirely by Clerk — there is no
 * second, independent login state. Sign-in/out delegate to Clerk; the resulting
 * [state] change flows back through [Clerk.userFlow].
 */
class AuthRepository(scope: CoroutineScope) {

    private companion object { const val TAG = "AuthRepository" }

    val state: StateFlow<AuthState> =
        combine(Clerk.isInitialized, Clerk.userFlow) { initialized, user ->
            val next = when {
                !initialized -> AuthState.Loading
                user == null -> AuthState.SignedOut
                else -> AuthState.SignedIn(user.toAuthUser())
            }
            // Diagnostic only: log the transition + non-PII shape (no email/name dumped).
            Log.d(TAG, "state → ${next::class.simpleName} (initialized=$initialized, hasUser=${user != null})")
            next
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    /**
     * Launches Clerk's managed Google OAuth flow (opens a Custom Tab, returns on
     * redirect). Returns `null` on success, or a user-facing error message. The
     * signed-in [state] is delivered via [Clerk.userFlow], not this return value.
     */
    suspend fun signInWithGoogle(): String? {
        Log.d(TAG, "signInWithGoogle: starting Clerk OAuth flow")
        var error: String? = null
        Clerk.auth.signInWithOAuth(OAuthProvider.GOOGLE)
            .onSuccess { result ->
                val complete = when (result.resultType) {
                    ResultType.SIGN_IN -> result.signIn?.status == SignIn.Status.COMPLETE
                    ResultType.SIGN_UP -> result.signUp?.status == SignUp.Status.COMPLETE
                    else -> false
                }
                // Diagnostic: field names only (e.g. "phone_number") — never values/PII.
                Log.d(
                    TAG,
                    "signInWithGoogle: onSuccess resultType=${result.resultType} complete=$complete " +
                        "signInStatus=${result.signIn?.status} signUpStatus=${result.signUp?.status} " +
                        "missing=${result.signUp?.missingFields} unverified=${result.signUp?.unverifiedFields}",
                )
                if (!complete) error = "Sign-in could not be completed. Please try again."
            }
            .onFailure {
                Log.e(TAG, "signInWithGoogle: onFailure ${it.errorMessage}", it.throwable)
                error = it.errorMessage ?: "Google sign-in failed. Please try again."
            }
        return error
    }

    /** Performs a real Clerk sign-out (invalidates the session). Returns an error message or null. */
    suspend fun signOut(): String? {
        Log.d(TAG, "signOut: calling Clerk.auth.signOut()")
        var error: String? = null
        Clerk.auth.signOut()
            .onSuccess { Log.d(TAG, "signOut: success") }
            .onFailure {
                Log.e(TAG, "signOut: failure ${it.errorMessage}", it.throwable)
                error = it.errorMessage ?: "Sign-out failed. Please try again."
            }
        return error
    }

    private fun User.toAuthUser(): AuthUser {
        val fullName = listOfNotNull(firstName, lastName)
            .joinToString(" ")
            .trim()
        return AuthUser(
            id = id,
            name = fullName,
            email = primaryEmailAddress?.emailAddress.orEmpty(),
            imageUrl = imageUrl.takeIf { it.isNotBlank() },
        )
    }
}
