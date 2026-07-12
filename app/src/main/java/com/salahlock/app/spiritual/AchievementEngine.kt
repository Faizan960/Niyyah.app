package com.salahlock.app.spiritual

import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.VerificationType
import com.salahlock.app.data.model.Achievement
import com.salahlock.app.data.model.AchievementRarity
import com.salahlock.app.data.model.MonthlyRank
import com.salahlock.app.data.model.PrayerName
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Derives all private achievements from raw history. Pure and deterministic:
 * achievements are recomputed from records + frozen monthly ranks, never
 * stored separately, so they can never drift out of sync with the data.
 *
 * Everything here is private to the device. There is no leaderboard, no
 * sharing, no comparison with anyone else — by design.
 */
object AchievementEngine {

    /** All inputs needed to evaluate achievements over the full history. */
    data class Context(
        val allRecords: List<PrayerRecord>,
        /** Rank of each fully generated month (from frozen reports). */
        val monthlyRanks: Map<YearMonth, MonthlyRank> = emptyMap(),
        /** Emergency overrides used per month. */
        val mercyByMonth: Map<YearMonth, Int> = emptyMap(),
        /** Days of Ramadan that fall inside recorded history; empty = cannot evaluate. */
        val ramadanDates: Set<LocalDate> = emptySet(),
    )

