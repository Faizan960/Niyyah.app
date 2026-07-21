package com.salahlock.app.ui.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.auth.AuthState
import com.salahlock.app.util.PermissionHelper
import com.salahlock.app.data.preferences.UserPreferences.ThemePreference
import com.salahlock.app.work.AutoBackupWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SignInState { IDLE, LOADING, SUCCESS, ERROR, NOT_CONFIGURED }

data class ProfileUiState(
    // Identity
    val isSignedIn: Boolean = false,
    val userName: String = "",
    val userEmail: String = "",
    val userPhotoUrl: String? = null,
    val signInState: SignInState = SignInState.IDLE,
    val signInError: String? = null,

    // Progress
    val currentStreak: Int = 0,
    val totalPrayers: Int = 0,

    // Account
    val syncStatus: String = "Not synced — cloud sync coming soon",

    // Preferences
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val calcMethod: String = "KARACHI",
    val lockDurationMin: Int = 30,
    val adhanEnabled: Boolean = true,
    val cityName: String = "Unknown",

    // Per-prayer lock enabled: displayName → enabled
    val prayerLockEnabled: LinkedHashMap<String, Boolean> = linkedMapOf(
        "Fajr" to true,
        "Dhuhr" to true,
        "Asr" to true,
        "Maghrib" to true,
        "Isha" to true,
    ),
    val blockedAppCount: Int = 0,

    // Sprint E.2 — backup (kept outside ProfileUiState to avoid coupling)
    val lastBackupMs: Long = 0L,
    val autoBackupFrequency: String = "DISABLED",

    // Sprint E — verification preferences
    val verificationMethod: String = "ASK_EVERY_TIME",
    val verificationConfirmCount: Int = 3,
    val reminderQuranEnabled: Boolean = true,
    val reminderHadithEnabled: Boolean = true,
    val reminderReflectionEnabled: Boolean = true,

    // Permissions
    val hasOverlayPermission: Boolean = true,
    val hasUsageStatsPermission: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val hasBatteryOptimization: Boolean = true,
    val hasExactAlarmPermission: Boolean = true,
    val hasLocationPermission: Boolean = true,
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val prefs = app.userPreferences
    private val authRepo = app.authRepository

    private val _extraState = MutableStateFlow(ProfileUiState())

    val uiState: StateFlow<ProfileUiState> = combine(
        prefs.themePreference,
        prefs.calcMethod,
        prefs.lockDurationMin,
        prefs.adhanEnabled,
        prefs.cityName,
        _extraState,
    ) { args: Array<Any?> ->
        val theme = args[0] as ThemePreference
        val method = args[1] as String
        val duration = args[2] as Int
        val adhan = args[3] as Boolean
        val city = args[4] as String
        val state = args[5] as ProfileUiState
        state.copy(
            themePreference = theme,
            calcMethod = method,
            lockDurationMin = duration,
            adhanEnabled = adhan,
            cityName = city,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState(),
    )

    init {
        checkPermissions()
        observePrayerLockState()
        observeBlockedCount()
        observeIdentity()
        observeStreak()
        observeVerificationPrefs()
        observeBackupPrefs()
    }

    // ── Sprint E.2: Backup ────────────────────────────────────────────────────

    data class BackupUiState(
        val isBackingUp: Boolean = false,
        val isRestoring: Boolean = false,
        val isLoadingPreview: Boolean = false,
        val resultMessage: String? = null,
        val errorMessage: String? = null,
        /** Preview extracted from a selected backup file, awaiting user confirmation. */
        val preview: com.salahlock.app.data.backup.BackupPreviewData? = null,
        /** URI of the file the user selected for restore — kept until confirmRestore(). */
        val pendingRestoreUri: Uri? = null,
    )

    private val _backupUiState = MutableStateFlow(BackupUiState())
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    private fun observeBackupPrefs() {
        viewModelScope.launch {
            combine(
                prefs.lastBackupMs,
                prefs.autoBackupFrequency,
            ) { lastMs, freq ->
                _extraState.update { it.copy(lastBackupMs = lastMs, autoBackupFrequency = freq) }
            }.collect {}
        }
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            _backupUiState.update { it.copy(isBackingUp = true, errorMessage = null, resultMessage = null) }
            try {
                val msg = app.backupRepository.createBackup(app.contentResolver, uri)
                prefs.setLastBackupMs(System.currentTimeMillis())
                _backupUiState.update { it.copy(isBackingUp = false, resultMessage = msg) }
            } catch (e: Exception) {
                _backupUiState.update { it.copy(isBackingUp = false, errorMessage = "Backup failed: ${e.message}") }
            }
        }
    }

    /**
     * Called when the user selects a .zip file to restore.
     * Extracts and shows a preview — no data is changed until [confirmRestore] is called.
     */
    fun loadBackupPreview(uri: Uri) {
        viewModelScope.launch {
            _backupUiState.update { it.copy(isLoadingPreview = true, errorMessage = null, preview = null, pendingRestoreUri = null) }
            try {
                val preview = app.backupRepository.extractPreview(app.contentResolver, uri)
                _backupUiState.update { it.copy(isLoadingPreview = false, preview = preview, pendingRestoreUri = uri) }
            } catch (e: Exception) {
                _backupUiState.update { it.copy(isLoadingPreview = false, errorMessage = "Invalid Niyyah backup: ${e.message}") }
            }
        }
    }

    /** Applies the restore for the backup selected in [loadBackupPreview]. */
    fun confirmRestore() {
        val uri = _backupUiState.value.pendingRestoreUri ?: return
        viewModelScope.launch {
            _backupUiState.update { it.copy(isRestoring = true, preview = null, pendingRestoreUri = null, errorMessage = null) }
            try {
                val msg = app.backupRepository.restoreBackup(app.contentResolver, uri)
                _backupUiState.update { it.copy(isRestoring = false, resultMessage = msg) }
            } catch (e: Exception) {
                _backupUiState.update { it.copy(isRestoring = false, errorMessage = "Restore failed: ${e.message}") }
            }
        }
    }

    fun dismissPreview() {
        _backupUiState.update { it.copy(preview = null, pendingRestoreUri = null) }
    }

    fun setAutoBackupFrequency(frequency: String) {
        viewModelScope.launch {
            prefs.setAutoBackupFrequency(frequency)
            AutoBackupWorker.schedule(app, frequency)
        }
    }

    fun clearBackupResult() {
        _backupUiState.update { it.copy(resultMessage = null, errorMessage = null) }
    }

    // ── Authentication (Clerk) ────────────────────────────────────────────────
    // BM-AUTH-001: Clerk is the sole auth authority. Identity is projected from
    // AuthRepository.state; sign-in/out delegate to Clerk.

    /** Starts Clerk's managed Google OAuth flow. Signed-in state arrives via [observeIdentity]. */
    fun startSignIn() {
        viewModelScope.launch {
            _extraState.update { it.copy(signInState = SignInState.LOADING, signInError = null) }
            val error = authRepo.signInWithGoogle()
            _extraState.update {
                if (error == null) it.copy(signInState = SignInState.SUCCESS, signInError = null)
                else it.copy(signInState = SignInState.ERROR, signInError = error)
            }
        }
    }

    fun clearSignInError() {
        _extraState.update { it.copy(signInState = SignInState.IDLE, signInError = null) }
    }

    /** Real Clerk sign-out — invalidates the session. Signed-out state arrives via [observeIdentity]. */
    fun signOut() {
        viewModelScope.launch {
            val error = authRepo.signOut()
            if (error != null) {
                _extraState.update { it.copy(signInState = SignInState.ERROR, signInError = error) }
            }
        }
    }

    private fun observeIdentity() {
        viewModelScope.launch {
            authRepo.state.collect { state ->
                when (state) {
                    is AuthState.SignedIn -> _extraState.update {
                        it.copy(
                            isSignedIn = true,
                            userName = state.user.name,
                            userEmail = state.user.email,
                            userPhotoUrl = state.user.imageUrl,
                        )
                    }
                    is AuthState.Error -> _extraState.update { it.copy(signInError = state.message) }
                    AuthState.SignedOut, AuthState.Loading -> _extraState.update {
                        it.copy(isSignedIn = false, userName = "", userEmail = "", userPhotoUrl = null)
                    }
                }
            }
        }
    }

    // ── Streak ────────────────────────────────────────────────────────────────

    private fun observeStreak() {
        viewModelScope.launch {
            app.streakRepository.observeStreakInfo().collect { info ->
                _extraState.update { it.copy(currentStreak = info.currentStreak) }
            }
        }
        viewModelScope.launch {
            val today = java.time.LocalDate.now().toString()
            val owner = com.salahlock.app.data.sync.ActiveOwnerProvider.shared.ownerId()
            app.database.prayerRecordDao().observeRecordsForDate(owner, today).collect { records ->
                _extraState.update { it.copy(totalPrayers = records.count { r -> r.verified || r.overrideUsed }) }
            }
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    fun checkPermissions() {
        _extraState.update {
            it.copy(
                hasOverlayPermission = PermissionHelper.canDrawOverlays(app),
                hasUsageStatsPermission = PermissionHelper.hasUsageStatsPermission(app),
                hasNotificationPermission = PermissionHelper.hasNotificationPermission(app),
                hasBatteryOptimization = PermissionHelper.isBatteryOptimizationIgnored(app),
                hasExactAlarmPermission = PermissionHelper.canScheduleExactAlarms(app),
                hasLocationPermission = PermissionHelper.hasLocationPermission(app),
            )
        }
    }

    // ── Prayer Lock ───────────────────────────────────────────────────────────

    private fun observePrayerLockState() {
        viewModelScope.launch {
            combine(
                prefs.lockFajr, prefs.lockDhuhr, prefs.lockAsr, prefs.lockMaghrib, prefs.lockIsha,
            ) { fajr, dhuhr, asr, maghrib, isha ->
                linkedMapOf("Fajr" to fajr, "Dhuhr" to dhuhr, "Asr" to asr, "Maghrib" to maghrib, "Isha" to isha)
            }.collect { map ->
                _extraState.update { it.copy(prayerLockEnabled = map) }
            }
        }
    }

    private fun observeBlockedCount() {
        viewModelScope.launch {
            app.blacklistRepository.observeBlockedCount().collect { count ->
                _extraState.update { it.copy(blockedAppCount = count) }
            }
        }
    }

    fun setPrayerLockEnabled(prayerDisplay: String, enabled: Boolean) = viewModelScope.launch {
        val prayer = when (prayerDisplay) {
            "Fajr" -> com.salahlock.app.data.model.PrayerName.FAJR
            "Dhuhr" -> com.salahlock.app.data.model.PrayerName.DHUHR
            "Asr" -> com.salahlock.app.data.model.PrayerName.ASR
            "Maghrib" -> com.salahlock.app.data.model.PrayerName.MAGHRIB
            "Isha" -> com.salahlock.app.data.model.PrayerName.ISHA
            else -> return@launch
        }
        prefs.setPrayerLockEnabled(prayer, enabled)
    }

    // ── Sprint E: Verification Preferences ───────────────────────────────────

    private fun observeVerificationPrefs() {
        viewModelScope.launch {
            combine(
                prefs.verificationMethod,
                prefs.verificationConfirmCount,
                prefs.reminderQuranEnabled,
                prefs.reminderHadithEnabled,
                prefs.reminderReflectionEnabled,
            ) { method, count, quran, hadith, reflection ->
                _extraState.update {
                    it.copy(
                        verificationMethod = method,
                        verificationConfirmCount = count,
                        reminderQuranEnabled = quran,
                        reminderHadithEnabled = hadith,
                        reminderReflectionEnabled = reflection,
                    )
                }
            }.collect {}
        }
    }

    fun setVerificationMethod(method: String) = viewModelScope.launch { prefs.setVerificationMethod(method) }
    fun setVerificationConfirmCount(count: Int) = viewModelScope.launch { prefs.setVerificationConfirmCount(count) }
    fun setReminderQuranEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setReminderQuranEnabled(enabled) }
    fun setReminderHadithEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setReminderHadithEnabled(enabled) }
    fun setReminderReflectionEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setReminderReflectionEnabled(enabled) }

    // ── Prefs ─────────────────────────────────────────────────────────────────

    fun setCalculationMethod(method: String) = viewModelScope.launch { prefs.setCalcMethod(method) }
    fun setThemePreference(theme: ThemePreference) = viewModelScope.launch { prefs.setThemePreference(theme) }
    fun setLockDuration(duration: Int) = viewModelScope.launch { prefs.setLockDuration(duration) }
    fun setAdhanEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setAdhanEnabled(enabled) }
}
