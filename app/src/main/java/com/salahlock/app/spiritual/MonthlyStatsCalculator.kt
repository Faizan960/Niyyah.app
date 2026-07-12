package com.salahlock.app.spiritual

import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.VerificationType
import com.salahlock.app.data.model.MonthlyStats
import com.salahlock.app.data.model.PrayerName
import java.time.LocalDate
import java.time.YearMonth

/**
 * Derives [MonthlyStats] for one calendar month from raw [PrayerRecord]s.
 *
 * Pure and deterministic — no clock, no DB, no Android dependency — so it is
 * fully unit-testable. The in-month streak here is intentionally independent of
 * [com.salahlock.app.data.repository.StreakRepository]'s lifetime streak: that
 * one powers the lock UI and allows weekly mercy; this one measures perfect
 * 5/5 days strictly inside the month for the frozen monthly report.
 */
object MonthlyStatsCalculator {

    /** The five obligatory prayers (excludes SUNRISE). */
    val DAILY_PRAYERS: List<PrayerName> = listOf(
        PrayerName.FAJR, PrayerName.DHUHR, PrayerName.ASR, PrayerName.MAGHRIB, PrayerName.ISHA,
    )

    /**
     * @param records prayer records whose `date` falls inside [month]; rows outside are ignored.
     * @param mercyUsed emergency overrides consumed during [month].
     * @param today used to cap elapsed days for the current (incomplete) month.
     */
    fun calculate(
        month: YearMonth,
        records: List<PrayerRecord>,
        mercyUsed: Int,
        today: LocalDate = LocalDate.now(),
    ): MonthlyStats {
        val elapsedDays = when {
            YearMonth.from(today) == month -> today.dayOfMonth
            month.atDay(1).isAfter(today) -> 0
            else -> month.lengthOfMonth()
        }
        val expected = elapsedDays * DAILY_PRAYERS.size

        // date -> set of completed daily prayers on that day
        val completedByDay: Map<LocalDate, Set<PrayerName>> = records
            .filter { it.verified || it.overrideUsed }
            .mapNotNull { rec ->
                val date = runCatching { LocalDate.parse(rec.date) }.getOrNull() ?: return@mapNotNull null
                val prayer = runCatching { PrayerName.valueOf(rec.prayerName) }.getOrNull() ?: return@mapNotNull null
                if (YearMonth.from(date) != month || prayer !in DAILY_PRAYERS) null else date to prayer
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

        val completed = completedByDay.values.sumOf { it.size }

        val lockVerified = records.count {
            it.verificationType == VerificationType.LOCK_VERIFIED.name && inMonth(it, month)
        }
        val selfReported = records.count {
            it.verificationType == VerificationType.SELF_REPORTED.name && inMonth(it, month)
        }

        val perPrayerDays: Map<PrayerName, Int> = DAILY_PRAYERS.associateWith { prayer ->
            completedByDay.values.count { prayer in it }
        }
        val perPrayerCompletion = perPrayerDays.mapValues { (_, days) ->
            if (elapsedDays == 0) 0.0 else days.toDouble() / elapsedDays
        }
        val perPrayerMissed = perPrayerDays.mapValues { (_, days) -> (elapsedDays - days).coerceAtLeast(0) }

        // Longest run of perfect (5/5) days and the run ending on the last elapsed day.
        var longest = 0
        var run = 0
        for (day in 1..elapsedDays) {
            val perfect = completedByDay[month.atDay(day)]?.size == DAILY_PRAYERS.size
            run = if (perfect) run + 1 else 0
            if (run > longest) longest = run
        }
        val endStreak = run

        return MonthlyStats(
            month = month,
            elapsedDays = elapsedDays,
            expectedPrayers = expected,
            completedPrayers = completed,
            missedPrayers = (expected - completed).coerceAtLeast(0),
            lockVerified = lockVerified,
            selfReported = selfReported,
            fajrDays = perPrayerDays[PrayerName.FAJR] ?: 0,
            longestStreakInMonth = longest,
            endOfMonthStreak = endStreak,
            mercyUsed = mercyUsed,
            perPrayerCompletion = perPrayerCompletion,
            perPrayerMissed = perPrayerMissed,
        )
    }

    private fun inMonth(record: PrayerRecord, month: YearMonth): Boolean =
        runCatching { YearMonth.from(LocalDate.parse(record.date)) == month }.getOrDefault(false)
}
