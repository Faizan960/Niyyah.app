package com.salahlock.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.AzkarUserStateEntity
import com.salahlock.app.data.db.entity.CollectionItemEntity
import com.salahlock.app.data.db.entity.EmergencyOverride
import com.salahlock.app.data.db.entity.HadithUserStateEntity
import com.salahlock.app.data.db.entity.LegacyOwnershipEntity
import com.salahlock.app.data.db.entity.OwnerIds
import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.db.entity.QuranProgressEntity
import com.salahlock.app.data.db.entity.StreakEntity
import com.salahlock.app.data.db.entity.UserCollectionEntity
import com.salahlock.app.data.sync.ActiveOwnerProvider
import com.salahlock.app.data.sync.LegacyAdoptionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * BM-013 Checkpoint B — proves one-time legacy adoption re-stamps EVERY owner-scoped
 * table (all 9) + moves legacy reflections, and that a second account can never
 * re-claim.
 */
@RunWith(AndroidJUnit4::class)
class AdoptionCoverageTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: LegacyAdoptionManager
    private lateinit var root: File
    private val A = "user_A"
    private val B = "user_B"

    @Before
    fun setUp() = runBlocking<Unit> {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        manager = LegacyAdoptionManager(db, ActiveOwnerProvider())
        root = File(ctx.cacheDir, "refl-${System.nanoTime()}")

        // Seed one legacy (__local__) row per owner-scoped table.
        db.legacyOwnershipDao().insertIfAbsent(LegacyOwnershipEntity())
        db.prayerRecordDao().upsert(PrayerRecord(date = "2026-07-01", prayerName = "FAJR"))
        db.streakDao().upsert(StreakEntity(bestStreak = 5))
        db.emergencyOverrideDao().upsert(EmergencyOverride(monthYear = "2026-07", count = 1))
        db.quranDao().insertBookmark(QuranBookmarkEntity(surahNumber = 2, ayahNumber = 255))
        db.quranDao().upsertProgress(QuranProgressEntity(surahNumber = 18, lastAyah = 3))
        val cid = db.collectionsDao().insertCollection(UserCollectionEntity(name = "Fav", clientUuid = "u1"))
        db.collectionsDao().insertItem(CollectionItemEntity(collectionId = cid, contentType = "QURAN", contentKey = "2:255", collectionUuid = "u1"))
        db.hadithUserStateDao().upsert(HadithUserStateEntity(hadithId = "bukhari-1-eng", isBookmarked = true))
        db.azkarUserStateDao().upsert(AzkarUserStateEntity(azkarRef = "azkar:v1:morning:0", isBookmarked = true))
        File(File(root, OwnerIds.LOCAL), "2026-06.json").apply { parentFile!!.mkdirs(); writeText("R") }
    }

    @After
    fun tearDown() {
        db.close(); root.deleteRecursively()
    }

    @Test
    fun adoption_reStampsAllTables_movesReflections_andIsOneTime() = runBlocking {
        val claimed = manager.adoptLegacyDataOnce(A, nowMs = 1000, reflectionRoot = root)
        assertTrue("first claim succeeds", claimed)

        // Every owner-scoped dataset now belongs to A, and nothing remains under __local__.
        assertEquals(1, db.prayerRecordDao().getAll(A).size)
        assertEquals(0, db.prayerRecordDao().getAll(OwnerIds.LOCAL).size)
        assertEquals(5, db.streakDao().getStreak(A)!!.bestStreak)
        assertEquals(1, db.emergencyOverrideDao().getAll(A).size)
        assertEquals(1, db.quranDao().getAllBookmarks(A).first().size)
        assertEquals(1, db.quranDao().getAllProgress(A).first().size)
        assertEquals(1, db.collectionsDao().observeCollections(A).first().size)
        val cid = db.collectionsDao().observeCollections(A).first().first().id
        assertEquals(1, db.collectionsDao().observeItems(A, cid).first().size)
        assertEquals(1, db.hadithUserStateDao().getAllForOwner(A).size)
        assertEquals(1, db.azkarUserStateDao().getAllForOwner(A).size)

        // Reflection file moved to A.
        assertTrue(File(File(root, A), "2026-06.json").exists())
        assertFalse(File(File(root, OwnerIds.LOCAL), "2026-06.json").exists())

        // Ledger records the adopter; a second account can NOT re-claim.
        assertEquals(A, db.legacyOwnershipDao().get()!!.adoptedBy)
        val second = manager.adoptLegacyDataOnce(B, nowMs = 2000, reflectionRoot = root)
        assertFalse("legacy data already adopted → B cannot claim", second)
        assertEquals(0, db.prayerRecordDao().getAll(B).size)
        assertEquals(A, db.legacyOwnershipDao().get()!!.adoptedBy)
    }

    /**
     * Regression (BM-013 Checkpoint C): a FRESH install has no singleton ledger row —
     * Room's onCreate path never runs MIGRATION_8_9's seed. Adoption must still seed
     * the row and claim; otherwise `claimIfUnclaimed` updates 0 rows and adoptedBy
     * stays null forever, breaking the logout→SignedOutNoUser transition. Uses its own
     * DB that, unlike setUp, deliberately does NOT pre-seed the singleton.
     */
    @Test
    fun adoption_seedsSingleton_onFreshInstallWithNoLedgerRow() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fresh = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val freshManager = LegacyAdoptionManager(fresh, ActiveOwnerProvider())
            assertEquals("fresh install has no ledger row", null, fresh.legacyOwnershipDao().get())

            val claimed = freshManager.adoptLegacyDataOnce(A, nowMs = 1000)
            assertTrue("adoption seeds the singleton then claims it", claimed)
            assertEquals(A, fresh.legacyOwnershipDao().get()!!.adoptedBy)

            // Still one-time: a second account cannot re-claim.
            assertFalse(freshManager.adoptLegacyDataOnce(B, nowMs = 2000))
            assertEquals(A, fresh.legacyOwnershipDao().get()!!.adoptedBy)
        } finally {
            fresh.close()
        }
    }

    /**
     * Regression (BM-013 Checkpoint C): reclaimStragglers must not crash when a
     * `__local__` straggler shares a natural key with a row the owner ALREADY has.
     * A plain UPDATE re-stamp threw SQLITE_CONSTRAINT_PRIMARYKEY and aborted the whole
     * adoption. Policy: the authenticated owner's existing row wins, the colliding
     * straggler is dropped, and non-colliding stragglers are still adopted.
     */
    @Test
    fun reclaimStragglers_ownerWinsOnCollision_dropsColliding_adoptsRest() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            d.legacyOwnershipDao().insertIfAbsent(LegacyOwnershipEntity(adoptedBy = A, adoptedAtMs = 1))
            // A already owns surah-18 progress and bookmark 2:255.
            d.quranDao().upsertProgress(QuranProgressEntity(surahNumber = 18, lastAyah = 50, ownerId = A))
            d.quranDao().insertBookmark(QuranBookmarkEntity(surahNumber = 2, ayahNumber = 255, ownerId = A))
            // Stragglers: colliding surah-18 progress, plus non-colliding surah-2 progress and bookmark 1:1.
            d.quranDao().upsertProgress(QuranProgressEntity(surahNumber = 18, lastAyah = 3, ownerId = OwnerIds.LOCAL))
            d.quranDao().upsertProgress(QuranProgressEntity(surahNumber = 2, lastAyah = 7, ownerId = OwnerIds.LOCAL))
            d.quranDao().insertBookmark(QuranBookmarkEntity(surahNumber = 1, ayahNumber = 1, ownerId = OwnerIds.LOCAL))

            LegacyAdoptionManager(d, ActiveOwnerProvider()).reclaimStragglers(A) // must NOT throw

            assertEquals("owner's existing surah-18 progress preserved", 50, d.quranDao().getProgress(A, 18)!!.lastAyah)
            assertEquals("non-colliding straggler progress adopted", 7, d.quranDao().getProgress(A, 2)!!.lastAyah)
            assertEquals("non-colliding straggler bookmark adopted (2:255 + 1:1)", 2, d.quranDao().getAllBookmarks(A).first().size)
            assertEquals("no leftover __local__ progress", 0, d.quranDao().getAllProgress(OwnerIds.LOCAL).first().size)
            assertEquals("no leftover __local__ bookmarks", 0, d.quranDao().getAllBookmarks(OwnerIds.LOCAL).first().size)
        } finally {
            d.close()
        }
    }
}
