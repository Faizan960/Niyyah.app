package com.salahlock.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores Google Sign-In identity (name, email, photo URL) locally using
 * EncryptedSharedPreferences. No data leaves the device. No Firebase or cloud sync.
 */
class UserIdentityPreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "user_identity_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    companion object {
        private const val KEY_IS_SIGNED_IN = "is_signed_in"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_GOOGLE_ID = "google_id"
    }

    private val _isSignedIn = MutableStateFlow(prefs.getBoolean(KEY_IS_SIGNED_IN, false))
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.getString(KEY_DISPLAY_NAME, "") ?: "")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _email = MutableStateFlow(prefs.getString(KEY_EMAIL, "") ?: "")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _photoUrl = MutableStateFlow(prefs.getString(KEY_PHOTO_URL, null))
    val photoUrl: StateFlow<String?> = _photoUrl.asStateFlow()

    fun saveIdentity(displayName: String, email: String, photoUrl: String?, googleId: String) {
        prefs.edit()
            .putBoolean(KEY_IS_SIGNED_IN, true)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PHOTO_URL, photoUrl)
            .putString(KEY_GOOGLE_ID, googleId)
            .apply()

        _isSignedIn.value = true
        _displayName.value = displayName
        _email.value = email
        _photoUrl.value = photoUrl
    }

    fun clearIdentity() {
        prefs.edit().clear().apply()
        _isSignedIn.value = false
        _displayName.value = ""
        _email.value = ""
        _photoUrl.value = null
    }
}
