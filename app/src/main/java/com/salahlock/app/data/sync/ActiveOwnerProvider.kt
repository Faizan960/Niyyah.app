package com.salahlock.app.data.sync

import com.salahlock.app.data.db.entity.OwnerIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * BM-013 — the single seam that resolves the **active owner id** used to scope
 * every user-owned Room query. Query scoping through this provider is the PRIMARY
 * local isolation mechanism; logout purge is only secondary defense.
 *
 * ## Ownership state machine (review item 2)
 *  - [OwnerScope.LegacyUnclaimed] → resolves to [OwnerIds.LOCAL]. The pre-account,
 *    never-adopted dataset.
 *  - [OwnerScope.Authenticated] → resolves to the Clerk user id. Reached after the
 *    legacy dataset is adopted (or a returning account signs in) — driven by
 *    [AccountSyncCoordinator] since Checkpoint C.
 *  - [OwnerScope.SignedOutNoUser] → resolves to [OwnerIds.NONE], which matches no
 *    stored rows. Reached when signing out AFTER adoption, so a previously-claimed
 *    private dataset is never re-exposed. Crucially, `__local__` is NOT reused as a
 *    signed-out bucket once adoption has happened.
 *
 * Checkpoint C: the scope is a [StateFlow] so live Room flows can re-key with
 * `flatMapLatest` — an open screen switches datasets the moment the owner changes
 * (mandatory visibility isolation on logout, without waiting for recomposition).
 */
sealed interface OwnerScope {
    data object LegacyUnclaimed : OwnerScope
    data class Authenticated(val clerkUserId: String) : OwnerScope
    data object SignedOutNoUser : OwnerScope
}

class ActiveOwnerProvider {

    private val _scope = MutableStateFlow<OwnerScope>(OwnerScope.LegacyUnclaimed)

    val scopeFlow: StateFlow<OwnerScope> get() = _scope

    /** Reactive owner id — collect via `flatMapLatest` to re-key live queries. */
    val ownerIdFlow = _scope.map { it.toOwnerId() }

    fun scope(): OwnerScope = _scope.value

    /** The owner id all user-scoped queries must filter by right now. */
    fun ownerId(): String = _scope.value.toOwnerId()

    private fun OwnerScope.toOwnerId(): String = when (this) {
        is OwnerScope.LegacyUnclaimed -> OwnerIds.LOCAL
        is OwnerScope.Authenticated -> clerkUserId
        is OwnerScope.SignedOutNoUser -> OwnerIds.NONE
    }

    /** Account signed in and now owns its data (post-adoption / returning user). */
    fun onAuthenticated(clerkUserId: String) {
        require(clerkUserId.isNotBlank()) { "clerkUserId required" }
        _scope.value = OwnerScope.Authenticated(clerkUserId)
    }

    /**
     * Sign-out transition. If the legacy dataset was ever adopted, we must NOT fall
     * back to `__local__` (that data now belongs to an account) — go to the empty
     * [OwnerScope.SignedOutNoUser] scope. Only a device that never adopted returns to
     * the legacy-unclaimed view.
     */
    fun onSignedOut(legacyEverAdopted: Boolean) {
        _scope.value = if (legacyEverAdopted) OwnerScope.SignedOutNoUser else OwnerScope.LegacyUnclaimed
    }

    companion object {
        /** Process-wide instance used by repositories (manual DI). */
        val shared: ActiveOwnerProvider = ActiveOwnerProvider()
    }
}
