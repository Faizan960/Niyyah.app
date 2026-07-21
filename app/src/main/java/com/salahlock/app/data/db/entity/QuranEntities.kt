package com.salahlock.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved Quran bookmark. `ayahNumber == null` marks a whole-surah bookmark;
 * otherwise it points at a single ayah within the surah.
 */
@Entity(
    tableName = "quran_bookmarks",
    // BM-013 — owner-aware unique key so two accounts can bookmark the same ayah.
    indices = [Index(value = ["ownerId", "surahNumber", "ayahNumber"], unique = true)],
)
data class QuranBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surahNumber: Int,
    val ayahNumber: Int? = null,
    /** Optional collection this bookmark belongs to; empty = uncollected. */
    val collectionName: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    // BM-013 — ownership + sync bookkeeping (default = legacy/unclaimed).
    @ColumnInfo(defaultValue = "__local__") val ownerId: String = OwnerIds.LOCAL,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)

/**
 * Per-surah reading position. One row per surah the user has opened;
 * `lastAyah` is the furthest ayah reached. Ordered by [timestampMs] this
 * table also serves as the "Recently read" list, and the newest row is
 * the "Continue reading" target.
 */
// BM-013 — reading position is per (owner, surah); composite PK replaces the
// single-owner surahNumber PK so accounts keep independent progress.
@Entity(tableName = "quran_progress", primaryKeys = ["ownerId", "surahNumber"])
data class QuranProgressEntity(
    val surahNumber: Int,
    val lastAyah: Int = 1,
    /** Furthest ayah ever reached in this surah — drives overall progress %. */
    val maxAyah: Int = 1,
    val timestampMs: Long = System.currentTimeMillis(),
    // BM-013 — ownership + sync bookkeeping (default = legacy/unclaimed).
    @ColumnInfo(defaultValue = "__local__") val ownerId: String = OwnerIds.LOCAL,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)