    /** Evaluates every achievement definition against [context]. */
    fun evaluate(context: Context): List<Achievement> {
        val completedByDay: Map<LocalDate, Set<PrayerName>> = context.allRecords
            .filter { it.verified || it.overrideUsed }
            .mapNotNull { rec ->
                val date = runCatching { LocalDate.parse(rec.date) }.getOrNull() ?: return@mapNotNull null
                val prayer = runCatching { PrayerName.valueOf(rec.prayerName) }.getOrNull() ?: return@mapNotNull null
                if (prayer in MonthlyStatsCalculator.DAILY_PRAYERS) date to prayer else null
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

        val fajrWeekEnd = consecutiveRunEnd(completedByDay.keys.filter { PrayerName.FAJR in completedByDay[it]!! }, 7)
        val perfectDays = completedByDay.filterValues { it.size == 5 }.keys
        val streak7End = consecutiveRunEnd(perfectDays, 7)
        val streak30End = consecutiveRunEnd(perfectDays, 30)
        val streak100End = consecutiveRunEnd(perfectDays, 100)
        val fridayMonth = firstCompleteFridayMonth(completedByDay, context)
        val noMercyMonth = firstNoMercyMonth(completedByDay, context)
        val verified100Month = hundredthVerifiedMonth(context.allRecords)
        val ramadanDone = context.ramadanDates.isNotEmpty() &&
            context.ramadanDates.all { completedByDay[it]?.size == 5 }
        val ramadanMonth = if (ramadanDone) YearMonth.from(context.ramadanDates.max()) else null

        fun firstRankMonth(min: MonthlyRank): YearMonth? =
            context.monthlyRanks.filterValues { it >= min }.keys.minOrNull()

        val muhsinMonth = firstRankMonth(MonthlyRank.MUHSIN)
        val sabiqMonth = firstRankMonth(MonthlyRank.SABIQ)
        val muqarrabMonth = firstRankMonth(MonthlyRank.MUQARRAB)

        return listOf(
            achievement("first_fajr_week", "🌅", "First Fajr Week",
                "Prayed Fajr 7 days in a row", AchievementRarity.COMMON, fajrWeekEnd?.let(YearMonth::from)),
            achievement("streak_7", "🌿", "7 Days of Consistency",
                "7 consecutive days with all 5 prayers", AchievementRarity.COMMON, streak7End?.let(YearMonth::from)),
            achievement("streak_30", "🌿", "30 Days of Consistency",
                "30 consecutive days with all 5 prayers", AchievementRarity.RARE, streak30End?.let(YearMonth::from)),
            achievement("streak_100", "🌿", "100 Days of Consistency",
                "100 consecutive days with all 5 prayers", AchievementRarity.EPIC, streak100End?.let(YearMonth::from)),
            achievement("friday_month", "🕌", "First Complete Friday Month",
                "Dhuhr completed on every Friday of a month", AchievementRarity.RARE, fridayMonth),
            achievement("no_mercy_month", "🤲", "A Steadfast Month",
                "A full month without a single emergency override", AchievementRarity.RARE, noMercyMonth),
            achievement("verified_100", "⭐", "100 Verified Prayers",
                "100 prayers verified through the lock", AchievementRarity.RARE, verified100Month),
            achievement("ramadan_consistency", "🌙", "Ramadan Consistency",
                "All 5 prayers on every day of Ramadan", AchievementRarity.LEGENDARY, ramadanMonth),
            achievement("first_muhsin", "✨", "First Muhsin Rank",
                "Reached محسن — one who excels", AchievementRarity.EPIC, muhsinMonth),
            achievement("first_sabiq", "✨", "First Sabiq Rank",
                "Reached سابق — one who races toward good", AchievementRarity.EPIC, sabiqMonth),
            achievement("first_muqarrab", "✨", "First Muqarrab Rank",
                "Reached مقرب — one brought near", AchievementRarity.LEGENDARY, muqarrabMonth),
        )
    }

    /** Achievements whose unlock month is exactly [month]. */
    fun newlyUnlockedIn(month: YearMonth, context: Context): List<Achievement> =
        evaluate(context).filter { it.unlocked && it.unlockedMonth == month }

    private fun achievement(
        id: String, emoji: String, title: String, description: String,
        rarity: AchievementRarity, unlockedMonth: YearMonth?,
    ) = Achievement(
        id = id, emoji = emoji, title = title, description = description,
        rarity = rarity, unlocked = unlockedMonth != null, unlockedMonth = unlockedMonth,
    )

    /** Date on which the first run of [length] consecutive dates completes, or null. */
    private fun consecutiveRunEnd(dates: Collection<LocalDate>, length: Int): LocalDate? {
        val sorted = dates.toSortedSet()
        var run = 0
        var prev: LocalDate? = null
        for (date in sorted) {
            run = if (prev != null && prev.plusDays(1) == date) run + 1 else 1
            if (run >= length) return date
            prev = date
        }
        return null
    }

    /** First fully elapsed month where Dhuhr was completed on every Friday. */
    private fun firstCompleteFridayMonth(
        completedByDay: Map<LocalDate, Set<PrayerName>>,
        context: Context,
    ): YearMonth? = fullyRecordedMonths(context).firstOrNull { month ->
        val fridays = (1..month.lengthOfMonth())
            .map(month::atDay)
            .filter { it.dayOfWeek == DayOfWeek.FRIDAY }
        fridays.isNotEmpty() && fridays.all { PrayerName.DHUHR in (completedByDay[it] ?: emptySet()) }
    }

    /** First fully elapsed month with zero mercy and meaningful activity (≥50% completion). */
    private fun firstNoMercyMonth(
        completedByDay: Map<LocalDate, Set<PrayerName>>,
        context: Context,
    ): YearMonth? = fullyRecordedMonths(context).firstOrNull { month ->
        val completed = completedByDay.entries
            .filter { YearMonth.from(it.key) == month }
            .sumOf { it.value.size }
        (context.mercyByMonth[month] ?: 0) == 0 && completed * 2 >= month.lengthOfMonth() * 5
    }

    /** Month during which the 100th LOCK_VERIFIED prayer happened. */
    private fun hundredthVerifiedMonth(records: List<PrayerRecord>): YearMonth? {
        val verifiedDates = records
            .filter { it.verificationType == VerificationType.LOCK_VERIFIED.name }
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .sorted()
        return verifiedDates.getOrNull(99)?.let(YearMonth::from)
    }

    /** Months fully covered by history: after the first record's month, before the last's. */
    private fun fullyRecordedMonths(context: Context): List<YearMonth> {
        val months = context.allRecords
            .mapNotNull { runCatching { YearMonth.from(LocalDate.parse(it.date)) }.getOrNull() }
        val first = months.minOrNull() ?: return emptyList()
        val last = months.maxOrNull() ?: return emptyList()
        return generateSequence(first) { it.plusMonths(1) }
            .takeWhile { it <= last }
            .toList()
    }
}
