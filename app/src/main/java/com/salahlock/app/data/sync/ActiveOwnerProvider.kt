package com.salahlock.app.data.sync

import com.salahlock.app.data.db.entity.OwnerIds

/**
 * BM-013 Checkpoint B — the single seam that resolves the **active owner id** used
 * to scope every user-owned Room query. Query scoping through this provider is the
 * PRIMARY local isolation mechanism; logout purge is only secondary defense.
 *
 * ## Ownership state machine (review item 2)
 *  - [OwnerScope.LegacyUnclaimed] → resolves to [OwnerIds.LOCAL]. The pre-account,
 *    never-adopted dataset. This is the ONLY state during the local-foundation
 *    checkpoint, so behavior is visually/functionally identical to before.
 *  - [OwnerScope.Authenticated] → resolves to the Clerk user id. Reached after the
 *    legacy dataset is adopted (or a returning account signs in) — wired by the sync
 *    slice, not here.
 *  - [OwnerScope.SignedOutNoUser] → resolves to [OwnerIds.NONE], which matches no
 *    stored rows. Reached when signing out AFTER adoption, so a previously-claimed
 *    private dataset is never re-exposed. Crucially, `__local__` is NOT reused as a
 *    signed-out bucket once adoption has happened.
 *
 * The live transitions are intentionally not driven during this checkpoint; the
 * architecture merely supports them safely.
 */
sealed interface OwnerScope {
    data object LegacyUnclaimed : OwnerScope
    data class Authenticated(val clerkUserId: String) : OwnerScope
    data object SignedOutNoUser : OwnerScope
}

class ActiveOwnerProvider {

    @Volatile
    private var scope: OwnerScope = OwnerScope.LegacyUnclaimed

    fun scope(): OwnerScope = scope

    /** The owner id all user-scoped queries must filter by right now. */
    fun ownerId(): String = when (val s = scope) {
        is OwnerScope.LegacyUnclaimed -> OwnerIds.LOCAL
        is OwnerScope.Authenticated -> s.clerkUserId
        is OwnerScope.SignedOutNoUser -> OwnerIds.NONE
    }

    /** Account signed in and now owns its data (post-adoption / returning user). */
    fun onAuthenticated(clerkUserId: String) {
        require(clerkUserId.isNotBlank()) { "clerkUserId required" }
        scope = OwnerScope.Authenticated(clerkUserId)
    }

    /**
     * Sign-out transition. If the legacy dataset was ever adopted, we must NOT fall
     * back to `__local__` (that data now belongs to an account) — go to the empty
     * [OwnerScope.SignedOutNoUser] scope. Only a device that never adopted returns to
     * the legacy-unclaimed view.
     */
    fun onSignedOut(legacyEverAdopted: Boolean) {
        scope = if (legacyEverAdopted) OwnerScope.SignedOutNoUser else OwnerScope.LegacyUnclaimed
    }

    companion object {
        /** Process-wide instance used by repositories (manual DI). */
        val shared: ActiveOwnerProvider = ActiveOwnerProvider()
    }
}
