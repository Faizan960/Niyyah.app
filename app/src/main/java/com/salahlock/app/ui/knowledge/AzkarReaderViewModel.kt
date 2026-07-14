package com.salahlock.app.ui.knowledge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.data.db.entity.AzkarEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AzkarReaderUiState(
    val category: String = "",
    val azkarList: List<AzkarEntity> = emptyList(),
    val isLoading: Boolean = true
)

class AzkarReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as com.salahlock.app.SalahLockApplication).knowledgeRepository

    private val _uiState = MutableStateFlow(AzkarReaderUiState())
    val uiState: StateFlow<AzkarReaderUiState> = _uiState.asStateFlow()

    fun loadCategory(category: String) {
        _uiState.value = _uiState.value.copy(category = category, isLoading = true)
        viewModelScope.launch {
            repository.touchAzkarCategory(category)
            repository.getAzkarByCategory(category).collect { list ->
                _uiState.value = _uiState.value.copy(azkarList = list, isLoading = false)
            }
        }
    }

    fun incrementProgress(azkar: AzkarEntity) {
        if (azkar.completedCount < azkar.targetCount) {
            viewModelScope.launch {
                repository.updateAzkarProgress(azkar.id, azkar.completedCount + 1)
            }
        }
    }

    fun toggleBookmark(id: Int, isBookmarked: Boolean) {
        viewModelScope.launch {
            repository.toggleAzkarBookmark(id, isBookmarked)
        }
    }
}
