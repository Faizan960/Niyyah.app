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
import java.time.Instant

/** Proves v12 monthly aggregates become append-only events without losing represented uses. */
@RunWith(AndroidJUnit4::class)
class Migration12to13Test {

    private val testDb = "emergency-events-migration-12-13-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrationPreservesEveryHistoricalUseAndExactPrayerRows() {
        val novemberTimestamp = Instant.parse("2026-11-20T12:00:00Z").toEpochMilli()
        val decemberTimestamp = Instant.parse("2026-12-02T12:00:00Z").toEpochMilli()
        helper.createDatabase(testDb, 12).apply {
            execSQL(
                "INSERT INTO prayer_records " +
                    "(id,date,prayerName,verified,overrideUsed,verificationType,timestampMs,ownerId,updatedAt) " +
                    "VALUES (1,'2026-11-01','FAJR',0,1,'SELF_REPORTED',100,'__local__',0)"
            )
            execSQL(
                "INSERT INTO prayer_records " +
                    "(id,date,prayerName,verified,overrideUsed,verificationType,timestampMs,ownerId,updatedAt) " +
                    "VALUES (2,'2026-11-02','ISHA',0,1,'SELF_REPORTED',200,'__local__',0)"
            )
            execSQL(
                "INSERT INTO emergency_overrides (id,monthYear,count,lastReason,lastUsedMs,ownerId,updatedAt) " +
                    "VALUES (10,'2026-11',3,'MEDICAL',$novemberTimestamp,'__local__',0)"
            )
            execSQL(
                "INSERT INTO emergency_overrides (id,monthYear,count,lastReason,lastUsedMs,ownerId,updatedAt) " +
                    "VALUES (11,'2026-12',1,'FAMILY_EMERGENCY',$decemberTimestamp,'__local__',0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 13, true, AppDatabase.MIGRATION_12_13)

        db.query("SELECT eventId FROM emergency_override_events").use { cursor ->
            val ids = mutableSetOf<String>()
            while (cursor.moveToNext()) ids += cursor.getString(0)
            assertEquals(4, ids.size)
        }
        db.query("SELECT COUNT(*) FROM emergency_override_events WHERE prayerIdentifier <> 'LEGACY_UNKNOWN'").use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM emergency_override_events WHERE reason = 'MEDICAL'").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getInt(0) >= 1)
        }
        db.query("SELECT COUNT(*) FROM prayer_records WHERE overrideUsed = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='emergency_overrides'").use {
            assertFalse(it.moveToFirst())
        }
        db.close()
    }
}
