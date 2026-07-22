package com.salahlock.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * BM-013 Checkpoint B — re-stamps the legacy (`__local__`) rows of every user-owned
 * table to an adopting Clerk account. Always invoked INSIDE the same DB transaction
 * as [LegacyOwnershipDao.claimIfUnclaimed] so adoption is all-or-nothing and can
 * never run twice (see AdoptionManager). Not triggered during the local-foundation
 * checkpoint.
 *
 * ## Conflict policy (Checkpoint C — authenticated wins)
 * The `reclaimStragglers` path can run when the owner ALREADY has rows: a `__local__`
 * straggler may share a natural key with an existing owned row. A plain
 * `UPDATE SET ownerId` would then hit the (ownerId, …) primary key and abort the whole
 * adoption transaction (SQLITE_CONSTRAINT_PRIMARYKEY). So each re-stamp is
 * `UPDATE OR IGNORE` — the owner's existing row wins, the colliding straggler is
 * skipped — followed by a delete of any leftover `__local__` row so a skipped straggler
 * can never linger invisibly. Non-colliding stragglers are still adopted. During the
 * FIRST adoption the owner has no rows, so nothing is skipped and nothing is deleted.
 */
@Dao
interface OwnershipAdoptionDao {
    @Transaction
    suspend fun adoptPrayerRecords(owner: String) {
        restampPrayerRecords(owner); dropLocalPrayerRecords()
    }

    @Transaction
    suspend fun adoptStreaks(owner: String) {
        restampStreaks(owner); dropLocalStreaks()
    }

    @Transaction
    suspend fun adoptOverrides(owner: String) {
        restampOverrides(owner); dropLocalOverrides()
    }

    @Transaction
    suspend fun adoptQuranBookmarks(owner: String) {
        restampQuranBookmarks(owner); dropLocalQuranBookmarks()
    }

    @Transaction
    suspend fun adoptQuranProgress(owner: String) {
        restampQuranProgress(owner); dropLocalQuranProgress()
    }

    @Transaction
    suspend fun adoptCollections(owner: String) {
        restampCollections(owner); dropLocalCollections()
    }

    @Transaction
    suspend fun adoptCollectionItems(owner: String) {
        restampCollectionItems(owner); dropLocalCollectionItems()
    }

    @Transaction
    suspend fun adoptHadithState(owner: String) {
        restampHadithState(owner); dropLocalHadithState()
    }

    @Transaction
    suspend fun adoptAzkarState(owner: String) {
        restampAzkarState(owner); dropLocalAzkarState()
    }

    // ---- re-stamp (owner wins on collision) + drop leftover colliding straggler ----

    @Query("UPDATE OR IGNORE prayer_records SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun restampPrayerRecords(owner: String): Int
    @Query("DELETE FROM prayer_records WHERE ownerId = '__local__'")
    suspend fun dropLocalPrayerRecords(): Int

    @Query("UPDATE OR IGNORE streaks SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun restampStreaks(owner: String): Int
    @Query("DELETE FROM streaks WHERE ownerId = '__local__'")
    suspend fun dropLocalStreaks(): Int

    @Query("UPDATE OR IGNORE emergency_overrides SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun restampOverrides(owner: String): Int
    @Query("DELETE FROM emergency_overrides WHERE ownerId = '__local__'")
    suspend fun dropLocalOverrides(): Int

    @Query("UPDATE OR IGNORE quran_bookmarks SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun restampQuranBookmarks(owner: String): Int
    @Query("DELETE FROM quran_bookmarks WHERE ownerId = '__local__'")
    suspend fun dropLocalQuranBookmarks(): Int

    @Query("UPDATE OR IGNORE quran_progress SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun restampQuranProgress(owner: String): Int
    @Query("DELETE FROM quran_progress WHERE ownerId = '__local__'")
    suspend fun dropLocalQuranProgress(): Int

    @Query("UPDATE OR IGNORE user_collections SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun restampCollections(owner: String): Int
    @Query("DELETE FROM user_collections WHERE ownerId = '__local__'")
    suspend fun dropLocalCollections(): Int

    @Query("UPDATE OR IGNORE collection_items SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun restampCollectionItems(owner: String): Int
    @Query("DELETE FROM collection_items WHERE ownerId = '__local__'")
    suspend fun dropLocalCollectionItems(): Int

    @Query("UPDATE OR IGNORE hadith_user_state SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun restampHadithState(owner: String): Int
    @Query("DELETE FROM hadith_user_state WHERE ownerId = '__local__'")
    suspend fun dropLocalHadithState(): Int

    @Query("UPDATE OR IGNORE azkar_user_state SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun restampAzkarState(owner: String): Int
    @Query("DELETE FROM azkar_user_state WHERE ownerId = '__local__'")
    suspend fun dropLocalAzkarState(): Int
}
