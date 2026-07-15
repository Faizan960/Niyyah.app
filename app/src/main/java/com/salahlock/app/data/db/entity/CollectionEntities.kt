package com.salahlock.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-created library on the Collections screen. */
@Entity(tableName = "user_collections")
data class UserCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis(),
)

/**
 * Membership of one bookmarked item in one collection. The item itself lives
 * in its module's own bookmark storage (quran_bookmarks / hadith_table /
 * azkar_table) — this table only references it by [contentType] + [contentKey],
 * so bookmark storage is never duplicated.
 *
 * Content keys:
 *  - QURAN:     "surah:ayah" ("2:286") or "surah:" for a whole-surah bookmark
 *  - HADITH:    hadith entity id ("bukhari-657-eng")
 *  - KNOWLEDGE: hadith entity id (bookmarked from a Knowledge topic)
 *  - AZKAR:     azkar row id ("42")
 */
@Entity(
    tableName = "collection_items",
    indices = [Index(value = ["collectionId", "contentType", "contentKey"], unique = true)],
)
data class CollectionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val contentType: String,
    val contentKey: String,
    val addedAtMs: Long = System.currentTimeMillis(),
)
