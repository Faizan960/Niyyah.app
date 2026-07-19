package com.salahlock.app.ui.reflection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.data.model.Achievement
import com.salahlock.app.data.model.AchievementRarity
import com.salahlock.app.data.model.JourneyEntry
import com.salahlock.app.data.model.MonthlyReport
import com.salahlock.app.data.model.PrayerName
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Stitch V2 "Niyyah Dark" tokens (design/stitch-v2/dark/monthly-reflection) ─
// This screen is always dark by design — a private, focused report.
private val Bg = Color(0xFF0B0F10)
private val Surface1 = Color(0xFF151B1C)
private val Surface2 = Color(0xFF232E31)
private val Gold = Color(0xFFEEC064)
private val Emerald = Color(0xFF18A67A)
private val TextPrimary = Color(0xFFF5F5F5)
private val TextSecondary = Color(0xFFA7A7A7)
private val Divider = Color(0xFF2A3335)
private val Danger = Color(0xFFFFB4AB)

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/**
 * Monthly Spiritual Reflection — a private report card. Everything on this
 * screen is local; the only outputs are user-chosen local PNG/PDF files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReflectionScreen(
    onBack: () -> Unit,
    viewModel: ReflectionViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri -> uri?.let { viewModel.exportPng(it) } }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let { viewModel.exportPdf(it) } }

    LaunchedEffect(state.exportResult) {
        state.exportResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportResult()
        }
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Monthly Reflection",
                        color = TextPrimary,
                        fontFamily = com.salahlock.app.theme.NiyyahSerif,
                        fontWeight = FontWeight.Medium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { MonthSelector(state, viewModel::selectMonth) }

            val report = state.report
            if (report == null) {
                item {
                    Text(
                        "No prayer history for this month yet.",
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                if (state.isCurrentMonthPreview) item { PreviewBanner() }
                item { RankCard(report) }
                item { ProgressCard(report) }
                if (report.comparison != null) item { ComparisonCard(report) }
                item { MissedPrayersCard(report) }
                item { report.motivation.let { MotivationCard(report) } }
                if (report.newAchievements.isNotEmpty()) {
                    item { AchievementsCard("Earned this month", report.newAchievements) }
                }
                if (state.journey.size >= 2) item { JourneyCard(state.journey) }
                if (state.achievements.any { it.unlocked }) {
                    item { AchievementsCard("All achievements", state.achievements.filter { it.unlocked }) }
                }
                item { ExportRow(report, pngLauncher::launch, pdfLauncher::launch) }
                item { PrivacyFooter() }
            }
        }
    }
}

@Composable
private fun MonthSelector(state: ReflectionUiState, onSelect: (YearMonth) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.availableMonths) { month ->
            val selected = month == state.selectedMonth
            FilterChip(
                selected = selected,
                onClick = { onSelect(month) },
                label = {
                    Text(
                        month.format(monthFormatter),
                        color = if (selected) Bg else TextSecondary,
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Surface1,
                    selectedContainerColor = Gold,
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun PreviewBanner() {
    Text(
        "This month is still in progress — the final report is saved when the month ends.",
        color = TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1, RoundedCornerShape(12.dp))
            .padding(12.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ReflectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1, RoundedCornerShape(24.dp))
            .padding(24.dp),
        content = content,
    )
}

@Composable
private fun RankCard(report: MonthlyReport) {
    ReflectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your rank this month", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Text(report.rank.arabicName, color = Gold, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "${report.rank.transliteration} · ${report.rank.translation}",
                color = TextPrimary, fontSize = 16.sp,
            )
            report.previousRank?.let { prev ->
                if (prev != report.rank) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Last month: ${prev.transliteration}",
                        color = TextSecondary, fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Score ${report.score.toInt()} / 100", color = Emerald, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ProgressCard(report: MonthlyReport) {
    val s = report.stats
    ReflectionCard {
        Text("Progress", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatBlock("${s.completedPrayers}/${s.expectedPrayers}", "Prayers")
            StatBlock("${s.completionPercent}%", "Completion")
            StatBlock("${s.longestStreakInMonth}d", "Best streak")
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = {
                if (s.expectedPrayers == 0) 0f else s.completedPrayers.toFloat() / s.expectedPrayers
            },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Gold,
            trackColor = Surface2,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatBlock("${s.fajrDays}/${s.elapsedDays}", "Fajr days")
            StatBlock("${s.lockVerified}", "Verified")
            StatBlock("${s.mercyUsed}", "Mercy used")
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ComparisonCard(report: MonthlyReport) {
    val c = report.comparison ?: return
    ReflectionCard {
        Text("Compared to last month", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        DeltaRow("Prayer completion", c.completionDelta, "%", higherIsBetter = true)
        DeltaRow("Fajr", c.fajrDelta, "%", higherIsBetter = true)
        DeltaRow("Missed prayers", c.missedDelta, "", higherIsBetter = false)
        DeltaRow("Longest streak", c.streakDelta, "d", higherIsBetter = true)
    }
}

@Composable
private fun DeltaRow(label: String, delta: Int, unit: String, higherIsBetter: Boolean) {
    val good = if (higherIsBetter) delta >= 0 else delta <= 0
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp)
        Text(
            "${if (delta > 0) "+" else ""}$delta$unit",
            color = if (delta == 0) TextSecondary else if (good) Emerald else Danger,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MissedPrayersCard(report: MonthlyReport) {
    val missed = report.stats.perPrayerMissed
    ReflectionCard {
        Text("Missed prayers", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        listOf(
            PrayerName.FAJR, PrayerName.DHUHR, PrayerName.ASR,
            PrayerName.MAGHRIB, PrayerName.ISHA,
        ).forEach { prayer ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(prayer.displayName, color = TextPrimary, fontSize = 14.sp)
                Text(
                    "${missed[prayer] ?: 0}",
                    color = if ((missed[prayer] ?: 0) == 0) Emerald else TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }
        val growth = missed.filterValues { it > 0 }.maxByOrNull { it.value }?.key
        if (growth != null) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(12.dp))
            Text(
                "Your greatest opportunity for growth this month was ${growth.displayName}.",
                color = Gold, fontSize = 14.sp, fontStyle = FontStyle.Italic,
            )
        }
    }
}

@Composable
private fun MotivationCard(report: MonthlyReport) {
    val m = report.motivation
    ReflectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(m.headline, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(m.body, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                "“${m.quote}”",
                color = TextPrimary, fontSize = 16.sp,
                fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(m.reference, color = Gold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AchievementsCard(title: String, achievements: List<Achievement>) {
    ReflectionCard {
        Text(title, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        achievements.forEach { a ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(a.description, color = TextSecondary, fontSize = 12.sp)
                }
                RarityBadge(a.rarity)
            }
        }
    }
}

@Composable
private fun RarityBadge(rarity: AchievementRarity) {
    val color = when (rarity) {
        AchievementRarity.COMMON -> TextSecondary
        AchievementRarity.RARE -> Emerald
        AchievementRarity.EPIC -> Color(0xFF9C7BD4)
        AchievementRarity.LEGENDARY -> Gold
    }
    Text(
        rarity.label,
        color = color,
        fontSize = 11.sp,
        modifier = Modifier
            .background(Surface2, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Feature 9 + 12: spiritual journey — rank per month and completion trend. */
