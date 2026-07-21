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

/**
 * Transactional, one-time record of whether the legacy (`__local__`) dataset has
 * been adopted by an authenticated account, and by whom. Single row (id = 1).
 *
 * This is deliberately SEPARATE from the per-user `first_sync_done_<user>` flag:
 *  - [adoptedBy] answers "has the one global legacy dataset already been claimed?"
 *    → guarantees User B can never re-claim data User A already adopted.
 *  - `first_sync_done_<user>` (DataStore, added at the sync slice) answers "has THIS
 *    account completed its first cloud pull?".
 *
 * In this checkpoint the row exists but adoption is NOT auto-triggered (infrastructure
 * only) — the claim runs later, behind approval, with the Quran-bookmark sync slice.
 */
@Entity(tableName = "legacy_ownership")
data class LegacyOwnershipEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    /** Null = legacy data still unclaimed; a Clerk user id = already adopted. */
    val adoptedBy: String? = null,
    val adoptedAtMs: Long = 0L,
)
