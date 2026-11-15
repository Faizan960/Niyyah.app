package com.salahlock.app.ui.home

import com.salahlock.app.data.db.entity.LocalMasjidEntity
import com.salahlock.app.data.model.PrayerName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class JamaatDisplayTimingTest {
    private val masjid = LocalMasjidEntity(
        masjidName = "Modern Masjid",
        fajr = "06:00",
        dhuhr = "13:00",
        asr = "17:00",
        maghrib = "18:30",
        isha = "20:00",
    )

    @Test
    fun afterIsha_heroDisplaysTomorrowFajrJamaatAtConfiguredTime() {
        val today = LocalDate.of(2026, 11, 15)
        val next = nextJamaatPrayer(masjid, today.atTime(22, 19))

        assertEquals(PrayerName.FAJR, next?.name)
        assertEquals(today.plusDays(1), next?.time?.toLocalDate())
        assertEquals(LocalTime.of(6, 0), next?.time?.toLocalTime())
    }

    @Test
    fun beforeAsr_heroDisplaysTodaysConfiguredAsrJamaat() {
        val today = LocalDate.of(2026, 11, 15)
        val next = nextJamaatPrayer(masjid, today.atTime(16, 30))

        assertEquals(PrayerName.ASR, next?.name)
        assertEquals(LocalTime.of(17, 0), next?.time?.toLocalTime())
    }
}
