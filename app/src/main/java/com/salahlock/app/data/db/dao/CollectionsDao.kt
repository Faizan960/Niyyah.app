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

    @Insert
    suspend fun insertCollection(collection: UserCollectionEntity): Long

    @Query("UPDATE user_collections SET name = :name WHERE id = :id")
    suspend fun renameCollection(id: Long, name: String)

    @Query("DELETE FROM user_collections WHERE id = :id")
    suspend fun deleteCollectionRow(id: Long)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId")
    suspend fun deleteItemsForCollection(collectionId: Long)

    /** Deleting a collection removes its membership rows too. */
    @Transaction
    suspend fun deleteCollection(id: Long) {
        deleteItemsForCollection(id)
        deleteCollectionRow(id)
    }

    @Query("SELECT * FROM user_collections ORDER BY createdAtMs DESC")
    fun observeCollections(): Flow<List<UserCollectionEntity>>

    @Query("SELECT * FROM user_collections WHERE id = :id")
    fun observeCollection(id: Long): Flow<UserCollectionEntity?>

    @Query("SELECT COUNT(*) FROM user_collections")
    fun observeCollectionCount(): Flow<Int>

    // ---------------------------------------------------------------- items

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: CollectionItemEntity)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND contentType = :type AND contentKey = :key")
    suspend fun deleteItem(collectionId: Long, type: String, key: String)

    /** Removes the item from every collection (used when its bookmark is removed). */
    @Query("DELETE FROM collection_items WHERE contentType = :type AND contentKey = :key")
    suspend fun deleteItemEverywhere(type: String, key: String)

    @Query("SELECT * FROM collection_items WHERE collectionId = :collectionId ORDER BY addedAtMs DESC")
    fun observeItems(collectionId: Long): Flow<List<CollectionItemEntity>>

    @Query("SELECT collectionId, COUNT(*) AS count FROM collection_items GROUP BY collectionId")
    fun observeItemCounts(): Flow<List<CollectionItemCount>>
}

data class CollectionItemCount(val collectionId: Long, val count: Int)
