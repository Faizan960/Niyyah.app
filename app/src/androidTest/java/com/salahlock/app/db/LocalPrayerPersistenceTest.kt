package com.salahlock.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.OwnerIds
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.data.repository.EmergencyOverrideRepository
import com.salahlock.app.data.repository.OverrideReason
import com.salahlock.app.data.repository.OverrideResult
import com.salahlock.app.data.repository.StreakRepository
import com.salahlock.app.data.sync.ActiveOwnerProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * fix/local-persistence-auth — the core invariant test: device-local prayer tracking is
 * recorded and read under [OwnerIds.LOCAL] REGARDLESS of the Clerk-driven auth scope, and
 * survives every auth-state change. Drives [ActiveOwnerProvider.shared] through
 * Authenticated → SignedOutNoUser (the states that used to hide/orphan prayer data) and
 * proves [StreakRepository] / [EmergencyOverrideRepository] are unaffected.
 */
@RunWith(AndroidJUnit4::class)
class LocalPrayerPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var streakRepo: StreakRepository
    private lateinit var overrideRepo: EmergencyOverrideRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        streakRepo = StreakRepository(db.prayerRecordDao(), db.streakDao())
        overrideRepo = EmergencyOverrideRepository(
            db, db.emergencyOverrideDao(), db.prayerRecordDao(), streakRepo,
        )
    }

    @After
    fun tearDown() {
        db.close()
        // Restore the process-wide scope so this test can't bleed into others.
        ActiveOwnerProvider.shared.onSignedOut(legacyEverAdopted = false) // → LegacyUnclaimed
    }

    @Test
    fun prayerData_recordsToLocal_andSurvivesSignInThenSignOut() = runBlocking {
        val today = LocalDate.now().toString()
        val clerkUser = "clerk_user_1"

        // 1) User is SIGNED IN (scope resolves to the Clerk user id).
        ActiveOwnerProvider.shared.onAuthenticated(clerkUser)

        // Record 4 prayers normally + the 5th via an emergency override → a full 5/5 day.
        listOf(PrayerName.FAJR, PrayerName.DHUHR, PrayerName.ASR, PrayerName.MAGHRIB)
            .forEach { streakRepo.recordVerification(today, it, wasOverride = false) }
        val overrideResult = overrideRepo.useOverrideForPrayer(today, PrayerName.ISHA, OverrideReason.TRAVELLING)
        assertTrue("override consumed", overrideResult is OverrideResult.Consumed)

        // Data lands under __local__, NEVER under the signed-in Clerk owner.
        assertEquals(5, db.prayerRecordDao().getAll(OwnerIds.LOCAL).size)
        assertEquals(0, db.prayerRecordDao().getAll(clerkUser).size)
        assertNull("streak is not stored under the Clerk owner", db.streakDao().getStreak(clerkUser))
        assertEquals(1, streakRepo.observeStreakInfo().first().currentStreak)
        assertEquals(1, overrideRepo.getOverrideState().usedThisMonth)

        // 2) User SIGNS OUT after adoption (scope → SignedOutNoUser = "__none__").
        //    This is the exact state that previously hid all prayer data.
        ActiveOwnerProvider.shared.onSignedOut(legacyEverAdopted = true)
        assertEquals("__none__", ActiveOwnerProvider.shared.ownerId())

        // Everything is still there and still readable through the repositories.
        assertEquals(5, db.prayerRecordDao().getAll(OwnerIds.LOCAL).size)
        assertEquals("streak survives logout", 1, streakRepo.observeStreakInfo().first().currentStreak)
        assertEquals("override survives logout", 1, overrideRepo.getOverrideState().usedThisMonth)

        // 3) A brand-new verification while signed OUT still records to __local__.
        streakRepo.recordVerification(today, PrayerName.FAJR, wasOverride = false) // idempotent upsert
        assertEquals(5, db.prayerRecordDao().getAll(OwnerIds.LOCAL).size)
        assertEquals(0, db.prayerRecordDao().getAll(OwnerIds.NONE).size)
    }
}
