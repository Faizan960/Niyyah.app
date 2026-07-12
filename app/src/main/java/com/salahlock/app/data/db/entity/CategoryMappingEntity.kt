package com.salahlock.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hadith_category_mapping")
data class CategoryMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topic: String,      // e.g. "Salah"
    val collection: String, // e.g. "bukhari"
    val bookNumber: String  // e.g. "8"
)
