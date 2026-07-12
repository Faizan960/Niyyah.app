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
)

/**
 * Monthly Spiritual Reflection. Reads frozen reports from
 * [com.salahlock.app.data.repository.SpiritualReportRepository]; stored months
 * are shown verbatim (Feature 2 — no regeneration), while the in-progress
 * month is a live preview.
 */
class ReflectionViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as SalahLockApplication).spiritualReportRepository

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

            _uiState.update {
                it.copy(
                    isLoading = false,
                    availableMonths = listOf(current) + stored,
                    selectedMonth = initialMonth,
                    report = report,
                    isCurrentMonthPreview = initialMonth == current,
                    journey = journey,
                    achievements = achievements,
                )
            }
        }
    }

    fun selectMonth(month: YearMonth) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = YearMonth.now()
            val report = loadReport(month, current)
            _uiState.update {
                it.copy(
                    selectedMonth = month,
                    report = report,
                    isCurrentMonthPreview = month == current,
                )
            }
        }
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
