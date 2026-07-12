package com.salahlock.app.ui.knowledge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.db.entity.AzkarEntity
import com.salahlock.app.data.db.entity.HadithEntity
import com.salahlock.app.data.db.entity.formattedReference
import com.salahlock.app.data.db.entity.globalNumber
import com.salahlock.app.data.repository.KnowledgeRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A search result with enough context to display inline and navigate to directly. */
data class HadithSearchResult(
    val hadith: HadithEntity,
    val bookTitle: String,
    val matchHighlight: String,
)

enum class SyncStatus { IDLE, SYNCING, SUCCESS, ERROR }

data class KnowledgeUiState(
    val isSyncing: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val syncError: String? = null,
    val selectedTab: KnowledgeTab = KnowledgeTab.HADITH,
    val dailyHadith: HadithEntity? = null,
    val bookmarkedHadiths: List<HadithEntity> = emptyList(),
    val recentHadiths: List<HadithEntity> = emptyList(),
    val categories: List<String> = listOf("Salah", "Faith", "Dua", "Family", "Character", "Patience"),
    val azkarCategories: List<String> = listOf(
        "Morning", "Evening", "After Prayer",
        "Before Wudu", "After Wudu",
        "Entering Mosque", "Leaving Mosque",
        "Sleep", "Waking Up", "Travel", "Food", "Anxiety",
    ),
    val searchQuery: String = "",
    val searchResults: List<HadithSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isSearchActive: Boolean = false,
)

enum class KnowledgeTab { HADITH, AZKAR }

@OptIn(FlowPreview::class)
class KnowledgeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication

    // Use application-scoped singleton — prevents duplicate Retrofit instances and concurrent syncs.
    private val repository = app.knowledgeRepository

    private val _uiState = MutableStateFlow(KnowledgeUiState())
    val uiState: StateFlow<KnowledgeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        syncData()
        loadBookmarksAndRecent()
        observeSearch()
    }

    private fun syncData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncStatus = SyncStatus.SYNCING)
            val syncResult = repository.syncHadithCollectionsIfNeeded()
            repository.syncAzkarIfNeeded()
            loadDailyHadith()

            val newStatus = when (syncResult) {
                KnowledgeRepository.SyncResult.Success,
                KnowledgeRepository.SyncResult.AlreadySynced -> SyncStatus.SUCCESS
                KnowledgeRepository.SyncResult.PartialFailure -> SyncStatus.ERROR
            }
            val errorMsg = if (newStatus == SyncStatus.ERROR)
                "Some hadiths failed to load. Check your connection and tap Retry."
            else null

            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                syncStatus = newStatus,
                syncError = errorMsg,
            )
        }
    }

    fun retrySyncData() {
        _uiState.value = _uiState.value.copy(syncError = null)
        syncData()
    }

    private suspend fun loadDailyHadith() {
        val hadiths = repository.getHadithsByTopic("Salah", "eng", limit = 100, offset = 0)
        if (hadiths.isNotEmpty()) {
            val dayOfYear = LocalDate.now().dayOfYear
            _uiState.value = _uiState.value.copy(dailyHadith = hadiths[dayOfYear % hadiths.size])
        }
    }

    private fun loadBookmarksAndRecent() {
        viewModelScope.launch {
            repository.getBookmarkedHadiths().collect { bookmarked ->
                _uiState.value = _uiState.value.copy(bookmarkedHadiths = bookmarked)
            }
        }
        viewModelScope.launch {
            repository.getRecentHadiths().collect { recent ->
                _uiState.value = _uiState.value.copy(recentHadiths = recent)
            }
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(350L)
                .collect { query ->
                    if (query.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                        )
                        return@collect
                    }

                    _uiState.value = _uiState.value.copy(isSearching = true)
                    val results = performSearch(query.trim())
                    _uiState.value = _uiState.value.copy(
                        searchResults = results,
                        isSearching = false,
                    )
                }
        }
    }

    /**
     * Dispatches the query to the appropriate search strategy:
     * - "bukhari 52" / "muslim 178" → reference-number lookup
     * - "bukhari prayer" / "muslim charity" → collection-scoped keyword search
     * - "salah" / "patience" → full keyword search
     */
    private suspend fun performSearch(query: String): List<HadithSearchResult> {
        // Pattern: "{collection} {number}" — e.g., "bukhari 647" or "b 52"
        val refPattern = Regex("""^(bukhari|b|muslim|m)\s+(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
        val refMatch = refPattern.find(query)
        if (refMatch != null) {
            val collection = refMatch.groupValues[1].let {
                if (it.equals("b", ignoreCase = true)) "bukhari"
                else if (it.equals("m", ignoreCase = true)) "muslim"
                else it.lowercase()
            }
            val globalNum = refMatch.groupValues[2]
            val hadith = repository.getHadithByGlobalNumber(collection, globalNum, "eng")
            return if (hadith != null) {
                val bookTitle = repository.getBookTitle(collection, hadith.bookNumber) ?: "Book ${hadith.bookNumber}"
                listOf(HadithSearchResult(hadith, bookTitle, hadith.translationText.take(150)))
            } else emptyList()
        }

        // Pattern: "{collection} {keyword}" — e.g., "bukhari prayer"
        val collectionPattern = Regex("""^(bukhari|muslim)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val collectionMatch = collectionPattern.find(query)
        if (collectionMatch != null) {
            val collection = collectionMatch.groupValues[1].lowercase()
            val keyword = collectionMatch.groupValues[2]
            return repository.searchInCollection(collection, keyword, "eng")
                .map { hadith ->
                    val bookTitle = repository.getBookTitle(collection, hadith.bookNumber) ?: "Book ${hadith.bookNumber}"
                    HadithSearchResult(hadith, bookTitle, buildHighlight(hadith, keyword))
                }
        }

        // General keyword search
        return repository.searchHadiths(query, "eng").map { hadith ->
            val bookTitle = repository.getBookTitle(hadith.collection, hadith.bookNumber)
                ?: "Book ${hadith.bookNumber}"
            HadithSearchResult(hadith, bookTitle, buildHighlight(hadith, query))
        }
    }

    private fun buildHighlight(hadith: HadithEntity, query: String): String {
        val text = hadith.translationText
        val idx = text.indexOf(query, ignoreCase = true)
        return if (idx >= 0) {
            val start = maxOf(0, idx - 30)
            val end = minOf(text.length, idx + query.length + 120)
            (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
        } else {
            text.take(150)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            isSearchActive = query.isNotBlank(),
        )
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearchActive = false,
        )
    }

    fun selectTab(tab: KnowledgeTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }
}
