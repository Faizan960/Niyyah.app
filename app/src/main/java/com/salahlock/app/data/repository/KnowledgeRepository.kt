package com.salahlock.app.data.repository

import android.content.Context
import android.util.Log
import com.salahlock.app.data.api.HadithApiService
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.AzkarEntity
import com.salahlock.app.data.db.entity.CategoryMappingEntity
import com.salahlock.app.data.db.entity.CollectionBookEntity
import com.salahlock.app.data.db.entity.HadithEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.json.JSONArray
import java.io.InputStreamReader
import java.io.BufferedReader

class KnowledgeRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val hadithDao = db.hadithDao()
    private val azkarDao = db.azkarDao()

    private val apiService: HadithApiService by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HadithApiService::class.java)
    }

    // --- Hadith Data Sync ---

    /** Returns true if all collections synced successfully, false if any failed. */
    suspend fun syncHadithCollectionsIfNeeded(): SyncResult {
        return withContext(Dispatchers.IO) {
            Log.d("KnowledgeSync", "syncHadithCollectionsIfNeeded called")

            val totalHadiths = hadithDao.getCollectionCount("bukhari", "eng") + hadithDao.getCollectionCount("muslim", "eng")
            val totalAzkar = azkarDao.getAzkarCount()
            Log.d("RoomCounts", "At app startup: Hadith Count = $totalHadiths, Azkar Count = $totalAzkar")

            var allSucceeded = true

            val bukhariBooks = hadithDao.getBookCountForCollection("bukhari")
            Log.d("KnowledgeSync", "Existing bukhari books count: $bukhariBooks")
            if (bukhariBooks == 0) {
                Log.d("KnowledgeSync", "Syncing Bukhari...")
                if (!syncCollection("eng-bukhari", "bukhari", "eng")) allSucceeded = false
                if (!syncCollection("ara-bukhari", "bukhari", "ara")) allSucceeded = false
            }

            val muslimBooks = hadithDao.getBookCountForCollection("muslim")
            Log.d("KnowledgeSync", "Existing muslim books count: $muslimBooks")
            if (muslimBooks == 0) {
                Log.d("KnowledgeSync", "Syncing Muslim...")
                if (!syncCollection("eng-muslim", "muslim", "eng")) allSucceeded = false
                if (!syncCollection("ara-muslim", "muslim", "ara")) allSucceeded = false
            }

            seedHadithCategories()

            val finalBukhariCount = hadithDao.getCollectionCount("bukhari", "eng")
            val finalBukhariBooks = hadithDao.getBookCountForCollection("bukhari")
            Log.d("RoomCounts", "Final check -> Bukhari: $finalBukhariCount hadiths, $finalBukhariBooks books")

            if (allSucceeded) SyncResult.Success else SyncResult.PartialFailure
        }
    }

    enum class SyncResult { Success, PartialFailure, AlreadySynced }

    /** All collections the app knows how to sync from the fawazahmed0 hadith API. */
    val supportedCollections = listOf("bukhari", "muslim", "nasai", "abudawud", "tirmidhi", "ibnmajah")

    /**
     * On-demand sync for a single collection (used when the user opens one of
     * the six books that isn't cached yet). Returns true when the collection
     * is available locally afterwards.
     */
    suspend fun syncSingleCollectionIfNeeded(collection: String): Boolean =
        withContext(Dispatchers.IO) {
            if (hadithDao.getBookCountForCollection(collection) > 0) return@withContext true
            val eng = syncCollection("eng-$collection", collection, "eng")
            val ara = syncCollection("ara-$collection", collection, "ara")
            eng && ara
        }

    /** Returns true on success, false on any failure. */
    private suspend fun syncCollection(edition: String, collectionName: String, language: String): Boolean {
        return try {
            Log.d("KnowledgeSync", "Requesting $edition from API...")
            val response = apiService.getEdition(edition)
            Log.d("KnowledgeSync", "API success for $edition. Total raw hadiths: ${response.hadiths.size}")

            var insertedCount = 0
            response.hadiths.chunked(900).forEach { chunk ->
                val entities = chunk.map {
                    // Global number: continuous sequential across the entire collection.
                    // This is the standard citation number (e.g., "Bukhari 647").
                    val globalNum = if (it.hadithNumber % 1.0 == 0.0)
                        it.hadithNumber.toInt().toString()
                    else
                        it.hadithNumber.toString()

                    // Within-book number: resets to 1 for each book. NOT used for display.
                    val withinBookNum = it.reference?.hadith?.toString() ?: globalNum
                    val bookNum = it.reference?.book?.toString() ?: "0"

                    HadithEntity(
                        id = "$collectionName-$globalNum-$language",
                        collection = collectionName,
                        bookNumber = bookNum,
                        hadithNumber = withinBookNum,
                        arabicText = if (language == "ara") it.text else "",
                        translationText = if (language != "ara") it.text else "",
                        language = language,
                    )
                }
                hadithDao.insertAll(entities)
                insertedCount += entities.size
            }
            Log.d("KnowledgeSync", "Inserted $insertedCount hadith entities for $edition.")

            // Save book metadata from the English version only (Arabic titles are not needed here)
            if (language == "eng") {
                val books = response.metadata.sections.map { (bookNumStr, title) ->
                    CollectionBookEntity(
                        collectionName = collectionName,
                        bookNumber = bookNumStr,
                        title = title.ifBlank { "Introduction" },
                    )
                }
                hadithDao.insertCollectionBooks(books)
                Log.d("KnowledgeSync", "Saved ${books.size} books for $collectionName.")
            }

            Log.d("KnowledgeSync", "Synced $edition successfully.")
            true
        } catch (e: Throwable) {
            Log.e("KnowledgeSync", "Failed to sync $edition: ${e.message}", e)
            false
        }
    }

    private suspend fun seedHadithCategories() {
        // Clear first — REPLACE with autoGenerate ids appends duplicates otherwise.
        hadithDao.clearCategoryMappings()
        val mappings = listOf(
            CategoryMappingEntity(0, "Fasting", "bukhari", "30"),
            // Knowledge-library categories (BM-006): real Bukhari books per theme.
            CategoryMappingEntity(0, "Theology", "bukhari", "2"),   // Belief
            CategoryMappingEntity(0, "Theology", "bukhari", "97"),  // Oneness of Allah
            CategoryMappingEntity(0, "History", "bukhari", "63"),   // Merits of the Ansar
            CategoryMappingEntity(0, "History", "bukhari", "64"),   // Military Expeditions
            CategoryMappingEntity(0, "Spirituality", "bukhari", "80"), // Invocations
            CategoryMappingEntity(0, "Spirituality", "bukhari", "81"), // Softening of the Hearts
            CategoryMappingEntity(0, "Jurisprudence", "bukhari", "8"),  // Prayers
            CategoryMappingEntity(0, "Jurisprudence", "bukhari", "24"), // Zakat
            CategoryMappingEntity(0, "Jurisprudence", "bukhari", "30"), // Fasting
            CategoryMappingEntity(0, "Salah", "bukhari", "8"),
            CategoryMappingEntity(0, "Salah", "bukhari", "9"),
            CategoryMappingEntity(0, "Salah", "bukhari", "10"),
            CategoryMappingEntity(0, "Faith", "bukhari", "2"),
            CategoryMappingEntity(0, "Knowledge", "bukhari", "3"),
            CategoryMappingEntity(0, "Charity", "bukhari", "24"),
            CategoryMappingEntity(0, "Family", "bukhari", "67"), // Marriage
            CategoryMappingEntity(0, "Character", "bukhari", "78"), // Good manners
            CategoryMappingEntity(0, "Patience", "bukhari", "75"), // Patients
            CategoryMappingEntity(0, "Justice", "bukhari", "46"), // Oppressions
            CategoryMappingEntity(0, "Dua", "bukhari", "80"), // Invocations
            CategoryMappingEntity(0, "Brotherhood", "bukhari", "78")
        )
        hadithDao.insertCategoryMappings(mappings)
    }

    // --- Azkar Data Sync ---
    suspend fun syncAzkarIfNeeded() {
        withContext(Dispatchers.IO) {
            val count = azkarDao.getAzkarCount()
            Log.d("AzkarSeeder", "syncAzkarIfNeeded called. Current Azkar count: $count")
            if (count == 0) {
                // Seed local json Azkar
                seedAzkarFromJson()
            }
        }
    }

    private suspend fun seedAzkarFromJson() {
        try {
            val inputStream = context.assets.open("azkar.json")
            val text = BufferedReader(InputStreamReader(inputStream)).readText()
            val jsonArray = JSONArray(text)
            val azkarList = mutableListOf<AzkarEntity>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                azkarList.add(
                    AzkarEntity(
                        category = obj.getString("category"),
                        arabic = obj.getString("arabic"),
                        transliteration = obj.getString("transliteration"),
                        translation = obj.getString("translation"),
                        reference = obj.getString("reference"),
                        targetCount = obj.getInt("count")
                    )
                )
            }
            azkarDao.insertAll(azkarList)
            Log.d("AzkarSeeder", "Seeded ${azkarList.size} Azkar from JSON asset.")
        } catch (e: Exception) {
            Log.e("AzkarSeeder", "Failed to seed Azkar from JSON", e)
        }
    }
    
    // --- Data Access Flows ---
    fun getAzkarByCategory(cat: String) = azkarDao.getAzkarByCategory(cat)
    fun getBookmarkedAzkar() = azkarDao.getBookmarkedAzkar()
    fun getAzkarCategoryCounts() = azkarDao.getCategoryCounts()
    suspend fun touchAzkarCategory(category: String) =
        azkarDao.touchCategory(category, System.currentTimeMillis())
    suspend fun searchAzkar(query: String) = azkarDao.searchAzkar(query)
    suspend fun getAllCategoryMappings() = hadithDao.getAllCategoryMappings()
    fun getBookmarkedHadiths() = hadithDao.getBookmarkedHadiths()
    fun getRecentHadiths() = hadithDao.getRecentHadiths()
    
    suspend fun getHadithsByTopic(topic: String, lang: String, limit: Int, offset: Int) = hadithDao.getHadithsByTopic(topic, lang, limit, offset)
    suspend fun getHadithsByBook(collection: String, bookNumber: String, lang: String, limit: Int, offset: Int) = hadithDao.getHadithsByBook(collection, bookNumber, lang, limit, offset)

    suspend fun getHadithCountByTopic(topic: String, lang: String) = hadithDao.getHadithCountByTopic(topic, lang)
    suspend fun getHadithCountByBook(collection: String, bookNumber: String, lang: String) = hadithDao.getHadithCountByBook(collection, bookNumber, lang)
    suspend fun getBooksForCollection(collection: String) = hadithDao.getBooksForCollection(collection)
    suspend fun getCollectionCountEng(collection: String) = hadithDao.getCollectionCount(collection, "eng")
    suspend fun searchHadiths(query: String, lang: String) = hadithDao.searchHadiths(query, lang)
    
    /** Search hadiths by global number within a collection (e.g., "Bukhari 52" → collection="bukhari", number="52"). */
    suspend fun getHadithByGlobalNumber(collection: String, globalNumber: String, language: String) =
        hadithDao.getHadithByGlobalNumber(collection, globalNumber, language)

    /** Keyword search within a specific collection. */
    suspend fun searchInCollection(collection: String, keyword: String, language: String) =
        hadithDao.searchInCollection(collection, keyword, language)

    /** Get the zero-based index of a hadith within its book's list, for reader navigation. */
    suspend fun getHadithPositionInBook(collection: String, bookNumber: String, language: String, hadithId: String): Int =
        hadithDao.getHadithPositionInBook(collection, bookNumber, language, hadithId)

    /** Get the human-readable book title for a given collection + book number. */
    suspend fun getBookTitle(collection: String, bookNumber: String): String? =
        hadithDao.getBookTitle(collection, bookNumber)

    suspend fun updateAzkarProgress(id: Int, count: Int) = azkarDao.updateProgress(id, count)
    suspend fun toggleAzkarBookmark(id: Int, bookmarked: Boolean) = azkarDao.updateBookmarkStatus(id, bookmarked)
    suspend fun toggleHadithBookmark(id: String, bookmarked: Boolean) = hadithDao.updateBookmarkStatus(id, bookmarked)
    suspend fun updateReadTimestamp(id: String, timestamp: Long) = hadithDao.updateReadTimestamp(id, timestamp)
    suspend fun resetAzkarProgress() = azkarDao.resetAllProgress()
}
