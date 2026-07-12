package com.salahlock.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.theme.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            viewModel.triggerLocationFetch()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle background glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            EmeraldPrimary.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Main scrollable content
        val scrollState = rememberScrollState()
        // Header fades subtly as content scrolls — clamped so it never disappears fully.
        val headerAlpha = (1f - (scrollState.value / 300f)).coerceIn(0.6f, 1f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // SL-001: respect status-bar/cutout insets instead of a hardcoded 48dp —
                // keeps the avatar fully visible on hole-punch + gesture-nav devices.
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 120.dp), // Extra bottom padding for floating nav
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            HomeHeader(
                locationName = state.activeMasjidName.ifBlank { state.cityName },
                locationLabel = if (state.activeMasjidName.isNotBlank()) "Local Timings" else null,
                userName = state.userName,
                onAvatarClick = onNavigateToProfile,
                modifier = Modifier.alpha(headerAlpha),
            )
            
            if (state.isOffline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = "Offline",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "You are offline. Prayer times are generated locally and may be slightly inaccurate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            // ═══ VIEWPORT 1 — PRAYER EXPERIENCE (hero) ═══════════════════════════
            // SL-013: the hero previously counted down to currentPrayer.time — a moment
            // already in the past — which clamped to a frozen "00:00" (worst after Isha,
            // where it sat all night). Now:
            //  • during a prayer window → countdown to the NEXT prayer (= window close)
            //  • after the window (e.g. post-Isha) → "Next: Fajr" hero with real countdown
            val now = LocalDateTime.now()
            val currentPrayer = state.todayPrayers?.currentPrayer(now)
            val inWindow = currentPrayer != null &&
                state.todayPrayers?.isWithinWindow(currentPrayer.name, now) == true
            if (currentPrayer != null && inWindow) {
                CurrentPrayerHeroCard(
                    prayerName = currentPrayer.name.name,
                    timeString = currentPrayer.time.format(DateTimeFormatter.ofPattern("hh:mm a")),
                    timeRemaining = state.nextPrayer?.let {
                        formatTimeRemaining(state.currentTimeMs, it.time)
                    } ?: "—",
                )
                // Verify the current prayer directly from Home — reuses StreakRepository
                // (single source of truth; same path the lock overlay records through).
                val verified = state.todayRecords[currentPrayer.name]
                    ?.let { it.verified || it.overrideUsed } == true
                HomeVerifyRow(
                    prayerName = currentPrayer.name.displayName,
                    verified = verified,
                    onVerify = { viewModel.verifyPrayer(currentPrayer.name) },
                )
            } else if (state.nextPrayer != null) {
                CurrentPrayerHeroCard(
                    prayerName = state.nextPrayer!!.name.name,
                    timeString = state.nextPrayer!!.time.format(DateTimeFormatter.ofPattern("hh:mm a")),
                    timeRemaining = formatTimeRemaining(state.currentTimeMs, state.nextPrayer!!.time)
                )
            }

            // Next Prayer Card — only when the hero is showing the CURRENT prayer
            // (when the hero itself already shows "next", this card would duplicate it)
            if (currentPrayer != null && inWindow && state.nextPrayer != null) {
                NextPrayerCard(
                    prayerName = state.nextPrayer!!.name.name,
                    timeString = state.nextPrayer!!.time.format(DateTimeFormatter.ofPattern("hh:mm a")),
                    timeRemaining = formatTimeRemaining(state.currentTimeMs, state.nextPrayer!!.time)
                )
            }

            // Today's Salah + Streak
            val completedCount = state.todayRecords.size
            DailyProgressRing(
                progress = completedCount / 5f,
                completed = completedCount,
                total = 5,
                streakDays = state.streakInfo.currentStreak,
            )

            // Pause SalahLock — a primary action inside the hero, not a utility
            PauseSalahLockCard(
                isPaused = state.isPaused,
                pauseUntilMs = state.pauseUntilMs,
                currentTimeMs = state.currentTimeMs,
                onPauseMinutes = { viewModel.pauseForMinutes(it) },
                onPauseUntilNextPrayer = { viewModel.pauseUntilNextPrayer() },
                onResume = { viewModel.resumePause() },
            )

            // ═══ VIEWPORT 2 — PRAYER TIMELINE ════════════════════════════════════
            state.todayPrayers?.let { daily ->
                PrayerTimeline(
                    prayers = listOf(
                        PrayerTimelineItem(PrayerName.FAJR, daily.fajr, state.todayRecords.values.find { it.prayerName == PrayerName.FAJR.name }),
                        PrayerTimelineItem(PrayerName.DHUHR, daily.dhuhr, state.todayRecords.values.find { it.prayerName == PrayerName.DHUHR.name }),
                        PrayerTimelineItem(PrayerName.ASR, daily.asr, state.todayRecords.values.find { it.prayerName == PrayerName.ASR.name }),
                        PrayerTimelineItem(PrayerName.MAGHRIB, daily.maghrib, state.todayRecords.values.find { it.prayerName == PrayerName.MAGHRIB.name }),
                        PrayerTimelineItem(PrayerName.ISHA, daily.isha, state.todayRecords.values.find { it.prayerName == PrayerName.ISHA.name })
                    ),
                    currentPrayerName = currentPrayer?.name
                )
            }

            // ═══ LOCAL MASJID — directly below the Prayer Journey, above Qibla ════
            // ALWAYS rendered (Sprint N.3.7) — shows an empty "Setup" state when no
            // masjid is configured so first-time users can discover the feature.
            // When a masjid is active, state.nextPrayer already reflects its jamaat
            // times, so no extra data access is needed for the "next jamaat" line.
            val masjidConfigured = state.activeMasjidName.isNotBlank()
            val jamaatLabel = if (masjidConfigured) {
                state.nextPrayer?.let {
                    "${it.name.displayName} Jamaat • ${it.time.format(DateTimeFormatter.ofPattern("h:mm a"))}"
                }
            } else null
            LocalMasjidCard(
                name = state.activeMasjidName,
                jamaatLabel = jamaatLabel,
                jummaLabel = state.jummaTime.takeIf { it.isNotBlank() && masjidConfigured }?.let { hhmm ->
                    runCatching {
                        "Jumma • " + java.time.LocalTime.parse(hhmm)
                            .format(DateTimeFormatter.ofPattern("h:mm a"))
                    }.getOrNull()
                },
                onEdit = onNavigateToLocalMasjid,
                onOpen = onNavigateToLocalMasjid,
            )

            // ═══ QIBLA ═══════════════════════════════════════════════════════════
            QiblaCard(bearing = state.qiblaBearing, onOpen = onNavigateToQibla)
            // SalahLock statistics card removed (Sprint N.3.5) — lock analytics now
            // live only in the Lock Apps tab. Business logic (lockSummary) untouched.

            // Put warnings at the very bottom
            if (state.locationMissing) {
                LocationMissingWarning(onRequestPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                })
            }
            if (state.batteryOptimizationNeeded) {
                BatteryOptimizationWarning(
                    onFix = {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }
            if (state.isLockActive) {
                LockActiveWarning()
            }
        }
    }
}

