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

    // BM-013 — all bookmark/progress access is owner-scoped (WHERE ownerId = :owner).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: QuranBookmarkEntity): Long

    @Query("DELETE FROM quran_bookmarks WHERE ownerId = :owner AND id = :id")
    suspend fun deleteBookmark(owner: String, id: Long)

    @Query("DELETE FROM quran_bookmarks WHERE ownerId = :owner AND surahNumber = :surah AND ayahNumber = :ayah")
    suspend fun deleteAyahBookmark(owner: String, surah: Int, ayah: Int)

    @Query("DELETE FROM quran_bookmarks WHERE ownerId = :owner AND surahNumber = :surah AND ayahNumber IS NULL")
    suspend fun deleteSurahBookmark(owner: String, surah: Int)

    @Query("SELECT * FROM quran_bookmarks WHERE ownerId = :owner ORDER BY createdAtMs DESC")
    fun getAllBookmarks(owner: String): Flow<List<QuranBookmarkEntity>>

    // BM-013 Checkpoint C — one-shot reads for the sync engine (snapshot / outbox keying).
    @Query("SELECT * FROM quran_bookmarks WHERE ownerId = :owner")
    suspend fun getAllBookmarksOnce(owner: String): List<QuranBookmarkEntity>

    @Query("SELECT * FROM quran_bookmarks WHERE ownerId = :owner AND id = :id LIMIT 1")
    suspend fun getBookmarkById(owner: String, id: Long): QuranBookmarkEntity?

    @Query("SELECT COUNT(*) FROM quran_bookmarks WHERE ownerId = :owner")
    fun getBookmarkCount(owner: String): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM quran_bookmarks WHERE ownerId = :owner AND surahNumber = :surah AND ayahNumber = :ayah)")
    fun isAyahBookmarked(owner: String, surah: Int, ayah: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM quran_bookmarks WHERE ownerId = :owner AND surahNumber = :surah AND ayahNumber IS NULL)")
    fun isSurahBookmarked(owner: String, surah: Int): Flow<Boolean>

    @Query("UPDATE quran_bookmarks SET collectionName = :collection WHERE ownerId = :owner AND id = :id")
    suspend fun moveBookmarkToCollection(owner: String, id: Long, collection: String)

    // ------------------------------------------------------------ progress

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: QuranProgressEntity)

    @Query("SELECT * FROM quran_progress WHERE ownerId = :owner AND surahNumber = :surah")
    suspend fun getProgress(owner: String, surah: Int): QuranProgressEntity?

    @Query("SELECT * FROM quran_progress WHERE ownerId = :owner ORDER BY timestampMs DESC LIMIT 1")
    fun getLastRead(owner: String): Flow<QuranProgressEntity?>

    @Query("SELECT * FROM quran_progress WHERE ownerId = :owner ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentlyRead(owner: String, limit: Int = 10): Flow<List<QuranProgressEntity>>

    @Query("SELECT * FROM quran_progress WHERE ownerId = :owner")
    fun getAllProgress(owner: String): Flow<List<QuranProgressEntity>>
}
