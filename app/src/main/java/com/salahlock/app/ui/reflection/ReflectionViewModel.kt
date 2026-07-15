package com.salahlock.app.ui.reflection

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.Achievement
import com.salahlock.app.data.model.JourneyEntry
import com.salahlock.app.data.model.MonthlyReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class ReflectionUiState(
    val isLoading: Boolean = true,
    /** Months with a frozen report, newest first. */
    val availableMonths: List<YearMonth> = emptyList(),
    /** The month currently displayed. */
    val selectedMonth: YearMonth? = null,
    val report: MonthlyReport? = null,
    /** True when [selectedMonth] is the in-progress month (live preview, not frozen). */
    val isCurrentMonthPreview: Boolean = false,
    val journey: List<JourneyEntry> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    val exportResult: String? = null,

    // BM-006.8 — live reading + chart data
    /** Highest juz reached across all Quran reading positions (0 = none). */
    val quranJuzReached: Int = 0,
    /** Overall Quran completion 0–100 (furthest ayah reached per surah). */
    val quranPercent: Int = 0,
    /** Daily prayer-completion percentages for the selected month (index 0 = day 1). */
    val dailySeries: List<Int> = emptyList(),
    /** Weekly average completion percentages (W1..W5) for the selected month. */
    val weeklySeries: List<Int> = emptyList(),
)

/**
 * Monthly Spiritual Reflection. Reads frozen reports from
 * [com.salahlock.app.data.repository.SpiritualReportRepository]; stored months
 * are shown verbatim (Feature 2 — no regeneration), while the in-progress
 * month is a live preview.
 */
class ReflectionViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as SalahLockApplication
    private val repo = application.spiritualReportRepository

    private val _uiState = MutableStateFlow(ReflectionUiState())
    val uiState: StateFlow<ReflectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Catch up on any month that closed while the app was dormant.
            runCatching { repo.ensureReportsUpToDate() }

            val stored = repo.storedMonths().sortedDescending()
            val current = YearMonth.now()
            val journey = repo.journey()
            val achievements = runCatching { repo.allAchievements() }.getOrDefault(emptyList())

            // Default view: latest frozen month if any, else the live preview.
            val initialMonth = stored.firstOrNull() ?: current
            val report = loadReport(initialMonth, current)
            val (daily, weekly) = chartSeries(initialMonth)
            val (juz, percent) = quranProgress()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    availableMonths = listOf(current) + stored,
                    selectedMonth = initialMonth,
                    report = report,
                    isCurrentMonthPreview = initialMonth == current,
                    journey = journey,
                    achievements = achievements,
                    dailySeries = daily,
                    weeklySeries = weekly,
                    quranJuzReached = juz,
                    quranPercent = percent,
                )
            }
        }
    }

    fun selectMonth(month: YearMonth) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = YearMonth.now()
            val report = loadReport(month, current)
            val (daily, weekly) = chartSeries(month)
            _uiState.update {
                it.copy(
                    selectedMonth = month,
                    report = report,
                    isCurrentMonthPreview = month == current,
                    dailySeries = daily,
                    weeklySeries = weekly,
                )
            }
        }
    }

    /** Daily + weekly completion percentages from prayer_records for [month]. */
    private suspend fun chartSeries(month: YearMonth): Pair<List<Int>, List<Int>> {
        val today = java.time.LocalDate.now()
        val lastDay = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
        val records = runCatching {
            application.database.prayerRecordDao()
                .getRecordsBetween(month.atDay(1).toString(), month.atEndOfMonth().toString())
        }.getOrDefault(emptyList())
        val doneByDay = records
            .filter { it.verified || it.overrideUsed }
            .groupingBy { it.date }
            .eachCount()
        val daily = (1..lastDay).map { day ->
            val date = month.atDay(day).toString()
            ((doneByDay[date] ?: 0) * 100 / 5).coerceAtMost(100)
        }
        val weekly = daily.chunked(7).map { week -> week.sum() / week.size }
        return daily to weekly
    }

    /** Highest juz reached + overall completion % from quran_progress. */
    private suspend fun quranProgress(): Pair<Int, Int> {
        val surahs = runCatching { application.quranRepository.getSurahs() }.getOrDefault(emptyList())
        if (surahs.isEmpty()) return 0 to 0
        val progress = application.quranRepository.getAllProgress().first()
        val totalAyahs = surahs.sumOf { it.ayahs.size }
        val readAyahs = progress.sumOf { p ->
            val cap = surahs.firstOrNull { it.number == p.surahNumber }?.ayahs?.size ?: p.maxAyah
            p.maxAyah.coerceAtMost(cap)
        }
        val juz = progress.mapNotNull { p ->
            val surah = surahs.firstOrNull { it.number == p.surahNumber } ?: return@mapNotNull null
            (surah.ayahs.firstOrNull { it.numberInSurah == p.maxAyah } ?: surah.ayahs.lastOrNull())?.juz
        }.maxOrNull() ?: 0
        val percent = if (totalAyahs == 0) 0 else readAyahs * 100 / totalAyahs
        return juz to percent
    }

    private suspend fun loadReport(month: YearMonth, current: YearMonth): MonthlyReport? =
        if (month == current) runCatching { repo.currentMonthReport() }.getOrNull()
        else repo.getReport(month)

    // ── Export (Feature 11) — local file only, no social sharing ─────────────

    fun exportPng(uri: Uri) = export(uri) { report ->
        ReflectionExporter.renderPng(getApplication(), report)
    }

    fun exportPdf(uri: Uri) = export(uri) { report ->
        ReflectionExporter.renderPdf(getApplication(), report)
    }

    private fun export(uri: Uri, render: (MonthlyReport) -> ByteArray) {
        val report = _uiState.value.report ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val bytes = render(report)
                getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?.use { it.write(bytes) }
                    ?: error("Could not open output file")
                "Reflection exported"
            }.getOrElse { "Export failed: ${it.message}" }
            _uiState.update { it.copy(exportResult = result) }
        }
    }

    fun clearExportResult() = _uiState.update { it.copy(exportResult = null) }
}
