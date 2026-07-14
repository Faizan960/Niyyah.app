package com.salahlock.app.ui.azkar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.db.entity.AzkarEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A favorited category summary for the Favorites bento. */
data class FavoriteCategory(
    val category: String,
    val favoriteCount: Int,
)

data class AzkarHubState(
    /** Real per-category dua counts from azkar_table. */
    val categoryCounts: Map<String, Int> = emptyMap(),
    /** Categories containing bookmarked azkar, most-favorited first. */
    val favoriteCategories: List<FavoriteCategory> = emptyList(),
    val favorites: List<AzkarEntity> = emptyList(),
)

/** Backs the Azkar hub: live category counts + favorites. */
class AzkarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SalahLockApplication).knowledgeRepository

    private val _state = MutableStateFlow(AzkarHubState())
    val state: StateFlow<AzkarHubState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.syncAzkarIfNeeded()
        }
        viewModelScope.launch {
            repository.getAzkarCategoryCounts().collect { counts ->
                _state.value = _state.value.copy(
                    categoryCounts = counts.associate { it.category to it.count },
                )
            }
        }
        viewModelScope.launch {
            repository.getBookmarkedAzkar().collect { favorites ->
                _state.value = _state.value.copy(
                    favorites = favorites,
                    favoriteCategories = favorites
                        .groupBy { it.category }
                        .map { (cat, list) -> FavoriteCategory(cat, list.size) }
                        .sortedByDescending { it.favoriteCount },
                )
            }
        }
    }
}
