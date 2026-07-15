package com.salahlock.app.data.repository

import com.salahlock.app.data.db.dao.CollectionsDao
import com.salahlock.app.data.db.entity.CollectionItemEntity
import com.salahlock.app.data.db.entity.UserCollectionEntity
import com.salahlock.app.data.model.BookmarkItem
import com.salahlock.app.data.model.BookmarkType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A user collection with its live item count, for the Collections screen. */
data class CollectionSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val createdAtMs: Long,
)

/**
 * User-created libraries. Collections only hold references
 * (contentType + contentKey) to bookmarks owned by the per-module stores —
 * see [BookmarksRepository] for the aggregation side.
 */
class CollectionsRepository(private val dao: CollectionsDao) {

    fun observeCollections(): Flow<List<CollectionSummary>> = combine(
        dao.observeCollections(),
        dao.observeItemCounts(),
    ) { collections, counts ->
        val countById = counts.associate { it.collectionId to it.count }
        collections.map {
            CollectionSummary(it.id, it.name, countById[it.id] ?: 0, it.createdAtMs)
        }
    }

    fun observeCollection(id: Long): Flow<UserCollectionEntity?> = dao.observeCollection(id)

    fun observeItems(collectionId: Long): Flow<List<CollectionItemEntity>> =
        dao.observeItems(collectionId)

    fun observeCollectionCount(): Flow<Int> = dao.observeCollectionCount()

    suspend fun create(name: String): Long =
        dao.insertCollection(UserCollectionEntity(name = name.trim()))

    suspend fun rename(id: Long, name: String) = dao.renameCollection(id, name.trim())

    suspend fun delete(id: Long) = dao.deleteCollection(id)

    suspend fun addItem(collectionId: Long, item: BookmarkItem) = dao.insertItem(
        CollectionItemEntity(
            collectionId = collectionId,
            contentType = item.type.name,
            contentKey = item.key,
        ),
    )

    suspend fun removeItem(collectionId: Long, type: BookmarkType, key: String) =
        dao.deleteItem(collectionId, type.name, key)
}
