package com.salahlock.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.ui.components.ProfileAvatar
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings — Figma frame 1:1468 (light).
 *
 * BM-006.9: every row is functional and persisted via [SettingsViewModel]
 * (UserPreferences / UserIdentityPreferences / BackupRepository). A DATA
 * group (Backup/Restore) and About row were added — required functionality
 * with the same row styling as the frame.
 */
private val RowDivider = Color(0x33C5C6CE)
private val ToggleOff = Color(0xFFE5E2E1)
private val LogoutRed = Color(0xFFBA1A1A)

private const val SUPPORT_EMAIL = "faizanpatel2226@gmail.com"

private enum class SettingsDialog {
    NONE, SECURITY, CALC_METHOD, ADHAN, PRIVACY, TERMS, ABOUT, LOGOUT, RESTORE_CONFIRM,
}

@Composable
fun SettingsScreen(
    onOpenProfile: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(SettingsDialog.NONE) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.createBackup(uri) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            dialog = SettingsDialog.RESTORE_CONFIRM
        }
    }

    fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                },
            )
        }
    }

    fun openSupportEmail() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:$SUPPORT_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, "Niyyah — Feedback & Support")
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "Settings",
                style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
                color = NiyyahColors.TextPrimary,
            )
            SettingsGroup("ACCOUNT") {
                AccountRow(
                    name = uiState.userName.ifBlank { "Guest" },
                    detail = if (uiState.isSignedIn) uiState.userEmail else "Sign in from Profile",
                    photoUrl = uiState.userPhotoUrl,
                    onClick = onOpenProfile,
                )
                RowDividerLine()
                SettingsRow(
                    iconRes = R.drawable.ic_set_security, iconW = 16.dp, iconH = 20.dp,
                    label = "Security & Password",
                    onClick = { dialog = SettingsDialog.SECURITY },
                    trailing = { Chevron() },
                )
            }
            SettingsGroup("APPEARANCE") {
                SettingsRow(
                    iconRes = R.drawable.ic_set_darkmode, iconW = 18.dp, iconH = 18.dp,
                    label = "Dark Mode",
                    onClick = { viewModel.setDarkMode(!uiState.darkMode) },
                    trailing = {
                        Toggle(on = uiState.darkMode) { viewModel.setDarkMode(it) }
                    },
                )
            }
            SettingsGroup("PRAYER SETTINGS") {
                SettingsRow(
                    iconRes = R.drawable.ic_set_calc_method, iconW = 16.dp, iconH = 20.dp,
                    label = "Calculation Method", subLabel = uiState.calcMethod,
                    onClick = { dialog = SettingsDialog.CALC_METHOD },
                    trailing = { Chevron() },
                )
                RowDividerLine()
                SettingsRow(
                    iconRes = R.drawable.ic_set_adhan, iconW = 20.dp, iconH = 20.dp,
                    label = "Adhan Alerts",
                    subLabel = if (uiState.adhanEnabled) "On" else "Off",
                    onClick = { dialog = SettingsDialog.ADHAN },
                    trailing = { Chevron() },
                )
            }
            SettingsGroup("DATA") {
                SettingsRow(
                    iconRes = R.drawable.ic_export_download, iconW = 18.dp, iconH = 18.dp,
                    label = "Backup",
                    subLabel = if (uiState.lastBackupMs > 0L)
                        "Last backup: " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH)
                            .format(Date(uiState.lastBackupMs))
                    else "Never backed up",
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date())
                        backupLauncher.launch("niyyah-backup-$stamp.zip")
                    },
                    trailing = { Chevron() },
                )
                RowDividerLine()
                SettingsRow(
                    iconRes = R.drawable.ic_set_security, iconW = 16.dp, iconH = 20.dp,
                    label = "Restore",
                    subLabel = "From a Niyyah backup file",
                    onClick = { restoreLauncher.launch(arrayOf("application/zip")) },
                    trailing = { Chevron() },
                )
            }
            SettingsGroup("NOTIFICATIONS") {
                SettingsRow(
                    iconRes = R.drawable.ic_set_announcements, iconW = 20.dp, iconH = 16.dp,
                    label = "Announcements",
                    onClick = { viewModel.setAnnouncementsEnabled(!uiState.announcementsEnabled) },
                    trailing = {
                        Toggle(on = uiState.announcementsEnabled) {
                            viewModel.setAnnouncementsEnabled(it)
                        }
                    },
                )
                RowDividerLine()
                SettingsRow(
                    iconRes = R.drawable.ic_set_mail, iconW = 20.dp, iconH = 16.dp,
                    label = "Email Newsletter",
                    onClick = { viewModel.setEmailNewsletterEnabled(!uiState.emailNewsletterEnabled) },
                    trailing = {
                        Toggle(on = uiState.emailNewsletterEnabled) {
                            viewModel.setEmailNewsletterEnabled(it)
                        }
                    },
                )
            }
            SettingsGroup("PRIVACY & PERMISSIONS") {
                SettingsRow(
                    iconRes = R.drawable.ic_set_location, iconW = 22.dp, iconH = 22.dp,
                    label = "Location Access",
                    onClick = { openAppSettings() },
                    trailing = {
                        Text(
                            text = if (uiState.hasLocationPermission) "While Using" else "Not allowed",
                            style = NiyyahType.Badge,
                            color = NiyyahColors.TextBody,
                        )
                    },
                )
                RowDividerLine()
                SettingsRow(
                    iconRes = R.drawable.ic_set_privacy, iconW = 16.dp, iconH = 20.dp,
                    label = "Privacy Policy",
                    onClick = { dialog = SettingsDialog.PRIVACY },
                    trailing = { ExternalLink() },
                )
                RowDividerLine()
                SettingsRow(
                    iconRes = R.drawable.ic_set_terms, iconW = 18.dp, iconH = 19.dp,
                    label = "Terms of Service",
                    onClick = { dialog = SettingsDialog.TERMS },
                    trailing = { ExternalLink() },
                )
            }
            SettingsGroup("HELP") {
                SettingsRow(
                    iconRes = R.drawable.ic_set_faq, iconW = 20.dp, iconH = 20.dp,
                    label = "FAQ & Support",
                    subLabel = "Email us your feedback",
                    onClick = { openSupportEmail() },
                    trailing = { Chevron() },
                )
                RowDividerLine()
                SettingsRow(
                    iconRes = R.drawable.ic_set_announcements, iconW = 20.dp, iconH = 16.dp,
                    label = "About Niyyah",
                    onClick = { dialog = SettingsDialog.ABOUT },
                    trailing = { Chevron() },
                )
                RowDividerLine()
                SettingsRow(
                    iconRes = R.drawable.ic_set_logout, iconW = 18.dp, iconH = 18.dp,
                    label = "Log Out",
                    labelColor = LogoutRed,
                    iconTint = LogoutRed,
                    onClick = { dialog = SettingsDialog.LOGOUT },
                    trailing = {},
                )
            }
        }
    }

    when (dialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.SECURITY -> InfoDialog(
            title = "Security & Password",
            body = "Niyyah uses your Google account for sign-in — there is no separate " +
                "password to manage. All of your worship data stays on this device.",
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.CALC_METHOD -> ChoiceDialog(
            title = "Calculation Method",
            options = listOf("KARACHI", "MWL", "ISNA"),
            selected = uiState.calcMethod,
            onSelect = {
                viewModel.setCalcMethod(it)
                dialog = SettingsDialog.NONE
            },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.ADHAN -> ChoiceDialog(
            title = "Adhan Alerts",
            options = listOf("On", "Off"),
            selected = if (uiState.adhanEnabled) "On" else "Off",
            onSelect = {
                viewModel.setAdhanEnabled(it == "On")
                dialog = SettingsDialog.NONE
            },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.PRIVACY -> InfoDialog(
            title = "Privacy Policy",
            body = "Niyyah is private by design. Your prayers, reading progress, bookmarks, " +
                "reflections and settings are stored only on this device. Location is used " +
                "solely to calculate prayer times and Qibla direction and never leaves your " +
                "phone. Nothing is shared, sold, or uploaded — backups are files you create " +
                "and keep yourself.",
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.TERMS -> InfoDialog(
            title = "Terms of Service",
            body = "Niyyah is offered as a companion for worship, provided as-is without " +
                "warranty. Prayer times are calculated estimates — always confirm with your " +
                "local masjid. You remain responsible for backups of your own data. " +
                "May Allah accept your efforts.",
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.ABOUT -> InfoDialog(
            title = "About Niyyah",
            body = "Niyyah ${appVersionName(context)}\n\nNiyyah means intention. " +
                "Every act begins with intention — this app exists to help you live " +
                "every day with conscious intention for Allah.",
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.LOGOUT -> ConfirmDialog(
            title = "Log Out",
            body = "Sign out of your Google account? Your prayers, bookmarks and " +
                "reflections stay safely on this device.",
            confirmLabel = "Log Out",
            confirmColor = LogoutRed,
            onConfirm = {
                viewModel.logOut()
                dialog = SettingsDialog.NONE
            },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.RESTORE_CONFIRM -> ConfirmDialog(
            title = "Restore Backup",
            body = "Restore from this backup file? Your current data will be replaced " +
                "by the backup's contents.",
            confirmLabel = "Restore",
            confirmColor = NiyyahColors.Navy,
            onConfirm = {
                pendingRestoreUri?.let { viewModel.restoreBackup(it) }
                pendingRestoreUri = null
                dialog = SettingsDialog.NONE
            },
            onDismiss = {
                pendingRestoreUri = null
                dialog = SettingsDialog.NONE
            },
        )
    }

    uiState.operationMessage?.let { message ->
        InfoDialog(
            title = if (uiState.isBusy) "Working…" else "Done",
            body = message,
            onDismiss = { viewModel.clearMessage() },
        )
    }
}

private fun appVersionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
}.getOrDefault("")

/** Header — node 1:1502. */
@Composable
private fun SettingsHeader() {
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
            // Balances the trailing icon so the wordmark stays centred (BM-009.2:
            // the non-functional hamburger was removed).
            Box(modifier = Modifier.width(16.dp))
            Text(text = "NIYYAH", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = "Notifications",
                tint = NiyyahColors.TextBody,
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

/** Section label + rounded group card — node 1:1516. */
@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = NiyyahType.LabelUppercase,
            color = NiyyahColors.TextBody,
            modifier = Modifier.padding(start = 16.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NiyyahShapes.Card)
                .background(NiyyahColors.Surface)
                .border(1.dp, NiyyahColors.Hairline, NiyyahShapes.Card),
        ) {
            content()
        }
    }
}

@Composable
private fun RowDividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RowDivider),
    )
}

/** Account row — node 1:1520. Live identity. */
@Composable
private fun AccountRow(name: String, detail: String, photoUrl: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileAvatar(
            photoUrl = photoUrl,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = NiyyahType.Body.copy(fontSize = 18.sp, lineHeight = 28.sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(text = detail, style = NiyyahType.Body, color = NiyyahColors.TextBody)
        }
        Chevron()
    }
}

/** Generic settings row — node 1:1529. */
@Composable
private fun SettingsRow(
    iconRes: Int,
    iconW: androidx.compose.ui.unit.Dp,
    iconH: androidx.compose.ui.unit.Dp,
    label: String,
    subLabel: String? = null,
    labelColor: Color = NiyyahColors.TextPrimary,
    iconTint: Color = NiyyahColors.TextPrimary,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.width(iconW).height(iconH),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = NiyyahType.Body, color = labelColor)
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = NiyyahType.Badge,
                    color = NiyyahColors.TextBody,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        trailing()
    }
}

