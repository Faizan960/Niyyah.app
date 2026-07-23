package com.salahlock.app.ui.profile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.theme.*
import com.salahlock.app.data.preferences.UserPreferences.ThemePreference
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salahlock.app.theme.MotionTokens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBlacklist: () -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item { ProfileHeader(state, viewModel) }
            // Monthly Spiritual Reflection — private frozen monthly reports.
            item {
                SectionTitle("Your Journey")
                CardGroup {
                    SettingsRowItem(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Monthly Reflections",
                        subtitle = "Your private spiritual report, month by month",
                        onClick = { onNavigate("monthly_reflections") },
                    )
                }
            }
            // SL-009 — Settings converted from inline expanding sections to dedicated
            // pages. Each row navigates to a page that reuses the SAME section
            // composable + ProfileViewModel (no duplicated state or logic).
            item {
                SectionTitle("Settings")
                CardGroup {
                    SettingsRowItem(icon = Icons.Outlined.Verified, title = "Verification Settings", subtitle = "Method, confirmations, reminders", onClick = { onNavigate("settings/verification") })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsRowItem(icon = Icons.Outlined.Palette, title = "Appearance Settings", subtitle = "Theme", onClick = { onNavigate("settings/appearance") })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsRowItem(icon = Icons.Outlined.Backup, title = "Backup Settings", subtitle = "Create, restore, auto backup", onClick = { onNavigate("settings/backup") })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsRowItem(icon = Icons.Outlined.Tune, title = "App Settings", subtitle = "Calculation, adhan, lock duration", onClick = { onNavigate("settings/app") })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    // BM-HOME-PRAYER-UX — Prayer time source (GPS ⇄ Local Masjid).
                    SettingsRowItem(icon = Icons.Outlined.LocationOn, title = "Prayer Time Source", subtitle = "GPS or Local Masjid", onClick = { onNavigate("settings/prayer_source") })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    // SL-006 — Lock Per Prayer relocated from the App Blocker tab.
                    SettingsRowItem(icon = Icons.Outlined.Lock, title = "Lock Per Prayer", subtitle = "Enable locking per prayer", onClick = { onNavigate("settings/lock_per_prayer") })
                }
            }
            item {
                SupportSection(
                    onFeedback = { sendFeedback(context) },
                    onHelp = { onNavigate("help_center") },
                )
            }
            // SL-008 — Permission Status lives at the BOTTOM of Settings, as a page.
            item {
                CardGroup {
                    SettingsRowItem(icon = Icons.Outlined.AdminPanelSettings, title = "Permission Status", subtitle = "Usage, overlay, notifications, battery", onClick = { onNavigate("settings/permissions") })
                }
            }
        }
    }
}

/**
 * Fully vertical profile header card.
 *
 * Structure (top to bottom, all centered, no offsets, no overlays):
 *   Avatar (72dp) → Name → Email/subtitle → CTA button
 *   ── divider ──
 *   Streak  |  Today's Salah
 *
 * Sprint D.3.4: stats are inline inside the card, not a separate LazyColumn item.
 * Padding: 24dp. Corner: 24dp. No emerald background fills.
 */
