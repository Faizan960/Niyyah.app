package com.salahlock.app.ui.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.repository.QuranRepository
import com.salahlock.app.data.repository.Surah
import com.salahlock.app.data.repository.TOTAL_AYAHS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Continue reading" card state, derived from the newest quran_progress row. */
data class ContinueReading(
    val surahNumber: Int,
    val surahName: String,
    val lastAyah: Int,
    val ayahCount: Int,
    val juz: Int,
    /** Overall Quran progress 0f..1f (sum of furthest ayahs / 6236). */
    val overallProgress: Float,
)

data class RecentSurah(
    val surahNumber: Int,
    val surahName: String,
    val lastAyah: Int,
)

data class QuranHubState(
    val loading: Boolean = true,
    val continueReading: ContinueReading? = null,
    val recents: List<RecentSurah> = emptyList(),
    val bookmarkCount: Int = 0,
    val collectionCount: Int = 0,
)

/** Backs the Quran hub + surah list + search. */
class QuranViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = QuranRepository.getInstance(application)

    private val _hub = MutableStateFlow(QuranHubState())
    val hub: StateFlow<QuranHubState> = _hub.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** null = "Surah" tab; 1..30 = juz filter. */
    private val _selectedJuz = MutableStateFlow<Int?>(null)
    val selectedJuz: StateFlow<Int?> = _selectedJuz.asStateFlow()

    private val _surahs = MutableStateFlow<List<Surah>>(emptyList())
    val surahs: StateFlow<List<Surah>> = _surahs.asStateFlow()

    init {
        viewModelScope.launch {
            val all = repo.getSurahs()
            _surahs.value = all
            val byNumber = all.associateBy { it.number }
            combine(
                repo.getLastRead(),
                repo.getRecentlyRead(),
                repo.getAllProgress(),
                repo.getAllBookmarks(),
            ) { last, recents, progress, bookmarks ->
                val overall = progress.sumOf { p ->
                    minOf(p.maxAyah, byNumber[p.surahNumber]?.ayahCount ?: 0)
                }.toFloat() / TOTAL_AYAHS
                QuranHubState(
                    loading = false,
                    continueReading = last?.let { p ->
                        val s = byNumber[p.surahNumber] ?: return@let null
                        ContinueReading(
                            surahNumber = s.number,
                            surahName = s.transliteration,
                            lastAyah = p.lastAyah,
                            ayahCount = s.ayahCount,
                            juz = s.ayahs.getOrNull(p.lastAyah - 1)?.juz ?: s.startJuz,
                            overallProgress = overall,
                        )
                    },
                    recents = recents.mapNotNull { p ->
                        byNumber[p.surahNumber]?.let { s ->
                            RecentSurah(s.number, s.transliteration, p.lastAyah)
                        }
                    },
                    bookmarkCount = bookmarks.size,
                    collectionCount = bookmarks.map { it.collectionName }.filter { it.isNotEmpty() }.distinct().size,
                )
            }.collect { _hub.value = it }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        refreshList()
    }

    fun selectJuz(juz: Int?) {
        _selectedJuz.value = juz
        refreshList()
    }

    private fun refreshList() {
        viewModelScope.launch {
            val juz = _selectedJuz.value
            val base = if (juz != null) repo.getSurahsInJuz(juz) else repo.getSurahs()
            _surahs.update { com.salahlock.app.data.repository.filterSurahs(base, _query.value) }
        }
    }
}