data class PrayerTimelineItem(val name: PrayerName, val time: LocalDateTime, val record: PrayerRecord?)

// ─────────────────────────────────────────────────────────────────────────────
// Sprint N.3 — Home dashboard cards: Pause, Qibla, Local Masjid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PauseSalahLockCard(
    isPaused: Boolean,
    pauseUntilMs: Long,
    currentTimeMs: Long,
    onPauseMinutes: (Int) -> Unit,
    onPauseUntilNextPrayer: () -> Unit,
    onResume: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏸", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Pause Niyyah",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (isPaused) {
                val remainingMin = ((pauseUntilMs - currentTimeMs).coerceAtLeast(0L) / 60_000L).toInt() + 1
                Text(
                    "Paused — resumes in ~$remainingMin min",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldAccent,
                )
                Spacer(Modifier.height(12.dp))
                PauseChip("Resume now", filled = true, onClick = onResume)
            } else {
                Text(
                    "Pause restrictions temporarily",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PauseChip("15m", modifier = Modifier.weight(1f)) { onPauseMinutes(15) }
                    PauseChip("30m", modifier = Modifier.weight(1f)) { onPauseMinutes(30) }
                    PauseChip("1h", modifier = Modifier.weight(1f)) { onPauseMinutes(60) }
                }
                Spacer(Modifier.height(8.dp))
                PauseChip("Until next prayer", modifier = Modifier.fillMaxWidth()) { onPauseUntilNextPrayer() }
            }
        }
    }
}

@Composable
private fun PauseChip(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) EmeraldPrimary else EmeraldPrimary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) Color.White else EmeraldPrimary,
        )
    }
}

@Composable
private fun QiblaCard(bearing: Int, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧭", fontSize = 28.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Qibla",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (bearing > 0) "Direction: $bearing°" else "Set location to calculate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Open →",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = EmeraldPrimary,
            )
        }
    }
}

