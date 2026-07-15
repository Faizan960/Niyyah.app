package com.salahlock.app.ui.reflection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.model.MonthlyReport
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Monthly Reflection — Figma frame 1:1320 (light).
 *
 * BM-006.8: fully live from [ReflectionViewModel] — rank, prayer consistency
 * (with in-month streak), Quran progress from quran_progress, newly earned
 * achievements, and a Journey Flow chart drawn from real daily/weekly
 * completion. Export Report writes a PNG via the existing exporter.
 */
private val TextBody = Color(0xFF45474E)
private val CardRadius = RoundedCornerShape(12.dp)
private val RingTrack = Color(0xFFE5E2E1)
private val RingGold = Color(0xFFEEC064)

@Composable
fun ReflectionScreen(viewModel: ReflectionViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri -> if (uri != null) viewModel.exportPng(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ReflectionHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            IntroSection()
            RankAndProgress(uiState)
            JourneyFlowCard(daily = uiState.dailySeries, weekly = uiState.weeklySeries)
            VerseAndExport(
                report = uiState.report,
                onExport = { exportLauncher.launch("niyyah-reflection.png") },
            )
        }
    }
}

/** Header — node 1:1354. */
@Composable
private fun ReflectionHeader() {
    Column(modifier = Modifier.fillMaxWidth().background(NiyyahColors.HeaderBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_menu),
                contentDescription = "Menu",
                tint = TextBody,
                modifier = Modifier.width(18.dp).height(12.dp),
            )
            Text(text = "NIYYAH", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = "Notifications",
                tint = TextBody,
                modifier = Modifier.width(16.dp).height(20.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NiyyahColors.Hairline),
        )
    }
}

/** Intro — node 1:1364. */
@Composable
private fun IntroSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Monthly Reflection",
            style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "A moment to pause, review your spiritual journey, and set intentions for the days ahead. Your progress is a beautiful testament to your dedication.",
            style = NiyyahType.Body.copy(fontSize = 18.sp, lineHeight = 28.sp),
            color = TextBody,
            textAlign = TextAlign.Center,
        )
    }
}

/** Rank card + consistency stats — node 1:1368. All values live. */
@Composable
private fun RankAndProgress(uiState: ReflectionUiState) {
    val report = uiState.report
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Rank card — node 1:1369.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, CardRadius, ambientColor = Color(0x1A1E2D4C), spotColor = Color(0x1A1E2D4C))
                .background(NiyyahColors.SurfaceElevated, CardRadius)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "SPIRITUAL RANK",
                style = NiyyahType.LabelUppercase.copy(letterSpacing = 1.4.sp),
                color = TextBody,
            )
            Column(
                modifier = Modifier.padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_rank_medal),
                    contentDescription = null,
                    tint = NiyyahColors.Gold,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = report?.rank?.transliteration ?: "Seeker",
                    style = NiyyahType.Quote.copy(fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.36).sp),
                    color = NiyyahColors.TextPrimary,
                )
                if (report?.previousRank != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_trend_up),
                            contentDescription = null,
                            tint = Color(0xFF007354),
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "Previous: ${report.previousRank.transliteration}",
                            style = NiyyahType.Badge,
                            color = Color(0xFF007354),
                        )
                    }
                }
            }
            // Achievements newly earned this month (real data; empty = hidden).
            if (!report?.newAchievements.isNullOrEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    report.newAchievements.forEach { achievement ->
                        Text(
                            text = "${achievement.emoji} ${achievement.title}",
                            style = NiyyahType.Badge,
                            color = TextBody,
                        )
                    }
                }
            }
        }
        // Prayer consistency — node 1:1382 (live, with real in-month streak).
        StatCard(
            label = "PRAYER CONSISTENCY",
            iconRes = R.drawable.ic_clock,
            percent = report?.stats?.completionPercent ?: 0,
            ringColor = NiyyahColors.Navy,
            statLine = report?.let { "${it.stats.completedPrayers}/${it.stats.expectedPrayers} Prayers" } ?: "—",
            statSub = report?.let { "Longest streak: ${it.stats.longestStreakInMonth} days" }
                ?: "Keep your rhythm",
        )
        // Quran progress — node 1:1401 (live from quran_progress).
        StatCard(
            label = "QURAN PROGRESS",
            iconRes = R.drawable.ic_quran_small,
            percent = uiState.quranPercent,
            ringColor = RingGold,
            statLine = if (uiState.quranJuzReached > 0) "Juz ${uiState.quranJuzReached} Reached"
            else "Begin reading",
            statSub = "Overall progress",
        )
    }
}

