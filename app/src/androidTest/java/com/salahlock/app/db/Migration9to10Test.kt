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
 * BM-013 Checkpoint C — proves the v9→v10 migration adds sync_outbox + sync_state
 * (validated against the exported 10.json) WITHOUT touching any existing user data.
 */
@RunWith(AndroidJUnit4::class)
class Migration9to10Test {

    private val TEST_DB = "bm013-migration-9-10-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate9To10_addsSyncTables_preservesUserData() {
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL(
                "INSERT INTO quran_bookmarks (id,surahNumber,ayahNumber,collectionName,createdAtMs,ownerId,updatedAt) " +
                    "VALUES (1,2,255,'',444,'__local__',0)"
            )
            execSQL("INSERT INTO legacy_ownership (id,adoptedBy,adoptedAtMs) VALUES (1,NULL,0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_10)

        // Existing user data untouched.
        db.query("SELECT surahNumber, ayahNumber, ownerId FROM quran_bookmarks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
            assertEquals(255, c.getInt(1))
            assertEquals("__local__", c.getString(2))
        }
        // New tables exist and start empty.
        db.query("SELECT COUNT(*) FROM sync_outbox").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM sync_state").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }
}
