package com.salahlock.app.ui.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.repository.Surah
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuranReaderState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val surah: Surah? = null,
    /** Ayah the list should scroll to when the surah opens (resume/bookmark target). */
    val initialAyah: Int = 1,
    val bookmarkedAyahs: Set<Int> = emptySet(),
    val isSurahBookmarked: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

/**
 * BM-010.3 — Quran reader. Text from the preserved quran.json via
 * QuranRepository; bookmarks and reading position persist in Room (QuranDao).
 * Prev/next surah navigation reloads in place — no nav-stack growth.
 */
class QuranReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SalahLockApplication).quranRepository

    private val _uiState = MutableStateFlow(QuranReaderState())
    val uiState: StateFlow<QuranReaderState> = _uiState.asStateFlow()

    private var bookmarksJob: Job? = null

    fun load(surahNumber: Int, requestedAyah: Int? = null) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val surah = repository.getSurah(surahNumber)
                if (surah == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "This surah could not be found.",
                    )
                    return@launch
                }
                // Resume where the reader left off unless an explicit ayah was requested.
                val initial = requestedAyah ?: repository.getProgress(surahNumber)?.lastAyah ?: 1
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    surah = surah,
                    initialAyah = initial.coerceIn(1, surah.ayahCount),
                    hasPrevious = surahNumber > 1,
                    hasNext = surahNumber < 114,
                )
                observeBookmarks(surahNumber)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "The Quran library could not be loaded. Please try again.",
                )
            }
        }
    }

    private fun observeBookmarks(surahNumber: Int) {
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            repository.getAllBookmarks().collect { rows ->
                val forSurah = rows.filter { it.surahNumber == surahNumber }
                _uiState.value = _uiState.value.copy(
                    bookmarkedAyahs = forSurah.mapNotNull { it.ayahNumber }.toSet(),
                    isSurahBookmarked = forSurah.any { it.ayahNumber == null },
                )
            }
        }
    }

    fun toggleAyahBookmark(ayah: Int) {
        val surah = _uiState.value.surah ?: return
        val bookmarked = ayah in _uiState.value.bookmarkedAyahs
        viewModelScope.launch {
            repository.toggleAyahBookmark(surah.number, ayah, !bookmarked)
        }
    }

    fun toggleSurahBookmark() {
        val surah = _uiState.value.surah ?: return
        val bookmarked = _uiState.value.isSurahBookmarked
        viewModelScope.launch {
            repository.toggleSurahBookmark(surah.number, !bookmarked)
        }
    }

    /** Called (debounced by the screen) as the reader scrolls. */
    fun saveReadingPosition(ayah: Int) {
        val surah = _uiState.value.surah ?: return
        viewModelScope.launch {
            repository.saveReadingPosition(surah.number, ayah.coerceIn(1, surah.ayahCount))
        }
    }

    fun openAdjacentSurah(delta: Int) {
        val current = _uiState.value.surah?.number ?: return
        val target = current + delta
        if (target in 1..114) load(target, requestedAyah = null)
    }
}
