package com.salahlock.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.preferences.UserPreferences.ThemePreference
import com.salahlock.app.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    // Account
    val isSignedIn: Boolean = false,
    val userName: String = "",
    val userEmail: String = "",
    val userPhotoUrl: String? = null,

    // Appearance
    val darkMode: Boolean = false,

    // Prayer settings
    val calcMethod: String = "KARACHI",
    val adhanEnabled: Boolean = true,

    // Notifications
    val announcementsEnabled: Boolean = true,
    val emailNewsletterEnabled: Boolean = false,

    // Privacy & permissions
    val hasLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,

    // Data
    val lastBackupMs: Long = 0L,

    // Transient operation feedback (backup/restore results)
    val operationMessage: String? = null,
    val isBusy: Boolean = false,
)

/**
 * Backs the Settings screen. All persistence goes through the existing
 * [com.salahlock.app.data.preferences.UserPreferences],
 * [com.salahlock.app.data.preferences.UserIdentityPreferences] and
 * [com.salahlock.app.data.backup.BackupRepository] — no new storage.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val prefs = app.userPreferences
    private val identity = app.userIdentity
    private val authClient = com.salahlock.app.auth.GoogleAuthClient(app)

    private val transient = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.themePreference,
        prefs.calcMethod,
        prefs.adhanEnabled,
        prefs.notificationsEnabled,
        prefs.emailNewsletterEnabled,
        prefs.lastBackupMs,
        identity.isSignedIn,
        identity.displayName,
        identity.email,
        identity.photoUrl,
        transient,
    ) { values: Array<Any?> ->
        val base = values[10] as SettingsUiState
        base.copy(
            darkMode = (values[0] as ThemePreference).let {
                it == ThemePreference.DARK || it == ThemePreference.AMOLED
            },
            calcMethod = values[1] as String,
            adhanEnabled = values[2] as Boolean,
            announcementsEnabled = values[3] as Boolean,
            emailNewsletterEnabled = values[4] as Boolean,
            lastBackupMs = values[5] as Long,
            isSignedIn = values[6] as Boolean,
            userName = values[7] as String,
            userEmail = values[8] as String,
            userPhotoUrl = values[9] as String?,
            hasLocationPermission = PermissionHelper.hasLocationPermission(app),
            hasNotificationPermission = PermissionHelper.hasNotificationPermission(app),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    // ── Appearance ────────────────────────────────────────────────────────────

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch {
        prefs.setThemePreference(if (enabled) ThemePreference.DARK else ThemePreference.LIGHT)
    }

    // ── Prayer settings ───────────────────────────────────────────────────────

    fun setCalcMethod(method: String) = viewModelScope.launch { prefs.setCalcMethod(method) }

    fun setAdhanEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setAdhanEnabled(enabled) }

    // ── Notifications ─────────────────────────────────────────────────────────

    fun setAnnouncementsEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setNotificationsEnabled(enabled)
    }

    fun setEmailNewsletterEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setEmailNewsletterEnabled(enabled)
    }

    // ── Data: backup / restore ────────────────────────────────────────────────

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            transient.update { it.copy(isBusy = true, operationMessage = null) }
            val message = runCatching {
                val result = app.backupRepository.createBackup(app.contentResolver, uri)
                prefs.setLastBackupMs(System.currentTimeMillis())
                result
            }.getOrElse { "Backup failed: ${it.message}" }
            transient.update { it.copy(isBusy = false, operationMessage = message) }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            transient.update { it.copy(isBusy = true, operationMessage = null) }
            val message = runCatching {
                app.backupRepository.restoreBackup(app.contentResolver, uri)
            }.getOrElse { "Restore failed: ${it.message}" }
            transient.update { it.copy(isBusy = false, operationMessage = message) }
        }
    }

    fun clearMessage() = transient.update { it.copy(operationMessage = null) }

    // ── Account ───────────────────────────────────────────────────────────────

    fun logOut() {
        // Clear the local identity immediately so the UI flips to signed-out
        // without waiting on the Credential Manager round-trip…
        identity.clearIdentity()
        transient.update { it.copy(operationMessage = "Signed out. Your data stays on this device.") }
        // …then revoke the Credential Manager selection so the next sign-in shows
        // the account picker instead of silently restoring the same account.
        viewModelScope.launch { authClient.signOut() }
    }
}
