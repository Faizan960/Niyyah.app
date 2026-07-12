package com.salahlock.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.salahlock.app.data.db.entity.AzkarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AzkarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(azkar: List<AzkarEntity>)

    @Query("SELECT * FROM azkar_table WHERE category = :category ORDER BY id ASC")
    fun getAzkarByCategory(category: String): Flow<List<AzkarEntity>>

    @Query("SELECT * FROM azkar_table WHERE isBookmarked = 1")
    fun getBookmarkedAzkar(): Flow<List<AzkarEntity>>

    @Query("UPDATE azkar_table SET completedCount = :count WHERE id = :id")
    suspend fun updateProgress(id: Int, count: Int)

    @Query("UPDATE azkar_table SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: Int, isBookmarked: Boolean)
    
    @Query("UPDATE azkar_table SET completedCount = 0")
    suspend fun resetAllProgress()
    
    @Query("SELECT COUNT(*) FROM azkar_table")
    suspend fun getAzkarCount(): Int
}
