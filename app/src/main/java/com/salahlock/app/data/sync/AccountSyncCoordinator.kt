package com.salahlock.app.data.sync

import android.content.Context
import android.util.Log
import com.salahlock.app.auth.AuthRepository
import com.salahlock.app.auth.AuthState
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.work.QuranBookmarkSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * BM-013 Checkpoint C — the single place where Clerk auth lifecycle drives local
 * ownership + cloud sync. Collects [AuthRepository.state] for the whole process
 * lifetime and performs, in a strict order that can never expose another user's
 * data:
 *
 * ## On SignedIn(user)
 *  1. Read the one-time legacy ledger:
 *     - **unclaimed** → [LegacyAdoptionManager.adoptLegacyDataOnce]: claim + re-stamp
 *       every cloud-synced `__local__` row (Quran bookmarks/progress, collections,
 *       Hadith/Azkar state) to this user atomically, then activate their scope
 *       (adoption manager flips the scope itself immediately after the commit, so
 *       there is no window where claimed data belongs to nobody visible). Device-local
 *       prayer/streak/override data is intentionally NOT adopted — it stays `__local__`.
 *     - **adopted by THIS user** → idempotent straggler sweep, then activate.
 *     - **adopted by ANOTHER user** → activate this user's own (empty) scope only;
 *       the other account's rows are unreachable by query scoping. Never re-claim.
 *  2. Schedule a sync pass (first sync reconciles local vs cloud; see engine).
 *
 * ## On SignedOut
 *  Visibility isolation is IMMEDIATE and mandatory: the scope flips to
 *  [OwnerScope.SignedOutNoUser] (post-adoption) so no private row matches any
 *  query — live screens re-key via the owner StateFlow. Pending unsynced outbox
 *  events are RETAINED (owner-tagged, invisible, pushed on next sign-in) — logout
 *  never blocks on network and never destroys unsynced data. Device-local data
 *  (blacklist, lock config, prayer cache, permissions) is untouched; there is no
 *  physical purge in this slice (visibility isolation ≠ cache purge).
 */
class AccountSyncCoordinator(
    private val appContext: Context,
    private val db: AppDatabase,
    private val authRepository: AuthRepository,
    private val activeOwner: ActiveOwnerProvider,
    private val adoptionManager: LegacyAdoptionManager,
    /** Injectable for tests; production schedules the WorkManager sync worker. */
    private val scheduleSync: (Context) -> Unit = { QuranBookmarkSyncWorker.schedule(it) },
) {
    private companion object { const val TAG = "AccountSyncCoord" }

    fun start(scope: CoroutineScope) {
        scope.launch {
            authRepository.state.collect { state ->
                when (state) {
                    is AuthState.SignedIn -> onSignedIn(state.user.id)
                    AuthState.SignedOut -> onSignedOut()
                    // Loading/Error: keep the current scope; never guess an owner.
                    else -> Unit
                }
            }
        }
    }

    internal suspend fun onSignedIn(userId: String) {
        runCatching {
            when (val adoptedBy = db.legacyOwnershipDao().get()?.adoptedBy) {
                null -> {
                    val claimed = adoptionManager.adoptLegacyDataOnce(
                        clerkUserId = userId,
                        nowMs = System.currentTimeMillis(),
                    )
                    Log.d(TAG, "Legacy adoption attempted: claimedNow=$claimed")
                }
                userId -> adoptionManager.reclaimStragglers(userId)
                else -> Log.d(TAG, "Legacy data owned by a different account — not exposed.")
                    .also { /* no re-claim, ever */ }
            }
        }.onFailure { Log.e(TAG, "Adoption step failed: ${it.message}", it) }
        // Activate this user's scope regardless of adoption outcome (their own data
        // is theirs; adoption failure only delays legacy claim until next sign-in).
        activeOwner.onAuthenticated(userId)
        runCatching { scheduleSync(appContext) }
    }

    internal suspend fun onSignedOut() {
        val everAdopted = runCatching { db.legacyOwnershipDao().get()?.adoptedBy != null }
            .getOrDefault(true) // fail CLOSED: assume adopted → empty scope
        activeOwner.onSignedOut(everAdopted)
        Log.d(TAG, "Signed out → scope=${if (everAdopted) "SignedOutNoUser" else "LegacyUnclaimed"}")
    }
}
