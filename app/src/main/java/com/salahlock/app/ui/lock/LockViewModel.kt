package com.salahlock.app.ui.lock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.data.repository.OverrideReason
import com.salahlock.app.data.repository.OverrideResult
import com.salahlock.app.verification.VerificationMethod
import com.salahlock.app.verification.VerificationMethod.Companion.fromString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LockUiState(
    val prayer: PrayerName = PrayerName.FAJR,
    val lockEndMs: Long = 0L,
    val showOverrideSheet: Boolean = false,
    val currentTimeMs: Long = System.currentTimeMillis(),
    // Sprint E — verification preferences (loaded from UserPreferences)
    val verificationMethod: VerificationMethod = VerificationMethod.ASK_EVERY_TIME,
    val confirmCount: Int = 3,
    val reminderQuran: Boolean = true,
    val reminderHadith: Boolean = true,
    val reminderReflection: Boolean = true,
    val currentStreak: Int = 0,
)

class LockViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        observeVerificationPrefs()
        observeStreak()
    }

    fun init(prayer: PrayerName, lockEndMs: Long) {
        _state.update { it.copy(prayer = prayer, lockEndMs = lockEndMs) }
    }

    private fun observeVerificationPrefs() {
        viewModelScope.launch {
            combine(
                app.userPreferences.verificationMethod,
                app.userPreferences.verificationConfirmCount,
                app.userPreferences.reminderQuranEnabled,
                app.userPreferences.reminderHadithEnabled,
                app.userPreferences.reminderReflectionEnabled,
            ) { method, count, quran, hadith, reflection ->
                val vm = fromString(method)
                _state.update { it.copy(
                    verificationMethod = vm,
                    confirmCount = count,
                    reminderQuran = quran,
                    reminderHadith = hadith,
                    reminderReflection = reflection,
                ) }
            }.collect {}
        }
    }

    private fun observeStreak() {
        viewModelScope.launch {
            app.streakRepository.observeStreakInfo().collect { info ->
                _state.update { it.copy(currentStreak = info.currentStreak) }
            }
        }
    }

    fun showOverrideSheet() {
        _state.update { it.copy(showOverrideSheet = true) }
    }

    fun dismissOverrideSheet() {
        _state.update { it.copy(showOverrideSheet = false) }
    }

    /**
     * Records the prayer as verified — called after successful spiritual accountability verification.
     */
    suspend fun recordPrayerCompleted() {
        app.streakRepository.recordVerification(
            date = LocalDate.now().toString(),
            prayer = _state.value.prayer,
        )
    }

    /**
     * Appends a device-local emergency event for the currently locked prayer. It never
     * writes prayer completion or streak state.
     */
    suspend fun useOverride(reason: OverrideReason): OverrideResult {
        val result = app.overrideRepository.useOverrideForPrayer(
            date = LocalDate.now().toString(),
            prayer = _state.value.prayer,
            reason = reason,
        )
        return result
    }
}
