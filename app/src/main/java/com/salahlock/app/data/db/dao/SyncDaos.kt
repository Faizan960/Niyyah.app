package com.salahlock.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.salahlock.app.data.db.entity.AzkarUserStateEntity
import com.salahlock.app.data.db.entity.HadithUserStateEntity
import com.salahlock.app.data.db.entity.LegacyOwnershipEntity
import com.salahlock.app.data.db.entity.SyncOutboxEntity
import com.salahlock.app.data.db.entity.SyncStateEntity
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

/**
 * BM-013 Checkpoint C — transactional outbox access. Enqueue happens INSIDE the
 * same Room transaction as the local mutation (see QuranRepository); the REPLACE
 * strategy + unique (ownerId, domain, entityKey) index coalesce to the newest
 * operation per entity. The worker clears rows strictly by id, so an entity
 * re-mutated mid-push keeps its fresh event.
 */
@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(event: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE ownerId = :owner AND domain = :domain ORDER BY id ASC LIMIT :limit")
    suspend fun pending(owner: String, domain: String, limit: Int = 200): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE ownerId = :owner")
    suspend fun countForOwner(owner: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE ownerId = :owner AND domain = :domain AND entityKey = :key)")
    suspend fun hasPending(owner: String, domain: String, key: String): Boolean

    @Query("DELETE FROM sync_outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1, lastAttemptMs = :nowMs, lastError = :error WHERE id IN (:ids)")
    suspend fun recordAttempt(ids: List<Long>, nowMs: Long, error: String?)
}

/** BM-013 Checkpoint C — per-user sync progress (first-sync flag + pull watermark). */
@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE ownerId = :owner LIMIT 1")
    suspend fun get(owner: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)
}
