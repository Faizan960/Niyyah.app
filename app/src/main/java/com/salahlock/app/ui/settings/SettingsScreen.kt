package com.salahlock.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Settings — Figma frame 1:1468 (light).
 *
 * NOTE: visual shell mirroring the frame. Rows are not yet wired to
 * preferences (the old settings screens were deleted in BM-004); toggle
 * states are static as drawn in the frame.
 */
private val TextBody = Color(0xFF45474E)
private val GroupBorder = Color(0x4DC5C6CE)
private val RowDivider = Color(0x33C5C6CE)
private val ToggleOff = Color(0xFFE5E2E1)
private val LogoutRed = Color(0xFFBA1A1A)

@Composable
fun SettingsScreen() {
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
                AccountRow()
                RowDividerLine()
                SettingsRow(iconRes = R.drawable.ic_set_security, iconW = 16.dp, iconH = 20.dp, label = "Security & Password", trailing = { Chevron() })
            }
            SettingsGroup("APPEARANCE") {
                SettingsRow(iconRes = R.drawable.ic_set_darkmode, iconW = 18.dp, iconH = 18.dp, label = "Dark Mode", trailing = { Toggle(on = false) })
            }
            SettingsGroup("PRAYER SETTINGS") {
                SettingsRow(
                    iconRes = R.drawable.ic_set_calc_method, iconW = 16.dp, iconH = 20.dp,
                    label = "Calculation Method", subLabel = "ISNA",
                    trailing = { Chevron() },
                )
                RowDividerLine()
                SettingsRow(iconRes = R.drawable.ic_set_adhan, iconW = 20.dp, iconH = 20.dp, label = "Adhan Alerts", trailing = { Chevron() })
            }
            SettingsGroup("NOTIFICATIONS") {
                SettingsRow(iconRes = R.drawable.ic_set_announcements, iconW = 20.dp, iconH = 16.dp, label = "Announcements", trailing = { Toggle(on = true) })
                RowDividerLine()
                SettingsRow(iconRes = R.drawable.ic_set_mail, iconW = 20.dp, iconH = 16.dp, label = "Email Newsletter", trailing = { Toggle(on = false) })
            }
            SettingsGroup("PRIVACY & PERMISSIONS") {
                SettingsRow(
                    iconRes = R.drawable.ic_set_location, iconW = 22.dp, iconH = 22.dp,
                    label = "Location Access",
                    trailing = {
                        Text(text = "While Using", style = NiyyahType.Badge, color = TextBody)
                    },
                )
                RowDividerLine()
                SettingsRow(iconRes = R.drawable.ic_set_privacy, iconW = 16.dp, iconH = 20.dp, label = "Privacy Policy", trailing = { ExternalLink() })
                RowDividerLine()
                SettingsRow(iconRes = R.drawable.ic_set_terms, iconW = 18.dp, iconH = 19.dp, label = "Terms of Service", trailing = { ExternalLink() })
            }
            SettingsGroup("HELP") {
                SettingsRow(iconRes = R.drawable.ic_set_faq, iconW = 20.dp, iconH = 20.dp, label = "FAQ & Support", trailing = { Chevron() })
                RowDividerLine()
                SettingsRow(
                    iconRes = R.drawable.ic_set_logout, iconW = 18.dp, iconH = 18.dp,
                    label = "Log Out",
                    labelColor = LogoutRed,
                    iconTint = LogoutRed,
                    trailing = {},
                )
            }
        }
    }
}

/** Header — node 1:1502. */
@Composable
private fun SettingsHeader() {
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
                .background(GroupBorder),
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
            color = TextBody,
            modifier = Modifier.padding(start = 16.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NiyyahShapes.Card)
                .background(NiyyahColors.Surface)
                .border(1.dp, GroupBorder, NiyyahShapes.Card),
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

/** Account row — node 1:1520. */
@Composable
private fun AccountRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.img_profile_avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Aisha Rahman",
                style = NiyyahType.Body.copy(fontSize = 18.sp, lineHeight = 28.sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(text = "Personal Plan", style = NiyyahType.Body, color = TextBody)
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
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                    color = TextBody,
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
        tint = TextBody,
        modifier = Modifier.width(8.dp).height(12.dp),
    )
}

@Composable
private fun ExternalLink() {
    Icon(
        painter = painterResource(R.drawable.ic_external_link),
        contentDescription = null,
        tint = TextBody,
        modifier = Modifier.size(18.dp),
    )
}

/** Static toggle as drawn — node 1:1546 / 1:1578. */
@Composable
private fun Toggle(on: Boolean) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(24.dp)
            .background(if (on) NiyyahColors.TextPrimary else ToggleOff, NiyyahShapes.Pill)
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
