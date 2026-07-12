package com.salahlock.app.ui.blacklist

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.db.entity.AppBlacklistItem
import com.salahlock.app.data.model.AppCategory
import com.salahlock.app.data.model.BlockProfile
import com.salahlock.app.data.repository.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppSortOrder(val displayName: String) {
    ALPHABETICAL("A – Z"),
    RECENTLY_INSTALLED("Recently Installed"),
}

data class UiAppItem(
    val entity: AppBlacklistItem,
    val icon: Drawable?,
    val category: AppCategory,
    val firstInstallTime: Long,
    val isSelected: Boolean = false,
)

data class BlacklistUiState(
    val items: List<UiAppItem> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedCategory: AppCategory? = null,
    val sortOrder: AppSortOrder = AppSortOrder.ALPHABETICAL,
    val activeProfile: BlockProfile = BlockProfile.CUSTOM,
    val isMultiSelectMode: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val blockedCount: Int = 0,
    val totalCount: Int = 0,
    /**
     * Precomputed filtered + sorted list (Sprint L.4). Computed once in the ViewModel
     * via [computeFilteredItems] whenever inputs change — never sorted in recomposition.
     */
    val filteredItems: List<UiAppItem> = emptyList(),
) {
    val availableCategories: List<AppCategory>
        get() = items.map { it.category }.toSet().sortedBy { it.displayName }
}

/**
 * Filters by search/category, then sorts BLOCKED apps first, alphabetically (or by
 * install date) within each group. Pure function — call from the ViewModel only.
 */
private fun computeFilteredItems(
    items: List<UiAppItem>,
    query: String,
    category: AppCategory?,
    sortOrder: AppSortOrder,
): List<UiAppItem> {
    var list = items
    if (query.isNotBlank()) {
        val q = query.lowercase()
        list = list.filter {
            it.entity.appLabel.lowercase().contains(q) || it.entity.packageName.lowercase().contains(q)
        }
    }
    if (category != null) {
        list = list.filter { it.category == category }
    }
    val withinGroup: Comparator<UiAppItem> = when (sortOrder) {
        AppSortOrder.ALPHABETICAL -> compareBy { it.entity.appLabel.lowercase() }
        AppSortOrder.RECENTLY_INSTALLED -> compareByDescending { it.firstInstallTime }
    }
    // Blocked first, then the chosen order within each group.
    return list.sortedWith(
        compareByDescending<UiAppItem> { it.entity.isBlocked }.then(withinGroup)
    )
}