@Composable
private fun LocalMasjidCard(
    name: String,
    jamaatLabel: String?,
    jummaLabel: String? = null,
    onEdit: () -> Unit,
    onOpen: () -> Unit,
) {
    val configured = name.isNotBlank()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🕌", fontSize = 28.sp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (configured) "YOUR MASJID" else "LOCAL MASJID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSage,
                        letterSpacing = 1.2.sp,
                    )
                    // SL-017 — hierarchy: name (large) → NEXT JAMAAT (emphasis) → Jumma
                    Text(
                        if (configured) name else "No masjid selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (configured && jamaatLabel != null) {
                        Text(
                            "NEXT JAMAAT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSage,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            jamaatLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldPrimary,
                        )
                    } else {
                        Text(
                            if (configured) "Tap to view timings" else "Add your local masjid timings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (jummaLabel != null) {
                        Text(
                            jummaLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldAccent,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (configured) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Edit",
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldPrimary,
                    )
                    Text(
                        "Open →",
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldPrimary,
                    )
                }
            } else {
                Text(
                    "Setup →",
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldPrimary,
                )
            }
        }
    }
}

@Composable
fun HomeHeader(
    locationName: String,
    locationLabel: String? = null,
    userName: String = "",
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hijri date via Android ICU (available API 24+, minSdk is 26)
    val hijriDate = remember {
        runCatching {
            val cal = android.icu.util.IslamicCalendar()
            val day = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
            val months = listOf(
                "Muharram", "Safar", "Rabiʼ al-Awwal", "Rabiʼ al-Thani",
                "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Shaʼban",
                "Ramadan", "Shawwal", "Dhu al-Qiʼdah", "Dhu al-Hijjah"
            )
            val month = months.getOrElse(cal.get(android.icu.util.Calendar.MONTH)) { "" }
            "$day $month"
        }.getOrDefault("")
    }
    val dayOfWeek = remember {
        LocalDateTime.now().dayOfWeek
            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
    }
    val firstName = userName.ifBlank { "Musafir" }.split(" ").firstOrNull() ?: "Musafir"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // Stitch V2 greeting: single serif display line, sans date line below
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = "Assalamu Alaikum, $firstName.",
                fontFamily = NiyyahSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.25).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = buildString {
                    if (hijriDate.isNotEmpty()) { append(hijriDate); append(" • ") }
                    append(dayOfWeek)
                    if (locationName.isNotEmpty()) { append(" — "); append(locationName) }
                    if (locationLabel != null) { append(" · "); append(locationLabel) }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )
        }
        Spacer(Modifier.width(12.dp))

        // Account avatar — Google photo → initials → person icon (reused app-wide)
        com.salahlock.app.ui.navigation.ProfileAvatar(onClick = onAvatarClick)
    }
}

/**
 * Stitch V2 hero — mode-asymmetric:
 *  light/home: filled navy card, "NEXT PRAYER" pill, big serif prayer name.
 *  dark/home:  bordered surface card, "UPCOMING PRAYER" emerald label,
 *              emerald countdown + "REMAINING".
 */
