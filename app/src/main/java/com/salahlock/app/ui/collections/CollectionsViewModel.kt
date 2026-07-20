package com.salahlock.app.ui.collections

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

data class CollectionsUiState(
    val isLoading: Boolean = true,
    val collections: List<CollectionSummary> = emptyList(),
)

/** BM-010.5 — user collections list over the preserved CollectionsRepository. */
class CollectionsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SalahLockApplication).collectionsRepository

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCollections().collect {
                _uiState.value = CollectionsUiState(isLoading = false, collections = it)
            }
        }
    }

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.create(name) }
    }

    fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.rename(id, name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

data class CollectionDetailUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val items: List<BookmarkItem> = emptyList(),
    /** True once the collection row itself is gone (deleted elsewhere). */
    val collectionMissing: Boolean = false,
)

/**
 * BM-010.5 — one collection's contents. collection_items only stores
 * (contentType, contentKey) references; they are resolved against the live
 * unified bookmark stream, so a bookmark removed in its home module simply
 * stops appearing here — storage is never duplicated.
 */
class CollectionDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SalahLockApplication
    private val collectionsRepository = app.collectionsRepository
    private val bookmarksRepository = app.bookmarksRepository

    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    private var collectionId: Long = -1

    fun load(id: Long) {
        if (collectionId == id) return
        collectionId = id
        viewModelScope.launch {
            combine(
                collectionsRepository.observeCollection(id),
                collectionsRepository.observeItems(id),
                bookmarksRepository.observeAll(),
            ) { collection, memberships, bookmarks ->
                if (collection == null) {
                    CollectionDetailUiState(isLoading = false, collectionMissing = true)
                } else {
                    val byKey = bookmarks.associateBy { it.type.name to it.key }
                    val resolved = memberships.mapNotNull { m ->
                        byKey[m.contentType to m.contentKey]
                    }
                    CollectionDetailUiState(
                        isLoading = false,
                        name = collection.name,
                        items = resolved,
                    )
                }
            }.collect { _uiState.value = it }
        }
    }

    fun removeItem(item: BookmarkItem) {
        if (collectionId < 0) return
        viewModelScope.launch {
            collectionsRepository.removeItem(collectionId, item.type, item.key)
        }
    }
}
