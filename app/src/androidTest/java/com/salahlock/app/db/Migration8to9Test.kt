package com.salahlock.app.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salahlock.app.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BM-013 Checkpoint B — proves the v8→v9 migration:
 *  - preserves ALL existing user + device data (no loss),
 *  - keeps static corpus (hadith/azkar text) intact and un-owned,
 *  - normalizes hadith/azkar user-state into the new owner-scoped tables,
 *  - assigns a deterministic azkar ref, stable collection UUIDs, and an unclaimed
 *    legacy-ownership ledger.
 *
 * Runs on a real device via MigrationTestHelper (runMigrationsAndValidate also
 * asserts the resulting schema matches the exported 9.json exactly).
 */
@RunWith(AndroidJUnit4::class)
class Migration8to9Test {

    private val TEST_DB = "bm013-migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate8To9_preservesData_normalizesState_addsStableIds() {
        // ── Seed a representative v8 database ──────────────────────────────
        helper.createDatabase(TEST_DB, 8).apply {
            // user-owned
            execSQL("INSERT INTO prayer_records (id,date,prayerName,verified,overrideUsed,verificationType,timestampMs) VALUES (1,'2026-07-01','FAJR',1,0,'LOCK_VERIFIED',111)")
            execSQL("INSERT INTO prayer_records (id,date,prayerName,verified,overrideUsed,verificationType,timestampMs) VALUES (2,'2026-07-01','DHUHR',0,1,'SELF_REPORTED',222)")
            execSQL("INSERT INTO streaks (id,currentStreak,bestStreak,lastFullDay,lastMercyWeek,mercyUsedThisWeek) VALUES (1,3,9,'2026-07-01','2026-W27',0)")
            execSQL("INSERT INTO emergency_overrides (id,monthYear,count,lastReason,lastUsedMs) VALUES (1,'2026-07',2,'travel',333)")
            execSQL("INSERT INTO quran_bookmarks (id,surahNumber,ayahNumber,collectionName,createdAtMs) VALUES (1,2,255,'',444)")
            execSQL("INSERT INTO quran_progress (surahNumber,lastAyah,maxAyah,timestampMs) VALUES (18,10,25,555)")
            execSQL("INSERT INTO user_collections (id,name,createdAtMs) VALUES (1,'Favourites',666)")
            execSQL("INSERT INTO collection_items (id,collectionId,contentType,contentKey,addedAtMs) VALUES (1,1,'QURAN','2:255',777)")
            // device-only (must be untouched)
            execSQL("INSERT INTO app_blacklist (id,packageName,appLabel,isBlocked) VALUES (1,'com.example.app','Example',1)")
            // static corpus + user-state flags on corpus (v8)
            execSQL("INSERT INTO hadith_table (id,collection,bookNumber,hadithNumber,arabicText,translationText,language,isBookmarked,lastReadTimestamp,bookmarkSource) VALUES ('bukhari-1-eng','bukhari','1','1','AR','TR','eng',1,888,'knowledge')")
            execSQL("INSERT INTO hadith_table (id,collection,bookNumber,hadithNumber,arabicText,translationText,language,isBookmarked,lastReadTimestamp,bookmarkSource) VALUES ('bukhari-2-eng','bukhari','1','2','AR2','TR2','eng',0,0,'hadith')")
            execSQL("INSERT INTO azkar_table (id,category,arabic,transliteration,translation,reference,targetCount,completedCount,isBookmarked,lastReadTimestamp,language) VALUES (1,'Morning','A','t','tr','ref',3,0,0,0,'eng')")
            execSQL("INSERT INTO azkar_table (id,category,arabic,transliteration,translation,reference,targetCount,completedCount,isBookmarked,lastReadTimestamp,language) VALUES (2,'Morning','B','t','tr','ref',3,0,1,0,'eng')")
            execSQL("INSERT INTO azkar_table (id,category,arabic,transliteration,translation,reference,targetCount,completedCount,isBookmarked,lastReadTimestamp,language) VALUES (3,'Evening','C','t','tr','ref',7,5,0,0,'eng')")
            close()
        }