@Composable
fun ProfileHeader(state: ProfileUiState, viewModel: ProfileViewModel) {
    // BM-AUTH-001: sign-in/out are driven end-to-end by the ViewModel via Clerk
    // (AuthRepository) — Clerk is the single authentication authority.

    // Count-up animations hoisted outside AnimatedContent — survive auth transitions
    val streakAnim = remember { Animatable(0f) }
    LaunchedEffect(state.currentStreak) {
        streakAnim.animateTo(state.currentStreak.toFloat(), MotionTokens.slowTween())
    }
    val prayerAnim = remember { Animatable(0f) }
    LaunchedEffect(state.totalPrayers) {
        prayerAnim.animateTo(state.totalPrayers.toFloat(), MotionTokens.slowTween())
    }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLight) MaterialTheme.colorScheme.surface else ElevatedSurface
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        border = if (!isLight) BorderStroke(1.dp, GoldAccent.copy(alpha = 0.15f)) else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Identity section ─────────────────────────────────────────────
            AnimatedContent(
                targetState = state.isSignedIn,
                transitionSpec = {
                    fadeIn(MotionTokens.normalTween()) togetherWith fadeOut(MotionTokens.normalTween())
                },
                label = "authIdentity",
            ) { isSignedIn ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isSignedIn) {
                        // Avatar — 72dp initials, no offset, no zIndex
                        val initials = state.userName
                            .split(" ")
                            .filter { it.isNotBlank() }
                            .take(2)
                            .map { it.first().uppercaseChar() }
                            .joinToString("")
                            .ifEmpty { "?" }
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = initials,
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = state.userName.ifBlank { "Musafir" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = state.userEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            modifier = Modifier.height(40.dp),
                        ) {
                            Text("Sign Out", style = MaterialTheme.typography.labelLarge)
                        }

                    } else {
                        // Signed-out state — avatar + CTA
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Sign in to sync your progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Backup your streak and prayer history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        if (state.signInState == SignInState.NOT_CONFIGURED) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Google Sign-In is not configured.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }

                        if (state.signInError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.signInError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = { viewModel.startSignIn() },
                            modifier = Modifier.height(40.dp),
                            enabled = state.signInState != SignInState.LOADING,
                        ) {
                            if (state.signInState == SignInState.LOADING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = EmeraldPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Cloud sync is in development.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Divider ──────────────────────────────────────────────────────
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp,
            )

            // ── Stats row ────────────────────────────────────────────────────
            val streakCount = streakAnim.value.toInt()
            val prayerCount = prayerAnim.value.toInt()
            // Correct plural: "1 Day", "2 Days"
            val streakLabel = if (streakCount == 1) "Day" else "Days"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                // Streak
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (streakCount > 0) "$streakCount $streakLabel" else "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (streakCount > 0) GoldAccent else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "STREAK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSage,
                        letterSpacing = 1.sp,
                    )
                }

                // Vertical divider between stats
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )

                // Today's Salah
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "$prayerCount / 5",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (prayerCount > 0) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "TODAY'S SALAH",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSage,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressSection(state: ProfileUiState) {
    SectionTitle("Your Journey")

    val streakAnim = remember { Animatable(0f) }
    LaunchedEffect(state.currentStreak) {
        streakAnim.animateTo(state.currentStreak.toFloat(), MotionTokens.slowTween())
    }
    val prayerAnim = remember { Animatable(0f) }
    LaunchedEffect(state.totalPrayers) {
        prayerAnim.animateTo(state.totalPrayers.toFloat(), MotionTokens.slowTween())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProgressCard(
            title = "Streak",
            value = if (state.currentStreak > 0) "${streakAnim.value.toInt()} days" else "—",
            modifier = Modifier.weight(1f),
        )
        ProgressCard(
            title = "Today's Salah",
            value = "${prayerAnim.value.toInt()} / 5",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ProgressCard(title: String, value: String, modifier: Modifier = Modifier) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else ElevatedSurface

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (!isLight) androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.10f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MutedSage,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Backup & Transfer section.
 *
 * Features:
 *  - Create Backup → SAF → ZIP (manifest.json + metadata.json + AES-256-GCM backup.json)
 *  - Share Backup → cache → Android share sheet (WhatsApp, Drive, Telegram, Files, …)
 *  - Restore Backup → SAF picker → preview dialog → confirm → restore
 *  - Auto Backup → WorkManager periodic job (offline, no network)
 *
 * No cloud. No account required. User owns their data.
 */
@Composable
fun BackupSection(state: ProfileUiState, viewModel: ProfileViewModel) {
    val backupState by viewModel.backupUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // SAF — create new ZIP backup file
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.createBackup(it) } }

    // SAF — open existing ZIP backup for restore (shows preview first)
    val restorePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadBackupPreview(it) } }

    val lastBackupText = when {
        state.lastBackupMs == 0L -> "No backup yet"
        else -> "Last backup: ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(state.lastBackupMs))}"
    }
    val busy = backupState.isBackingUp || backupState.isRestoring || backupState.isLoadingPreview

    SectionTitle("Backup & Transfer")

    // Feedback banner (result or error)
    if (backupState.resultMessage != null || backupState.errorMessage != null) {
        val msg = backupState.resultMessage ?: backupState.errorMessage ?: ""
        val isErr = backupState.errorMessage != null
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isErr) MaterialTheme.colorScheme.errorContainer else EmeraldPrimary.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, style = MaterialTheme.typography.bodySmall,
                    color = if (isErr) MaterialTheme.colorScheme.onErrorContainer else EmeraldPrimary,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.clearBackupResult() }) {
                    Text("OK", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // Restore preview confirmation dialog
    val preview = backupState.preview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPreview() },
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewRow("Backup date",     preview.backupDate)
                    PreviewRow("App version",     "Niyyah ${preview.appVersion}")
                    PreviewRow("Prayer records",  "${preview.prayerRecords}")
                    PreviewRow("Current streak",  "${preview.currentStreak} ${if (preview.currentStreak == 1) "Day" else "Days"}")
                    PreviewRow("Blocked apps",    "${preview.blockedApps}")
                    if (preview.localMasjidName != null) {
                        PreviewRow("Local Masjid", preview.localMasjidName)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Existing prayer history and settings will be replaced.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmRestore() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPreview() }) { Text("Cancel") }
            },
        )
    }

    CardGroup {
        // Create Backup
        SettingsRowItem(
            icon = Icons.Outlined.Backup,
            title = if (backupState.isBackingUp) "Creating backup…" else "Create Backup",
            subtitle = lastBackupText,
            onClick = {
                if (!busy) {
                    val filename = "Niyyah_Backup_${
                        SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date())
                    }.zip"
                    createBackupLauncher.launch(filename)
                }
            },
            trailing = if (backupState.isBackingUp) {
                { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = EmeraldPrimary) }
            } else null,
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // Share Backup (share sheet)
        SettingsRowItem(
            icon = Icons.Outlined.Share,
            title = "Share Backup",
            subtitle = "Send via WhatsApp, Telegram, Files…",
            onClick = {
                if (!busy) scope.launch {
                    try {
                        val shareUri = context.let { ctx ->
                            (ctx.applicationContext as? com.salahlock.app.SalahLockApplication)
                                ?.backupRepository?.createShareableBackup()
                        } ?: return@launch
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Niyyah Backup")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Backup"))
                    } catch (e: Exception) {
                        android.util.Log.e("BackupSection", "Share failed: ${e.message}", e)
                    }
                }
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // Restore Backup
        SettingsRowItem(
            icon = Icons.Outlined.Restore,
            title = when {
                backupState.isLoadingPreview -> "Reading backup…"
                backupState.isRestoring -> "Restoring…"
                else -> "Restore Backup"
            },
            subtitle = "Import from a .zip backup file",
            onClick = {
                if (!busy) restorePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            },
            trailing = if (backupState.isLoadingPreview || backupState.isRestoring) {
                { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = EmeraldPrimary) }
            } else null,
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // Auto Backup frequency
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Auto Backup", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("Saves to device storage automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DISABLED" to "Off", "DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly")
                    .forEach { (value, label) ->
                        val selected = state.autoBackupFrequency == value
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setAutoBackupFrequency(value) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = EmeraldPrimary,
                            ),
                        )
                    }
            }
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SalahLockSection(state: ProfileUiState, viewModel: ProfileViewModel, onNavigateToBlacklist: () -> Unit) {
    SectionTitle("Prayer Lock Rules")
    CardGroup {
        SettingsRowItem(icon = Icons.Outlined.Apps, title = "Manage Locked Apps", subtitle = "${state.blockedAppCount} apps locked", onClick = onNavigateToBlacklist)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // Per-prayer lock toggles
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Lock Per Prayer", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Disable locking for specific prayers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            state.prayerLockEnabled.forEach { (prayer, enabled) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(prayer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { viewModel.setPrayerLockEnabled(prayer, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldPrimary,
                        ),
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // Permissions sub-section
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = EmeraldPrimary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Permission Status", fontWeight = FontWeight.Bold)
                val allGranted = state.hasOverlayPermission && state.hasUsageStatsPermission && state.hasLocationPermission
                Text(
                    text = if (allGranted) "All Systems Go" else "Action Required",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (allGranted) EmeraldPrimary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun AppearanceSection(state: ProfileUiState, viewModel: ProfileViewModel) {
    SectionTitle("Appearance")
    CardGroup {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Theme", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemePreviewCard(
                    modifier = Modifier.weight(1f),
                    title = "System",
                    icon = Icons.Outlined.SettingsSuggest,
                    isSelected = state.themePreference == ThemePreference.SYSTEM,
                    onClick = { viewModel.setThemePreference(ThemePreference.SYSTEM) }
                )
                ThemePreviewCard(
                    modifier = Modifier.weight(1f),
                    title = "Light",
                    icon = Icons.Outlined.LightMode,
                    isSelected = state.themePreference == ThemePreference.LIGHT,
                    onClick = { viewModel.setThemePreference(ThemePreference.LIGHT) }
                )
                ThemePreviewCard(
                    modifier = Modifier.weight(1f),
                    title = "Dark",
                    icon = Icons.Outlined.DarkMode,
                    isSelected = state.themePreference == ThemePreference.DARK,
                    onClick = { viewModel.setThemePreference(ThemePreference.DARK) }
                )
                ThemePreviewCard(
                    modifier = Modifier.weight(1f),
                    title = "AMOLED",
                    icon = Icons.Outlined.Brightness3,
                    isSelected = state.themePreference == ThemePreference.AMOLED,
                    onClick = { viewModel.setThemePreference(ThemePreference.AMOLED) }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        
        SettingsRowItem(
            icon = Icons.Outlined.InvertColors, 
            title = "Dynamic Colors", 
            subtitle = "Follow system wallpaper colors", 
            onClick = { /* Disabled Placeholder */ }
        )
    }
}

@Composable
fun ThemePreviewCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) EmeraldPrimary else Color.Transparent,
        label = "ThemePreviewBorder"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) EmeraldPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        label = "ThemePreviewBackground"
    )

    Card(
        modifier = modifier
            .height(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = title, 
                tint = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title, 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SettingsSection(state: ProfileUiState, viewModel: ProfileViewModel) {
    SectionTitle("Settings")
    var showCalcDialog by remember { mutableStateOf(false) }

    CardGroup {
        SettingsRowItem(icon = Icons.Outlined.Calculate, title = "Calculation Method", subtitle = state.calcMethod, onClick = { showCalcDialog = true })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = EmeraldPrimary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Adhan Alerts", fontWeight = FontWeight.Bold)
                Text("Lock apps when adhan calls", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = state.adhanEnabled,
                onCheckedChange = { viewModel.setAdhanEnabled(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary, checkedTrackColor = EmeraldPrimary.copy(alpha = 0.3f))
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        SettingsRowItem(icon = Icons.Outlined.Timer, title = "Lock Duration", subtitle = "${state.lockDurationMin} minutes", onClick = {})
    }

    if (showCalcDialog) {
        AlertDialog(
            onDismissRequest = { showCalcDialog = false },
            title = { Text("Calculation Method") },
            text = {
                Column {
                    val methods = listOf("KARACHI", "ISNA", "MWL", "EGYPT")
                    methods.forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setCalculationMethod(method)
                                    showCalcDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = state.calcMethod == method, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = method)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCalcDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
fun VerificationSection(state: ProfileUiState, viewModel: ProfileViewModel) {
    SectionTitle("Verification")
    CardGroup {
        // Method selector
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Verification Method", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("How you confirm prayer before unlocking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            listOf(
                "TEXT" to "Text",
                "VOICE" to "Voice",
                "ASK_EVERY_TIME" to "Ask Every Time",
            ).forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.verificationMethod == value,
                        onClick = { viewModel.setVerificationMethod(value) },
                        colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // Confirmation count
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Confirmations", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("How many times to repeat the affirmation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            listOf(1, 2, 3).forEach { count ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.verificationConfirmCount == count,
                        onClick = { viewModel.setVerificationConfirmCount(count) },
                        colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // Reminder sources
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Reminder Sources", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("Which types of reminders to show before verification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            data class ReminderToggle(val label: String, val checked: Boolean, val onToggle: (Boolean) -> Unit)
            listOf(
                ReminderToggle("Quran", state.reminderQuranEnabled) { viewModel.setReminderQuranEnabled(it) },
                ReminderToggle("Hadith", state.reminderHadithEnabled) { viewModel.setReminderHadithEnabled(it) },
                ReminderToggle("Reflection", state.reminderReflectionEnabled) { viewModel.setReminderReflectionEnabled(it) },
            ).forEach { (label, checked, setter) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = checked,
                        onCheckedChange = { setter(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldPrimary),
                    )
                }
            }
        }
    }
}

@Composable
fun SupportSection(onFeedback: () -> Unit = {}, onHelp: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SectionTitle("Support")
    CardGroup {
        SettingsRowItem(icon = Icons.Outlined.Feedback, title = "Send Feedback", subtitle = "Email or share your thoughts", onClick = onFeedback)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        SettingsRowItem(icon = Icons.Outlined.HelpOutline, title = "Help Center", subtitle = "FAQ and common questions", onClick = onHelp)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        // Shown only when Google UMP reports privacy options are required for this
        // user/region. Opens the UMP-managed consent form (no custom consent UI).
        if (com.salahlock.app.ads.ConsentManager.privacyOptionsRequired) {
            SettingsRowItem(
                icon = Icons.Outlined.PrivacyTip,
                title = "Privacy choices",
                subtitle = "Manage ad consent & privacy options",
                onClick = {
                    (context as? android.app.Activity)?.let {
                        com.salahlock.app.ads.ConsentManager.showPrivacyOptionsForm(it)
                    }
                },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        }
        SettingsRowItem(icon = Icons.Outlined.Info, title = "About Niyyah", subtitle = "Version ${com.salahlock.app.BuildConfig.VERSION_NAME}", onClick = {})
    }
}

/**
 * SL-010 — Send Feedback via email intent; falls back to the generic share sheet
 * when no email app is installed. No network code, no new dependencies.
 */
fun sendFeedback(context: android.content.Context) {
    val subject = "Niyyah Feedback (v${com.salahlock.app.BuildConfig.VERSION_NAME})"
    val email = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("mailto:")
        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("salahlock.app@gmail.com"))
        putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
    }
    try {
        context.startActivity(android.content.Intent.createChooser(email, "Send Feedback"))
    } catch (_: Exception) {
        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
            putExtra(android.content.Intent.EXTRA_TEXT, "My feedback about Niyyah:\n\n")
        }
        runCatching { context.startActivity(android.content.Intent.createChooser(share, "Send Feedback")) }
    }
}

// ── SL-009: Dedicated settings pages ─────────────────────────────────────────────
// Thin Scaffold wrappers that REUSE the existing section composables + ProfileViewModel.
// No duplicated state: all preferences live in DataStore/Room behind the same repos.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.(ProfileUiState, ProfileViewModel) -> Unit,
) {
    val viewModel: ProfileViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp),
        ) {
            content(state, viewModel)
        }
    }
}

@Composable
fun VerificationSettingsScreen(onBack: () -> Unit) =
    SettingsSubPage("Verification", onBack) { state, vm -> VerificationSection(state, vm) }

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) =
    SettingsSubPage("Appearance", onBack) { state, vm -> AppearanceSection(state, vm) }

