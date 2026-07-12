package com.salahlock.app.data.repository

import android.content.Context
import android.util.Log
import com.salahlock.app.data.db.dao.EmergencyOverrideDao
import com.salahlock.app.data.db.dao.PrayerRecordDao
import com.salahlock.app.data.model.Achievement
import com.salahlock.app.data.model.JourneyEntry
import com.salahlock.app.data.model.MonthlyComparison
import com.salahlock.app.data.model.MonthlyRank
import com.salahlock.app.data.model.MonthlyReport
import com.salahlock.app.data.model.Motivation
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.spiritual.AchievementEngine
import com.salahlock.app.spiritual.MonthlyStatsCalculator
import com.salahlock.app.spiritual.MotivationEngine
import com.salahlock.app.spiritual.RamadanResolver
import com.salahlock.app.spiritual.RankCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * Frozen monthly spiritual reports — 100% local, 100% private.
 *
 * # Storage
 * One JSON file per month in `filesDir/reflections/` (e.g. `2026-06.json`),
 * written once when the month closes and never regenerated: the original
 * score, rank, motivation message and achievements are preserved forever
 * (Feature 2). No Room migration — the DB schema is untouched.
 *
 * # Generation
 * [ensureReportsUpToDate] is idempotent and cheap when nothing is missing.
 * It walks from the first recorded month to last month and generates any
 * month without a file, in order (so each month's "improvement vs previous"
 * uses the frozen previous report). Called from the Application scope at
 * launch — surviving reboots and app updates without WorkManager.
 *
 * # Backup
 * [exportAll]/[importAll] plug into the existing ZIP backup so reports
 * survive a device migration.
 */
