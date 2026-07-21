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
class CollectionsRepository(
    private val dao: CollectionsDao,
    // BM-013 — active owner seam; resolves to __local__ during the local foundation.
    private val activeOwner: com.salahlock.app.data.sync.ActiveOwnerProvider =
        com.salahlock.app.data.sync.ActiveOwnerProvider.shared,
) {
    private val owner get() = activeOwner.ownerId()

    fun observeCollections(): Flow<List<CollectionSummary>> = combine(
        dao.observeCollections(owner),
        dao.observeItemCounts(owner),
    ) { collections, counts ->
        val countById = counts.associate { it.collectionId to it.count }
        collections.map {
            CollectionSummary(it.id, it.name, countById[it.id] ?: 0, it.createdAtMs)
        }
    }

    fun observeCollection(id: Long): Flow<UserCollectionEntity?> = dao.observeCollection(owner, id)

    fun observeItems(collectionId: Long): Flow<List<CollectionItemEntity>> =
        dao.observeItems(owner, collectionId)

    fun observeCollectionCount(): Flow<Int> = dao.observeCollectionCount(owner)

    /**
     * Creates a collection with a freshly-minted stable UUID — it is NEVER persisted
     * with an empty [UserCollectionEntity.clientUuid]. The Long row id is returned for
     * local navigation; the UUID is the cross-device identity.
     */
    suspend fun create(name: String): Long {
        val uuid = java.util.UUID.randomUUID().toString()
        return dao.insertCollection(
            UserCollectionEntity(name = name.trim(), clientUuid = uuid, ownerId = owner),
        )
    }

    suspend fun rename(id: Long, name: String) = dao.renameCollection(owner, id, name.trim())

    suspend fun delete(id: Long) = dao.deleteCollection(owner, id)

    suspend fun addItem(collectionId: Long, item: BookmarkItem) = dao.insertItem(
        CollectionItemEntity(
            collectionId = collectionId,
            contentType = item.type.name,
            contentKey = item.key,
            collectionUuid = dao.getCollectionUuid(owner, collectionId).orEmpty(),
            ownerId = owner,
        ),
    )

    suspend fun removeItem(collectionId: Long, type: BookmarkType, key: String) =
        dao.deleteItem(owner, collectionId, type.name, key)
}
