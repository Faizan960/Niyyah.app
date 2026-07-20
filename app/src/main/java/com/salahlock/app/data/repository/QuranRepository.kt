package com.salahlock.app.data.repository

import android.content.Context
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.db.entity.QuranProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One ayah: Uthmani Arabic, Saheeh International English, juz number. */
data class Ayah(
    val numberInSurah: Int,
    val arabic: String,
    val english: String,
    val juz: Int,
)

/** Surah metadata + verses, loaded from the bundled quran.json asset. */
data class Surah(
    val number: Int,
    val arabicName: String,
    val transliteration: String,
    val englishName: String,
    val revelationType: String,
    val ayahs: List<Ayah>,
) {
    val ayahCount: Int get() = ayahs.size
    /** Juz of the surah's first ayah — used for the list badge and juz filter. */
    val startJuz: Int get() = ayahs.first().juz
}

/** Total ayahs in the Quran; denominator for overall reading progress. */
const val TOTAL_AYAHS = 6236

/**
 * Case-insensitive surah filter across English name, transliteration,
 * Arabic name and surah number. Pure — unit-testable without Android.
 */
fun filterSurahs(surahs: List<Surah>, query: String): List<Surah> {
    val q = query.trim()
    if (q.isEmpty()) return surahs
    val lower = q.lowercase()
    return surahs.filter { s ->
        s.englishName.lowercase().contains(lower) ||
            s.transliteration.lowercase().contains(lower) ||
            s.arabicName.contains(q) ||
            s.number.toString() == q
    }
}

/**
 * Quran data layer. Text comes from the bundled asset (assets/quran.json:
 * quran-uthmani + en.sahih editions, alquran.cloud); bookmarks and reading
 * progress live in Room via [com.salahlock.app.data.db.dao.QuranDao].
 */
class QuranRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).quranDao()

    private val loadMutex = Mutex()
    @Volatile private var cachedSurahs: List<Surah>? = null

    // ------------------------------------------------------------ text

    /** Loads (and memoizes) all 114 surahs. Safe to call repeatedly. */
    suspend fun getSurahs(): List<Surah> {
        cachedSurahs?.let { return it }
        return loadMutex.withLock {
            cachedSurahs ?: withContext(Dispatchers.IO) { parseAsset() }.also { cachedSurahs = it }
        }
    }

    suspend fun getSurah(number: Int): Surah? = getSurahs().firstOrNull { it.number == number }

    /**
     * Case-insensitive search across English name, Arabic name, transliteration
     * and surah number.
     */
    suspend fun searchSurahs(query: String): List<Surah> = filterSurahs(getSurahs(), query)

    /** Surahs whose text intersects the given juz (1..30). */
    suspend fun getSurahsInJuz(juz: Int): List<Surah> =
        getSurahs().filter { s -> s.ayahs.any { it.juz == juz } }

    private fun parseAsset(): List<Surah> {
        val text = appContext.assets.open("quran.json").bufferedReader().use { it.readText() }
        val root = json.decodeFromString<QuranAssetRoot>(text)
        return root.surahs.map { s ->
            Surah(
                number = s.n,
                arabicName = s.ar,
                transliteration = s.tr,
                englishName = s.en,
                revelationType = s.type,
                ayahs = s.ayahs.mapIndexed { i, a ->
                    Ayah(
                        numberInSurah = i + 1,
                        arabic = a[0].jsonPrimitiveContent(),
                        english = a[1].jsonPrimitiveContent(),
                        juz = a[2].jsonPrimitiveContent().toInt(),
                    )
                },
            )
        }
    }

    // ------------------------------------------------------------ bookmarks

    fun getAllBookmarks(): Flow<List<QuranBookmarkEntity>> = dao.getAllBookmarks()
    fun getBookmarkCount(): Flow<Int> = dao.getBookmarkCount()
    fun isAyahBookmarked(surah: Int, ayah: Int): Flow<Boolean> = dao.isAyahBookmarked(surah, ayah)
    fun isSurahBookmarked(surah: Int): Flow<Boolean> = dao.isSurahBookmarked(surah)

    suspend fun toggleAyahBookmark(surah: Int, ayah: Int, bookmarked: Boolean) {
        if (bookmarked) dao.insertBookmark(QuranBookmarkEntity(surahNumber = surah, ayahNumber = ayah))
        else dao.deleteAyahBookmark(surah, ayah)
    }

    suspend fun toggleSurahBookmark(surah: Int, bookmarked: Boolean) {
        if (bookmarked) dao.insertBookmark(QuranBookmarkEntity(surahNumber = surah))
        else dao.deleteSurahBookmark(surah)
    }

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    suspend fun moveBookmarkToCollection(id: Long, collection: String) =
        dao.moveBookmarkToCollection(id, collection)

    // ------------------------------------------------------------ progress

    fun getLastRead(): Flow<QuranProgressEntity?> = dao.getLastRead()
    suspend fun getProgress(surah: Int): QuranProgressEntity? = dao.getProgress(surah)
    fun getRecentlyRead(limit: Int = 10): Flow<List<QuranProgressEntity>> = dao.getRecentlyRead(limit)
    fun getAllProgress(): Flow<List<QuranProgressEntity>> = dao.getAllProgress()

    /** Records that the user is reading [surah] at [ayah]. */
    suspend fun saveReadingPosition(surah: Int, ayah: Int) {
        val existing = dao.getProgress(surah)
        dao.upsertProgress(
            QuranProgressEntity(
                surahNumber = surah,
                lastAyah = ayah,
                maxAyah = maxOf(ayah, existing?.maxAyah ?: 1),
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Volatile private var INSTANCE: QuranRepository? = null
        fun getInstance(context: Context): QuranRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuranRepository(context).also { INSTANCE = it }
            }
    }
}

// ------------------------------------------------------------ asset schema

@Serializable
private data class QuranAssetRoot(val surahs: List<QuranAssetSurah>)

@Serializable
private data class QuranAssetSurah(
    val n: Int,
    val ar: String,
    val tr: String,
    val en: String,
    val type: String,
    /** Each ayah is a compact [arabic, english, juz] triple. */
    val ayahs: List<List<kotlinx.serialization.json.JsonPrimitive>>,
)

private fun kotlinx.serialization.json.JsonPrimitive.jsonPrimitiveContent(): String = content