class SpiritualReportRepository(
    context: Context,
    private val prayerRecordDao: PrayerRecordDao,
    private val emergencyOverrideDao: EmergencyOverrideDao,
) {
    private val tag = "SpiritualReportRepo"
    private val dir: File = File(context.filesDir, "reflections")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val generateMutex = Mutex()

    // ── Persistence ──────────────────────────────────────────────────────────

    private fun fileFor(month: YearMonth) = File(dir, "$month.json")

    /** Months that have a frozen report on disk, oldest first. */
    fun storedMonths(): List<YearMonth> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { YearMonth.parse(it.nameWithoutExtension) }.getOrNull() }
            ?.sorted()
            ?: emptyList()

    fun getStored(month: YearMonth): StoredMonthlyReflection? =
        fileFor(month).takeIf { it.exists() }?.let { file ->
            runCatching { json.decodeFromString<StoredMonthlyReflection>(file.readText()) }
                .onFailure { Log.e(tag, "Corrupt reflection file ${file.name}: ${it.message}") }
                .getOrNull()
        }

    private fun save(reflection: StoredMonthlyReflection) {
        dir.mkdirs()
        fileFor(YearMonth.parse(reflection.month)).writeText(json.encodeToString(reflection))
    }

    /** Full export for backup. */
    fun exportAll(): List<StoredMonthlyReflection> = storedMonths().mapNotNull(::getStored)

    /** Restore from backup. Existing files win — frozen reports are never overwritten. */
    fun importAll(reflections: List<StoredMonthlyReflection>) {
        reflections.forEach { r ->
            val month = runCatching { YearMonth.parse(r.month) }.getOrNull() ?: return@forEach
            if (!fileFor(month).exists()) save(r)
        }
    }

    // ── Generation ───────────────────────────────────────────────────────────

    /**
     * Generates every missing closed-month report. Safe to call repeatedly;
     * does nothing when all reports exist.
     */
    suspend fun ensureReportsUpToDate(today: LocalDate = LocalDate.now()): Unit =
        withContext(Dispatchers.IO) {
            generateMutex.withLock {
                val earliest = prayerRecordDao.getEarliestDate()
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@withLock
                val firstMonth = YearMonth.from(earliest)
                val lastClosed = YearMonth.from(today).minusMonths(1)
                var month = firstMonth
                while (month <= lastClosed) {
                    if (!fileFor(month).exists()) {
                        runCatching { save(buildReflection(month, today)) }
                            .onFailure { Log.e(tag, "Failed to generate $month: ${it.message}") }
                    }
                    month = month.plusMonths(1)
                }
            }
        }

    /** Live (non-persisted) report for the in-progress month, for the UI preview. */
    suspend fun currentMonthReport(today: LocalDate = LocalDate.now()): MonthlyReport =
        withContext(Dispatchers.IO) { buildReflection(YearMonth.from(today), today).toReport() }

    /** Stored report as UI model. */
    fun getReport(month: YearMonth): MonthlyReport? = getStored(month)?.toReport()

    /** Feature 9: the historical rank + completion progression, oldest first. */
    fun journey(): List<JourneyEntry> = exportAll().map { r ->
        JourneyEntry(
            month = YearMonth.parse(r.month),
            score = r.score,
            rank = rankOf(r.rank),
            completionPercent = r.completionPercent,
        )
    }

    /** Feature 7/8: full achievement list (locked + unlocked) across all history. */
    suspend fun allAchievements(): List<Achievement> = withContext(Dispatchers.IO) {
        AchievementEngine.evaluate(achievementContext())
    }

    private suspend fun buildReflection(month: YearMonth, today: LocalDate): StoredMonthlyReflection {
        val records = prayerRecordDao.getRecordsBetween(
            month.atDay(1).toString(), month.atEndOfMonth().toString(),
        )
        val mercy = emergencyOverrideDao.getForMonth(monthKey(month))?.count ?: 0
        val stats = MonthlyStatsCalculator.calculate(month, records, mercy, today)

        val prev = getStored(month.minusMonths(1))
        val score = RankCalculator.score(stats, prev?.completionPercent)
        val rank = RankCalculator.rank(score)
        val motivation = MotivationEngine.generate(stats, prev?.completionPercent)
        val growth = MotivationEngine.growthOpportunity(stats)

        // Achievements newly earned in this month, using ranks known so far + this one.
        val context = achievementContext(extraRank = month to rank)
        val newAchievements = AchievementEngine.newlyUnlockedIn(month, context)

        val prevFajrPct = prev?.let { if (it.elapsedDays == 0) 0 else it.fajrDays * 100 / it.elapsedDays }
        val fajrPct = if (stats.elapsedDays == 0) 0 else stats.fajrDays * 100 / stats.elapsedDays

        return StoredMonthlyReflection(
            month = month.toString(),
            generatedAtMs = System.currentTimeMillis(),
            score = score,
            rank = rank.name,
            previousRank = prev?.rank,
            previousScore = prev?.score,
            elapsedDays = stats.elapsedDays,
            expectedPrayers = stats.expectedPrayers,
            completedPrayers = stats.completedPrayers,
            missedPrayers = stats.missedPrayers,
            lockVerified = stats.lockVerified,
            selfReported = stats.selfReported,
            fajrDays = stats.fajrDays,
            longestStreakInMonth = stats.longestStreakInMonth,
            endOfMonthStreak = stats.endOfMonthStreak,
            mercyUsed = stats.mercyUsed,
            perPrayerCompletion = stats.perPrayerCompletion.mapKeys { it.key.name },
            perPrayerMissed = stats.perPrayerMissed.mapKeys { it.key.name },
            improvementPercent = prev?.let { stats.completionPercent - it.completionPercent },
            fajrDelta = prevFajrPct?.let { fajrPct - it },
            missedDelta = prev?.let { stats.missedPrayers - it.missedPrayers },
            streakDelta = prev?.let { stats.longestStreakInMonth - it.longestStreakInMonth },
            motivationHeadline = motivation.headline,
            motivationBody = motivation.body,
            motivationQuote = motivation.quote,
            motivationReference = motivation.reference,
            growthOpportunity = growth?.name,
            newAchievementIds = newAchievements.map { it.id },
        )
    }

    private suspend fun achievementContext(
        extraRank: Pair<YearMonth, MonthlyRank>? = null,
    ): AchievementEngine.Context {
        val allRecords = prayerRecordDao.getAll()
        val ranks = exportAll()
            .associate { YearMonth.parse(it.month) to rankOf(it.rank) }
            .let { if (extraRank != null) it + extraRank else it }
        val mercyByMonth = emergencyOverrideDao.getAll().mapNotNull { o ->
            runCatching { YearMonth.parse(o.monthYear) }.getOrNull()?.let { it to o.count }
        }.toMap()
        val dates = allRecords.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        val ramadan = if (dates.isEmpty()) emptySet() else
            runCatching { RamadanResolver.ramadanDates(dates.min(), dates.max()) }.getOrDefault(emptySet())
        return AchievementEngine.Context(
            allRecords = allRecords,
            monthlyRanks = ranks,
            mercyByMonth = mercyByMonth,
            ramadanDates = ramadan,
        )
    }

    /** "2026-06" — matches EmergencyOverride.monthYear format. */
    private fun monthKey(month: YearMonth) =
        "%04d-%02d".format(month.year, month.monthValue)

    private fun rankOf(name: String): MonthlyRank =
        runCatching { MonthlyRank.valueOf(name) }.getOrDefault(MonthlyRank.MUBTADI)
}