@OptIn(FlowPreview::class)
class BlacklistViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val pm = application.packageManager

    private val _uiState = MutableStateFlow(BlacklistUiState())
    val uiState: StateFlow<BlacklistUiState> = _uiState.asStateFlow()

    init {
        loadApps()
        observeProfile()
    }
    // SL-006: Lock Per Prayer moved to Profile → Settings → Lock Per Prayer.
    // Its single state holder is ProfileViewModel (via UserPreferences) — the
    // duplicate copy that lived here has been removed.

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load enriched app list from PackageManager + DB on IO thread
            val enriched: List<UiAppItem> = withContext(Dispatchers.IO) {
                val installedWithMeta = app.blacklistRepository.getInstalledAppsWithCategory()
                installedWithMeta.map { info ->
                    val icon: Drawable? = try { pm.getApplicationIcon(info.packageName) } catch (_: Exception) { null }
                    UiAppItem(
                        entity = AppBlacklistItem(packageName = info.packageName, appLabel = info.appLabel, isBlocked = info.isBlocked),
                        icon = icon,
                        category = info.category,
                        firstInstallTime = info.firstInstallTime,
                    )
                }
            }

            _uiState.update { state ->
                state.copy(
                    items = enriched,
                    isLoading = false,
                    blockedCount = enriched.count { it.entity.isBlocked },
                    totalCount = enriched.size,
                )
            }
            recomputeFiltered()

            // After initial load, observe DB changes to keep blocked state in sync
            observeDbChanges()
        }
    }

    private fun observeDbChanges() {
        viewModelScope.launch {
            app.blacklistRepository.observeAll()
                .debounce(150L)
                .collect { dbItems ->
                    val dbMap = dbItems.associateBy { it.packageName }
                    _uiState.update { state ->
                        val updated = state.items.map { uiItem ->
                            val dbItem = dbMap[uiItem.entity.packageName]
                            if (dbItem != null && dbItem.isBlocked != uiItem.entity.isBlocked) {
                                uiItem.copy(entity = uiItem.entity.copy(isBlocked = dbItem.isBlocked, id = dbItem.id))
                            } else {
                                uiItem.copy(entity = uiItem.entity.copy(id = dbItem?.id ?: uiItem.entity.id))
                            }
                        }
                        state.copy(
                            items = updated,
                            blockedCount = updated.count { it.entity.isBlocked },
                        )
                    }
                    // Re-sort instantly when block state changes (toggle / block all /
                    // allow all / profile preset all funnel through the DB observer).
                    recomputeFiltered()
                }
        }
    }

    /** Recomputes [BlacklistUiState.filteredItems] from the current state. VM-only. */
    private fun recomputeFiltered() = _uiState.update { s ->
        s.copy(filteredItems = computeFilteredItems(s.items, s.searchQuery, s.selectedCategory, s.sortOrder))
    }

    private fun observeProfile() {
        viewModelScope.launch {
            app.userPreferences.blockProfile.collect { profileName ->
                _uiState.update { it.copy(activeProfile = BlockProfile.fromName(profileName)) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recomputeFiltered()
    }

    fun setCategory(category: AppCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
        recomputeFiltered()
    }

    fun setSortOrder(order: AppSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        recomputeFiltered()
    }

    fun toggle(item: UiAppItem) = viewModelScope.launch {
        app.blacklistRepository.toggle(item.entity)
    }

    fun blockAll() = viewModelScope.launch {
        app.blacklistRepository.blockAll()
    }

    fun unblockAll() = viewModelScope.launch {
        app.blacklistRepository.unblockAll()
    }

    fun blockCategory(category: AppCategory) = viewModelScope.launch {
        app.blacklistRepository.blockCategory(category)
    }

    fun applyProfile(profile: BlockProfile) = viewModelScope.launch {
        app.blacklistRepository.applyProfile(profile)
        app.userPreferences.setBlockProfile(profile.name)
    }

    // --- Multi-select ---
    fun enterMultiSelect() {
        _uiState.update { it.copy(isMultiSelectMode = true, selectedPackages = emptySet()) }
    }

    fun exitMultiSelect() {
        _uiState.update { it.copy(isMultiSelectMode = false, selectedPackages = emptySet()) }
    }

    fun toggleSelection(packageName: String) {
        _uiState.update { state ->
            val updated = if (state.selectedPackages.contains(packageName)) {
                state.selectedPackages - packageName
            } else {
                state.selectedPackages + packageName
            }
            state.copy(selectedPackages = updated)
        }
    }

    fun selectAll() {
        val visible = _uiState.value.filteredItems.map { it.entity.packageName }.toSet()
        _uiState.update { it.copy(selectedPackages = visible) }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedPackages = emptySet()) }
    }

    fun blockSelected() = viewModelScope.launch {
        val selected = _uiState.value.selectedPackages.toList()
        if (selected.isNotEmpty()) {
            app.database.appBlacklistDao().setBlockedForPackages(selected, true)
        }
        exitMultiSelect()
    }

    fun unblockSelected() = viewModelScope.launch {
        val selected = _uiState.value.selectedPackages.toList()
        if (selected.isNotEmpty()) {
            app.database.appBlacklistDao().setBlockedForPackages(selected, false)
        }
        exitMultiSelect()
    }
}
