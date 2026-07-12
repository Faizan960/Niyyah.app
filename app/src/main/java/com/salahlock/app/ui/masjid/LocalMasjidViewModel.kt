package com.salahlock.app.ui.masjid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.db.entity.LocalMasjidEntity
import com.salahlock.app.data.model.PrayerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class LocalMasjidUiState(
    val masjidName: String = "",
    val fajr: String = "",
    val dhuhr: String = "",
    val asr: String = "",
    val maghrib: String = "",
    val isha: String = "",
    // SL-004 — Jumma (Friday). Optional; stored in DataStore, not Room (no migration).
    val jumma1: String = "",
    val jumma2: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
)

/** Validates "HH:mm" format (24-hour). */
private fun isValidTime(value: String): Boolean {
    if (!value.matches(Regex("\\d{1,2}:\\d{2}"))) return false
    val parts = value.split(":")
    val h = parts[0].toIntOrNull() ?: return false
    val m = parts[1].toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}

class LocalMasjidViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val repo = app.prayerSourceRepository

    private val _state = MutableStateFlow(LocalMasjidUiState())
    val state: StateFlow<LocalMasjidUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = repo.getLocalMasjidOnce()
            val j1 = app.userPreferences.jumma1.first()
            val j2 = app.userPreferences.jumma2.first()
            _state.update {
                it.copy(
                    masjidName = existing?.masjidName ?: it.masjidName,
                    fajr = existing?.fajr ?: it.fajr,
                    dhuhr = existing?.dhuhr ?: it.dhuhr,
                    asr = existing?.asr ?: it.asr,
                    maghrib = existing?.maghrib ?: it.maghrib,
                    isha = existing?.isha ?: it.isha,
                    jumma1 = j1,
                    jumma2 = j2,
                )
            }
        }
    }

    fun update(field: String, value: String) = _state.update { s ->
        when (field) {
            "name"    -> s.copy(masjidName = value)
            "fajr"    -> s.copy(fajr = value)
            "dhuhr"   -> s.copy(dhuhr = value)
            "asr"     -> s.copy(asr = value)
            "maghrib" -> s.copy(maghrib = value)
            "isha"    -> s.copy(isha = value)
            "jumma1"  -> s.copy(jumma1 = value)
            "jumma2"  -> s.copy(jumma2 = value)
            else -> s
        }
    }

    fun save() {
        val s = _state.value
        val times = listOf(s.fajr, s.dhuhr, s.asr, s.maghrib, s.isha)
        val invalidTime = times.firstOrNull { it.isNotBlank() && !isValidTime(it) }

        if (s.masjidName.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter the masjid name.") }
            return
        }
        if (times.any { it.isBlank() }) {
            _state.update { it.copy(errorMessage = "Please enter all five prayer times.") }
            return
        }
        if (invalidTime != null) {
            _state.update { it.copy(errorMessage = "Invalid time format: \"$invalidTime\". Use HH:mm (e.g. 04:30).") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            repo.saveLocalMasjid(
                LocalMasjidEntity(
                    masjidName = s.masjidName.trim(),
                    fajr = s.fajr.trim(),
                    dhuhr = s.dhuhr.trim(),
                    asr = s.asr.trim(),
                    maghrib = s.maghrib.trim(),
                    isha = s.isha.trim(),
                    enabled = true,
                )
            )
            // SL-004 — Jumma persisted in DataStore alongside the Room timetable.
            app.userPreferences.setJummaTimes(s.jumma1, s.jumma2)
            repo.setPrayerSource(PrayerSource.LOCAL_MASJID)
            _state.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    /**
     * QoL: pre-fills the five prayer times from the current calculated/API times.
     * Reuses [PrayerTimesRepository.getTodayPrayers] — the user then tweaks only the
     * jamaat differences. No new repository or model.
     */
    fun importApiTimes() {
        viewModelScope.launch {
            val daily = app.prayerTimesRepository.getTodayPrayers().first() ?: run {
                _state.update { it.copy(errorMessage = "Prayer times unavailable — set your location first.") }
                return@launch
            }
            val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            _state.update {
                it.copy(
                    fajr = daily.fajr.format(fmt),
                    dhuhr = daily.dhuhr.format(fmt),
                    asr = daily.asr.format(fmt),
                    maghrib = daily.maghrib.format(fmt),
                    isha = daily.isha.format(fmt),
                )
            }
        }
    }
}