/** Stat card with progress ring — node 1:1382. */
@Composable
private fun StatCard(
    label: String,
    iconRes: Int,
    percent: Int,
    ringColor: Color,
    statLine: String,
    statSub: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, CardRadius)
            .border(1.dp, NiyyahColors.Border, CardRadius)
            .padding(25.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = NiyyahType.LabelUppercase.copy(letterSpacing = 1.4.sp),
                color = TextBody,
            )
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = TextBody,
                modifier = Modifier.size(20.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2f
                    drawArc(
                        color = RingTrack,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * percent / 100f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    text = "$percent%",
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = statLine,
                    style = NiyyahType.Body,
                    color = Color(0xFF1C1B1B),
                )
                Text(text = statSub, style = NiyyahType.Badge, color = TextBody)
            }
        }
    }
}

/** Journey Flow — node 1:1420. Chart drawn from real daily/weekly completion. */
@Composable
private fun JourneyFlowCard(daily: List<Int>, weekly: List<Int>) {
    var showWeekly by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, CardRadius)
            .border(1.dp, NiyyahColors.Border, CardRadius)
            .padding(25.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Journey Flow",
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartChip(label = "Daily", selected = !showWeekly) { showWeekly = false }
                ChartChip(label = "Weekly", selected = showWeekly) { showWeekly = true }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(192.dp),
        ) {
            val series = if (showWeekly) weekly else daily
            if (series.isEmpty()) {
                Text(
                    text = "Your journey will appear here as you pray.",
                    style = NiyyahType.Badge,
                    color = NiyyahColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                JourneyChart(
                    series = series,
                    labels = if (showWeekly) series.indices.map { "W${it + 1}" } else emptyList(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(1.dp)
                    .background(Color(0xFFE5E2E1)),
            )
        }
        Text(
            text = "\"Small, consistent deeds are most beloved.\"",
            style = NiyyahType.Body.copy(fontStyle = FontStyle.Italic),
            color = TextBody,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private val NiyyahShapeChip = RoundedCornerShape(12.dp)

/** Daily/Weekly toggle chip — same styling as the frame's static chips. */
@Composable
private fun ChartChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) NiyyahColors.TextPrimary else Color(0xFFF2F0EC),
                NiyyahShapeChip,
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = NiyyahType.Badge,
            color = if (selected) Color.White else Color(0xFF666666),
        )
    }
}

/**
 * Real completion line chart: smooth navy line + dots, with optional axis
 * labels underneath (weekly view). Values are 0–100.
 */
@Composable
private fun JourneyChart(series: List<Int>, labels: List<String>, modifier: Modifier = Modifier) {
    // Hoisted out of the (non-composable) DrawScope so the theme-aware tokens resolve.
    val lineColor = NiyyahColors.Navy
    val dotColor = NiyyahColors.TextPrimary
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            if (series.isEmpty()) return@Canvas
            val stepX = if (series.size == 1) 0f else size.width / (series.size - 1)
            fun pointAt(i: Int) = Offset(
                x = if (series.size == 1) size.width / 2f else stepX * i,
                y = size.height - (series[i].coerceIn(0, 100) / 100f) * size.height,
            )
            val path = androidx.compose.ui.graphics.Path()
            for (i in series.indices) {
                val p = pointAt(i)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            // Dots only when sparse enough to stay calm (weekly / short months).
            if (series.size <= 8) {
                for (i in series.indices) {
                    drawCircle(
                        color = dotColor,
                        radius = 3.dp.toPx(),
                        center = pointAt(i),
                    )
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labels.forEach { label ->
                    Text(text = label, style = NiyyahType.Badge, color = NiyyahColors.TextSecondary)
                }
            }
        }
    }
}

/** Motivational verse + export — node 1:1457. Quote is live from the report. */
@Composable
private fun VerseAndExport(report: MonthlyReport?, onExport: () -> Unit) {
    val quote = report?.motivation?.quote ?: "\"And He found you lost and guided you.\""
    val reference = report?.motivation?.reference ?: "— SURAH AD-DUHA (93:7)"
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_quote_marks),
                contentDescription = null,
                tint = NiyyahColors.Gold,
                modifier = Modifier.width(21.dp).height(15.dp),
            )
            Text(
                text = quote,
                style = NiyyahType.Quote.copy(
                    fontSize = 36.sp,
                    lineHeight = 58.sp,
                    letterSpacing = (-0.36).sp,
                    fontStyle = FontStyle.Italic,
                ),
                color = NiyyahColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = reference.uppercase(),
                style = NiyyahType.Badge.copy(letterSpacing = 1.2.sp),
                color = TextBody,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier
                .padding(top = 32.dp)
                .background(Color(0x26CEC0BB), RoundedCornerShape(16.dp))
                .clickable { onExport() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_export_download),
                contentDescription = null,
                tint = NiyyahColors.Navy,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Export Report",
                style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                color = NiyyahColors.Navy,
            )
        }
    }
}
