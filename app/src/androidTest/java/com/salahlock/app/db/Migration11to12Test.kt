package com.salahlock.app.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salahlock.app.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * chore/remove-free-passes — proves the v11→v12 migration drops the removed weekly
 * free-pass ("mercy") columns from `streaks` while PRESERVING every surviving
 * streak value: current streak, best streak, last full day, and ownership are
 * copied through the table rebuild untouched.
 */
@RunWith(AndroidJUnit4::class)
class Migration11to12Test {

    private val TEST_DB = "remove-free-passes-migration-11-12-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate11To12_dropsMercyColumns_preservesStreakValues() {
        helper.createDatabase(TEST_DB, 11).apply {
            execSQL(
                "INSERT INTO streaks (id,currentStreak,bestStreak,lastFullDay,lastMercyWeek,mercyUsedThisWeek,ownerId,updatedAt) " +
                    "VALUES (1,10,30,'2026-10-27','2026-W40',1,'__local__',123)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, AppDatabase.MIGRATION_11_12)

        // Columns are gone.
        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(streaks)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) columns.add(c.getString(nameIdx))
        }
        assertFalse(columns.contains("lastMercyWeek"))
        assertFalse(columns.contains("mercyUsedThisWeek"))

        // Data survived the rebuild.
        db.query(
            "SELECT id, currentStreak, bestStreak, lastFullDay, ownerId, updatedAt FROM streaks"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals(10, c.getInt(1))
            assertEquals(30, c.getInt(2))
            assertEquals("2026-10-27", c.getString(3))
            assertEquals("__local__", c.getString(4))
            assertEquals(123L, c.getLong(5))
            assertFalse(c.moveToNext())
        }
        db.close()
    }
}
