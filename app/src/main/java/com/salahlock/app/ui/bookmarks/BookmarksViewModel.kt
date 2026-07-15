package com.salahlock.app.ui.bookmarks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.BookmarkItem
import com.salahlock.app.data.model.BookmarkType
import com.salahlock.app.data.repository.CollectionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val isLoading: Boolean = true,
    /** All bookmarks after filter + search + sort. */
    val items: List<BookmarkItem> = emptyList(),
    /** True when the user has no bookmarks at all (drives the empty state). */
    val isEmpty: Boolean = false,
    val filter: BookmarkType? = null,
    val query: String = "",
    val sortNewestFirst: Boolean = true,
    /** Collections for the add-to-collection sheet. */
    val collections: List<CollectionSummary> = emptyList(),
)

/** Backs the Bookmarks screen: unified live list, search, type filter, removal. */
class BookmarksViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val bookmarksRepository = app.bookmarksRepository
    private val collectionsRepository = app.collectionsRepository

    private val filter = MutableStateFlow<BookmarkType?>(null)
    private val query = MutableStateFlow("")
    private val sortNewestFirst = MutableStateFlow(true)

    val uiState: StateFlow<BookmarksUiState> = combine(
        bookmarksRepository.observeAll(),
        filter,
        query,
        sortNewestFirst,
        collectionsRepository.observeCollections(),
    ) { all, filter, query, newestFirst, collections ->
        val filtered = all
            .filter { filter == null || it.type == filter }
            .filter { it.matches(query) }
            .let { if (newestFirst) it else it.reversed() }
        BookmarksUiState(
            isLoading = false,
            items = filtered,
            isEmpty = all.isEmpty(),
            filter = filter,
            query = query,
            sortNewestFirst = newestFirst,
            collections = collections,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookmarksUiState())

    /** Case-insensitive across title, body, meta and Arabic text. */
    private fun BookmarkItem.matches(q: String): Boolean {
        val needle = q.trim()
        if (needle.isEmpty()) return true
        return title.contains(needle, ignoreCase = true) ||
            body.contains(needle, ignoreCase = true) ||
            meta.contains(needle, ignoreCase = true)
    }

    fun setFilter(type: BookmarkType?) {
        filter.value = type
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun toggleSort() {
        sortNewestFirst.value = !sortNewestFirst.value
    }

    fun remove(item: BookmarkItem) {
        viewModelScope.launch { bookmarksRepository.remove(item) }
    }

    fun addToCollection(collectionId: Long, item: BookmarkItem) {
        viewModelScope.launch { collectionsRepository.addItem(collectionId, item) }
    }

    fun createCollectionAndAdd(name: String, item: BookmarkItem) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = collectionsRepository.create(name)
            collectionsRepository.addItem(id, item)
        }
    }
}
