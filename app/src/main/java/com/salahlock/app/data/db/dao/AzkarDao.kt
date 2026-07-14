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

    @Query("SELECT category, COUNT(*) AS count FROM azkar_table GROUP BY category")
    fun getCategoryCounts(): Flow<List<AzkarCategoryCount>>

    /** Marks a category as recently read (drives recents/reflection stats). */
    @Query("UPDATE azkar_table SET lastReadTimestamp = :timestamp WHERE category = :category")
    suspend fun touchCategory(category: String, timestamp: Long)

    /** Case-insensitive search across translation, transliteration and Arabic. */
    @Query("""
        SELECT * FROM azkar_table
        WHERE translation LIKE '%' || :query || '%'
           OR transliteration LIKE '%' || :query || '%'
           OR arabic LIKE '%' || :query || '%'
        ORDER BY category, id
    """)
    suspend fun searchAzkar(query: String): List<AzkarEntity>
}

data class AzkarCategoryCount(val category: String, val count: Int)
