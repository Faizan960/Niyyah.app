package com.salahlock.app.data.repository

import com.salahlock.app.data.db.entity.PrayerRecord
import java.time.LocalDate

/**
 * Pure, unit-testable streak rule: a day counts toward the streak only when ALL
 * five daily prayers are completed (verified or emergency-overridden).
 *
 * The previous weekly "mercy" / free-pass allowance (one 4/5 day per week counted)
 * was REMOVED with that feature — a 4/5 day now simply ends the streak. The
 * in-month streak used by the monthly report
 * ([com.salahlock.app.spiritual.MonthlyStatsCalculator]) measures the same strict
 * 5/5 rule, so the two agree.
 */
internal object StreakCalculator {

    /** Days lookback window; matches the repository's recent-records query. */
    const val WINDOW_DAYS = 30

    /**
     * Counts consecutive fully-completed days ending today (or yesterday if today
     * is still incomplete — an in-progress day must never break the streak).
     * A prayer completed via emergency override (`overrideUsed`) still counts:
     * the user acted on the prayer; the override itself is tracked separately.
     */
    fun calculate(byDay: Map<String, List<PrayerRecord>>, today: LocalDate): Int {
        var streak = 0

        for (i in 0 until WINDOW_DAYS) {
            val date = today.minusDays(i.toLong())
            val dayRecords = byDay[date.toString()] ?: emptyList()
            val completedCount = dayRecords.count { it.verified || it.overrideUsed }

            when {
                completedCount >= 5 -> streak++
                i == 0 -> continue // Don't break on today (day might still be incomplete)
                else -> break
            }
        }
        return streak
    }
}