@Composable
fun CurrentPrayerHeroCard(prayerName: String, timeString: String, timeRemaining: String) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    if (isLight) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Navy),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "NEXT PRAYER",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            letterSpacing = 1.5.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            timeString,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            "in $timeRemaining",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
                Text(
                    text = prayerName,
                    fontFamily = NiyyahSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    letterSpacing = (-0.5).sp,
                    color = Color.White,
                )
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "UPCOMING PRAYER",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldSecondary,
                    letterSpacing = 2.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = prayerName,
                        fontFamily = NiyyahSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                Column {
                    Text(
                        text = timeRemaining,
                        fontFamily = NiyyahSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        color = StitchDarkPrimaryBright,
                    )
                    Text(
                        text = "REMAINING",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 2.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun NextPrayerCard(prayerName: String, timeString: String, timeRemaining: String) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "NEXT",
                color = MutedSage,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = prayerName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeString,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$timeRemaining away",
                color = MutedSage,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * Vertical prayer timeline — replaces the horizontal circle grid.
 *
 * Shows each prayer as a row with:
 *  • Dot + vertical connector on the left
 *  • Prayer name + time in the center
 *  • Status badge (✓ / NOW / NEXT) on the right
 *
 * Emerald is used only for the active dot and NOW label — no fills.
 */
@Composable
fun PrayerTimeline(prayers: List<PrayerTimelineItem>, currentPrayerName: PrayerName?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "TODAY'S PRAYER JOURNEY",
            color = MutedSage,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        val now = LocalDateTime.now()
        val nextUpcomingIndex = prayers.indexOfFirst { it.time.isAfter(now) }

        // SL-003 — timeline lives in a surface card for visual weight; rows keep
        // the exact same state logic (completed / current / upcoming untouched).
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {

        prayers.forEachIndexed { index, item ->
            val isCurrent = item.name == currentPrayerName
            val isVerified = item.record != null && (item.record.verified || item.record.overrideUsed)
            val isPast = item.time.isBefore(now) && !isCurrent
            val isNext = !isCurrent && index == nextUpcomingIndex && !isPast
            val isLast = index == prayers.lastIndex

            val dotColor by animateColorAsState(
                targetValue = when {
                    isCurrent -> EmeraldPrimary
                    isVerified -> EmeraldPrimary.copy(alpha = 0.45f)
                    isPast -> MutedSage.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = MotionTokens.normalTween(),
                label = "dot_$index",
            )
            val dotScale by animateFloatAsState(
                targetValue = if (isCurrent) 1.2f else 1f,
                animationSpec = MotionTokens.noBounceSpring(),
                label = "dotScale_$index",
            )

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top,
            ) {
                // Left rail — dot + connector line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(32.dp),
                ) {
                    Spacer(Modifier.height(2.dp))
                    // SL-003 — clearer progress node: verified shows a check inside,
                    // current gets an emerald ring, upcoming stays hollow.
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                            .clip(CircleShape)
                            .background(dotColor)
                            .then(
                                if (isCurrent) Modifier.border(2.dp, EmeraldPrimary.copy(alpha = 0.35f), CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isVerified) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    if (isPast || isCurrent) EmeraldPrimary.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Center — prayer name + time
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (isLast) 0.dp else 16.dp),
                ) {
                    Text(
                        text = item.name.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.onSurface
                            isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        },
                    )
                    Text(
                        text = item.time.format(DateTimeFormatter.ofPattern("h:mm a")),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPast && !isCurrent) MutedSage.copy(alpha = 0.45f) else MutedSage,
                    )
                }

                // Right — status badge
                Box(
                    modifier = Modifier.padding(top = 2.dp, bottom = if (isLast) 0.dp else 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    when {
                        isVerified -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Prayed",
                            tint = EmeraldPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                        isCurrent -> Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldPrimary.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = "NOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        isNext -> Text(
                            text = "NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSage,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
        }
        } // inner Column (SL-003 card body)
        } // Card
    }
}

@Composable
fun DailyProgressRing(progress: Float, completed: Int, total: Int, streakDays: Int = 0) {
    // Animate the ring fill from its previous value. animateFloatAsState shows the
    // target immediately on first composition (no zero-restart) and animates only
    // when the value actually changes — exactly the required behavior.
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = MotionTokens.slowTween(),
        label = "ringProgress",
    )

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else ElevatedSurface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (isLight) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else BorderStroke(1.dp, GoldAccent.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Today's Salah",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MutedSage,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = when (completed) {
                        0 -> "Not started yet"
                        total -> "All five prayed · جزاكم الله"
                        else -> "$completed of $total completed"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (streakDays > 0) {
                    Spacer(Modifier.height(4.dp))
                    // Scale-pop when the streak value changes (1.0 → 0.9 → 1.05 → 1.0, no bounce).
                    val streakScale = remember { Animatable(1f) }
                    LaunchedEffect(streakDays) {
                        streakScale.snapTo(0.9f)
                        streakScale.animateTo(1.05f, MotionTokens.fastTween())
                        streakScale.animateTo(1f, MotionTokens.fastTween())
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            scaleX = streakScale.value
                            scaleY = streakScale.value
                        },
                    ) {
                        Text("🔥", fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$streakDays day streak",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = GoldAccent,
                        )
                    }
                }
            }

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(76.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    strokeWidth = 7.dp,
                    strokeCap = StrokeCap.Round,
                )
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(76.dp),
                    color = EmeraldPrimary,
                    trackColor = Color.Transparent,
                    strokeWidth = 7.dp,
                    strokeCap = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$completed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "/ $total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// Sprint N.3.3 — Quick Actions grid removed (duplicate navigation; Qibla/Azkar/
// Hadith/Masjid now have dedicated cards or live in the Knowledge tab).

/**
 * Home verification row — lets the user mark the current prayer prayed without the
 * lock overlay. Reuses [HomeViewModel.verifyPrayer] → StreakRepository → PrayerRecord
 * (single source of truth). Shows a completed state once recorded.
 */
@Composable
private fun HomeVerifyRow(prayerName: String, verified: Boolean, onVerify: () -> Unit) {
    if (verified) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$prayerName completed",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = EmeraldPrimary,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EmeraldPrimary)
                .clickable(onClick = onVerify)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "I Prayed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
fun LocationMissingWarning(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onRequestPermission() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Location access needed to calculate accurate prayer times.",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun BatteryOptimizationWarning(onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onFix() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Battery optimization may block prayer alarms.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Tap to exempt Niyyah (required for MIUI, Samsung, Realme).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
fun LockActiveWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Niyyah is protecting your prayer time.",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// Format time remaining
fun formatTimeRemaining(currentMs: Long, target: LocalDateTime): String {
    val targetMs = target.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    var diffMs = targetMs - currentMs
    if (diffMs < 0) diffMs = 0

    val h = (diffMs / (1000 * 60 * 60)).toInt()
    val m = ((diffMs / (1000 * 60)) % 60).toInt()
    val s = ((diffMs / 1000) % 60).toInt()

    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
