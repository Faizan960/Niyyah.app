package com.salahlock.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved Quran bookmark. `ayahNumber == null` marks a whole-surah bookmark;
 * otherwise it points at a single ayah within the surah.
 */
@Entity(
    tableName = "quran_bookmarks",
    indices = [Index(value = ["surahNumber", "ayahNumber"], unique = true)],
)
data class QuranBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surahNumber: Int,
    val ayahNumber: Int? = null,
    /** Optional collection this bookmark belongs to; empty = uncollected. */
    val collectionName: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
)

/**
 * Per-surah reading position. One row per surah the user has opened;
 * `lastAyah` is the furthest ayah reached. Ordered by [timestampMs] this
 * table also serves as the "Recently read" list, and the newest row is
 * the "Continue reading" target.
 */
@Entity(tableName = "quran_progress")
data class QuranProgressEntity(
    @PrimaryKey val surahNumber: Int,
    val lastAyah: Int = 1,
    /** Furthest ayah ever reached in this surah — drives overall progress %. */
    val maxAyah: Int = 1,
    val timestampMs: Long = System.currentTimeMillis(),
)
