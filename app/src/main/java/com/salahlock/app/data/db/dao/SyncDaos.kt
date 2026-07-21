package com.salahlock.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.salahlock.app.data.db.entity.AzkarUserStateEntity
import com.salahlock.app.data.db.entity.HadithUserStateEntity
import com.salahlock.app.data.db.entity.LegacyOwnershipEntity
import kotlinx.coroutines.flow.Flow

/**
 * BM-013 Checkpoint B — user-owned Hadith bookmark/read state, keyed by the corpus's
 * stable string id. Every read is explicitly scoped to the active `ownerId`, so
 * User A's rows can never surface while User B is active (query scoping is the
 * PRIMARY local isolation mechanism; logout purge is secondary defense).
 */
@Dao
interface HadithUserStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: HadithUserStateEntity)

    @Query("SELECT * FROM hadith_user_state WHERE ownerId = :owner AND hadithId = :id LIMIT 1")
    suspend fun getState(owner: String, id: String): HadithUserStateEntity?

    @Query("SELECT * FROM hadith_user_state WHERE ownerId = :owner AND isBookmarked = 1 ORDER BY lastReadTimestamp DESC")
    fun observeBookmarked(owner: String): Flow<List<HadithUserStateEntity>>

    @Query("SELECT * FROM hadith_user_state WHERE ownerId = :owner AND lastReadTimestamp > 0 ORDER BY lastReadTimestamp DESC LIMIT 20")
    fun observeRecent(owner: String): Flow<List<HadithUserStateEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM hadith_user_state WHERE ownerId = :owner AND hadithId = :id AND isBookmarked = 1)")
    fun isBookmarked(owner: String, id: String): Flow<Boolean>

    @Query("DELETE FROM hadith_user_state WHERE ownerId = :owner AND hadithId = :id")
    suspend fun delete(owner: String, id: String)

    /** Owner-scoped export for backup/migration. */
    @Query("SELECT * FROM hadith_user_state WHERE ownerId = :owner")
    suspend fun getAllForOwner(owner: String): List<HadithUserStateEntity>
}

/**
 * BM-013 Checkpoint B — user-owned Azkar bookmark/progress state, keyed by the
 * deterministic azkar ref (never the unstable autoincrement id). Owner-scoped.
 */
@Dao
interface AzkarUserStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AzkarUserStateEntity)

    @Query("SELECT * FROM azkar_user_state WHERE ownerId = :owner AND azkarRef = :ref LIMIT 1")
    suspend fun getState(owner: String, ref: String): AzkarUserStateEntity?

    @Query("SELECT * FROM azkar_user_state WHERE ownerId = :owner AND isBookmarked = 1")
    fun observeBookmarked(owner: String): Flow<List<AzkarUserStateEntity>>

    @Query("DELETE FROM azkar_user_state WHERE ownerId = :owner AND azkarRef = :ref")
    suspend fun delete(owner: String, ref: String)

    @Query("SELECT * FROM azkar_user_state WHERE ownerId = :owner")
    suspend fun getAllForOwner(owner: String): List<AzkarUserStateEntity>
}

/**
 * BM-013 Checkpoint B — one-time legacy-adoption ledger (single row id=1). Distinct
 * from the per-user first-cloud-sync flag. Infrastructure only in this checkpoint:
 * the row is created but adoption is not auto-triggered.
 */
@Dao
interface LegacyOwnershipDao {
    @Query("SELECT * FROM legacy_ownership WHERE id = 1 LIMIT 1")
    suspend fun get(): LegacyOwnershipEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: LegacyOwnershipEntity)

    /**
     * Atomically claims the still-unclaimed legacy dataset for [userId]. Only affects
     * the row while `adoptedBy IS NULL`, so a second account can never re-claim it.
     * Returns the number of rows changed (1 = this call performed the claim, 0 = it
     * was already adopted). The caller re-stamps `__local__` rows in the same DB
     * transaction.
     */
    @Query("UPDATE legacy_ownership SET adoptedBy = :userId, adoptedAtMs = :ts WHERE id = 1 AND adoptedBy IS NULL")
    suspend fun claimIfUnclaimed(userId: String, ts: Long): Int
}