// ─────────────────────────────────────────────────────────────────────────────
// Frozen on-disk form. Everything the report showed is stored verbatim so the
// original rank + motivation are preserved even if formulas change later.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class StoredMonthlyReflection(
    val version: Int = 1,
    /** "2026-06" */
    val month: String,
    val generatedAtMs: Long = 0,
    val score: Double = 0.0,
    val rank: String = MonthlyRank.MUBTADI.name,
    val previousRank: String? = null,
    val previousScore: Double? = null,
    val elapsedDays: Int = 0,
    val expectedPrayers: Int = 0,
    val completedPrayers: Int = 0,
    val missedPrayers: Int = 0,
    val lockVerified: Int = 0,
    val selfReported: Int = 0,
    val fajrDays: Int = 0,
    val longestStreakInMonth: Int = 0,
    val endOfMonthStreak: Int = 0,
    val mercyUsed: Int = 0,
    val perPrayerCompletion: Map<String, Double> = emptyMap(),
    val perPrayerMissed: Map<String, Int> = emptyMap(),
    val improvementPercent: Int? = null,
    val fajrDelta: Int? = null,
    val missedDelta: Int? = null,
    val streakDelta: Int? = null,
    val motivationHeadline: String = "",
    val motivationBody: String = "",
    val motivationQuote: String = "",
    val motivationReference: String = "",
    val growthOpportunity: String? = null,
    val newAchievementIds: List<String> = emptyList(),
) {
    val completionPercent: Int
        get() = if (expectedPrayers == 0) 0 else completedPrayers * 100 / expectedPrayers

    /** Rebuilds the UI model from the frozen data (never recomputed). */
    fun toReport(): MonthlyReport {
        val monthValue = YearMonth.parse(month)
        val stats = com.salahlock.app.data.model.MonthlyStats(
            month = monthValue,
            elapsedDays = elapsedDays,
            expectedPrayers = expectedPrayers,
            completedPrayers = completedPrayers,
            missedPrayers = missedPrayers,
            lockVerified = lockVerified,
            selfReported = selfReported,
            fajrDays = fajrDays,
            longestStreakInMonth = longestStreakInMonth,
            endOfMonthStreak = endOfMonthStreak,
            mercyUsed = mercyUsed,
            perPrayerCompletion = perPrayerCompletion.mapKeys { PrayerName.valueOf(it.key) },
            perPrayerMissed = perPrayerMissed.mapKeys { PrayerName.valueOf(it.key) },
        )
        val allDefs = AchievementEngine.evaluate(AchievementEngine.Context(emptyList()))
        val newAchievements = newAchievementIds.mapNotNull { id ->
            allDefs.firstOrNull { it.id == id }
                ?.copy(unlocked = true, unlockedMonth = monthValue)
        }
        return MonthlyReport(
            stats = stats,
            score = score,
            rank = runCatching { MonthlyRank.valueOf(rank) }.getOrDefault(MonthlyRank.MUBTADI),
            previousRank = previousRank?.let { runCatching { MonthlyRank.valueOf(it) }.getOrNull() },
            previousScore = previousScore,
            improvementPercent = improvementPercent,
            comparison = if (improvementPercent != null) MonthlyComparison(
                completionDelta = improvementPercent,
                fajrDelta = fajrDelta ?: 0,
                missedDelta = missedDelta ?: 0,
                streakDelta = streakDelta ?: 0,
            ) else null,
            motivation = Motivation(
                headline = motivationHeadline,
                body = motivationBody,
                quote = motivationQuote,
                reference = motivationReference,
            ),
            newAchievements = newAchievements,
        )
    }
}
