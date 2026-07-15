package com.salahlock.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hadith_table")
data class HadithEntity(
    @PrimaryKey val id: String, // e.g., "bukhari-657"
    val collection: String,     // e.g., "bukhari"
    val bookNumber: String,
    val hadithNumber: String,
    val arabicText: String,
    val translationText: String,
    val language: String,       // e.g., "eng" or "ara"
    val isBookmarked: Boolean = false,
    val lastReadTimestamp: Long = 0L,
    /**
     * Which module the bookmark was made from: "hadith" (collection/book reader)
     * or "knowledge" (topic reader). Drives the Bookmarks screen's type tabs.
     */
    @ColumnInfo(defaultValue = "hadith")
    val bookmarkSource: String = "hadith",
)
