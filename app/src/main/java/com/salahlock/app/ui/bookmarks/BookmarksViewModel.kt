package com.salahlock.app.ui.bookmarks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.BookmarkItem
import com.salahlock.app.data.model.BookmarkType
import com.salahlock.app.data.repository.CollectionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val isLoading: Boolean = true,
    val allItems: List<BookmarkItem> = emptyList(),
    val typeFilter: BookmarkType? = null,
    val searchQuery: String = "",
    val collections: List<CollectionSummary> = emptyList(),
) {
    /** Items after the type filter and case-insensitive search are applied. */
    val visibleItems: List<BookmarkItem>
        get() {
            val typed = typeFilter?.let { t -> allItems.filter { it.type == t } } ?: allItems
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) return typed
            return typed.filter {
                it.title.lowercase().contains(q) ||
                    it.body.lowercase().contains(q) ||
                    it.meta.lowercase().contains(q)
            }
        }
}

/**
 * BM-010.4 — unified Bookmarks screen state. Reads the preserved
 * BookmarksRepository aggregation (Quran/Hadith/Knowledge/Azkar); collection
 * membership goes through the preserved CollectionsRepository.
 */
class BookmarksViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SalahLockApplication
    private val bookmarksRepository = app.bookmarksRepository
    private val collectionsRepository = app.collectionsRepository

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                bookmarksRepository.observeAll(),
                collectionsRepository.observeCollections(),
            ) { items, collections -> items to collections }
                .collect { (items, collections) ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allItems = items,
                        collections = collections,
                    )
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun clearSearch() = onSearchQueryChanged("")

    fun onTypeFilterChanged(type: BookmarkType?) {
        _uiState.value = _uiState.value.copy(typeFilter = type)
    }

    fun remove(item: BookmarkItem) {
        viewModelScope.launch { bookmarksRepository.remove(item) }
    }

    fun addToCollection(collectionId: Long, item: BookmarkItem) {
        viewModelScope.launch { collectionsRepository.addItem(collectionId, item) }
    }

    /** Creates a collection and files [item] into it in one step. */
    fun createCollectionWith(name: String, item: BookmarkItem) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = collectionsRepository.create(name)
            collectionsRepository.addItem(id, item)
        }
    }
}