        // ── Migrate + validate schema against 9.json ─────────────────────
        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9)

        // Preservation: prayer history + new ownership default.
        db.query("SELECT date,prayerName,verificationType,ownerId FROM prayer_records ORDER BY id").use {
            assertTrue(it.moveToFirst())
            assertEquals("2026-07-01", it.getString(0)); assertEquals("FAJR", it.getString(1))
            assertEquals("LOCK_VERIFIED", it.getString(2)); assertEquals("__local__", it.getString(3))
            assertTrue(it.moveToNext()); assertEquals("SELF_REPORTED", it.getString(2))
        }
        // Streak best preserved.
        db.query("SELECT bestStreak,ownerId FROM streaks WHERE id=1").use {
            assertTrue(it.moveToFirst()); assertEquals(9, it.getInt(0)); assertEquals("__local__", it.getString(1))
        }
        // Overrides, quran bookmark, quran progress preserved.
        db.query("SELECT count FROM emergency_overrides WHERE monthYear='2026-07'").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        db.query("SELECT surahNumber,ayahNumber FROM quran_bookmarks").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)); assertEquals(255, it.getInt(1)) }
        db.query("SELECT maxAyah FROM quran_progress WHERE surahNumber=18").use { assertTrue(it.moveToFirst()); assertEquals(25, it.getInt(0)) }

        // Device-only untouched.
        db.query("SELECT packageName,isBlocked FROM app_blacklist").use { assertTrue(it.moveToFirst()); assertEquals("com.example.app", it.getString(0)); assertEquals(1, it.getInt(1)) }

        // Collections: stable UUID backfilled, item mirrors it.
        var collUuid = ""
        db.query("SELECT clientUuid FROM user_collections WHERE id=1").use {
            assertTrue(it.moveToFirst()); collUuid = it.getString(0)
            assertNotEquals("", collUuid); assertEquals(36, collUuid.length) // UUID shape
        }
        db.query("SELECT collectionUuid FROM collection_items WHERE id=1").use {
            assertTrue(it.moveToFirst()); assertEquals(collUuid, it.getString(0))
        }

        // Static corpus preserved and NOT owner-stamped (no ownerId column on corpus).
        db.query("SELECT translationText FROM hadith_table WHERE id='bukhari-1-eng'").use { assertTrue(it.moveToFirst()); assertEquals("TR", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM azkar_table").use { assertTrue(it.moveToFirst()); assertEquals(3, it.getInt(0)) }

        // Deterministic azkar refs on corpus.
        db.query("SELECT azkarRef FROM azkar_table ORDER BY id").use {
            assertTrue(it.moveToFirst()); assertEquals("azkar:v1:morning:0", it.getString(0))
            assertTrue(it.moveToNext()); assertEquals("azkar:v1:morning:1", it.getString(0))
            assertTrue(it.moveToNext()); assertEquals("azkar:v1:evening:0", it.getString(0))
        }

        // Hadith user-state normalized off corpus (only the bookmarked/read one).
        db.query("SELECT ownerId,hadithId,isBookmarked,bookmarkSource FROM hadith_user_state").use {
            assertTrue(it.moveToFirst()); assertEquals("__local__", it.getString(0))
            assertEquals("bukhari-1-eng", it.getString(1)); assertEquals(1, it.getInt(2)); assertEquals("knowledge", it.getString(3))
            assertTrue("only the bookmarked/read hadith migrates", !it.moveToNext())
        }
        // Azkar user-state normalized off corpus, keyed by stable ref.
        db.query("SELECT azkarRef,isBookmarked,completedCount FROM azkar_user_state ORDER BY azkarRef").use {
            assertTrue(it.moveToFirst())
            assertEquals("azkar:v1:evening:0", it.getString(0)); assertEquals(0, it.getInt(1)); assertEquals(5, it.getInt(2))
            assertTrue(it.moveToNext())
            assertEquals("azkar:v1:morning:1", it.getString(0)); assertEquals(1, it.getInt(1)); assertEquals(0, it.getInt(2))
            assertTrue("only bookmarked/in-progress azkar migrate", !it.moveToNext())
        }

        // Legacy-ownership ledger created, unclaimed.
        db.query("SELECT adoptedBy FROM legacy_ownership WHERE id=1").use {
            assertTrue(it.moveToFirst()); assertNull(it.getString(0))
        }
        db.close()
    }
}
