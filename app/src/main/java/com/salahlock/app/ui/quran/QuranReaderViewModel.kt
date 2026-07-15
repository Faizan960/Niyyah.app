package com.salahlock.app.ui.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.data.repository.QuranRepository
import com.salahlock.app.data.repository.Surah
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the surah reader: verses, per-ayah + surah bookmarks, position saves. */
class QuranReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = QuranRepository.getInstance(application)

    private val _surah = MutableStateFlow<Surah?>(null)
    val surah: StateFlow<Surah?> = _surah.asStateFlow()

    private val surahNumber = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val surahBookmarked: StateFlow<Boolean> = surahNumber
        .flatMapLatest { n -> if (n == 0) flowOf(false) else repo.isSurahBookmarked(n) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Set of bookmarked ayah numbers within the open surah. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bookmarkedAyahs: StateFlow<Set<Int>> = surahNumber
        .flatMapLatest { n ->
            repo.getAllBookmarks().map { list ->
                list.filter { it.surahNumber == n && it.ayahNumber != null }
                    .mapNotNull { it.ayahNumber }
                    .toSet()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun load(surah: Int) {
        if (surahNumber.value == surah && _surah.value != null) return
        surahNumber.value = surah
        viewModelScope.launch { _surah.value = repo.getSurah(surah) }
    }

    fun toggleAyahBookmark(ayah: Int) {
        val n = surahNumber.value.takeIf { it > 0 } ?: return
        val currentlyBookmarked = bookmarkedAyahs.value.contains(ayah)
        viewModelScope.launch { repo.toggleAyahBookmark(n, ayah, !currentlyBookmarked) }
    }

    fun toggleSurahBookmark() {
        val n = surahNumber.value.takeIf { it > 0 } ?: return
        val current = surahBookmarked.value
        viewModelScope.launch { repo.toggleSurahBookmark(n, !current) }
    }

    fun savePosition(ayah: Int) {
        val n = surahNumber.value.takeIf { it > 0 } ?: return
        viewModelScope.launch { repo.saveReadingPosition(n, ayah) }
    }
}
