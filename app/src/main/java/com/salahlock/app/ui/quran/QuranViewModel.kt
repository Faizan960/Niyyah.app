package com.salahlock.app.ui.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.repository.PageListItem
import com.salahlock.app.data.repository.Surah
import com.salahlock.app.data.repository.TOTAL_AYAHS
import com.salahlock.app.data.repository.filterSurahs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** BM-QURAN-PAGES — the two presentations of the ONE canonical Quran position. */
enum class ReadingMode { SURAH, PAGES }

/** "Continue reading" target — newest quran_progress row joined with its surah. */
data class ContinueReading(
    val surah: Surah,
    val lastAyah: Int,
)

/** Page-mode Continue Reading — the same canonical position resolved to a page. */
data class PageContinue(
    val page: Int,
    val surahTransliteration: String,
    val juz: Int,
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
    // ── BM-QURAN-PAGES ──
    val readingMode: ReadingMode = ReadingMode.SURAH,
    /** All 604 pages (lightweight rows) for the page browser; loaded lazily. */
    val pageList: List<PageListItem> = emptyList(),
    /** Starting page for each juz (index 0 = juz 1). */
    val juzStartPages: List<Int> = emptyList(),
    /** Page-mode Continue Reading derived from the same canonical position. */
    val pageContinue: PageContinue? = null,
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

    private val app = application as SalahLockApplication
    private val repository = app.quranRepository
    private val prefs = app.userPreferences

    private val _uiState = MutableStateFlow(QuranHubState())
    val uiState: StateFlow<QuranHubState> = _uiState.asStateFlow()

    init {
        loadSurahs()
        observeReadingMode()
        loadPageIndex()
    }

    private fun observeReadingMode() {
        viewModelScope.launch {
            prefs.quranReadingMode.collect { m ->
                val mode = runCatching { ReadingMode.valueOf(m) }.getOrDefault(ReadingMode.SURAH)
                _uiState.value = _uiState.value.copy(readingMode = mode)
            }
        }
    }

    /** Builds the 604-page browser index + juz jump targets once (offline, cached). */
    private fun loadPageIndex() {
        viewModelScope.launch {
            runCatching {
                val pages = repository.getPageList()
                val juz = repository.getJuzStartPages()
                _uiState.value = _uiState.value.copy(pageList = pages, juzStartPages = juz)
            }
        }
    }

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch { prefs.setQuranReadingMode(mode.name) }
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
                // Same canonical (surah, ayah) resolved to its Mushaf page for page mode.
                val pageTarget = last?.let { p ->
                    runCatching {
                        val page = repository.pageForSurahAyah(p.surahNumber, p.lastAyah)
                        val item = _uiState.value.pageList.getOrNull(page - 1)
                        PageContinue(
                            page = page,
                            surahTransliteration = item?.surahTransliteration
                                ?: byNumber[p.surahNumber]?.transliteration ?: "",
                            juz = item?.juz ?: 1,
                        )
                    }.getOrNull()
                }
                _uiState.value = _uiState.value.copy(continueReading = target, pageContinue = pageTarget)
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
