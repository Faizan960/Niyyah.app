package com.salahlock.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.salahlock.app.data.db.entity.PrayerTimeCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerTimeCacheDao {
    @Query("SELECT * FROM prayer_time_cache WHERE dateString = :dateString LIMIT 1")
    fun getPrayerTimesForDate(dateString: String): Flow<PrayerTimeCacheEntity?>

    @Query("SELECT * FROM prayer_time_cache WHERE dateString = :dateString LIMIT 1")
    suspend fun getPrayerTimesForDateSync(dateString: String): PrayerTimeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prayerTimes: List<PrayerTimeCacheEntity>)

    @Query("DELETE FROM prayer_time_cache")
    suspend fun clearAll()
}
