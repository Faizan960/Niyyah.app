package com.salahlock.app.spiritual

import com.salahlock.app.data.model.MonthlyRank
import com.salahlock.app.data.model.MonthlyStats

/**
 * Scores one month of worship on a 0–100 scale and maps it to a [MonthlyRank].
 *
 * The score is deliberately NOT completion-percentage alone:
 *
 * ```
 * score =
 *     45 × completionRatio                          (prayers done / expected)
 *   + 15 × fajrRatio                                (Fajr days / elapsed days)
 *   +  6 × min(longestStreakInMonth / 15, 1)        (sustained perfect days)
 *   +  4 × min(endOfMonthStreak / 7, 1)             (finishing the month strong)
 *   + 10 × verifiedRatio                            (lock-verified / completed)
 *   + 10 × improvementFactor                        (vs last month, see below)
 *   +  5 × balanceRatio                             (weakest prayer's completion)
 *   + jamaatBonus                                   (up to +5, currently 0*)
 *   − mercyPenalty                                  (2 per override, capped 10)
 * ```
 *
 * improvementFactor = clamp((completionDeltaPct + 10) / 20, 0..1): −10pp or worse
 * scores 0, +10pp or better scores 1, no history is neutral (0.5 → 5 points).
 *
 * *jamaatBonus: the app has no jamaat-attendance data source yet (the local
 * masjid feature stores timetables, not attendance). The parameter is wired so
 * the formula is future-proof; callers pass 0 today.
 *
 * Rank thresholds live on [MonthlyRank]: 0 / 40 / 55 / 70 / 83 / 93.
 */
object RankCalculator {

    fun score(
        stats: MonthlyStats,
        previousCompletionPercent: Int?,
        jamaatPrayers: Int = 0,
    ): Double {
        if (stats.expectedPrayers == 0) return 0.0

        val completionRatio = stats.completedPrayers.toDouble() / stats.expectedPrayers
        val fajrRatio = if (stats.elapsedDays == 0) 0.0 else stats.fajrDays.toDouble() / stats.elapsedDays
        val longestStreakFactor = (stats.longestStreakInMonth / 15.0).coerceAtMost(1.0)
        val endStreakFactor = (stats.endOfMonthStreak / 7.0).coerceAtMost(1.0)
        val verifiedRatio =
            if (stats.completedPrayers == 0) 0.0
            else stats.lockVerified.toDouble() / stats.completedPrayers
        val improvementFactor = previousCompletionPercent?.let { prev ->
            val delta = stats.completionPercent - prev
            ((delta + 10.0) / 20.0).coerceIn(0.0, 1.0)
        } ?: 0.5
        val balanceRatio = stats.perPrayerCompletion.values.minOrNull() ?: 0.0
        val jamaatBonus = (jamaatPrayers * 0.5).coerceAtMost(5.0)
        val mercyPenalty = (stats.mercyUsed * 2.0).coerceAtMost(10.0)

        val raw = 45 * completionRatio +
            15 * fajrRatio +
            6 * longestStreakFactor +
            4 * endStreakFactor +
            10 * verifiedRatio +
            10 * improvementFactor +
            5 * balanceRatio +
            jamaatBonus -
            mercyPenalty

        return raw.coerceIn(0.0, 100.0)
    }

    fun rank(score: Double): MonthlyRank = MonthlyRank.fromScore(score)
}
