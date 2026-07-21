package com.salahlock.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-created library on the Collections screen.
 *
 * BM-013: [clientUuid] is the stable cross-device identity (device-local autoinc
 * [id] can't be — two devices both mint id=1). Local Room relationships continue
 * to use the Long [id] internally; the UUID is the sync key and is mapped to items
 * via [CollectionItemEntity.collectionUuid]. Backfilled once during the v8→v9
 * migration; minted at creation thereafter.
 */
@Entity(tableName = "user_collections")
data class UserCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val clientUuid: String = "",
    // BM-013 — ownership + sync bookkeeping (default = legacy/unclaimed).
    @ColumnInfo(defaultValue = "__local__") val ownerId: String = OwnerIds.LOCAL,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
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
    /** BM-013 — stable UUID of the owning collection (mirrors [collectionId] locally). */
    @ColumnInfo(defaultValue = "") val collectionUuid: String = "",
    // BM-013 — ownership + sync bookkeeping (default = legacy/unclaimed).
    @ColumnInfo(defaultValue = "__local__") val ownerId: String = OwnerIds.LOCAL,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)
