package com.salahlock.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.db.entity.QuranProgressEntity
import com.salahlock.app.data.sync.QuranBookmarkSyncEngine.Companion as SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
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

// ── BM-QURAN-PAGES — Mushaf page-view models (rendered from the SAME corpus) ──

/** One ayah rendered on a Mushaf page. Canonical identity stays (surah, ayah). */
data class PageAyah(
    val surahNumber: Int,
    val ayahNumber: Int,
    val arabic: String,
    val english: String,
)

/** A surah heading shown when a surah begins on a page. */
data class PageSurahHeader(
    val surahNumber: Int,
    val transliteration: String,
    val englishName: String,
    val arabicName: String,
    /** Fatiha's bismillah is ayah 1; Tawbah has none — both suppress the standalone line. */
    val showBismillah: Boolean,
)

/** Ordered blocks that make up one page. */
sealed interface PageBlock {
    data class Header(val header: PageSurahHeader) : PageBlock
    data class AyahLine(val ayah: PageAyah) : PageBlock
}

/** Full rendered content of a single Mushaf page. */
data class QuranPageContent(
    val page: Int,
    val juz: Int,
    /** Surah of the page's first ayah — shown in the reader chrome. */
    val startSurahTransliteration: String,
    /** First ayah on the page — the canonical position persisted for Continue Reading. */
    val firstSurah: Int,
    val firstAyah: Int,
    val blocks: List<PageBlock>,
)

