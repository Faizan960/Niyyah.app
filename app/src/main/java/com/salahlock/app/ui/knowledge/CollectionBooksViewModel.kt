package com.salahlock.app.ui.knowledge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.data.db.entity.CollectionBookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CollectionBooksUiState(
    val collectionName: String = "",
    val books: List<CollectionBookEntity> = emptyList(),
    val isLoading: Boolean = true
)

class CollectionBooksViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as com.salahlock.app.SalahLockApplication).knowledgeRepository
    private val _uiState = MutableStateFlow(CollectionBooksUiState())
    val uiState: StateFlow<CollectionBooksUiState> = _uiState.asStateFlow()

    fun loadBooks(collectionName: String) {
        _uiState.value = _uiState.value.copy(collectionName = collectionName, isLoading = true)
        viewModelScope.launch {
            val books = repository.getBooksForCollection(collectionName)
            _uiState.value = _uiState.value.copy(books = books, isLoading = false)
        }
    }
}