@Composable
fun BackupSettingsScreen(onBack: () -> Unit) =
    SettingsSubPage("Backup & Transfer", onBack) { state, vm -> BackupSection(state, vm) }

@Composable
fun AppSettingsScreen(onBack: () -> Unit) =
    SettingsSubPage("App Settings", onBack) { state, vm -> SettingsSection(state, vm) }

/** SL-006 — Lock Per Prayer page. Reuses ProfileViewModel.setPrayerLockEnabled → UserPreferences. */
@Composable
fun LockPerPrayerScreen(onBack: () -> Unit) =
    SettingsSubPage("Lock Per Prayer", onBack) { state, vm ->
        Text(
            "Disable locking for specific prayers. Locking stays active for all others.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        CardGroup {
            state.prayerLockEnabled.forEach { (prayer, enabled) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(prayer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { vm.setPrayerLockEnabled(prayer, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldPrimary),
                    )
                }
            }
        }
    }

/** SL-008/SL-009 — Permission Status page (bottom entry of Settings). ON_RESUME refresh preserved. */
@Composable
fun PermissionStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh when returning from system settings (preserved from RC.5).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.checkPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    SettingsSubPage("Permission Status", onBack) { s, _ ->
        PermissionStatusSection(s) { kind ->
            context.startActivity(permissionIntent(context, kind))
        }
    }
}

