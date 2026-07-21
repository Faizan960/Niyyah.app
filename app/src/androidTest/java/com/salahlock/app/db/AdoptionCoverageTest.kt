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
}
