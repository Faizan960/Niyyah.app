package com.salahlock.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.salahlock.app.data.db.entity.CollectionItemEntity
import com.salahlock.app.data.db.entity.UserCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionsDao {

    // ---------------------------------------------------------- collections

    // BM-013 — all collection access is owner-scoped (WHERE ownerId = :owner).
    @Insert
    suspend fun insertCollection(collection: UserCollectionEntity): Long

    @Query("UPDATE user_collections SET name = :name WHERE ownerId = :owner AND id = :id")
    suspend fun renameCollection(owner: String, id: Long, name: String)

    @Query("DELETE FROM user_collections WHERE ownerId = :owner AND id = :id")
    suspend fun deleteCollectionRow(owner: String, id: Long)

    @Query("DELETE FROM collection_items WHERE ownerId = :owner AND collectionId = :collectionId")
    suspend fun deleteItemsForCollection(owner: String, collectionId: Long)

    /** Deleting a collection removes its membership rows too. */
    @Transaction
    suspend fun deleteCollection(owner: String, id: Long) {
        deleteItemsForCollection(owner, id)
        deleteCollectionRow(owner, id)
    }

    @Query("SELECT * FROM user_collections WHERE ownerId = :owner ORDER BY createdAtMs DESC")
    fun observeCollections(owner: String): Flow<List<UserCollectionEntity>>

    @Query("SELECT * FROM user_collections WHERE ownerId = :owner AND id = :id")
    fun observeCollection(owner: String, id: Long): Flow<UserCollectionEntity?>

    @Query("SELECT COUNT(*) FROM user_collections WHERE ownerId = :owner")
    fun observeCollectionCount(owner: String): Flow<Int>

    /** Stable UUID of a collection — used to stamp membership rows. */
    @Query("SELECT clientUuid FROM user_collections WHERE ownerId = :owner AND id = :id")
    suspend fun getCollectionUuid(owner: String, id: Long): String?

    // ---------------------------------------------------------------- items

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: CollectionItemEntity)

    @Query("DELETE FROM collection_items WHERE ownerId = :owner AND collectionId = :collectionId AND contentType = :type AND contentKey = :key")
    suspend fun deleteItem(owner: String, collectionId: Long, type: String, key: String)

    /** Removes the item from every collection (used when its bookmark is removed). */
    @Query("DELETE FROM collection_items WHERE ownerId = :owner AND contentType = :type AND contentKey = :key")
    suspend fun deleteItemEverywhere(owner: String, type: String, key: String)

    @Query("SELECT * FROM collection_items WHERE ownerId = :owner AND collectionId = :collectionId ORDER BY addedAtMs DESC")
    fun observeItems(owner: String, collectionId: Long): Flow<List<CollectionItemEntity>>

    @Query("SELECT collectionId, COUNT(*) AS count FROM collection_items WHERE ownerId = :owner GROUP BY collectionId")
    fun observeItemCounts(owner: String): Flow<List<CollectionItemCount>>
}

data class CollectionItemCount(val collectionId: Long, val count: Int)