/** SL-010 — Help Center: simple local FAQ, no network. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpCenterScreen(onBack: () -> Unit) {
    val faqs = listOf(
        "How does Niyyah work?" to "When a prayer time begins, the apps you selected are locked until you confirm your prayer. Confirm from the lock screen or the Home tab.",
        "Which apps get locked?" to "Only the apps you choose in the Lock Apps tab. Calls, SMS, and essential system apps are never blocked.",
        "How do I verify a prayer?" to "Tap \"I Prayed\" on Home, or complete the short reflection + typed/voice affirmation on the lock screen.",
        "What is Pause Niyyah?" to "A temporary break (15m–1h or until the next prayer). While paused, no locking or adhan notifications occur. Prayer tracking continues.",
        "What are emergency overrides?" to "Three per month. Use one to bypass a lock in a genuine emergency — it still counts toward your day's record.",
        "How do local masjid timings work?" to "Enter your masjid's jamaat times in Home → Your Masjid. They replace the calculated times everywhere, including locking.",
        "Why does Niyyah need Usage Access and Display Over Apps?" to "Usage Access detects which app is open during a prayer window; Display Over Apps shows the prayer reminder over it. Both are used only during prayer windows.",
        "Is my data private?" to "Yes. Prayer history, location, and settings stay on your device. Backups are local files you control. Nothing is uploaded.",
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            items(faqs.size) { i ->
                val (q, a) = faqs[i]
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(q, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ── Permission Status (RC.5 — moved from Lock Apps tab) ─────────────────────────

private fun permissionIntent(context: android.content.Context, kind: String): android.content.Intent = when (kind) {
    "overlay" -> com.salahlock.app.util.PermissionHelper.overlaySettingsIntent(context)
    "usage" -> android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
    "battery" -> com.salahlock.app.util.PermissionHelper.batteryOptimizationIntent(context)
    "notifications" -> android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    else -> android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
}

@Composable
fun PermissionStatusSection(state: ProfileUiState, onOpen: (String) -> Unit) {
    SectionTitle("Permission Status")
    CardGroup {
        PermissionStatusRow("Usage Access", state.hasUsageStatsPermission) { onOpen("usage") }
        PermissionStatusRow("Display Over Apps", state.hasOverlayPermission) { onOpen("overlay") }
        PermissionStatusRow("Notifications", state.hasNotificationPermission) { onOpen("notifications") }
        PermissionStatusRow("Ignore Battery Optimization", state.hasBatteryOptimization) { onOpen("battery") }
    }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Granted", style = MaterialTheme.typography.labelMedium, color = EmeraldPrimary)
            }
        } else {
            Text("Grant →", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    // Stitch V2 section header: small uppercase sans label, wide tracking
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
fun CardGroup(content: @Composable ColumnScope.() -> Unit) {
    // Stitch V2 card: surface + 1dp hairline, 16dp radius, no shadow
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
