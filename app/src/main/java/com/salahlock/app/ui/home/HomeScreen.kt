package com.salahlock.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.theme.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * BM-011 — Niyyah Home ("New homescreen" reference).
 *
 * Structure (top → bottom), theme-aware for light + dark:
 *   Header (NIYYAH centered · Clerk avatar right)  →  greeting + Hijri/Gregorian
 *   date  →  Next-Prayer hero (mosque atmosphere, live countdown, verify state,
 *   interval progress)  →  Today's prayers timeline  →  Daily Intention  →  Quick
 *   Actions (Qibla · Azkar · Bookmarks · Masjid)  →  Local Masjid card.
 *
 * All prayer/verification/location/identity values come from [HomeViewModel];
 * only static labels are hardcoded. System-state warnings (offline, location,
 * battery, lock, pause) render as hairline cards beneath the primary content.
 */
@Composable
fun HomeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToQibla: () -> Unit,
    onNavigateToAzkar: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToLocalMasjid: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.any { it.value }) viewModel.triggerLocationFetch()
    }

    val now = LocalDateTime.now()
    val currentPrayer = state.todayPrayers?.currentPrayer(now)
    val inWindow = currentPrayer != null &&
        state.todayPrayers?.isWithinWindow(currentPrayer.name, now) == true
    val heroPrayer = if (inWindow) currentPrayer else state.nextPrayer
    val heroVerified = inWindow && state.todayRecords[currentPrayer!!.name]
        ?.let { it.verified || it.overrideUsed } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HomeHeader(isLight = isLight, onAvatarClick = onNavigateToProfile)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            GreetingBlock(userName = state.userName)

            heroPrayer?.let { prayer ->
                NextPrayerHero(
                    isLight = isLight,
                    prayerName = prayer.name.displayName,
                    timeString = prayer.time.toClock(),
                    remaining = formatTimeRemaining(state.currentTimeMs, state.nextPrayer?.time ?: prayer.time),
                    locationLine = state.activeMasjidName.ifBlank { state.cityName },
                    progress = intervalProgress(state, now),
                    untilLabel = state.nextPrayer?.let {
                        "Until ${it.name.displayName}  •  ${it.time.toClock()}"
                    },
                    showVerify = inWindow && !heroVerified,
                    verified = heroVerified,
                    onVerify = { currentPrayer?.let { viewModel.verifyPrayer(it.name) } },
                )
            }

            state.todayPrayers?.let { daily ->
                PrayerTimeline(
                    isLight = isLight,
                    items = daily.toList(),
                    records = state.todayRecords,
                    currentName = if (inWindow) currentPrayer?.name else null,
                    nextName = state.nextPrayer?.name,
                )
            }

            DailyIntentionCard(isLight = isLight)

            HomeQuickActions(
                isLight = isLight,
                onQibla = onNavigateToQibla,
                onAzkar = onNavigateToAzkar,
                onBookmarks = onNavigateToBookmarks,
                onMasjid = onNavigateToLocalMasjid,
            )

            LocalMasjidCard(
                isLight = isLight,
                masjidName = state.activeMasjidName,
                jamaatLabel = if (state.activeMasjidName.isNotBlank()) {
                    state.nextPrayer?.let {
                        "${it.name.displayName} Jamaat  •  ${it.time.toClock()}"
                    }
                } else null,
                onOpen = onNavigateToLocalMasjid,
            )

            // ── System-state hairline warnings (kept from prior sprints) ──────────
            if (state.isOffline) {
                AlertHairlineCard(
                    icon = Icons.Outlined.CloudOff,
                    text = "You are offline. Prayer times are generated locally and may be slightly inaccurate.",
                )
            }
            if (state.isPaused) {
                val remainingMin =
                    ((state.pauseUntilMs - state.currentTimeMs).coerceAtLeast(0L) / 60_000L).toInt() + 1
                AlertHairlineCard(
                    icon = Icons.Outlined.PauseCircle,
                    text = "Niyyah is paused — resumes in ~$remainingMin min.",
                    actionLabel = "Resume",
                    onAction = { viewModel.resumePause() },
                )
            }
            if (state.locationMissing) {
                AlertHairlineCard(
                    icon = Icons.Outlined.LocationOn,
                    text = "Location is needed for accurate prayer times.",
                    actionLabel = "Enable",
                    onAction = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                )
            }
            if (state.batteryOptimizationNeeded) {
                AlertHairlineCard(
                    icon = Icons.Outlined.BatteryAlert,
                    text = "Allow Niyyah to run in the background so prayer alarms fire reliably.",
                    actionLabel = "Fix",
                    onAction = {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                )
            }
            if (state.isLockActive) {
                AlertHairlineCard(
                    icon = Icons.Outlined.Lock,
                    text = "Niyyah is protecting your prayer time.",
                )
            }
        }
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** Reference uses uppercase AM/PM (e.g. "12:46 PM"). */
private fun LocalDateTime.toClock(): String =
    format(TIME_FMT).uppercase(java.util.Locale.getDefault())

