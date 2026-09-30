package com.salahlock.app.emergency

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.OwnerIds
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.data.repository.EmergencyOverrideRepository
import com.salahlock.app.data.repository.MAX_OVERRIDES_PER_MONTH
import com.salahlock.app.data.repository.OverrideReason
import com.salahlock.app.data.repository.OverrideResult
import com.salahlock.app.data.repository.StreakRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * fix/emergency-feature — behavioural contract for the repaired emergency-override flow.
 * All emergency data is device-local ([OwnerIds.LOCAL]); these tests use a real in-memory
 * Room DB so the transaction, the (date, prayer) idempotency key, and the monthly limit
 * are exercised against the actual DAOs — not mocks.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyOverrideRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var streakRepo: StreakRepository
    private lateinit var repo: EmergencyOverrideRepository
    private val today = LocalDate.now().toString()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        streakRepo = StreakRepository(db.prayerRecordDao(), db.streakDao())
        repo = EmergencyOverrideRepository(db, db.emergencyOverrideDao(), db.prayerRecordDao(), streakRepo)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun override_consumesOne_marksTargetPrayer_andCounts() = runBlocking {
        val result = repo.useOverrideForPrayer(today, PrayerName.FAJR, OverrideReason.MEDICAL)

        assertTrue("first use is consumed", result is OverrideResult.Consumed)
        assertEquals(1, repo.getOverrideState().usedThisMonth)

        // The exact target prayer is marked overrideUsed (and NOT falsely verified).
        val fajr = db.prayerRecordDao().getRecord(OwnerIds.LOCAL, today, "FAJR")!!
        assertTrue("target prayer overrideUsed", fajr.overrideUsed)
        assertFalse("override is not a normal verification", fajr.verified)
    }

    @Test
    fun override_appliesOnlyToTargetPrayer() = runBlocking {
        repo.useOverrideForPrayer(today, PrayerName.ISHA, OverrideReason.TRAVELLING)

        assertNull("FAJR untouched", db.prayerRecordDao().getRecord(OwnerIds.LOCAL, today, "FAJR"))
        assertNull("DHUHR untouched", db.prayerRecordDao().getRecord(OwnerIds.LOCAL, today, "DHUHR"))
        assertNull("ASR untouched", db.prayerRecordDao().getRecord(OwnerIds.LOCAL, today, "ASR"))
        assertNull("MAGHRIB untouched", db.prayerRecordDao().getRecord(OwnerIds.LOCAL, today, "MAGHRIB"))
        assertEquals(1, db.prayerRecordDao().getAll(OwnerIds.LOCAL).size)
    }

    @Test
    fun override_isIdempotent_forSamePrayer() = runBlocking {
        assertTrue(repo.useOverrideForPrayer(today, PrayerName.FAJR, OverrideReason.OTHER) is OverrideResult.Consumed)
        // A second attempt on the SAME prayer must not consume another allowance.
        val again = repo.useOverrideForPrayer(today, PrayerName.FAJR, OverrideReason.OTHER)
        assertTrue("second attempt already handled", again is OverrideResult.AlreadyHandled)
        assertEquals("still only one consumed", 1, repo.getOverrideState().usedThisMonth)
        assertEquals("no duplicate record", 1, db.prayerRecordDao().getAll(OwnerIds.LOCAL).size)
    }

    @Test
    fun override_doesNotDowngradeAlreadyVerifiedPrayer() = runBlocking {
        streakRepo.recordVerification(today, PrayerName.FAJR, wasOverride = false)
        val result = repo.useOverrideForPrayer(today, PrayerName.FAJR, OverrideReason.OTHER)

        assertTrue("already verified → handled, not consumed", result is OverrideResult.AlreadyHandled)
        assertEquals("no override charged", 0, repo.getOverrideState().usedThisMonth)
        val fajr = db.prayerRecordDao().getRecord(OwnerIds.LOCAL, today, "FAJR")!!
        assertTrue("verification preserved", fajr.verified)
        assertFalse("not flipped to override", fajr.overrideUsed)
    }

    @Test
    fun override_stopsAtMonthlyLimit_andWritesNothingWhenExhausted() = runBlocking {
        // Exhaust the 3/month allowance across three distinct prayers.
        listOf(PrayerName.FAJR, PrayerName.DHUHR, PrayerName.ASR).forEach {
            assertTrue(repo.useOverrideForPrayer(today, it, OverrideReason.OTHER) is OverrideResult.Consumed)
        }
        assertEquals(MAX_OVERRIDES_PER_MONTH, repo.getOverrideState().usedThisMonth)
        assertFalse(repo.getOverrideState().canOverride)

        // The 4th prayer must be rejected with NO database mutation.
        val denied = repo.useOverrideForPrayer(today, PrayerName.MAGHRIB, OverrideReason.OTHER)
        assertTrue("limit reached", denied is OverrideResult.NoAllowance)
        assertEquals("count unchanged", MAX_OVERRIDES_PER_MONTH, repo.getOverrideState().usedThisMonth)
        assertNull("no record for the denied prayer", db.prayerRecordDao().getRecord(OwnerIds.LOCAL, today, "MAGHRIB"))
    }

    @Test
    fun override_countsTowardStreakCompletion() = runBlocking {
        // 4 normal verifications + 1 emergency override = a full 5/5 day → streak of 1.
        listOf(PrayerName.FAJR, PrayerName.DHUHR, PrayerName.ASR, PrayerName.MAGHRIB).forEach {
            streakRepo.recordVerification(today, it, wasOverride = false)
        }
        repo.useOverrideForPrayer(today, PrayerName.ISHA, OverrideReason.FAMILY_EMERGENCY)

        assertEquals(5, db.prayerRecordDao().getAll(OwnerIds.LOCAL).size)
        assertEquals("override completes the day", 1, streakRepo.observeStreakInfo().first().currentStreak)
    }
}
