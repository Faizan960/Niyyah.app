package com.salahlock.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collection_book_entity")
data class CollectionBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val collectionName: String, // e.g., "bukhari"
    val bookNumber: String,     // e.g., "1"
    val title: String           // e.g., "Revelation"
)
