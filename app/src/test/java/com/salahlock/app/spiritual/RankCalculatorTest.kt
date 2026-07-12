package com.salahlock.app.spiritual

import com.salahlock.app.data.model.MonthlyRank
import com.salahlock.app.data.model.MonthlyStats
import com.salahlock.app.data.model.PrayerName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class RankCalculatorTest {

    private fun stats(
        completed: Int,
        expected: Int = 150,
        elapsedDays: Int = 30,
        fajrDays: Int = 0,
        longestStreak: Int = 0,
        endStreak: Int = 0,
        lockVerified: Int = 0,
        mercy: Int = 0,
        perPrayer: Map<PrayerName, Double> = MonthlyStatsCalculator.DAILY_PRAYERS.associateWith { 0.0 },
    ) = MonthlyStats(
        month = YearMonth.of(2026, 6),
        elapsedDays = elapsedDays,
        expectedPrayers = expected,
        completedPrayers = completed,
        missedPrayers = expected - completed,
        lockVerified = lockVerified,
        selfReported = completed - lockVerified,
        fajrDays = fajrDays,
        longestStreakInMonth = longestStreak,
        endOfMonthStreak = endStreak,
        mercyUsed = mercy,
        perPrayerCompletion = perPrayer,
        perPrayerMissed = perPrayer.mapValues { ((1 - it.value) * elapsedDays).toInt() },
    )

    @Test
    fun `empty month scores zero and ranks Mubtadi`() {
        val score = RankCalculator.score(stats(completed = 0, expected = 0, elapsedDays = 0), null)
        assertEquals(0.0, score, 0.001)
        assertEquals(MonthlyRank.MUBTADI, RankCalculator.rank(score))
    }

    @Test
    fun `perfect month reaches Muqarrab`() {
        val perfect = stats(
            completed = 150, fajrDays = 30, longestStreak = 30, endStreak = 30,
            lockVerified = 150,
            perPrayer = MonthlyStatsCalculator.DAILY_PRAYERS.associateWith { 1.0 },
        )
        // With +10pp improvement over last month, everything maxes out.
        val score = RankCalculator.score(perfect, previousCompletionPercent = 80)
        assertTrue("score was $score", score >= 93.0)
        assertEquals(MonthlyRank.MUQARRAB, RankCalculator.rank(score))
    }

    @Test
    fun `score is not completion percentage alone`() {
        // Same completion, different Fajr consistency → different scores.
        val weakFajr = RankCalculator.score(stats(completed = 100, fajrDays = 5), null)
        val strongFajr = RankCalculator.score(stats(completed = 100, fajrDays = 28), null)
        assertTrue(strongFajr > weakFajr)
    }

    @Test
    fun `mercy usage lowers the score`() {
        val without = RankCalculator.score(stats(completed = 120), null)
        val with = RankCalculator.score(stats(completed = 120, mercy = 3), null)
        assertEquals(without - 6.0, with, 0.001)
    }

    @Test
    fun `mercy penalty is capped at 10`() {
        val without = RankCalculator.score(stats(completed = 120), null)
        val many = RankCalculator.score(stats(completed = 120, mercy = 50), null)
        assertEquals(without - 10.0, many, 0.001)
    }

    @Test
    fun `improvement raises and decline lowers the score`() {
        val base = stats(completed = 105) // 70%
        val improved = RankCalculator.score(base, previousCompletionPercent = 55)
        val neutral = RankCalculator.score(base, previousCompletionPercent = null)
        val declined = RankCalculator.score(base, previousCompletionPercent = 90)
        assertTrue(improved > neutral)
        assertTrue(declined < neutral)
    }

    @Test
    fun `jamaat bonus is capped at 5 points`() {
        val base = stats(completed = 100)
        val none = RankCalculator.score(base, null, jamaatPrayers = 0)
        val many = RankCalculator.score(base, null, jamaatPrayers = 100)
        assertEquals(none + 5.0, many, 0.001)
    }

    @Test
    fun `rank thresholds match the finalized ladder`() {
        assertEquals(MonthlyRank.MUBTADI, MonthlyRank.fromScore(0.0))
        assertEquals(MonthlyRank.MUBTADI, MonthlyRank.fromScore(39.9))
        assertEquals(MonthlyRank.MUSALLI, MonthlyRank.fromScore(40.0))
        assertEquals(MonthlyRank.MUWAZIB, MonthlyRank.fromScore(55.0))
        assertEquals(MonthlyRank.MUHSIN, MonthlyRank.fromScore(70.0))
        assertEquals(MonthlyRank.SABIQ, MonthlyRank.fromScore(83.0))
        assertEquals(MonthlyRank.MUQARRAB, MonthlyRank.fromScore(93.0))
        assertEquals(MonthlyRank.MUQARRAB, MonthlyRank.fromScore(100.0))
    }

    @Test
    fun `score never exceeds 100 or drops below 0`() {
        val perfect = stats(
            completed = 150, fajrDays = 30, longestStreak = 30, endStreak = 30, lockVerified = 150,
            perPrayer = MonthlyStatsCalculator.DAILY_PRAYERS.associateWith { 1.0 },
        )
        assertTrue(RankCalculator.score(perfect, 0, jamaatPrayers = 100) <= 100.0)
        assertTrue(RankCalculator.score(stats(completed = 0, mercy = 10), 100) >= 0.0)
    }
}