@Composable
private fun Chevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = NiyyahColors.TextBody,
        modifier = Modifier.width(8.dp).height(12.dp),
    )
}

@Composable
private fun ExternalLink() {
    Icon(
        painter = painterResource(R.drawable.ic_external_link),
        contentDescription = null,
        tint = NiyyahColors.TextBody,
        modifier = Modifier.size(18.dp),
    )
}

/** Interactive toggle — same look as the frame (node 1:1546 / 1:1578). */
@Composable
private fun Toggle(on: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(24.dp)
            .background(if (on) NiyyahColors.TextPrimary else ToggleOff, NiyyahShapes.Pill)
            .clickable { onToggle(!on) }
            .padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .shadow(1.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

// ── Dialogs (28dp radius per the design language) ────────────────────────────

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = NiyyahColors.Surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Text(text = body, style = NiyyahType.Body, color = NiyyahColors.TextBody)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", style = NiyyahType.LabelUppercase, color = NiyyahColors.Navy)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = NiyyahColors.Surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = option,
                            style = NiyyahType.Body,
                            color = NiyyahColors.TextPrimary,
                        )
                        if (option == selected) {
                            Text(
                                text = "Selected",
                                style = NiyyahType.Badge,
                                color = NiyyahColors.Navy,
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = NiyyahType.LabelUppercase, color = NiyyahColors.TextBody)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = NiyyahColors.Surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Text(text = body, style = NiyyahType.Body, color = NiyyahColors.TextBody)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = NiyyahType.LabelUppercase, color = NiyyahColors.TextBody)
                    }
                    TextButton(onClick = onConfirm) {
                        Text(confirmLabel, style = NiyyahType.LabelUppercase, color = confirmColor)
                    }
                }
            }
        }
    }
}
