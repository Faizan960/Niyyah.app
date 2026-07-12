package com.salahlock.app.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.AppCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
)

data class OnboardingAppsState(
    val isLoading: Boolean = true,
    val apps: List<OnboardingApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
) {
    val filtered: List<OnboardingApp>
        get() = if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    val selectedCount: Int get() = selected.size
}

/**
 * Backs the onboarding "Choose Apps To Lock" step.
 *
 * Reuses [com.salahlock.app.data.repository.AppBlacklistRepository] — the SAME
 * Room-backed blacklist the App Blacklist screen and lock engine use. No separate
 * onboarding storage: selection is persisted via [AppBlacklistRepository.applySelection].
 */
class OnboardingAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as SalahLockApplication).blacklistRepository

    private val _state = MutableStateFlow(OnboardingAppsState())
    val state: StateFlow<OnboardingAppsState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            // Ensure DB rows exist (also seeds sensible defaults on first run).
            repo.syncInstalledApps()
            val installed = repo.getInstalledAppsWithCategory()
                .sortedBy { it.appLabel.lowercase() }
            _state.update {
                it.copy(
                    isLoading = false,
                    apps = installed.map { a -> OnboardingApp(a.packageName, a.appLabel, a.category) },
                    // Pre-select the default-blocked set so common distractions start ticked.
                    selected = installed.filter { a -> a.isBlocked }.map { a -> a.packageName }.toSet(),
                )
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun toggle(pkg: String) = _state.update {
        it.copy(selected = if (pkg in it.selected) it.selected - pkg else it.selected + pkg)
    }

    /** Presets are additive — adds every installed app in [category] to the selection. */
    fun applyPreset(category: AppCategory) = _state.update { s ->
        val add = s.apps.filter { it.category == category }.map { it.packageName }
        s.copy(selected = s.selected + add)
    }

    /** Persists the current selection into the shared blacklist, then invokes [onDone]. */
    fun persist(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.applySelection(_state.value.selected)
            onDone()
        }
    }
}
