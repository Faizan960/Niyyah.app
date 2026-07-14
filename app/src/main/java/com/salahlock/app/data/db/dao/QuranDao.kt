package com.salahlock.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.db.entity.QuranProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuranDao {

    // ------------------------------------------------------------ bookmarks

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: QuranBookmarkEntity): Long

    @Query("DELETE FROM quran_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM quran_bookmarks WHERE surahNumber = :surah AND ayahNumber = :ayah")
    suspend fun deleteAyahBookmark(surah: Int, ayah: Int)

    @Query("DELETE FROM quran_bookmarks WHERE surahNumber = :surah AND ayahNumber IS NULL")
    suspend fun deleteSurahBookmark(surah: Int)

    @Query("SELECT * FROM quran_bookmarks ORDER BY createdAtMs DESC")
    fun getAllBookmarks(): Flow<List<QuranBookmarkEntity>>

    @Query("SELECT COUNT(*) FROM quran_bookmarks")
    fun getBookmarkCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM quran_bookmarks WHERE surahNumber = :surah AND ayahNumber = :ayah)")
    fun isAyahBookmarked(surah: Int, ayah: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM quran_bookmarks WHERE surahNumber = :surah AND ayahNumber IS NULL)")
    fun isSurahBookmarked(surah: Int): Flow<Boolean>

    @Query("UPDATE quran_bookmarks SET collectionName = :collection WHERE id = :id")
    suspend fun moveBookmarkToCollection(id: Long, collection: String)

    // ------------------------------------------------------------ progress

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: QuranProgressEntity)

    @Query("SELECT * FROM quran_progress WHERE surahNumber = :surah")
    suspend fun getProgress(surah: Int): QuranProgressEntity?

    @Query("SELECT * FROM quran_progress ORDER BY timestampMs DESC LIMIT 1")
    fun getLastRead(): Flow<QuranProgressEntity?>

    @Query("SELECT * FROM quran_progress ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentlyRead(limit: Int = 10): Flow<List<QuranProgressEntity>>

    @Query("SELECT * FROM quran_progress")
    fun getAllProgress(): Flow<List<QuranProgressEntity>>
}
