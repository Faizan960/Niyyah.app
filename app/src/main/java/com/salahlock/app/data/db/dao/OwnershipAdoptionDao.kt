package com.salahlock.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * BM-013 Checkpoint B — re-stamps the legacy (`__local__`) rows of every user-owned
 * table to an adopting Clerk account. Always invoked INSIDE the same DB transaction
 * as [LegacyOwnershipDao.claimIfUnclaimed] so adoption is all-or-nothing and can
 * never run twice (see AdoptionManager). Not triggered during the local-foundation
 * checkpoint.
 */
@Dao
interface OwnershipAdoptionDao {
    @Query("UPDATE prayer_records SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun adoptPrayerRecords(owner: String): Int

    @Query("UPDATE streaks SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun adoptStreaks(owner: String): Int

    @Query("UPDATE emergency_overrides SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun adoptOverrides(owner: String): Int

    @Query("UPDATE quran_bookmarks SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun adoptQuranBookmarks(owner: String): Int

    @Query("UPDATE quran_progress SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun adoptQuranProgress(owner: String): Int

    @Query("UPDATE user_collections SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun adoptCollections(owner: String): Int

    @Query("UPDATE collection_items SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun adoptCollectionItems(owner: String): Int

    @Query("UPDATE hadith_user_state SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun adoptHadithState(owner: String): Int

    @Query("UPDATE azkar_user_state SET ownerId = :owner WHERE ownerId = '__local__'")
    suspend fun adoptAzkarState(owner: String): Int
}
