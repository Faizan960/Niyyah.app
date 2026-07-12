package com.salahlock.app.spiritual

import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.VerificationType
import com.salahlock.app.data.model.AchievementRarity
import com.salahlock.app.data.model.MonthlyRank
import com.salahlock.app.data.model.PrayerName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AchievementEngineTest {

    private fun record(
        date: LocalDate,
        prayer: PrayerName,
        verificationType: VerificationType = VerificationType.SELF_REPORTED,
    ) = PrayerRecord(
        date = date.toString(),
        prayerName = prayer.name,
        verified = true,
        verificationType = verificationType.name,
    )

    /** All five prayers on every day in the range. */
    private fun perfectDays(from: LocalDate, days: Int): List<PrayerRecord> =
        (0 until days).flatMap { offset ->
            MonthlyStatsCalculator.DAILY_PRAYERS.map { record(from.plusDays(offset.toLong()), it) }
        }

    private fun find(context: AchievementEngine.Context, id: String) =
        AchievementEngine.evaluate(context).first { it.id == id }

    @Test
    fun `all achievements locked with no history`() {
        val all = AchievementEngine.evaluate(AchievementEngine.Context(emptyList()))
        assertEquals(11, all.size)
        assertTrue(all.none { it.unlocked })
    }

    @Test
    fun `seven fajr days in a row unlocks First Fajr Week`() {
        val records = (0 until 7).map { record(LocalDate.of(2026, 6, 1).plusDays(it.toLong()), PrayerName.FAJR) }
        val a = find(AchievementEngine.Context(records), "first_fajr_week")
        assertTrue(a.unlocked)
        assertEquals(YearMonth.of(2026, 6), a.unlockedMonth)
        assertEquals(AchievementRarity.COMMON, a.rarity)
    }

    @Test
    fun `broken fajr run does not unlock First Fajr Week`() {
        val records = (0 until 7).filter { it != 3 }
            .map { record(LocalDate.of(2026, 6, 1).plusDays(it.toLong()), PrayerName.FAJR) }
        assertFalse(find(AchievementEngine.Context(records), "first_fajr_week").unlocked)
    }

    @Test
    fun `streak achievements unlock at 7 and 30 but not 100`() {
        val context = AchievementEngine.Context(perfectDays(LocalDate.of(2026, 5, 1), 35))
        assertTrue(find(context, "streak_7").unlocked)
        assertTrue(find(context, "streak_30").unlocked)
        assertFalse(find(context, "streak_100").unlocked)
        // The 7th perfect day is 2026-05-07 → unlocked in May.
        assertEquals(YearMonth.of(2026, 5), find(context, "streak_7").unlockedMonth)
    }

    @Test
    fun `hundredth lock-verified prayer unlocks 100 Verified`() {
        val start = LocalDate.of(2026, 6, 1)
        val records = (0 until 20).flatMap { offset ->
            MonthlyStatsCalculator.DAILY_PRAYERS.map {
                record(start.plusDays(offset.toLong()), it, VerificationType.LOCK_VERIFIED)
            }
        }
        val a = find(AchievementEngine.Context(records), "verified_100")
        assertTrue(a.unlocked)
        assertEquals(YearMonth.of(2026, 6), a.unlockedMonth)
    }

    @Test
    fun `rank achievements come from frozen monthly ranks`() {
        val context = AchievementEngine.Context(
            allRecords = emptyList(),
            monthlyRanks = mapOf(
                YearMonth.of(2026, 4) to MonthlyRank.MUHSIN,
                YearMonth.of(2026, 5) to MonthlyRank.SABIQ,
            ),
        )
        assertEquals(YearMonth.of(2026, 4), find(context, "first_muhsin").unlockedMonth)
        // Sabiq ≥ Muhsin, so first_muhsin anchors to the earliest qualifying month.
        assertEquals(YearMonth.of(2026, 5), find(context, "first_sabiq").unlockedMonth)
        assertFalse(find(context, "first_muqarrab").unlocked)
    }

    @Test
    fun `ramadan consistency requires every ramadan day perfect`() {
        val ramadanStart = LocalDate.of(2026, 2, 18)
        val ramadan = (0 until 29).map { ramadanStart.plusDays(it.toLong()) }.toSet()
        val complete = AchievementEngine.Context(
            perfectDays(ramadanStart, 29), ramadanDates = ramadan,
        )
        assertTrue(find(complete, "ramadan_consistency").unlocked)
        assertEquals(AchievementRarity.LEGENDARY, find(complete, "ramadan_consistency").rarity)

        val incomplete = AchievementEngine.Context(
            perfectDays(ramadanStart, 28), ramadanDates = ramadan,
        )
        assertFalse(find(incomplete, "ramadan_consistency").unlocked)
    }

    @Test
    fun `ramadan achievement stays locked when dates unknown`() {
        val context = AchievementEngine.Context(perfectDays(LocalDate.of(2026, 6, 1), 30))
        assertFalse(find(context, "ramadan_consistency").unlocked)
    }

    @Test
    fun `newlyUnlockedIn returns only that month's unlocks`() {
        // 35 perfect days spanning May into June: streak_7 + streak_30 + fajr week all end in May.
        val context = AchievementEngine.Context(perfectDays(LocalDate.of(2026, 5, 1), 35))
        val mayUnlocks = AchievementEngine.newlyUnlockedIn(YearMonth.of(2026, 5), context)
        val juneUnlocks = AchievementEngine.newlyUnlockedIn(YearMonth.of(2026, 6), context)
        assertTrue(mayUnlocks.any { it.id == "streak_7" })
        assertTrue(mayUnlocks.any { it.id == "streak_30" })
        assertTrue(juneUnlocks.none { it.id == "streak_7" })
    }

    @Test
    fun `no mercy month requires zero overrides and real activity`() {
        // Two full months of history so May is "fully recorded".
        val records = perfectDays(LocalDate.of(2026, 5, 1), 61)
        val clean = AchievementEngine.Context(records, mercyByMonth = emptyMap())
        assertTrue(find(clean, "no_mercy_month").unlocked)

        val mercied = AchievementEngine.Context(
            records,
            mercyByMonth = mapOf(YearMonth.of(2026, 5) to 1, YearMonth.of(2026, 6) to 1, YearMonth.of(2026, 7) to 1),
        )
        assertFalse(find(mercied, "no_mercy_month").unlocked)
    }
}
