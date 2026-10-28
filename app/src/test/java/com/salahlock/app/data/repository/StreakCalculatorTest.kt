package com.salahlock.app.data.repository

import com.salahlock.app.data.db.entity.PrayerRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the strict streak rule after the weekly free-pass ("mercy") removal
 * (chore/remove-free-passes): a day counts ONLY with all five prayers completed.
 *
 * The old rule — one 4/5 day per week still counted — is gone: a 4/5 day now
 * BREAKS the streak. Emergency-overridden prayers still count as completed (the
 * user acted on the prayer); the override counter itself lives in
 * emergency_overrides and is unaffected.
 */
class StreakCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 28)

    private val fivePrayers = listOf("FAJR", "SUNRISE", "DHUHR", "ASR", "MAGHRIB", "ISHA").take(5)

    private fun day(date: LocalDate, completed: Int, overrides: Int = 0): List<PrayerRecord> =
        (0 until completed).map { i ->
            PrayerRecord(
                date = date.toString(),
                prayerName = fivePrayers[i],
                verified = i >= overrides, // first `overrides` prayers via emergency override
                overrideUsed = i < overrides,
            )
        }

    private fun streakOf(vararg days: Pair<LocalDate, List<PrayerRecord>>): Int =
        StreakCalculator.calculate(days.associate { (d, records) -> d.toString() to records }, today)

    @Test
    fun `consecutive fully completed days count up`() {
        val s = streakOf(
            today.minusDays(2) to day(today.minusDays(2), 5),
            today.minusDays(1) to day(today.minusDays(1), 5),
            today to day(today, 5),
        )
        assertEquals(3, s)
    }

    @Test
    fun `a four of five day breaks the streak - no weekly mercy pass`() {
        val s = streakOf(
            today.minusDays(2) to day(today.minusDays(2), 5),
            // Old behavior: 4/5 counted once per week. Removed — it must break.
            today.minusDays(1) to day(today.minusDays(1), 4),
            today to day(today, 5),
        )
        assertEquals(1, s)
    }

    @Test
    fun `emergency-overridden prayers count as completed`() {
        val s = streakOf(
            today.minusDays(1) to day(today.minusDays(1), 5, overrides = 2),
            today to day(today, 5, overrides = 1),
        )
        assertEquals(2, s)
    }

    @Test
    fun `an incomplete today does not break the streak`() {
        val s = streakOf(
            today.minusDays(2) to day(today.minusDays(2), 5),
            today.minusDays(1) to day(today.minusDays(1), 5),
            today to day(today, 2), // still in progress
        )
        assertEquals(2, s)
    }

    @Test
    fun `empty history is a zero streak`() {
        assertEquals(0, StreakCalculator.calculate(emptyMap(), today))
    }
}
