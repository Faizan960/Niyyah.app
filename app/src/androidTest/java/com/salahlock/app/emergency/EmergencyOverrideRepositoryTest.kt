package com.salahlock.app.emergency

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.OwnerIds
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.data.repository.EmergencyOverrideRepository
import com.salahlock.app.data.repository.OverrideReason
import com.salahlock.app.data.repository.OverrideResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Contract tests for unlimited, append-only, local-only emergency history. */
@RunWith(AndroidJUnit4::class)
class EmergencyOverrideRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: EmergencyOverrideRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = EmergencyOverrideRepository(db.emergencyOverrideDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun moreThanThreeOverridesAreAcceptedAndRemainIndependent() = runBlocking {
        val results = (1..6).map {
            repo.useOverrideForPrayer("2026-11-19", PrayerName.FAJR, OverrideReason.OTHER)
        }

        assertTrue(results.all { it is OverrideResult.Recorded })
        val events = db.emergencyOverrideDao().getAll(OwnerIds.LOCAL)
        assertEquals(6, events.size)
        assertEquals(6, events.map { it.eventId }.toSet().size)
        events.forEach { assertEquals(it, db.emergencyOverrideDao().getById(it.eventId)) }
    }

    @Test
    fun overridesRemainUnlimitedAcrossMonthBoundaries() = runBlocking {
        repeat(4) {
            repo.useOverrideForPrayer("2026-11-30", PrayerName.ISHA, OverrideReason.TRAVELLING)
        }
        repeat(4) {
            repo.useOverrideForPrayer("2026-12-01", PrayerName.ISHA, OverrideReason.TRAVELLING)
        }

        val events = db.emergencyOverrideDao().getAll(OwnerIds.LOCAL)
        assertEquals(8, events.size)
        assertEquals(4, events.count { it.date == "2026-11-30" })
        assertEquals(4, events.count { it.date == "2026-12-01" })
    }

    @Test
    fun everySupportedReasonIsPersisted() = runBlocking {
        OverrideReason.entries.forEach { reason ->
            repo.useOverrideForPrayer("2026-11-19", PrayerName.ASR, reason)
        }

        val persisted = db.emergencyOverrideDao().getAll(OwnerIds.LOCAL).map { it.reason }.toSet()
        assertEquals(OverrideReason.entries.map { it.name }.toSet(), persisted)
    }

    @Test
    fun overrideIsLocalOnlyAndDoesNotCreateSyncOutboxWork() = runBlocking {
        val result = repo.useOverrideForPrayer(
            "2026-11-19", PrayerName.DHUHR, OverrideReason.FAMILY_EMERGENCY,
        ) as OverrideResult.Recorded

        val event = db.emergencyOverrideDao().getById(result.eventId)!!
        assertEquals(OwnerIds.LOCAL, event.ownerId)
        assertEquals(0, db.emergencyOverrideDao().getAll("clerk_user").size)
        assertEquals(0, db.syncOutboxDao().countForOwner(OwnerIds.LOCAL))
    }

    @Test
    fun overrideNeverMarksPrayerOrStreakAsCompleted() = runBlocking {
        repo.useOverrideForPrayer("2026-11-19", PrayerName.MAGHRIB, OverrideReason.MEDICAL)

        assertNull(db.prayerRecordDao().getRecord(OwnerIds.LOCAL, "2026-11-19", PrayerName.MAGHRIB.name))
        assertNull(db.streakDao().getStreak(OwnerIds.LOCAL))
    }

    @Test
    fun existingVerifiedPrayerIsNeverDowngraded() = runBlocking {
        val streakRepo = com.salahlock.app.data.repository.StreakRepository(
            db.prayerRecordDao(), db.streakDao(),
        )
        streakRepo.recordVerification("2026-11-19", PrayerName.FAJR)

        repo.useOverrideForPrayer("2026-11-19", PrayerName.FAJR, OverrideReason.OTHER)

        val record = db.prayerRecordDao().getRecord(OwnerIds.LOCAL, "2026-11-19", PrayerName.FAJR.name)!!
        assertTrue(record.verified)
        assertFalse(record.overrideUsed)
        assertEquals(1, db.emergencyOverrideDao().getAll(OwnerIds.LOCAL).size)
    }

    @Test
    fun recordsSurviveDatabaseCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "override-process-death-${System.nanoTime()}.db"
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        val firstRepo = EmergencyOverrideRepository(first.emergencyOverrideDao())
        val result = firstRepo.useOverrideForPrayer(
            "2026-11-19", PrayerName.ISHA, OverrideReason.WORK_CRITICAL,
        ) as OverrideResult.Recorded
        first.close()

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            val event = reopened.emergencyOverrideDao().getById(result.eventId)
            assertEquals(PrayerName.ISHA.name, event?.prayerIdentifier)
            assertEquals(OverrideReason.WORK_CRITICAL.name, event?.reason)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }
}
