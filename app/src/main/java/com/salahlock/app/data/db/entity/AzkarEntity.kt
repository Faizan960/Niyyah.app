package com.salahlock.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Static Azkar corpus (shared by all users, re-seedable from `assets/azkar.json`).
 *
 * BM-013: [azkarRef] is the deterministic, cross-device stable content id
 * ([com.salahlock.app.data.sync.identity.AzkarRef]) — used as the key for
 * per-user bookmark/progress state in `azkar_user_state`.
 *
 * The [completedCount]/[isBookmarked]/[lastReadTimestamp] columns are **dormant**
 * as of v9: user state moved to `azkar_user_state`. They remain physically present
 * (to avoid a corpus table rebuild) but are no longer read or written by the app;
 * they will be dropped in a later corpus-schema bump.
 */
@Entity(tableName = "azkar_table")
data class AzkarEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // e.g. "Morning", "Evening"
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val reference: String,
    val targetCount: Int,
    val completedCount: Int = 0,
    val isBookmarked: Boolean = false,
    val lastReadTimestamp: Long = 0L,
    val language: String = "eng",
    /** BM-013 — deterministic stable content id; empty until seeded/migrated. */
    @ColumnInfo(defaultValue = "") val azkarRef: String = "",
)