/** Lightweight row for the page browser list (no ayah text loaded). */
data class PageListItem(
    val page: Int,
    val surahTransliteration: String,
    val juz: Int,
)

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
class QuranRepository internal constructor(
    context: Context,
    // Injectable for instrumented tests (in-memory DB / no-op scheduler); production
    // uses the real singleton DB + WorkManager via getInstance().
    private val db: AppDatabase = AppDatabase.getInstance(context.applicationContext),
    private val scheduleSync: (Context) -> Unit = {
        com.salahlock.app.work.QuranBookmarkSyncWorker.schedule(it)
    },
) {

    private val appContext = context.applicationContext
    private val dao = db.quranDao()
    // BM-013 — active owner seam. Snapshot for one-shot ops; [ownerFlow] re-keys
    // live queries the instant the owner changes (login/logout visibility isolation).
    private val activeOwner = com.salahlock.app.data.sync.ActiveOwnerProvider.shared
    private val owner get() = activeOwner.ownerId()
    private val ownerFlow get() = activeOwner.ownerIdFlow

    private val loadMutex = Mutex()
    @Volatile private var cachedSurahs: List<Surah>? = null

    // BM-QURAN-PAGES — page mapping is derived once and cached. Separate mutex so
    // getPageMapping() can call getSurahs() (which uses loadMutex) without deadlock.
    private val pageMutex = Mutex()
    @Volatile private var cachedMapping: QuranPageMapping? = null

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

    // ------------------------------------------------------------ pages (Mushaf)

    /**
     * Canonical 604-page mapping, built once from the bundled page-starts asset +
     * the loaded surah ayah counts (same corpus). Fully offline.
     */
    suspend fun getPageMapping(): QuranPageMapping {
        cachedMapping?.let { return it }
        val surahs = getSurahs() // acquires loadMutex first, OUTSIDE pageMutex
        return pageMutex.withLock {
            cachedMapping ?: withContext(Dispatchers.IO) {
                val json = appContext.assets.open("quran_pages.json")
                    .bufferedReader().use { it.readText() }
                QuranPageMapping(QuranPageMapping.parsePageStarts(json), surahs.map { it.ayahCount })
            }.also { cachedMapping = it }
        }
    }

    /** Deterministic (surah, ayah) → page. Powers Surah↔Page interoperability. */
    suspend fun pageForSurahAyah(surah: Int, ayah: Int): Int =
        getPageMapping().pageForAyah(surah, ayah)

    /** Total number of Mushaf pages (604). */
    suspend fun pageCount(): Int = getPageMapping().pageCount

    /** Lightweight browser list — one row per page, no ayah text loaded. */
    suspend fun getPageList(): List<PageListItem> {
        val m = getPageMapping()
        val byNumber = getSurahs().associateBy { it.number }
        return (1..m.pageCount).map { p ->
            val (s, a) = m.pageStart(p)
            val surah = byNumber[s]
            PageListItem(
                page = p,
                surahTransliteration = surah?.transliteration ?: "",
                juz = surah?.ayahs?.getOrNull(a - 1)?.juz ?: 1,
            )
        }
    }

    /** Starting page for each juz (index 0 = juz 1 … 29 = juz 30). */
    suspend fun getJuzStartPages(): List<Int> {
        val m = getPageMapping()
        val firstOfJuz = arrayOfNulls<Pair<Int, Int>>(31)
        for (surah in getSurahs()) {
            for (ayah in surah.ayahs) {
                if (firstOfJuz[ayah.juz] == null) firstOfJuz[ayah.juz] = surah.number to ayah.numberInSurah
            }
        }
        return (1..30).map { j -> firstOfJuz[j]?.let { m.pageForAyah(it.first, it.second) } ?: 1 }
    }

    /** Fully rendered content for a single page — Arabic + translation from the corpus. */
    suspend fun getPageContent(page: Int): QuranPageContent {
        val m = getPageMapping()
        val p = page.coerceIn(1, m.pageCount)
        val byNumber = getSurahs().associateBy { it.number }
        val refs = m.ayahRefsForPage(p)
        val blocks = ArrayList<PageBlock>(refs.size + 4)
        var lastSurah = -1
        for ((s, a) in refs) {
            val surah = byNumber[s] ?: continue
            if (s != lastSurah && a == 1) {
                blocks += PageBlock.Header(
                    PageSurahHeader(
                        surahNumber = s,
                        transliteration = surah.transliteration,
                        englishName = surah.englishName,
                        arabicName = surah.arabicName,
                        showBismillah = s != 1 && s != 9,
                    ),
                )
            }
            val ayah = surah.ayahs.getOrNull(a - 1) ?: continue
            blocks += PageBlock.AyahLine(PageAyah(s, a, ayah.arabic, ayah.english))
            lastSurah = s
        }
        val first = refs.first()
        val firstSurah = byNumber[first.first]
        return QuranPageContent(
            page = p,
            juz = firstSurah?.ayahs?.getOrNull(first.second - 1)?.juz ?: 1,
            startSurahTransliteration = firstSurah?.transliteration ?: "",
            firstSurah = first.first,
            firstAyah = first.second,
            blocks = blocks,
        )
    }

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

    // BM-013 Checkpoint C — live queries re-key on owner change so an open screen
    // switches datasets immediately on login/logout (visibility isolation).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getAllBookmarks(): Flow<List<QuranBookmarkEntity>> =
        ownerFlow.flatMapLatest { dao.getAllBookmarks(it) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getBookmarkCount(): Flow<Int> = ownerFlow.flatMapLatest { dao.getBookmarkCount(it) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun isAyahBookmarked(surah: Int, ayah: Int): Flow<Boolean> =
        ownerFlow.flatMapLatest { dao.isAyahBookmarked(it, surah, ayah) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun isSurahBookmarked(surah: Int): Flow<Boolean> =
        ownerFlow.flatMapLatest { dao.isSurahBookmarked(it, surah) }

    suspend fun toggleAyahBookmark(surah: Int, ayah: Int, bookmarked: Boolean) = mutateBookmark { o, now ->
        if (bookmarked) {
            val entity = QuranBookmarkEntity(surahNumber = surah, ayahNumber = ayah, ownerId = o, updatedAt = now)
            dao.insertBookmark(entity)
            SyncEngine.upsertEvent(o, entity, now)
        } else {
            dao.deleteAyahBookmark(o, surah, ayah)
            SyncEngine.deleteEvent(o, surah, ayah, now)
        }
    }

    suspend fun toggleSurahBookmark(surah: Int, bookmarked: Boolean) = mutateBookmark { o, now ->
        if (bookmarked) {
            val entity = QuranBookmarkEntity(surahNumber = surah, ownerId = o, updatedAt = now)
            dao.insertBookmark(entity)
            SyncEngine.upsertEvent(o, entity, now)
        } else {
            dao.deleteSurahBookmark(o, surah)
            SyncEngine.deleteEvent(o, surah, null, now)
        }
    }

    suspend fun deleteBookmark(id: Long) = mutateBookmark { o, now ->
        val row = dao.getBookmarkById(o, id) ?: return@mutateBookmark null
        dao.deleteBookmark(o, id)
        SyncEngine.deleteEvent(o, row.surahNumber, row.ayahNumber, now)
    }

    suspend fun moveBookmarkToCollection(id: Long, collection: String) = mutateBookmark { o, now ->
        dao.moveBookmarkToCollection(o, id, collection)
        dao.getBookmarkById(o, id)?.let { SyncEngine.upsertEvent(o, it, now) }
    }

    /**
     * BM-013 Checkpoint C — every bookmark mutation and its outbox event commit in
     * ONE Room transaction (atomic; process-death-safe). The event is enqueued only
     * for an authenticated owner: signed-out/legacy data reaches the cloud via the
     * first-sync snapshot after adoption, never via a signed-out outbox. The local
     * write NEVER waits for the network — the worker syncs when conditions allow.
     */
    private suspend fun mutateBookmark(
        block: suspend (owner: String, nowMs: Long) -> com.salahlock.app.data.db.entity.SyncOutboxEntity?,
    ) {
        val scope = activeOwner.scope()
        val ownerNow = owner
        val authenticated = scope is com.salahlock.app.data.sync.OwnerScope.Authenticated
        db.withTransaction {
            val event = block(ownerNow, System.currentTimeMillis())
            if (authenticated && event != null) db.syncOutboxDao().enqueue(event)
        }
        if (authenticated) {
            // Scheduling failure must never fail an already-committed local mutation.
            runCatching { scheduleSync(appContext) }
        }
    }

    // ------------------------------------------------------------ progress

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getLastRead(): Flow<QuranProgressEntity?> = ownerFlow.flatMapLatest { dao.getLastRead(it) }
    suspend fun getProgress(surah: Int): QuranProgressEntity? = dao.getProgress(owner, surah)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getRecentlyRead(limit: Int = 10): Flow<List<QuranProgressEntity>> =
        ownerFlow.flatMapLatest { dao.getRecentlyRead(it, limit) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getAllProgress(): Flow<List<QuranProgressEntity>> = ownerFlow.flatMapLatest { dao.getAllProgress(it) }

    /** Records that the user is reading [surah] at [ayah]. */
    suspend fun saveReadingPosition(surah: Int, ayah: Int) {
        val existing = dao.getProgress(owner, surah)
        dao.upsertProgress(
            QuranProgressEntity(
                surahNumber = surah,
                lastAyah = ayah,
                maxAyah = maxOf(ayah, existing?.maxAyah ?: 1),
                timestampMs = System.currentTimeMillis(),
                ownerId = owner,
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
