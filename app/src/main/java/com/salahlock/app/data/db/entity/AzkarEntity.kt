package com.salahlock.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val language: String = "eng"
)
