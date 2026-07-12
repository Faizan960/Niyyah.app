package com.salahlock.app.spiritual

import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.VerificationType
import com.salahlock.app.data.model.PrayerName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlyStatsCalculatorTest {

    private val june = YearMonth.of(2026, 6)
    private val july1 = LocalDate.of(2026, 7, 1)

    private fun record(day: Int, prayer: PrayerName, type: VerificationType = VerificationType.LOCK_VERIFIED) =
        PrayerRecord(
            date = june.atDay(day).toString(),
            prayerName = prayer.name,
            verified = true,
            verificationType = type.name,
        )

    @Test
    fun `closed month uses full month length`() {
        val stats = MonthlyStatsCalculator.calculate(june, emptyList(), 0, today = july1)
        assertEquals(30, stats.elapsedDays)
        assertEquals(150, stats.expectedPrayers)
        assertEquals(150, stats.missedPrayers)
        assertEquals(0, stats.completionPercent)
    }

    @Test
    fun `current month caps elapsed days at today`() {
        val stats = MonthlyStatsCalculator.calculate(june, emptyList(), 0, today = june.atDay(10))
        assertEquals(10, stats.elapsedDays)
        assertEquals(50, stats.expectedPrayers)
    }

    @Test
    fun `counts completion, fajr days and verification split`() {
        val records =
            (1..20).map { record(it, PrayerName.FAJR) } +
                (1..30).map { record(it, PrayerName.DHUHR, VerificationType.SELF_REPORTED) }
        val stats = MonthlyStatsCalculator.calculate(june, records, mercyUsed = 2, today = july1)
        assertEquals(50, stats.completedPrayers)
        assertEquals(20, stats.fajrDays)
        assertEquals(20, stats.lockVerified)
        assertEquals(30, stats.selfReported)
        assertEquals(2, stats.mercyUsed)
        assertEquals(10, stats.perPrayerMissed[PrayerName.FAJR])
        assertEquals(0, stats.perPrayerMissed[PrayerName.DHUHR])
        assertEquals(30, stats.perPrayerMissed[PrayerName.ISHA])
    }

    @Test
    fun `in-month streaks track perfect days only`() {
        // Days 1-4 perfect, day 5 missing Isha, days 6-30 perfect.
        val records = (1..30).flatMap { day ->
            MonthlyStatsCalculator.DAILY_PRAYERS
                .filterNot { day == 5 && it == PrayerName.ISHA }
                .map { record(day, it) }
        }
        val stats = MonthlyStatsCalculator.calculate(june, records, 0, today = july1)
        assertEquals(25, stats.longestStreakInMonth)
        assertEquals(25, stats.endOfMonthStreak)
    }

    @Test
    fun `records outside the month and duplicates are ignored`() {
        val outside = PrayerRecord(
            date = "2026-05-31", prayerName = PrayerName.FAJR.name, verified = true,
        )
        val dupes = listOf(record(1, PrayerName.FAJR), record(1, PrayerName.FAJR))
        val stats = MonthlyStatsCalculator.calculate(june, dupes + outside, 0, today = july1)
        assertEquals(1, stats.completedPrayers)
    }

    @Test
    fun `sunrise records never count toward the five dailies`() {
        val sunrise = PrayerRecord(
            date = june.atDay(1).toString(), prayerName = PrayerName.SUNRISE.name, verified = true,
        )
        val stats = MonthlyStatsCalculator.calculate(june, listOf(sunrise), 0, today = july1)
        assertEquals(0, stats.completedPrayers)
    }
}