@Composable
private fun JourneyCard(journey: List<JourneyEntry>) {
    val recent = journey.takeLast(12)
    ReflectionCard {
        Text("Spiritual journey", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = recent.size
            if (n < 2) return@Canvas
            val stepX = size.width / (n - 1)
            val points = recent.mapIndexed { i, e ->
                Offset(i * stepX, size.height * (1f - e.completionPercent / 100f))
            }
            // baseline grid
            drawLine(Divider, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            for (i in 0 until n - 1) {
                drawLine(Emerald, points[i], points[i + 1], 5f, StrokeCap.Round)
            }
            points.forEach { drawCircle(Gold, 8f, it) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            recent.forEach { e ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${e.completionPercent}%", color = TextPrimary, fontSize = 11.sp)
                    Text(e.month.format(monthShort), color = TextSecondary, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            recent.forEach { e ->
                Text(e.rank.arabicName, color = Gold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ExportRow(
    report: MonthlyReport,
    launchPng: (String) -> Unit,
    launchPdf: (String) -> Unit,
) {
    val base = "Niyyah_Reflection_${report.stats.month}"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { launchPng("$base.png") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
        ) {
            Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export Image")
        }
        OutlinedButton(
            onClick = { launchPdf("$base.pdf") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
        ) {
            Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export PDF")
        }
    }
}

@Composable
private fun PrivacyFooter() {
    Text(
        "Compete only against the version of yourself that prayed less yesterday.\nThis reflection never leaves your device.",
        color = TextSecondary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}
