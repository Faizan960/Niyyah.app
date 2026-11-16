package com.salahlock.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyPrayersBoundaryTest {
    private val day = LocalDate.of(2026, 11, 15)
    private val prayers = DailyPrayers(
        date = day.toString(),
        fajr = day.atTime(5, 0),
        sunrise = day.atTime(6, 15),
        dhuhr = day.atTime(12, 30),
        asr = day.atTime(15, 26),
        maghrib = day.atTime(18, 0),
        isha = day.atTime(19, 30),
    )

    @Test
    fun exactBoundary_movesCurrentPrayerToNewPrayer() {
        assertEquals(PrayerName.DHUHR, prayers.currentPrayer(day.atTime(15, 25, 59))?.name)
        assertEquals(PrayerName.ASR, prayers.currentPrayer(day.atTime(15, 26))?.name)
        assertEquals(PrayerName.MAGHRIB, prayers.nextPrayer(day.atTime(15, 26))?.name)
    }

    @Test
    fun prayerWindow_isStartInclusiveAndNextStartExclusive() {
        assertTrue(prayers.isWithinWindow(PrayerName.ASR, day.atTime(15, 26)))
        assertTrue(prayers.isWithinWindow(PrayerName.ASR, day.atTime(17, 59, 59)))
        assertFalse(prayers.isWithinWindow(PrayerName.ASR, day.atTime(18, 0)))
    }

    @Test
    fun afterIsha_usesTomorrowFajrAcrossMidnight() {
        val tomorrowFajr = day.plusDays(1).atTime(5, 1)
        assertNull(prayers.nextPrayer(day.atTime(23, 59)))
        assertEquals(
            tomorrowFajr,
            prayers.nextPrayerWithTomorrow(day.atTime(23, 59), tomorrowFajr)?.time,
        )
    }
}
