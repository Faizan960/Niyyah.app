package com.salahlock.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.salahlock.app.data.db.entity.HadithEntity
import com.salahlock.app.data.db.entity.CategoryMappingEntity
import com.salahlock.app.data.db.entity.CollectionBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HadithDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(hadiths: List<HadithEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryMappings(mappings: List<CategoryMappingEntity>)

    @Query("DELETE FROM hadith_category_mapping")
    suspend fun clearCategoryMappings()

    @Query("SELECT * FROM hadith_category_mapping")
    suspend fun getAllCategoryMappings(): List<CategoryMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionBooks(books: List<CollectionBookEntity>)

    @Query("SELECT * FROM hadith_table WHERE id = :id")
    suspend fun getHadithById(id: String): HadithEntity?

    @Query("SELECT * FROM hadith_table WHERE isBookmarked = 1 ORDER BY lastReadTimestamp DESC")
    fun getBookmarkedHadiths(): Flow<List<HadithEntity>>

    @Query("SELECT * FROM hadith_table WHERE lastReadTimestamp > 0 ORDER BY lastReadTimestamp DESC LIMIT 20")
    fun getRecentHadiths(): Flow<List<HadithEntity>>

    @Query("UPDATE hadith_table SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: String, isBookmarked: Boolean)

    @Query("UPDATE hadith_table SET isBookmarked = :isBookmarked, bookmarkSource = :source WHERE id = :id")
    suspend fun updateBookmarkStatus(id: String, isBookmarked: Boolean, source: String)

    @Query("UPDATE hadith_table SET lastReadTimestamp = :timestamp WHERE id = :id")
    suspend fun updateReadTimestamp(id: String, timestamp: Long)

    /**
     * Full-text keyword search across translation, Arabic text, and collection name.
     * Uses LIKE — acceptable for 15k rows. FTS can replace this if benchmarks demand it.
     * Results ordered by collection + global number for stable, predictable ordering.
     */
    @Query("""
        SELECT * FROM hadith_table
        WHERE (translationText LIKE '%' || :query || '%' OR arabicText LIKE '%' || :query || '%')
          AND language = :language
        ORDER BY collection ASC, CAST(id LIKE (collection || '-%') AS INTEGER) ASC
        LIMIT 100
    """)
    suspend fun searchHadiths(query: String, language: String): List<HadithEntity>

    /**
     * Search within a specific collection by keyword.
     * Used when user types "bukhari prayer" to narrow scope.
     */
    @Query("""
        SELECT * FROM hadith_table
        WHERE collection = :collection
          AND (translationText LIKE '%' || :keyword || '%' OR arabicText LIKE '%' || :keyword || '%')
          AND language = :language
        ORDER BY CAST(id AS TEXT) ASC
        LIMIT 50
    """)
    suspend fun searchInCollection(collection: String, keyword: String, language: String): List<HadithEntity>

    /**
     * Look up a hadith by its global sequential number within a collection.
     * The global number is the middle segment of the id: "{collection}-{globalNum}-{language}".
     * Used for reference-number queries like "Bukhari 52" or "Muslim 178".
     */
    @Query("""
        SELECT * FROM hadith_table
        WHERE id = :collection || '-' || :globalNumber || '-' || :language
        LIMIT 1
    """)
    suspend fun getHadithByGlobalNumber(collection: String, globalNumber: String, language: String): HadithEntity?

    /** Get the book title for a given collection and book number. */
    @Query("SELECT title FROM collection_book_entity WHERE collectionName = :collection AND bookNumber = :bookNumber LIMIT 1")
    suspend fun getBookTitle(collection: String, bookNumber: String): String?

    /**
     * Get the zero-based position of a hadith within its book's ordered list.
     * Used to open the reader at the correct page when navigating from search results.
     */
    @Query("""
        SELECT COUNT(*) FROM hadith_table
        WHERE collection = :collection
          AND bookNumber = :bookNumber
          AND language = :language
          AND CAST(id AS TEXT) < :targetId
    """)
    suspend fun getHadithPositionInBook(collection: String, bookNumber: String, language: String, targetId: String): Int

    @Transaction
    @Query("""
        SELECT h.* FROM hadith_table h
        INNER JOIN hadith_category_mapping c ON h.collection = c.collection AND h.bookNumber = c.bookNumber
        WHERE c.topic = :topic AND h.language = :language
        ORDER BY CAST(h.bookNumber AS INTEGER) ASC, CAST(h.hadithNumber AS INTEGER) ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getHadithsByTopic(topic: String, language: String, limit: Int = 30, offset: Int = 0): List<HadithEntity>
    
    @Query("""
        SELECT * FROM hadith_table
        WHERE collection = :collection AND bookNumber = :bookNumber AND language = :language
        ORDER BY CAST(hadithNumber AS INTEGER) ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getHadithsByBook(collection: String, bookNumber: String, language: String, limit: Int = 30, offset: Int = 0): List<HadithEntity>

    @Query("SELECT * FROM collection_book_entity WHERE collectionName = :collection ORDER BY CAST(bookNumber AS INTEGER) ASC")
    suspend fun getBooksForCollection(collection: String): List<CollectionBookEntity>
    
    @Query("SELECT COUNT(*) FROM collection_book_entity WHERE collectionName = :collection")
    suspend fun getBookCountForCollection(collection: String): Int
    
    @Query("SELECT COUNT(*) FROM hadith_table WHERE collection = :collection AND language = :language")
    suspend fun getCollectionCount(collection: String, language: String): Int
    
    @Query("""
        SELECT COUNT(h.id) FROM hadith_table h
        INNER JOIN hadith_category_mapping c ON h.collection = c.collection AND h.bookNumber = c.bookNumber
        WHERE c.topic = :topic AND h.language = :language
    """)
    suspend fun getHadithCountByTopic(topic: String, language: String): Int

    @Query("SELECT COUNT(*) FROM hadith_table WHERE collection = :collection AND bookNumber = :bookNumber AND language = :language")
    suspend fun getHadithCountByBook(collection: String, bookNumber: String, language: String): Int
}
