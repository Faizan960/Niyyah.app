package com.salahlock.app.data.db.dao

import androidx.room.*
import com.salahlock.app.data.db.entity.AppBlacklistItem
import com.salahlock.app.data.db.entity.EmergencyOverride
import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.StreakEntity
import kotlinx.coroutines.flow.Flow

// ── Prayer Records ──────────────────────────────────────────────────────────
@Dao
interface PrayerRecordDao {
    @Query("SELECT * FROM prayer_records WHERE date = :date ORDER BY prayerName ASC")
    fun observeRecordsForDate(date: String): Flow<List<PrayerRecord>>

    @Query("SELECT * FROM prayer_records WHERE date = :date ORDER BY prayerName ASC")
    suspend fun getRecordsForDate(date: String): List<PrayerRecord>

    @Query("SELECT * FROM prayer_records WHERE date = :date AND prayerName = :prayerName LIMIT 1")
    suspend fun getRecord(date: String, prayerName: String): PrayerRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PrayerRecord)

    @Query("SELECT * FROM prayer_records WHERE date >= :fromDate ORDER BY date DESC LIMIT 150")
    suspend fun getRecentRecords(fromDate: String): List<PrayerRecord>

    @Query("DELETE FROM prayer_records WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)

    /** Monthly Reflection: all records inside one calendar month (ISO dates sort lexically). */
    @Query("SELECT * FROM prayer_records WHERE date BETWEEN :fromDate AND :toDate ORDER BY date ASC")
    suspend fun getRecordsBetween(fromDate: String, toDate: String): List<PrayerRecord>

    /** Monthly Reflection: first day of recorded history, to know which months to generate. */
    @Query("SELECT MIN(date) FROM prayer_records")
    suspend fun getEarliestDate(): String?

    /** Sprint E.2: full export for backup */
    @Query("SELECT * FROM prayer_records ORDER BY date ASC, timestampMs ASC")
    suspend fun getAll(): List<PrayerRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PrayerRecord>)

    @Query("DELETE FROM prayer_records")
    suspend fun deleteAll()
}

// ── Streak ──────────────────────────────────────────────────────────────────
@Dao
interface StreakDao {
    @Query("SELECT * FROM streaks WHERE id = 1 LIMIT 1")
    fun observeStreak(): Flow<StreakEntity?>

    @Query("SELECT * FROM streaks WHERE id = 1 LIMIT 1")
    suspend fun getStreak(): StreakEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(streak: StreakEntity)
}

// ── Emergency Override ──────────────────────────────────────────────────────
@Dao
interface EmergencyOverrideDao {
    @Query("SELECT * FROM emergency_overrides WHERE monthYear = :monthYear LIMIT 1")
    suspend fun getForMonth(monthYear: String): EmergencyOverride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: EmergencyOverride)

    /** Sprint E.2: full export for backup */
    @Query("SELECT * FROM emergency_overrides")
    suspend fun getAll(): List<EmergencyOverride>
}

// ── App Blacklist ───────────────────────────────────────────────────────────
@Dao
interface AppBlacklistDao {
    @Query("SELECT * FROM app_blacklist ORDER BY isBlocked DESC, appLabel ASC")
    fun observeAll(): Flow<List<AppBlacklistItem>>

    @Query("SELECT * FROM app_blacklist WHERE appLabel LIKE '%' || :query || '%' ORDER BY isBlocked DESC, appLabel ASC")
    fun observeFiltered(query: String): Flow<List<AppBlacklistItem>>

    @Query("SELECT * FROM app_blacklist ORDER BY appLabel ASC")
    suspend fun getAllSync(): List<AppBlacklistItem>

    @Query("SELECT * FROM app_blacklist WHERE isBlocked = 1 ORDER BY appLabel ASC")
    suspend fun getBlockedPackages(): List<AppBlacklistItem>

    @Query("SELECT packageName FROM app_blacklist WHERE isBlocked = 1")
    fun observeBlockedPackageNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<AppBlacklistItem>)

    /** Replace-insert — updates existing rows (by unique packageName index) or inserts new. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AppBlacklistItem>)

    @Update
    suspend fun update(item: AppBlacklistItem)

    /** Batch-set isBlocked for specific packages. */
    @Query("UPDATE app_blacklist SET isBlocked = :blocked WHERE packageName IN (:packages)")
    suspend fun setBlockedForPackages(packages: List<String>, blocked: Boolean)

    /** Unblock every non-whitelisted app (used when switching profiles). */
    @Query("UPDATE app_blacklist SET isBlocked = 0")
    suspend fun unblockAll()

    @Query("DELETE FROM app_blacklist")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM app_blacklist WHERE isBlocked = 1")
    fun observeBlockedCount(): Flow<Int>
}
