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

    @Test
    fun calculatedCurrentPrayer_isNeverLabelledAsJamaat() {
        assertEquals(
            "Surat · Calculated start",
            heroTimingLabel(
                kind = HeroTimingKind.CALCULATED_START,
                cityName = "Surat",
                masjidName = "Modern Masjid",
            ),
        )
    }

    @Test
    fun savedCongregationTime_isLabelledWithMasjid() {
        assertEquals(
            "Modern Masjid · Jamaat",
            heroTimingLabel(
                kind = HeroTimingKind.JAMAAT,
                cityName = "Surat",
                masjidName = "Modern Masjid",
            ),
        )
    }

    @Test
    fun singleDigitSavedHour_remainsAValidJamaatCandidate() {
        val today = LocalDate.of(2026, 11, 15)
        val next = nextJamaatPrayer(
            masjid.copy(fajr = "6:00"),
            today.atTime(5, 30),
        )

        assertEquals(PrayerName.FAJR, next?.name)
        assertEquals(LocalTime.of(6, 0), next?.time?.toLocalTime())
    }

    @Test
    fun shortlyAfterMidnight_selectsTodaysEarlyIshaBeforeFajr() {
        val today = LocalDate.of(2026, 11, 15)
        val next = nextJamaatPrayer(
            masjid.copy(isha = "00:30"),
            today.atTime(0, 10),
        )

        assertEquals(PrayerName.ISHA, next?.name)
        assertEquals(today, next?.time?.toLocalDate())
        assertEquals(LocalTime.of(0, 30), next?.time?.toLocalTime())
    }

    @Test
    fun lateAtNight_includesTomorrowsEarlyIshaCandidate() {
        val today = LocalDate.of(2026, 11, 15)
        val next = nextJamaatPrayer(
            masjid.copy(isha = "00:30"),
            today.atTime(23, 0),
        )

        assertEquals(PrayerName.ISHA, next?.name)
        assertEquals(today.plusDays(1), next?.time?.toLocalDate())
        assertEquals(LocalTime.of(0, 30), next?.time?.toLocalTime())
    }
}