// ── Header: NIYYAH wordmark centered · Clerk avatar right ─────────────────────
@Composable
private fun HomeHeader(isLight: Boolean, onAvatarClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 48dp spacer balances the avatar so the wordmark stays optically centered.
        Spacer(Modifier.size(48.dp))
        Text(
            text = "NIYYAH",
            modifier = Modifier.weight(1f),
            fontFamily = NiyyahSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            letterSpacing = 4.sp,
            color = if (isLight) EmeraldPrimary else StitchDarkPrimaryBright,
            textAlign = TextAlign.Center,
        )
        com.salahlock.app.ui.navigation.ProfileAvatar(onClick = onAvatarClick)
    }
}

// ── Greeting + Hijri/Gregorian date ──────────────────────────────────────────
@Composable
private fun GreetingBlock(userName: String) {
    val hijriDate = remember {
        runCatching {
            val cal = android.icu.util.IslamicCalendar()
            val day = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
            val year = cal.get(android.icu.util.Calendar.YEAR)
            val months = listOf(
                "Muharram", "Safar", "Rabiʼ al-Awwal", "Rabiʼ al-Thani",
                "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Shaʼban",
                "Ramadan", "Shawwal", "Dhu al-Qiʼdah", "Dhu al-Hijjah",
            )
            "$day ${months.getOrElse(cal.get(android.icu.util.Calendar.MONTH)) { "" }} $year"
        }.getOrDefault("")
    }
    val gregorian = remember {
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }
    val firstName = userName.split(" ").firstOrNull().orEmpty()

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (firstName.isBlank()) "Assalamu Alaikum." else "Assalamu Alaikum, $firstName.",
            fontFamily = NiyyahSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = listOf(hijriDate, gregorian).filter { it.isNotBlank() }.joinToString("   •   "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Next-prayer hero ─────────────────────────────────────────────────────────
@Composable
private fun NextPrayerHero(
    isLight: Boolean,
    prayerName: String,
    timeString: String,
    remaining: String,
    locationLine: String,
    progress: Float,
    untilLabel: String?,
    showVerify: Boolean,
    verified: Boolean,
    onVerify: () -> Unit,
) {
    val accent = if (isLight) EmeraldPrimary else StitchDarkPrimaryBright
    val heroBg = if (isLight) Color(0xFFFBF8F1) else Color(0xFF0F1714)
    val heroBorder = if (isLight) LightDivider else Color(0xFF25332E)
    val nameColor = if (isLight) Navy else DarkTextPrimary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val sunColor = if (isLight) GoldAccent else StitchGold

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroBg)
            .border(1.dp, heroBorder, RoundedCornerShape(28.dp)),
    ) {
        // Provided mosque artwork (design/Reference/homescreenmosque.svg → vector
        // drawable). Rendered full-color, aspect preserved (Fit), pinned to the
        // top-right so it never overlaps the prayer text/countdown on the left.
        Box(Modifier.matchParentSize()) {
            Image(
                painter = painterResource(R.drawable.homescreenmosque),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopEnd,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(150.dp),
            )
        }

        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f))
                        .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Mosque,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "NEXT PRAYER",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.5.sp,
                        color = accent,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = prayerName,
                        fontFamily = NiyyahSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        letterSpacing = (-0.5).sp,
                        color = nameColor,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            timeString,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                        )
                    }
                    if (locationLine.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = muted,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                locationLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = muted,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = remaining,
                        fontFamily = NiyyahSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        letterSpacing = (-1).sp,
                        color = accent,
                    )
                    Text(
                        "remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                }
                if (verified) {
                    VerifyPill(
                        label = "Verified",
                        filled = false,
                        accent = accent,
                        onClick = null,
                    )
                } else if (showVerify) {
                    VerifyPill(
                        label = "Verify Salah",
                        filled = true,
                        accent = accent,
                        onClick = onVerify,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroProgressBar(
                    progress = progress,
                    accent = accent,
                    track = heroBorder,
                    marker = sunColor,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = sunColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (untilLabel != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    untilLabel,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 0.5.sp,
                    color = muted,
                )
            }
        }
    }
}

