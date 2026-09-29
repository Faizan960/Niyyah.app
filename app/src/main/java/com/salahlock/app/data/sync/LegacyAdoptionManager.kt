package com.salahlock.app.data.sync

import androidx.room.withTransaction
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.LegacyOwnershipEntity

/**
 * BM-013 Checkpoint B — one-time, transactional adoption of the legacy (`__local__`)
 * dataset by an authenticated Clerk account.
 *
 * ## Scope of adoption (device-local prayer data is EXCLUDED)
 * Adoption only re-stamps the **cloud-synced, per-account** datasets: Quran bookmarks
 * & progress, collections, and Hadith/Azkar user state. It deliberately does NOT touch
 * prayer_records, streaks, emergency_overrides, or reflection files: prayer tracking is
 * device-local and auth-independent by product requirement, so those rows must remain
 * under [OwnerIds.LOCAL][com.salahlock.app.data.db.entity.OwnerIds.LOCAL] and stay visible
 * across sign-in, sign-out, and every auth-state change.
 *
 * ## Guarantees
 *  - **Exactly once, globally.** [adoptLegacyDataOnce] claims via `claimIfUnclaimed`
 *    (a conditional UPDATE on the single `legacy_ownership` row). If it changed 0 rows,
 *    the legacy data was already adopted — so a *second* account (User B) can never
 *    re-claim data User A already owns.
 *  - **Atomic.** The claim and the re-stamping of every adopted table run in one Room
 *    transaction; a crash mid-way rolls back to "still unclaimed".
 */
class LegacyAdoptionManager(
    private val db: AppDatabase,
    private val activeOwner: ActiveOwnerProvider,
) {
    /**
     * Adopts the legacy cloud-synced dataset for [clerkUserId] if it is still unclaimed,
     * then activates owner-scoping onto that account. Returns true iff this call performed
     * the adoption (false = already adopted by someone, nothing re-stamped).
     */
    suspend fun adoptLegacyDataOnce(clerkUserId: String, nowMs: Long): Boolean {
        require(clerkUserId.isNotBlank()) { "clerkUserId required" }
        val claimed = db.withTransaction {
            // A fresh install builds the schema via Room's onCreate path, which never
            // runs MIGRATION_8_9's seed — so the singleton ledger row (id=1) can be
            // absent here. Seed it idempotently before the conditional claim, else
            // `claimIfUnclaimed` updates 0 rows and adoption silently never happens
            // (leaving logout stuck in LegacyUnclaimed instead of SignedOutNoUser).
            db.legacyOwnershipDao().insertIfAbsent(LegacyOwnershipEntity())
            val didClaim = db.legacyOwnershipDao().claimIfUnclaimed(clerkUserId, nowMs) == 1
            if (didClaim) {
                // Cloud-synced, per-account datasets only — prayer/streak/override and
                // reflections stay device-local (__local__) and are never adopted.
                val a = db.ownershipAdoptionDao()
                a.adoptQuranBookmarks(clerkUserId)
                a.adoptQuranProgress(clerkUserId)
                a.adoptCollections(clerkUserId)
                a.adoptCollectionItems(clerkUserId)
                a.adoptHadithState(clerkUserId)
                a.adoptAzkarState(clerkUserId)
            }
            didClaim
        }
        if (claimed) activeOwner.onAuthenticated(clerkUserId)
        return claimed
    }

    /**
     * Checkpoint C — idempotent straggler sweep for the user who ALREADY owns the
     * legacy dataset. Closes the tiny startup window where a cloud-synced mutation lands
     * while Clerk is still restoring the session (scope transiently LegacyUnclaimed → row
     * stamped `__local__` even though adoption happened earlier): re-running the re-stamp
     * folds such rows into their rightful owner. Strictly guarded — a DIFFERENT user can
     * never sweep data adopted by someone else. Prayer/streak/override are excluded (they
     * are intentionally device-local).
     */
    suspend fun reclaimStragglers(clerkUserId: String) {
        db.withTransaction {
            if (db.legacyOwnershipDao().get()?.adoptedBy != clerkUserId) return@withTransaction
            val a = db.ownershipAdoptionDao()
            a.adoptQuranBookmarks(clerkUserId)
            a.adoptQuranProgress(clerkUserId)
            a.adoptCollections(clerkUserId)
            a.adoptCollectionItems(clerkUserId)
            a.adoptHadithState(clerkUserId)
            a.adoptAzkarState(clerkUserId)
        }
    }
}
