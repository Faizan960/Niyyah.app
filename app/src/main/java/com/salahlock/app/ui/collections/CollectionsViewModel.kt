package com.salahlock.app.ui.collections

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.BookmarkType
import com.salahlock.app.data.repository.CollectionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionsUiState(
    val isLoading: Boolean = true,
    /** Live bookmark counts per module for the Saved bento grid. */
    val savedCounts: Map<BookmarkType, Int> = emptyMap(),
    /** User collections after search filtering, newest first. */
    val collections: List<CollectionSummary> = emptyList(),
    /** True when the user has not created any collection yet. */
    val hasNoCollections: Boolean = false,
    val query: String = "",
)

/** Backs the Collections screen: saved counts bento + user libraries CRUD. */
class CollectionsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val collectionsRepository = app.collectionsRepository
    private val bookmarksRepository = app.bookmarksRepository

    private val query = MutableStateFlow("")

    val uiState: StateFlow<CollectionsUiState> = combine(
        bookmarksRepository.observeCounts(),
        collectionsRepository.observeCollections(),
        query,
    ) { counts, collections, query ->
        val needle = query.trim()
        CollectionsUiState(
            isLoading = false,
            savedCounts = counts,
            collections = collections.filter {
                needle.isEmpty() || it.name.contains(needle, ignoreCase = true)
            },
            hasNoCollections = collections.isEmpty(),
            query = query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CollectionsUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { collectionsRepository.create(name) }
    }

    fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { collectionsRepository.rename(id, name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { collectionsRepository.delete(id) }
    }
}