@Composable
private fun VerifyPill(label: String, filled: Boolean, accent: Color, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(50)
    val base = Modifier.then(
        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
    Row(
        modifier = base
            .clip(shape)
            .background(if (filled) accent else accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = if (filled) 0f else 0.30f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (filled) Icons.Outlined.TaskAlt else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (filled) Color.White else accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) Color.White else accent,
        )
    }
}

@Composable
private fun HeroProgressBar(
    progress: Float,
    accent: Color,
    track: Color,
    marker: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.height(8.dp).fillMaxWidth()) {
        val y = size.height / 2f
        val stroke = size.height
        // Track
        drawLine(
            color = track,
            start = Offset(stroke / 2f, y),
            end = Offset(size.width - stroke / 2f, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Elapsed
        val end = (stroke / 2f + (size.width - stroke) * progress.coerceIn(0f, 1f))
        drawLine(
            color = accent,
            start = Offset(stroke / 2f, y),
            end = Offset(end, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Warm-gold transition marker at the current position
        if (progress in 0.02f..0.98f) {
            drawCircle(color = marker, radius = stroke * 0.9f, center = Offset(end, y))
        }
    }
}

// ── Today's prayers timeline ─────────────────────────────────────────────────
@Composable
private fun PrayerTimeline(
    isLight: Boolean,
    items: List<com.salahlock.app.data.model.PrayerTime>,
    records: Map<PrayerName, com.salahlock.app.data.db.entity.PrayerRecord>,
    currentName: PrayerName?,
    nextName: PrayerName?,
) {
    val accent = if (isLight) EmeraldPrimary else StitchDarkPrimaryBright
    val track = MaterialTheme.colorScheme.outline
    val gold = if (isLight) GoldAccent else StitchGold
    val currentIndex = items.indexOfFirst { it.name == currentName }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "TODAY'S PRAYERS",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Names
        Row(Modifier.fillMaxWidth()) {
            items.forEach { p ->
                val isCurrent = p.name == currentName
                Text(
                    text = p.name.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Node row with connecting line + gold transition dot behind the nodes
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val y = size.height / 2f
                val cell = size.width / items.size
                val firstX = cell * 0.5f
                val lastX = size.width - cell * 0.5f
                drawLine(
                    color = track.copy(alpha = 0.5f),
                    start = Offset(firstX, y),
                    end = Offset(lastX, y),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
                if (currentIndex >= 0) {
                    val curX = cell * (currentIndex + 0.5f)
                    // Emerald leading segment up to the current node
                    drawLine(
                        color = accent,
                        start = Offset(firstX, y),
                        end = Offset(curX, y),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                    // Warm-gold "next transition" dot just after the current node
                    if (currentIndex < items.size - 1) {
                        drawCircle(
                            color = gold,
                            radius = 5f,
                            center = Offset(curX + cell * 0.5f, y),
                        )
                    }
                }
            }
            Row(Modifier.matchParentSize()) {
                items.forEach { p ->
                    val record = records[p.name]
                    val completed = record?.let { it.verified || it.overrideUsed } == true
                    val isCurrent = p.name == currentName
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TimelineNode(completed = completed, isCurrent = isCurrent, accent = accent, track = track)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Times
        Row(Modifier.fillMaxWidth()) {
            items.forEach { p ->
                val isCurrent = p.name == currentName
                Text(
                    text = p.time.toClock(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TimelineNode(completed: Boolean, isCurrent: Boolean, accent: Color, track: Color) {
    when {
        isCurrent -> Box(
            Modifier
                .size(22.dp)
                .background(accent, CircleShape)
                .border(3.dp, accent.copy(alpha = 0.25f), CircleShape)
        )
        completed -> Box(
            Modifier.size(20.dp).background(accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "completed",
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
        else -> Box(
            Modifier
                .size(16.dp)
                .background(MaterialTheme.colorScheme.background, CircleShape)
                .border(2.dp, track.copy(alpha = 0.7f), CircleShape)
        )
    }
}

// ── Daily Intention ──────────────────────────────────────────────────────────
private val Intentions = listOf(
    "To approach every task today with patience, seeking only His pleasure.",
    "To act with patience and seek understanding before reacting.",
    "To be present in every prayer, unhurried and sincere.",
    "To speak gently and assume the best of others today.",
    "To be grateful for one small blessing I usually overlook.",
    "To begin everything today with bismillah and full presence.",
    "To let go of one worry and trust in His plan.",
)

@Composable
private fun DailyIntentionCard(isLight: Boolean) {
    val intention = remember { Intentions[java.time.LocalDate.now().dayOfYear % Intentions.size] }
    val accent = if (isLight) EmeraldPrimary else StitchDarkPrimaryBright
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
    ) {
        // Subtle botanical detail in the corner.
        Icon(
            Icons.Outlined.Spa,
            contentDescription = null,
            tint = (if (isLight) GoldAccent else StitchGold).copy(alpha = 0.20f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(56.dp),
        )
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "DAILY INTENTION",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "“$intention”",
                fontFamily = NiyyahSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = 21.sp,
                lineHeight = 30.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(0.82f),
            )
        }
    }
}

// ── Quick actions ────────────────────────────────────────────────────────────
@Composable
private fun HomeQuickActions(
    isLight: Boolean,
    onQibla: () -> Unit,
    onAzkar: () -> Unit,
    onBookmarks: () -> Unit,
    onMasjid: () -> Unit,
) {
    val accent = if (isLight) EmeraldPrimary else StitchDarkPrimaryBright
    Column(Modifier.fillMaxWidth()) {
        Text(
            "QUICK ACTIONS",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard(Modifier.weight(1f), accent, painter = null, icon = Icons.Outlined.Explore, label = "Qibla", onClick = onQibla)
            QuickActionCard(Modifier.weight(1f), accent, painter = R.drawable.ic_tasbih, icon = null, label = "Azkar", onClick = onAzkar)
            QuickActionCard(Modifier.weight(1f), accent, painter = null, icon = Icons.Outlined.BookmarkBorder, label = "Bookmarks", onClick = onBookmarks)
            QuickActionCard(Modifier.weight(1f), accent, painter = null, icon = Icons.Outlined.Mosque, label = "Masjid", onClick = onMasjid)
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    accent: Color,
    painter: Int?,
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (painter != null) {
            Icon(
                painter = painterResource(painter),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// ── Local masjid ─────────────────────────────────────────────────────────────
@Composable
private fun LocalMasjidCard(
    isLight: Boolean,
    masjidName: String,
    jamaatLabel: String?,
    onOpen: () -> Unit,
) {
    val accent = if (isLight) EmeraldPrimary else StitchDarkPrimaryBright
    val configured = masjidName.isNotBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (configured) masjidName else "Your local masjid",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (configured) (jamaatLabel ?: "Jamaat timings configured")
                    else "Set up your local masjid to receive accurate jamaat timings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (configured) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Set up",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

// ── Alert cards ──────────────────────────────────────────────────────────────
@Composable
private fun AlertHairlineCard(
    icon: ImageVector,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable(onClick = onAction),
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────
/** Fraction elapsed through the current prayer interval (most-recent past → next). */
private fun intervalProgress(state: HomeUiState, now: LocalDateTime): Float {
    val start = state.todayPrayers?.toList()?.lastOrNull { !it.time.isAfter(now) }?.time ?: return 0f
    val end = state.nextPrayer?.time ?: return 0f
    if (!end.isAfter(start)) return 0f
    val total = Duration.between(start, end).toMillis().toFloat()
    val done = Duration.between(start, now).toMillis().toFloat()
    return (done / total).coerceIn(0f, 1f)
}

fun formatTimeRemaining(nowMs: Long, target: LocalDateTime): String {
    val now = LocalDateTime.now()
    val duration = Duration.between(now, target)
    if (duration.isNegative) return "—"
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
