package com.salahlock.app.data.db.entity

import androidx.room.Entity

/**
 * BM-013 Checkpoint B — ownership constants + user-state entities.
 *
 * ## Ownership model
 * Every genuinely user-owned row carries an `ownerId`:
 *  - [OwnerIds.LOCAL] (`"__local__"`) = **legacy / unclaimed** data created before
 *    BM-013, or created while signed out. This is the default for all migrated rows
 *    and preserves today's signed-out behavior exactly.
 *  - a Clerk user id (`user_...`) = data owned by that authenticated account.
 *
 * Static corpus (hadith/azkar text) is NEVER owner-stamped — it stays device-global.
 * The user's bookmark/progress *state* over that corpus lives here instead, keyed by
 * the corpus row's **stable** identity (hadith `id` string, azkar stable ref).
 */
object OwnerIds {
    /** Legacy / unclaimed pre-account data (created before BM-013 or while never signed in). */
    const val LOCAL: String = "__local__"

    /**
     * "No active user" sentinel. Used AFTER the legacy dataset has been adopted, when
     * the app is signed out: scoping resolves to this id, which matches NO stored rows,
     * so a previously-claimed private dataset is never re-exposed. It is NOT a
     * permanent anonymous bucket — `__local__` means unclaimed-legacy only.
     */
    const val NONE: String = "__none__"
}

/**
 * User-owned bookmark/read state for a Hadith, keyed by the corpus's stable string
 * id (e.g. `"bukhari-657-eng"`). Replaces the `isBookmarked/bookmarkSource/
 * lastReadTimestamp` columns that previously lived on the shared `hadith_table`
 * corpus rows. `bookmarkSource` distinguishes a bookmark made from the Hadith
 * reader ("hadith") vs the Knowledge topic reader ("knowledge").
 */
@Entity(tableName = "hadith_user_state", primaryKeys = ["ownerId", "hadithId"])
data class HadithUserStateEntity(
    val ownerId: String = OwnerIds.LOCAL,
    val hadithId: String,
    val isBookmarked: Boolean = false,
    val bookmarkSource: String = "hadith",
    val lastReadTimestamp: Long = 0L,
    /** Client event time of the last local mutation (LWW input; server time added at sync). */
    val updatedAt: Long = 0L,
)

/**
 * User-owned bookmark/progress state for an Azkar entry, keyed by the deterministic
 * [com.salahlock.app.data.sync.identity.AzkarRef] (never the unstable autoincrement
 * row id). Replaces the `isBookmarked/completedCount/lastReadTimestamp` columns that
 * previously lived on the shared `azkar_table` corpus rows.
 */
@Entity(tableName = "azkar_user_state", primaryKeys = ["ownerId", "azkarRef"])
data class AzkarUserStateEntity(
    val ownerId: String = OwnerIds.LOCAL,
    val azkarRef: String,
    val isBookmarked: Boolean = false,
    val completedCount: Int = 0,
    val updatedAt: Long = 0L,
)

/** Sync domains carried by [SyncOutboxEntity]. Only Quran bookmarks are live (Checkpoint C). */
object SyncDomains {
    const val QURAN_BOOKMARK: String = "QURAN_BOOKMARK"
}

/** Operations carried by [SyncOutboxEntity]. */
object SyncOps {
    const val UPSERT: String = "UPSERT"
    const val DELETE: String = "DELETE"
}

/**
 * BM-013 Checkpoint C — transactional change journal ("outbox") for cloud sync.
 *
 * A row = "this owner's entity `entityKey` in `domain` needs its latest local state
 * (or deletion) pushed to the cloud". Rows are enqueued in the SAME Room transaction
 * as the local mutation, so a committed mutation can never lose its sync event
 * (process-death-safe), and an uncommitted one never leaks a phantom event.
 *
 * The unique (ownerId, domain, entityKey) index + REPLACE insert coalesce repeated
 * mutations of the same entity to the NEWEST operation (e.g. UPSERT then DELETE
 * leaves one DELETE row) — idempotent and bounded. The sync worker deletes a row
 * only by its exact `id` after a successful push, so an entity mutated *during* a
 * push keeps its fresh (REPLACEd, new-id) event.
 *
 * Never stores auth tokens; the worker fetches a fresh Clerk session token per run.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [androidx.room.Index(value = ["ownerId", "domain", "entityKey"], unique = true)],
)
data class SyncOutboxEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val domain: String,
    /** Natural key inside the domain; Quran bookmarks use `"<surah>:<ayah|0>"` (0 = whole surah). */
    val entityKey: String,
    /** [SyncOps.UPSERT] or [SyncOps.DELETE]. */
    val operation: String,
    /** JSON metadata for UPSERT (collectionName, createdAtMs); empty for DELETE. */
    val payload: String,
    val createdAtMs: Long,
    val attemptCount: Int = 0,
    val lastAttemptMs: Long = 0L,
    val lastError: String? = null,
)

/**
 * BM-013 Checkpoint C — per-user cloud-sync progress. `firstSyncDone` flips true
 * ONLY inside the same transaction that persists a successful first reconciliation
 * (never before), and `pullWatermark` is the server-authoritative `updated_at` of
 * the newest applied cloud row (ISO string; empty = never pulled). Keyed by owner,
 * so each account tracks its own first sync on this device — deliberately distinct
 * from the one-time global [LegacyOwnershipEntity] adoption ledger.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @androidx.room.PrimaryKey val ownerId: String,
    val firstSyncDone: Boolean = false,
    val pullWatermark: String = "",
)

/**
 * Transactional, one-time record of whether the legacy (`__local__`) dataset has
 * been adopted by an authenticated account, and by whom. Single row (id = 1).
 *
 * This is deliberately SEPARATE from the per-user `first_sync_done_<user>` flag:
 *  - [adoptedBy] answers "has the one global legacy dataset already been claimed?"
 *    → guarantees User B can never re-claim data User A already adopted.
 *  - [SyncStateEntity.firstSyncDone] answers "has THIS account completed its first
 *    cloud reconciliation on this device?".
 *
 * Checkpoint C: adoption is now live — triggered on sign-in by AccountSyncCoordinator.
 */
@Entity(tableName = "legacy_ownership")
data class LegacyOwnershipEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    /** Null = legacy data still unclaimed; a Clerk user id = already adopted. */
    val adoptedBy: String? = null,
    val adoptedAtMs: Long = 0L,
)
