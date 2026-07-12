package com.salahlock.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.theme.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Stitch V2 Home (design/stitch-v2 light/home + dark/home).
 *
 * Light: NIYYAH bar → centered date + "Assalamu Alaikum" → Today's Intention
 * card → navy hero (NEXT PRAYER pill, serif name, location, action pill) →
 * horizontal daily-prayer chip carousel.
 * Dark: NIYYAH bar → left greeting → bordered hero with emerald countdown →
 * prayer chip grid → Daily Intention card.
 *
 * Utilities not present in the Stitch layout (pause, Qibla, Local Masjid) live
 * behind the design's menu button as a bottom sheet, so no functionality is
 * lost. Conditional system warnings render as hairline alert cards.
 */
@Composable
fun HomeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToBlacklist: () -> Unit,
    onNavigateToQibla: () -> Unit,
    onNavigateToAzkar: () -> Unit,
    onNavigateToLocalMasjid: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    var showMenuSheet by remember { mutableStateOf(false) }

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
        NiyyahTopBar(
            onMenuClick = { showMenuSheet = true },
            onAvatarClick = onNavigateToProfile,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            if (state.isOffline) {
                AlertHairlineCard(
                    icon = Icons.Outlined.CloudOff,
                    text = "You are offline. Prayer times are generated locally and may be slightly inaccurate.",
                )
            }

            GreetingSection(isLight = isLight, userName = state.userName)

            if (isLight) IntentionCard()

            heroPrayer?.let { prayer ->
                val remaining = if (inWindow) {
                    state.nextPrayer?.let { formatTimeRemaining(state.currentTimeMs, it.time) } ?: "—"
                } else {
                    formatTimeRemaining(state.currentTimeMs, prayer.time)
                }
                PrayerHero(
                    isLight = isLight,
                    prayerName = prayer.name.displayName,
                    timeString = prayer.time.format(DateTimeFormatter.ofPattern("h:mm a")),
                    remaining = remaining,
                    locationLine = state.activeMasjidName.ifBlank { state.cityName },
                    showVerify = inWindow && !heroVerified,
                    verified = heroVerified,
                    onVerify = { currentPrayer?.let { viewModel.verifyPrayer(it.name) } },
                )
            }

            state.todayPrayers?.let { daily ->
                DailyPrayersSection(
                    isLight = isLight,
                    items = listOf(
                        Triple(PrayerName.FAJR, daily.fajr, state.todayRecords[PrayerName.FAJR]),
                        Triple(PrayerName.DHUHR, daily.dhuhr, state.todayRecords[PrayerName.DHUHR]),
                        Triple(PrayerName.ASR, daily.asr, state.todayRecords[PrayerName.ASR]),
                        Triple(PrayerName.MAGHRIB, daily.maghrib, state.todayRecords[PrayerName.MAGHRIB]),
                        Triple(PrayerName.ISHA, daily.isha, state.todayRecords[PrayerName.ISHA]),
                    ),
                    currentPrayerName = if (inWindow) currentPrayer?.name else null,
                    nextPrayerName = state.nextPrayer?.name,
                )
            }

            if (!isLight) IntentionCard()

            // Masjid line — hairline card per Stitch card language
            MasjidRow(
                name = state.activeMasjidName,
                jamaatLabel = if (state.activeMasjidName.isNotBlank()) {
                    state.nextPrayer?.let {
                        "${it.name.displayName} Jamaat • ${it.time.format(DateTimeFormatter.ofPattern("h:mm a"))}"
                    }
                } else null,
                onOpen = onNavigateToLocalMasjid,
            )

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

    if (showMenuSheet) {
        HomeMenuSheet(
            isPaused = state.isPaused,
            onDismiss = { showMenuSheet = false },
            onQibla = { showMenuSheet = false; onNavigateToQibla() },
            onMasjid = { showMenuSheet = false; onNavigateToLocalMasjid() },
            onAzkar = { showMenuSheet = false; onNavigateToAzkar() },
            onPauseMinutes = { viewModel.pauseForMinutes(it); showMenuSheet = false },
            onPauseUntilNextPrayer = { viewModel.pauseUntilNextPrayer(); showMenuSheet = false },
            onResume = { viewModel.resumePause(); showMenuSheet = false },
        )
    }
}

