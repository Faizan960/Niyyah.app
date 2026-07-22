package com.salahlock.app.data.sync

import androidx.room.withTransaction
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.LegacyOwnershipEntity
import java.io.File

/**
 * BM-013 Checkpoint B — one-time, transactional adoption of the legacy (`__local__`)
 * dataset by an authenticated Clerk account.
 *
 * ## Guarantees
 *  - **Exactly once, globally.** [LegacyAdoptionManager.adoptLegacyDataOnce] claims via
 *    `claimIfUnclaimed` (a conditional UPDATE on the single `legacy_ownership` row).
 *    If it changed 0 rows, the legacy data was already adopted — so a *second* account
 *    (User B) can never re-claim data User A already owns.
 *  - **Atomic.** The claim and the re-stamping of every user-owned table run in one
 *    Room transaction; a crash mid-way rolls back to "still unclaimed".
 *
 * ## Two distinct concepts (per BM-013 review item 3)
 *  - This manager + `legacy_ownership.adoptedBy` = *ownership/adoption state of the one
 *    legacy local dataset* (global, one-time).
 *  - `first_sync_done_<user>` (DataStore, added at the sync slice) = *per-user first
 *    cloud-sync completion*. They are not interchangeable.
 *
 * NOT invoked during the local-foundation checkpoint — this is the mechanism the
 * Quran-bookmark sync slice will call after sign-in. Reflection files are namespaced
 * separately (see SpiritualReportRepository.adoptLegacyReflections).
 */
class LegacyAdoptionManager(
    private val db: AppDatabase,
    private val activeOwner: ActiveOwnerProvider,
) {
    /**
     * Adopts the legacy dataset for [clerkUserId] if it is still unclaimed, then
     * activates owner-scoping onto that account. Returns true iff this call performed
     * the adoption (false = already adopted by someone, nothing re-stamped).
     */
    suspend fun adoptLegacyDataOnce(clerkUserId: String, nowMs: Long, reflectionRoot: File? = null): Boolean {
        require(clerkUserId.isNotBlank()) { "clerkUserId required" }
        // Step 1 — Room claim + re-stamp ALL owner-scoped datasets, atomically. Commits
        // first so User B can never re-claim, even if step 2 is interrupted.
        val claimed = db.withTransaction {
            // A fresh install builds the schema via Room's onCreate path, which never
            // runs MIGRATION_8_9's seed — so the singleton ledger row (id=1) can be
            // absent here. Seed it idempotently before the conditional claim, else
            // `claimIfUnclaimed` updates 0 rows and adoption silently never happens
            // (leaving logout stuck in LegacyUnclaimed instead of SignedOutNoUser).
            db.legacyOwnershipDao().insertIfAbsent(LegacyOwnershipEntity())
            val didClaim = db.legacyOwnershipDao().claimIfUnclaimed(clerkUserId, nowMs) == 1
            if (didClaim) {
                val a = db.ownershipAdoptionDao()
                a.adoptPrayerRecords(clerkUserId)
                a.adoptStreaks(clerkUserId)
                a.adoptOverrides(clerkUserId)
                a.adoptQuranBookmarks(clerkUserId)
                a.adoptQuranProgress(clerkUserId)
                a.adoptCollections(clerkUserId)
                a.adoptCollectionItems(clerkUserId)
                a.adoptHadithState(clerkUserId)
                a.adoptAzkarState(clerkUserId)
            }
            didClaim
        }
        if (claimed) {
            // Step 2 — move legacy reflection files (outside the DB txn; recoverable).
            reflectionRoot?.let { runCatching { ReflectionFiles.adoptLocalTo(it, clerkUserId) } }
            activeOwner.onAuthenticated(clerkUserId)
        }
        return claimed
    }

    /**
     * Checkpoint C — idempotent straggler sweep for the user who ALREADY owns the
     * legacy dataset. Closes the tiny startup window where a mutation lands while
     * Clerk is still restoring the session (scope transiently LegacyUnclaimed →
     * row stamped `__local__` even though adoption happened earlier): re-running
     * the re-stamp folds such rows into their rightful owner. Strictly guarded —
     * a DIFFERENT user can never sweep data adopted by someone else.
     */
    suspend fun reclaimStragglers(clerkUserId: String) {
        db.withTransaction {
            if (db.legacyOwnershipDao().get()?.adoptedBy != clerkUserId) return@withTransaction
            val a = db.ownershipAdoptionDao()
            a.adoptPrayerRecords(clerkUserId)
            a.adoptStreaks(clerkUserId)
            a.adoptOverrides(clerkUserId)
            a.adoptQuranBookmarks(clerkUserId)
            a.adoptQuranProgress(clerkUserId)
            a.adoptCollections(clerkUserId)
            a.adoptCollectionItems(clerkUserId)
            a.adoptHadithState(clerkUserId)
            a.adoptAzkarState(clerkUserId)
        }
    }

    /**
     * Startup recovery for a crash between the Room claim (step 1) and the reflection
     * move (step 2). If the ledger says the legacy data was adopted, finish moving any
     * leftover `__local__` reflections to that owner. Idempotent; no-op when unclaimed.
     */
    suspend fun recoverPendingReflectionAdoption(reflectionRoot: File) {
        val adoptedBy = db.legacyOwnershipDao().get()?.adoptedBy
        runCatching { ReflectionFiles.recoverPendingAdoption(reflectionRoot, adoptedBy) }
    }
}
