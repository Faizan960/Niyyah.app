package com.salahlock.app.ui.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.repository.Surah
import com.salahlock.app.data.repository.TOTAL_AYAHS
import com.salahlock.app.data.repository.filterSurahs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** "Continue reading" target — newest quran_progress row joined with its surah. */
data class ContinueReading(
    val surah: Surah,
    val lastAyah: Int,
)

/** Real revelation-place filter (dataset field), surfaced by the Quran search filter icon. */
enum class RevelationFilter(val label: String) {
    ALL("All Surahs"),
    MECCAN("Meccan"),
    MEDINAN("Medinan"),
}

data class QuranHubState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val surahs: List<Surah> = emptyList(),
    val searchQuery: String = "",
    val revelationFilter: RevelationFilter = RevelationFilter.ALL,
    val filteredSurahs: List<Surah> = emptyList(),
    val continueReading: ContinueReading? = null,
    /** Ayahs ever reached across all surahs, out of [TOTAL_AYAHS]. */
    val ayahsRead: Int = 0,
) {
    val progressPercent: Int
        get() = if (ayahsRead <= 0) 0 else (ayahsRead * 100 / TOTAL_AYAHS).coerceIn(0, 100)
}

/**
 * BM-010.2 — Quran hub (surah list) backed by the preserved QuranRepository:
 * text from assets/quran.json, progress from Room. Search reuses the same
 * filterSurahs() covered by SurahSearchTest.
 */
class QuranViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SalahLockApplication).quranRepository

    private val _uiState = MutableStateFlow(QuranHubState())
    val uiState: StateFlow<QuranHubState> = _uiState.asStateFlow()

    init {
        loadSurahs()
    }

    fun loadSurahs() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val surahs = repository.getSurahs()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    surahs = surahs,
                    filteredSurahs = applyFilters(surahs, _uiState.value.searchQuery, _uiState.value.revelationFilter),
                )
                observeProgress(surahs)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "The Quran library could not be loaded. Please try again.",
                )
            }
        }
    }

    private fun observeProgress(surahs: List<Surah>) {
        val byNumber = surahs.associateBy { it.number }
        viewModelScope.launch {
            repository.getLastRead().collect { last ->
                val target = last?.let { p ->
                    byNumber[p.surahNumber]?.let { ContinueReading(it, p.lastAyah) }
                }
                _uiState.value = _uiState.value.copy(continueReading = target)
            }
        }
        viewModelScope.launch {
            repository.getAllProgress().collect { rows ->
                _uiState.value = _uiState.value.copy(ayahsRead = rows.sumOf { it.maxAyah })
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredSurahs = applyFilters(_uiState.value.surahs, query, _uiState.value.revelationFilter),
        )
    }

    fun onRevelationFilterChanged(filter: RevelationFilter) {
        _uiState.value = _uiState.value.copy(
            revelationFilter = filter,
            filteredSurahs = applyFilters(_uiState.value.surahs, _uiState.value.searchQuery, filter),
        )
    }

    fun clearSearch() = onSearchQueryChanged("")

    private fun applyFilters(
        surahs: List<Surah>,
        query: String,
        filter: RevelationFilter,
    ): List<Surah> {
        val bySearch = filterSurahs(surahs, query)
        return when (filter) {
            RevelationFilter.ALL -> bySearch
            RevelationFilter.MECCAN -> bySearch.filter { it.revelationType.equals("Meccan", ignoreCase = true) }
            RevelationFilter.MEDINAN -> bySearch.filter { it.revelationType.equals("Medinan", ignoreCase = true) }
        }
    }
}
