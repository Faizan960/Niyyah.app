package com.salahlock.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salahlock.app.data.api.AladhanApiService
import com.salahlock.app.data.api.AladhanResponse
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.LocalMasjidEntity
import com.salahlock.app.data.model.PrayerSource
import com.salahlock.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class PrayerTimingSeparationTest {
    private lateinit var db: AppDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var repository: PrayerTimesRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = UserPreferences(context)
        prefs.setLocation(17.385, 78.4867, "Test City", "GPS")
        prefs.setCalcMethod("KARACHI")
        prefs.setMadhab("HANAFI")
        prefs.setPrayerSource(PrayerSource.LOCAL_MASJID.name)
        db.localMasjidDao().upsert(
            LocalMasjidEntity(
                masjidName = "Killa Masjid",
                fajr = "23:51",
                dhuhr = "23:52",
                asr = "23:55",
                maghrib = "23:57",
                isha = "23:59",
                enabled = true,
            ),
        )
        repository = PrayerTimesRepository(
            context,
            prefs,
            db.prayerTimeCacheDao(),
            object : AladhanApiService {
                override suspend fun getCalendar(
                    latitude: Double,
                    longitude: Double,
                    method: Int,
                    month: Int,
                    year: Int,
                    shafaq: String,
                    tune: String,
                    school: Int,
                ): AladhanResponse = error("Network must not be required for this test")
            },
            db.localMasjidDao(),
            Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun localJamaatSelection_doesNotOverwriteCalculatedPrayerStarts() = runBlocking {
        val actualStarts = repository.getTodayPrayers().filterNotNull().first()
        val jamaat = repository.getLocalJamaatTimes().filterNotNull().first()

        assertEquals("23:55", jamaat.asr)
        assertNotEquals(LocalTime.of(23, 55), actualStarts.asr.toLocalTime())
    }
}
