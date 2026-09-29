package com.salahlock.app.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salahlock.app.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * fix/local-persistence-auth — proves the v10→v11 migration folds device-local prayer
 * tracking (prayer_records, streaks, emergency_overrides) that earlier builds adopted to
 * a Clerk userId or orphaned under '__none__' back to '__local__', so existing installs
 * recover their prayer data. Full history is preserved; the best streak is never lost;
 * natural-key collisions collapse to a single row.
 */
@RunWith(AndroidJUnit4::class)
class Migration10to11Test {

    private val TEST_DB = "local-persistence-migration-10-11-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate10To11_foldsPrayerDataBackToLocal_preservesHistory_keepsBestStreak() {
        helper.createDatabase(TEST_DB, 10).apply {
            // prayer_records: adopted history (user_A), a signed-out orphan (__none__),
            // a genuinely-local row, and a (date,prayer) collision across owners.
            fun pr(id: Int, date: String, prayer: String, owner: String) = execSQL(
                "INSERT INTO prayer_records (id,date,prayerName,verified,overrideUsed,verificationType,timestampMs,ownerId,updatedAt) " +
                    "VALUES ($id,'$date','$prayer',1,0,'LOCK_VERIFIED',100,'$owner',0)"
            )
            pr(1, "2026-07-01", "FAJR", "user_A")
            pr(2, "2026-07-01", "DHUHR", "user_A")
            pr(3, "2026-07-02", "FAJR", "__none__")
            pr(4, "2026-07-03", "FAJR", "__local__")
            pr(10, "2026-07-05", "ASR", "user_A")   // collides with the __none__ ASR below
            pr(11, "2026-07-05", "ASR", "__none__")

            // streaks: the real (adopted) streak has the best achievement; a signed-out
            // fragment under __none__ must not win and erase it.
            execSQL(
                "INSERT INTO streaks (id,currentStreak,bestStreak,lastFullDay,lastMercyWeek,mercyUsedThisWeek,ownerId,updatedAt) " +
                    "VALUES (1,10,30,'2026-07-01','',0,'user_A',0)"
            )
            execSQL(
                "INSERT INTO streaks (id,currentStreak,bestStreak,lastFullDay,lastMercyWeek,mercyUsedThisWeek,ownerId,updatedAt) " +
                    "VALUES (1,3,3,'2026-07-02','',0,'__none__',0)"
            )

            // emergency_overrides: two different months across owners — both must survive.
            execSQL(
                "INSERT INTO emergency_overrides (id,monthYear,count,lastReason,lastUsedMs,ownerId,updatedAt) " +
                    "VALUES (1,'2026-07',2,'travel',100,'user_A',0)"
            )
            execSQL(
                "INSERT INTO emergency_overrides (id,monthYear,count,lastReason,lastUsedMs,ownerId,updatedAt) " +
                    "VALUES (2,'2026-08',1,'illness',100,'__none__',0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.MIGRATION_10_11)

        // Every prayer row is now device-local; full history preserved, collision collapsed.
        db.query("SELECT COUNT(*) FROM prayer_records WHERE ownerId = '__local__'").use { c ->
            c.moveToFirst(); assertEquals(5, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM prayer_records WHERE ownerId <> '__local__'").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        // The adopted history is recovered (readable under __local__ again).
        db.query("SELECT verified FROM prayer_records WHERE date='2026-07-01' AND prayerName='FAJR' AND ownerId='__local__'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        // The cross-owner (2026-07-05, ASR) collision collapsed to exactly one local row.
        db.query("SELECT COUNT(*) FROM prayer_records WHERE date='2026-07-05' AND prayerName='ASR'").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }

        // Streak: single local row carrying the best achievement (not the __none__ fragment).
        db.query("SELECT COUNT(*) FROM streaks").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT ownerId, currentStreak, bestStreak FROM streaks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("__local__", c.getString(0))
            assertEquals(10, c.getInt(1))
            assertEquals(30, c.getInt(2))
        }

        // Overrides: both months folded to local, none left under another owner.
        db.query("SELECT COUNT(*) FROM emergency_overrides WHERE ownerId = '__local__'").use { c ->
            c.moveToFirst(); assertEquals(2, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM emergency_overrides WHERE ownerId <> '__local__'").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
    }
}
