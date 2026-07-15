package com.salahlock.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** A Google account resolved from a Credential Manager response. */
data class GoogleIdentity(
    val googleId: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
)

/** Outcome of a Credential Manager request, mapped to what the UI needs to show. */
sealed interface GoogleAuthResult {
    data class Success(val identity: GoogleIdentity) : GoogleAuthResult
    /** User dismissed the account picker — not an error, return to idle. */
    data object Cancelled : GoogleAuthResult
    /** Silent sign-in found no previously authorized account. */
    data object NoCredential : GoogleAuthResult
    data class Error(val message: String) : GoogleAuthResult
}

/**
 * Production Google Sign-In using the modern Credential Manager + Google Identity
 * Services (no deprecated GoogleSignInClient, no Firebase). The Web client ID lives
 * in [GoogleAuthConfig]. Identity is persisted by
 * [com.salahlock.app.data.preferences.UserIdentityPreferences]; this class only
 * performs the credential exchange.
 */
class GoogleAuthClient(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    /** Explicit sign-in — always shows the Google account picker. */
    suspend fun signIn(): GoogleAuthResult {
        val option = GetSignInWithGoogleOption.Builder(GoogleAuthConfig.WEB_CLIENT_ID).build()
        return request(option)
    }

    /**
     * Silent sign-in — returns a previously authorized account without any UI, or
     * [GoogleAuthResult.NoCredential] if none. Used to refresh the session on launch.
     */
    suspend fun silentSignIn(): GoogleAuthResult {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(GoogleAuthConfig.WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        return request(option)
    }

    private suspend fun request(option: CredentialOption): GoogleAuthResult {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAuthResult.Success(
                    GoogleIdentity(
                        googleId = token.id,
                        // Google returns the account email as the credential id.
                        email = token.id,
                        displayName = token.displayName ?: token.givenName ?: token.id,
                        photoUrl = token.profilePictureUri?.toString(),
                    ),
                )
            } else {
                GoogleAuthResult.Error("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleAuthResult.NoCredential
        } catch (e: GoogleIdTokenParsingException) {
            GoogleAuthResult.Error("Could not read your Google account. Please try again.")
        } catch (e: GetCredentialException) {
            GoogleAuthResult.Error(e.message ?: "Sign-in failed. Please try again.")
        }
    }
}
