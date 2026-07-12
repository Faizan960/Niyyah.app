package com.salahlock.app.data.model

import java.time.YearMonth

/**
 * Personal monthly rank — the user competes only against their previous self.
 * Entirely local; never shown to anyone else, never leaves the device.
 *
 * Distinct from [SpiritualRank] (the streak-based rank used by the lock UI):
 * MonthlyRank is scored per calendar month by [com.salahlock.app.spiritual.RankCalculator].
 */
enum class MonthlyRank(
    val arabicName: String,
    val transliteration: String,
    val translation: String,
    /** Minimum monthly score (0–100) required for this rank. */
    val minScore: Int,
) {
    MUBTADI("مبتدئ", "Mubtadi", "Beginner", 0),
    MUSALLI("مصلي", "Musalli", "One who prays", 40),
    MUWAZIB("مواظب", "Muwazib", "Consistent worshipper", 55),
    MUHSIN("محسن", "Muhsin", "One who excels", 70),
    SABIQ("سابق", "Sabiq", "One who races toward good", 83),
    MUQARRAB("مقرب", "Muqarrab", "One brought near", 93);

    companion object {
        fun fromScore(score: Double): MonthlyRank =
            entries.reversed().first { score >= it.minScore }
    }
}

/** Raw per-month numbers derived from prayer_records + emergency_overrides. */
data class MonthlyStats(
    val month: YearMonth,
    /** Days of the month that have elapsed (full month length for past months). */
    val elapsedDays: Int,
    /** 5 × elapsedDays. */
    val expectedPrayers: Int,
    val completedPrayers: Int,
    val missedPrayers: Int,
    val lockVerified: Int,
    val selfReported: Int,
    /** Days on which Fajr was completed. */
    val fajrDays: Int,
    /** Longest run of consecutive 5/5 days inside this month. */
    val longestStreakInMonth: Int,
    /** Consecutive 5/5 days ending on the last elapsed day of the month. */
    val endOfMonthStreak: Int,
    /** Days on which the prayer was missed, keyed by the five daily prayers. */
    val perPrayerMissed: Map<PrayerName, Int>,
    /** Emergency overrides used this month. */
    val mercyUsed: Int,
    /** Completion ratio per prayer, 0.0–1.0, keyed by the five daily prayers. */
    val perPrayerCompletion: Map<PrayerName, Double>,
) {
    val completionPercent: Int
        get() = if (expectedPrayers == 0) 0 else (completedPrayers * 100) / expectedPrayers

    val bestPrayer: Pair<PrayerName, Double>? =
        perPrayerCompletion.maxByOrNull { it.value }?.toPair()

    val needsAttention: Pair<PrayerName, Double>? =
        perPrayerCompletion.minByOrNull { it.value }?.toPair()
}

/** One step of the Spiritual Journey timeline. */
data class JourneyEntry(
    val month: YearMonth,
    val score: Double,
    val rank: MonthlyRank,
    val completionPercent: Int,
)

/** How hard an achievement is to earn. Cosmetic only — never competitive. */
enum class AchievementRarity(val label: String) {
    COMMON("Common"),
    RARE("Rare"),
    EPIC("Epic"),
    LEGENDARY("Legendary"),
}

/** A private, locally-derived achievement. Never shared. */
data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val rarity: AchievementRarity,
    val unlocked: Boolean,
    /** Month in which the condition was first met, when determinable. */
    val unlockedMonth: YearMonth? = null,
)

/** Month-over-month deltas for the "Compared to last month" section. Null = no history. */
data class MonthlyComparison(
    /** Percentage-point change in overall completion. */
    val completionDelta: Int,
    /** Percentage-point change in Fajr consistency. */
    val fajrDelta: Int,
    /** Change in missed prayer count (negative = fewer missed = better). */
    val missedDelta: Int,
    /** Change in longest in-month streak. */
    val streakDelta: Int,
)

/** Personalized end-of-month message with an Islamic reminder. */
data class Motivation(
    val headline: String,
    val body: String,
    val quote: String,
    val reference: String,
)

/** Everything the Monthly Reflection screen needs, fully derived on demand. */
data class MonthlyReport(
    val stats: MonthlyStats,
    val score: Double,
    val rank: MonthlyRank,
    val previousRank: MonthlyRank?,
    val previousScore: Double?,
    /** Percentage-point change in completion vs the previous month; null if no history. */
    val improvementPercent: Int?,
    /** Month-over-month deltas; null when this is the first recorded month. */
    val comparison: MonthlyComparison?,
    val motivation: Motivation,
    /** Achievements newly earned in this month. */
    val newAchievements: List<Achievement>,
)
