package com.salahlock.app.spiritual

import com.salahlock.app.data.model.MonthlyStats
import com.salahlock.app.data.model.PrayerName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class MotivationEngineTest {

    private fun stats(
        completed: Int,
        expected: Int = 150,
        perPrayerMissed: Map<PrayerName, Int> = emptyMap(),
    ) = MonthlyStats(
        month = YearMonth.of(2026, 6),
        elapsedDays = 30,
        expectedPrayers = expected,
        completedPrayers = completed,
        missedPrayers = expected - completed,
        lockVerified = 0,
        selfReported = completed,
        fajrDays = 0,
        longestStreakInMonth = 0,
        endOfMonthStreak = 0,
        mercyUsed = 0,
        perPrayerCompletion = emptyMap(),
        perPrayerMissed = perPrayerMissed,
    )

    @Test
    fun `excellent month praises with Quran 5-48`() {
        val m = MotivationEngine.generate(stats(completed = 149), previousCompletionPercent = 95)
        assertEquals("MashaAllah.", m.headline)
        assertEquals("Quran 5:48", m.reference)
    }

    @Test
    fun `improved month cites Quran 29-6 with the delta`() {
        // 120/150 = 80%, prev 68% → +12
        val m = MotivationEngine.generate(stats(completed = 120), previousCompletionPercent = 68)
        assertEquals("Alhamdulillah.", m.headline)
        assertEquals("You improved your prayer consistency by 12%.", m.body)
        assertEquals("Quran 29:6", m.reference)
    }

    @Test
    fun `difficult month comforts with Quran 94-6`() {
        val m = MotivationEngine.generate(stats(completed = 40), previousCompletionPercent = null)
        assertEquals("This month was difficult.", m.headline)
        assertEquals("Quran 94:6", m.reference)
    }

    @Test
    fun `sharp decline is treated as difficult even at moderate completion`() {
        // 90/150 = 60%, prev 75% → −15
        val m = MotivationEngine.generate(stats(completed = 90), previousCompletionPercent = 75)
        assertEquals("Quran 94:6", m.reference)
    }

    @Test
    fun `steady month gets the consistency hadith`() {
        val m = MotivationEngine.generate(stats(completed = 100), previousCompletionPercent = 66)
        assertEquals("Bukhari & Muslim", m.reference)
    }

    @Test
    fun `same stats always produce the same message`() {
        val a = MotivationEngine.generate(stats(completed = 120), 68)
        val b = MotivationEngine.generate(stats(completed = 120), 68)
        assertEquals(a, b)
    }

    @Test
    fun `growth opportunity is the most missed prayer`() {
        val s = stats(
            completed = 130,
            perPrayerMissed = mapOf(
                PrayerName.FAJR to 14, PrayerName.DHUHR to 2, PrayerName.ASR to 1,
                PrayerName.MAGHRIB to 0, PrayerName.ISHA to 3,
            ),
        )
        assertEquals(PrayerName.FAJR, MotivationEngine.growthOpportunity(s))
    }

    @Test
    fun `no growth opportunity when nothing was missed`() {
        val s = stats(
            completed = 150,
            perPrayerMissed = MonthlyStatsCalculator.DAILY_PRAYERS.associateWith { 0 },
        )
        assertNull(MotivationEngine.growthOpportunity(s))
    }
}
