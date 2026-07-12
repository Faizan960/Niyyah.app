package com.salahlock.app.spiritual

import com.salahlock.app.data.model.MonthlyStats
import com.salahlock.app.data.model.Motivation
import com.salahlock.app.data.model.PrayerName

/**
 * Chooses the end-of-month message. Deterministic — the same stats always
 * produce the same message, so a frozen report never changes meaning.
 *
 * Tone rules (checked in order):
 *  1. Excellent  — completion ≥ 90%
 *  2. Improved   — completion up ≥ 5 percentage points vs last month
 *  3. Difficult  — completion < 40%, or down ≥ 10 points vs last month
 *  4. Steady     — everything else
 *
 * The philosophy of SalahLock: compete only against the version of yourself
 * that prayed less yesterday. Messages therefore never compare to anyone else.
 */
object MotivationEngine {

    fun generate(stats: MonthlyStats, previousCompletionPercent: Int?): Motivation {
        val completion = stats.completionPercent
        val delta = previousCompletionPercent?.let { completion - it }

        return when {
            completion >= 90 -> Motivation(
                headline = "MashaAllah.",
                body = "You completed ${stats.completedPrayers} out of ${stats.expectedPrayers} prayers.",
                quote = "So race to all that is good.",
                reference = "Quran 5:48",
            )

            delta != null && delta >= 5 -> Motivation(
                headline = "Alhamdulillah.",
                body = "You improved your prayer consistency by $delta%.",
                quote = "And whoever strives, strives only for himself.",
                reference = "Quran 29:6",
            )

            completion < 40 || (delta != null && delta <= -10) -> Motivation(
                headline = "This month was difficult.",
                body = "Do not focus on the prayers you missed. Focus on the next prayer.",
                quote = "Indeed, with hardship comes ease.",
                reference = "Quran 94:6",
            )

            else -> Motivation(
                headline = "Keep going.",
                body = "You completed ${stats.completedPrayers} prayers this month. Every one of them counts.",
                quote = "The most beloved of deeds to Allah are those that are most consistent, even if small.",
                reference = "Bukhari & Muslim",
            )
        }
    }

    /** "Your greatest opportunity for growth this month was Fajr." (Feature 5) */
    fun growthOpportunity(stats: MonthlyStats): PrayerName? =
        stats.perPrayerMissed
            .filterValues { it > 0 }
            .maxByOrNull { it.value }
            ?.key
}
