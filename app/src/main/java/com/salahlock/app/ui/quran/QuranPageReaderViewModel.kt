package com.salahlock.app.ui.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.repository.QuranPageContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Top-level reader chrome state (page count, translation pref, juz jump targets). */
data class PageReaderState(
    val pageCount: Int = 604,
    val initialPage: Int = 1,
    val ready: Boolean = false,
    val showTranslation: Boolean = false,
    val juzStartPages: List<Int> = emptyList(),
    val surahStartPages: List<Int> = emptyList(),
)

/**
 * BM-QURAN-PAGES — page (Mushaf) reader.
 *
 * Reuses the SAME [com.salahlock.app.data.repository.QuranRepository]: page content is
 * rendered from the bundled corpus + verified page mapping (offline), bookmarks flow
 * through the existing BM-013 ayah bookmark system, and reading position persists into
 * the SAME quran_progress (as the page's first ayah) so Surah and Page modes share one
 * canonical position.
 */
class QuranPageReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SalahLockApplication
    private val repository = app.quranRepository
    private val prefs = app.userPreferences

    private val _state = MutableStateFlow(PageReaderState())
    val state: StateFlow<PageReaderState> = _state.asStateFlow()

    /** Bookmarked ayah keys (surah*10000+ayah) — same source as Surah mode. */
    val bookmarkedKeys: StateFlow<Set<Long>> =
        repository.getAllBookmarks()
            .map { rows -> rows.mapNotNull { r -> r.ayahNumber?.let { key(r.surahNumber, it) } }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val cache = HashMap<Int, QuranPageContent>()
    private val cacheMutex = Mutex()

    fun init(requestedPage: Int) {
        if (_state.value.ready) return
        viewModelScope.launch {
            val count = runCatching { repository.pageCount() }.getOrDefault(604)
            val juz = runCatching { repository.getJuzStartPages() }.getOrDefault(emptyList())
            val surahStarts = runCatching {
                (1..114).map { repository.pageForSurahAyah(it, 1) }
            }.getOrDefault(emptyList())
            val translation = runCatching { prefs.quranPageTranslation.first() }.getOrDefault(false)
            _state.value = _state.value.copy(
                pageCount = count,
                initialPage = requestedPage.coerceIn(1, count),
                juzStartPages = juz,
                surahStartPages = surahStarts,
                showTranslation = translation,
                ready = true,
            )
            // Keep the translation toggle live.
            prefs.quranPageTranslation.collect { on ->
                _state.value = _state.value.copy(showTranslation = on)
            }
        }
    }

    /** Page content, cached so swiping back/forward never re-parses the corpus. */
    suspend fun pageContent(page: Int): QuranPageContent {
        cache[page]?.let { return it }
        return cacheMutex.withLock {
            cache[page] ?: repository.getPageContent(page).also { cache[page] = it }
        }
    }

    fun toggleTranslation() {
        viewModelScope.launch { prefs.setQuranPageTranslation(!_state.value.showTranslation) }
    }

    /** Persist the settled page's FIRST ayah — the shared canonical reading position. */
    fun savePagePosition(page: Int) {
        viewModelScope.launch {
            val content = runCatching { pageContent(page) }.getOrNull() ?: return@launch
            repository.saveReadingPosition(content.firstSurah, content.firstAyah)
        }
    }

    fun toggleAyahBookmark(surah: Int, ayah: Int) {
        val bookmarked = key(surah, ayah) in bookmarkedKeys.value
        viewModelScope.launch { repository.toggleAyahBookmark(surah, ayah, !bookmarked) }
    }

    /** The surah + ayah to open when the user picks "Open in Surah View" from [page]. */
    suspend fun surahAyahForPage(page: Int): Pair<Int, Int> =
        pageContent(page).let { it.firstSurah to it.firstAyah }

    companion object {
        fun key(surah: Int, ayah: Int): Long = surah.toLong() * 10_000L + ayah
    }
}
