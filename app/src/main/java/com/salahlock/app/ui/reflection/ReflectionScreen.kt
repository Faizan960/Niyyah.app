package com.salahlock.app.ui.reflection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
 * Wired to the existing [ReflectionViewModel]: rank, prayer consistency and
 * motivation quote are live; Quran progress and the Journey Flow chart mirror
 * the frame (no reading tracker / weekly series yet). Export Report writes a
 * PNG via the existing exporter.
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
            RankAndProgress(uiState.report)
            JourneyFlowCard()
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
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xCCFCF9F8))) {
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
                .background(Color(0x4DC5C6CE)),
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

/** Rank card + consistency stats — node 1:1368. */
@Composable
private fun RankAndProgress(report: MonthlyReport?) {
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
                            text = "Previous: ${report.previousRank!!.transliteration}",
                            style = NiyyahType.Badge,
                            color = Color(0xFF007354),
                        )
                    }
                }
            }
        }
        // Prayer consistency — node 1:1382 (live).
        StatCard(
            label = "PRAYER CONSISTENCY",
            iconRes = R.drawable.ic_clock,
            percent = report?.stats?.completionPercent ?: 0,
            ringColor = NiyyahColors.Navy,
            statLine = report?.let { "${it.stats.completedPrayers}/${it.stats.expectedPrayers} Prayers" } ?: "—",
            statSub = "Keep your rhythm",
        )
        // Quran progress — node 1:1401 (placeholder; no reading tracker yet).
        StatCard(
            label = "QURAN PROGRESS",
            iconRes = R.drawable.ic_quran_small,
            percent = 60,
            ringColor = RingGold,
            statLine = "3 Juz Read",
            statSub = "Steady pace",
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

/** Journey Flow — node 1:1420. Chart mirrors the frame (no weekly series yet). */
@Composable
private fun JourneyFlowCard() {
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
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF2F0EC), NiyyahShapeChip)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(text = "Daily", style = NiyyahType.Badge, color = Color(0xFF666666))
                }
                Box(
                    modifier = Modifier
                        .background(NiyyahColors.TextPrimary, NiyyahShapeChip)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(text = "Weekly", style = NiyyahType.Badge, color = Color.White)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(192.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.img_journey_chart),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AxisMarker(dotBottomPadding = 40.dp, label = "W1")
                AxisMarker(dotBottomPadding = 48.dp, label = "W2")
                AxisMarker(dotBottomPadding = 64.dp, label = "W3")
                AxisMarker(dotBottomPadding = 96.dp, label = "W4")
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

@Composable
private fun AxisMarker(dotBottomPadding: androidx.compose.ui.unit.Dp, label: String) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = dotBottomPadding)
                .size(6.dp)
                .background(NiyyahColors.TextPrimary, RoundedCornerShape(50)),
        )
        Text(text = label, style = NiyyahType.Badge, color = NiyyahColors.TextSecondary)
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
