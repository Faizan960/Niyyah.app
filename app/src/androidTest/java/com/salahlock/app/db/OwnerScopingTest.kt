package com.salahlock.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.db.entity.QuranProgressEntity
import com.salahlock.app.data.db.entity.UserCollectionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BM-013 Checkpoint B — proves owner-scoped DAO isolation: User A never receives
 * User B rows and vice-versa, and both accounts can hold the SAME natural key
 * (ayah / date-prayer / progress) without colliding. Covers the four required
 * tables: quran_bookmarks, quran_progress, prayer_records, collections.
 */
@RunWith(AndroidJUnit4::class)
class OwnerScopingTest {

    private lateinit var db: AppDatabase
    private val A = "user_AAA"
    private val B = "user_BBB"
    private val C = "user_CCC"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun quranBookmarks_isolatedPerOwner_sameAyahCoexists() = runBlocking {
        val dao = db.quranDao()
        dao.insertBookmark(QuranBookmarkEntity(surahNumber = 2, ayahNumber = 255, ownerId = A))
        dao.insertBookmark(QuranBookmarkEntity(surahNumber = 2, ayahNumber = 255, ownerId = B))
        dao.insertBookmark(QuranBookmarkEntity(surahNumber = 18, ayahNumber = 10, ownerId = A))

        assertEquals(2, dao.getAllBookmarks(A).first().size)
        assertEquals(1, dao.getAllBookmarks(B).first().size)
        assertTrue(dao.isAyahBookmarked(A, 2, 255).first())
        assertTrue(dao.isAyahBookmarked(B, 2, 255).first())
        assertFalse("C sees nothing", dao.isAyahBookmarked(C, 2, 255).first())
        // A's delete must not touch B's identical bookmark.
        dao.deleteAyahBookmark(A, 2, 255)
        assertFalse(dao.isAyahBookmarked(A, 2, 255).first())
        assertTrue("B's bookmark survives A's delete", dao.isAyahBookmarked(B, 2, 255).first())
    }

    @Test
    fun quranProgress_isolatedPerOwner_sameSurahIndependent() = runBlocking {
        val dao = db.quranDao()
        dao.upsertProgress(QuranProgressEntity(surahNumber = 18, lastAyah = 10, maxAyah = 25, ownerId = A))
        dao.upsertProgress(QuranProgressEntity(surahNumber = 18, lastAyah = 5, maxAyah = 5, ownerId = B))

        assertEquals(10, dao.getProgress(A, 18)!!.lastAyah)
        assertEquals(5, dao.getProgress(B, 18)!!.lastAyah)
        assertEquals(1, dao.getAllProgress(A).first().size)
        assertEquals(null, dao.getProgress(C, 18))
    }

    @Test
    fun prayerRecords_isolatedPerOwner_sameDatePrayerCoexists() = runBlocking {
        val dao = db.prayerRecordDao()
        dao.upsert(PrayerRecord(date = "2026-07-01", prayerName = "FAJR", verified = true, ownerId = A))
        dao.upsert(PrayerRecord(date = "2026-07-01", prayerName = "FAJR", verified = false, ownerId = B))

        assertEquals(1, dao.getRecordsForDate(A, "2026-07-01").size)
        assertTrue(dao.getRecord(A, "2026-07-01", "FAJR")!!.verified)
        assertFalse(dao.getRecord(B, "2026-07-01", "FAJR")!!.verified)
        assertEquals(0, dao.getRecordsForDate(C, "2026-07-01").size)
    }

    @Test
    fun collections_isolatedPerOwner() = runBlocking {
        val dao = db.collectionsDao()
        dao.insertCollection(UserCollectionEntity(name = "Fav", clientUuid = "u-a", ownerId = A))
        dao.insertCollection(UserCollectionEntity(name = "Fav", clientUuid = "u-b", ownerId = B))

        assertEquals(1, dao.observeCollections(A).first().size)
        assertEquals("u-a", dao.observeCollections(A).first().first().clientUuid)
        assertEquals(1, dao.observeCollections(B).first().size)
        assertEquals(0, dao.observeCollections(C).first().size)
    }
}