// ── Top bar: menu · NIYYAH serif wordmark · avatar ───────────────────────────
@Composable
private fun NiyyahTopBar(onMenuClick: () -> Unit, onAvatarClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                Icons.Outlined.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "NIYYAH",
            fontFamily = NiyyahSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            letterSpacing = 3.sp,
            color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                Navy
            } else {
                StitchDarkPrimaryBright
            },
        )
        com.salahlock.app.ui.navigation.ProfileAvatar(onClick = onAvatarClick)
    }
}

// ── Greeting ─────────────────────────────────────────────────────────────────
@Composable
private fun GreetingSection(isLight: Boolean, userName: String) {
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
    val firstName = userName.ifBlank { "" }.split(" ").firstOrNull().orEmpty()

    if (isLight) {
        // light/home: centered uppercase date, then serif display greeting
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = listOf(hijriDate.uppercase(), gregorian.uppercase())
                    .filter { it.isNotBlank() }
                    .joinToString("  •  "),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Assalamu Alaikum",
                fontFamily = NiyyahSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 40.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.8).sp,
                color = Navy,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        // dark/home: left-aligned serif greeting with name, date below
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = if (firstName.isBlank()) "Assalamu Alaikum." else "Assalamu Alaikum, $firstName.",
                fontFamily = NiyyahSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 34.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(hijriDate, gregorian).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Today's Intention ────────────────────────────────────────────────────────
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
private fun IntentionCard() {
    val intention = remember { Intentions[java.time.LocalDate.now().dayOfYear % Intentions.size] }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(24.dp)) {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = if (isLight) EmeraldPrimary else StitchDarkPrimaryBright,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = if (isLight) "TODAY'S INTENTION" else "DAILY INTENTION",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "“$intention”",
                    fontFamily = NiyyahSerif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ── Prayer hero ──────────────────────────────────────────────────────────────
@Composable
private fun PrayerHero(
    isLight: Boolean,
    prayerName: String,
    timeString: String,
    remaining: String,
    locationLine: String,
    showVerify: Boolean,
    verified: Boolean,
    onVerify: () -> Unit,
) {
    if (isLight) {
        // Navy 32dp-radius hero with soft navy shadow
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Navy,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Box(
                            Modifier
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "NEXT PRAYER",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                letterSpacing = 1.sp,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = prayerName,
                            fontFamily = NiyyahSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 48.sp,
                            lineHeight = 56.sp,
                            letterSpacing = (-1).sp,
                            color = Color.White,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            timeString,
                            fontFamily = NiyyahSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 24.sp,
                            lineHeight = 32.sp,
                            color = Color.White,
                        )
                        Text(
                            "in $remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (locationLine.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                locationLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                    if (showVerify || verified) {
                        Surface(
                            onClick = onVerify,
                            enabled = showVerify,
                            shape = RoundedCornerShape(50),
                            color = Color.White,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (verified) Icons.Filled.CheckCircle else Icons.Outlined.TaskAlt,
                                    contentDescription = null,
                                    tint = Navy,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (verified) "Prayer Verified" else "Verify Prayer",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Navy,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Bordered surface hero with emerald serif countdown
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "UPCOMING PRAYER",
                    style = MaterialTheme.typography.labelMedium,
                    color = StitchDarkPrimaryBright,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = prayerName,
                    fontFamily = NiyyahSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (locationLine.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$locationLine • $timeString",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = remaining,
                    fontFamily = NiyyahSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    color = StitchDarkPrimaryBright,
                )
                Text(
                    "REMAINING",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                )
                if (showVerify || verified) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        onClick = onVerify,
                        enabled = showVerify,
                        shape = RoundedCornerShape(4.dp),
                        color = if (verified) MaterialTheme.colorScheme.surfaceVariant else EmeraldSecondary,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (verified) Icons.Filled.CheckCircle else Icons.Outlined.TaskAlt,
                                contentDescription = null,
                                tint = if (verified) StitchDarkPrimaryBright else DarkTextPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (verified) "Prayer Verified" else "Verify Prayer",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (verified) StitchDarkPrimaryBright else DarkTextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Daily prayers ────────────────────────────────────────────────────────────
@Composable
private fun DailyPrayersSection(
    isLight: Boolean,
    items: List<Triple<PrayerName, LocalDateTime, com.salahlock.app.data.db.entity.PrayerRecord?>>,
    currentPrayerName: PrayerName?,
    nextPrayerName: PrayerName?,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "DAILY PRAYERS",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (isLight) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items.forEach { (name, time, record) ->
                    PrayerChipCard(
                        isLight = true,
                        name = name,
                        time = time,
                        verified = record?.let { it.verified || it.overrideUsed } == true,
                        isActive = name == currentPrayerName || (currentPrayerName == null && name == nextPrayerName),
                        modifier = Modifier.width(112.dp),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { (name, time, record) ->
                            PrayerChipCard(
                                isLight = false,
                                name = name,
                                time = time,
                                verified = record?.let { it.verified || it.overrideUsed } == true,
                                isActive = name == currentPrayerName || (currentPrayerName == null && name == nextPrayerName),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerChipCard(
    isLight: Boolean,
    name: PrayerName,
    time: LocalDateTime,
    verified: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = if (isLight) Navy else StitchDarkPrimaryBright
    val border = when {
        isActive && isLight -> BorderStroke(2.dp, Navy)
        isActive -> BorderStroke(1.dp, StitchDarkPrimaryBright.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    val container = when {
        isActive && !isLight -> EmeraldSecondary.copy(alpha = 0.05f)
        else -> MaterialTheme.colorScheme.surface
    }
    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(if (isLight) 16.dp else 8.dp),
            color = container,
            border = border,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (verified) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "${name.displayName} verified",
                        tint = if (isLight) EmeraldPrimary else StitchDarkPrimaryBright,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = name.displayName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp,
                    color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = time.format(DateTimeFormatter.ofPattern("h:mm a")),
                    fontFamily = NiyyahSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (isActive) {
            // Gold dot marker on the active chip (light export detail)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-4).dp)
                    .size(8.dp)
                    .background(GoldAccent, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

// ── Masjid row ───────────────────────────────────────────────────────────────
@Composable
private fun MasjidRow(name: String, jamaatLabel: String?, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Mosque,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { "Set up your local masjid" },
                    fontFamily = NiyyahSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (jamaatLabel != null) {
                    Text(
                        jamaatLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            Icon(
                icon,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(20.dp),
            )
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

// ── Menu sheet: utilities not present in the Stitch home layout ─────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeMenuSheet(
    isPaused: Boolean,
    onDismiss: () -> Unit,
    onQibla: () -> Unit,
    onMasjid: () -> Unit,
    onAzkar: () -> Unit,
    onPauseMinutes: (Int) -> Unit,
    onPauseUntilNextPrayer: () -> Unit,
    onResume: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            SheetRow(Icons.Outlined.Explore, "Qibla", onQibla)
            SheetRow(Icons.Outlined.Mosque, "Local Masjid", onMasjid)
            SheetRow(Icons.Outlined.MenuBook, "Azkar", onAzkar)
            HorizontalDivider(
                Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                "PAUSE NIYYAH",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (isPaused) {
                SheetRow(Icons.Outlined.PlayCircle, "Resume now", onResume)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { min ->
                        Surface(
                            onClick = { onPauseMinutes(min) },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (min == 60) "1h" else "${min}m",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = onPauseUntilNextPrayer,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Until next prayer",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────
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
