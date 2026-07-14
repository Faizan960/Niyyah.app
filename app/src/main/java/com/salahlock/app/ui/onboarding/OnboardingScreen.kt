package com.salahlock.app.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.data.preferences.UserPreferences
import com.salahlock.app.ui.theme.EbGaramond
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahType
import com.salahlock.app.util.PermissionHelper
import kotlinx.coroutines.launch

/**
 * Onboarding — 6-step flow from design/Reference:
 * Onboarding welcome screen.png → permissions-5 (Location) → permissions-3
 * (Background Activity) → permissions-1 (Overlay) → permissions-4 (Usage
 * Access) → permissions-2 (Choose Your Focus). Step order follows the dot
 * indicators in the reference frames.
 */
private const val STEP_COUNT = 6

private val GoldBar = Color(0xFFB08A3C)
private val DotInactive = Color(0xFFD9D9D9)
private val GoldOutline = Color(0xFFD4A84F)
private val GoldText = Color(0xFFB08A3C)
private val LavenderCircle = Color(0xFFE8EAF6)
private val CardBorder = Color(0x4DC5C6CE)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }

    fun next() {
        if (step < STEP_COUNT - 1) step++
    }

    fun finish() {
        scope.launch {
            UserPreferences(context.applicationContext).setOnboardingDone(true)
            onDone()
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { next() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NiyyahColors.Background),
    ) {
        when (step) {
            0 -> WelcomeStep(onContinue = { next() }, onSkip = { finish() })
            1 -> LocationStep(
                onAllow = {
                    if (PermissionHelper.hasLocationPermission(context)) next()
                    else locationLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                        ),
                    )
                },
                onLater = { next() },
                onBack = { step-- },
                onSkip = { finish() },
            )
            2 -> BackgroundStep(
                onAllow = {
                    if (!PermissionHelper.isBatteryOptimizationIgnored(context)) {
                        runCatching { context.startActivity(PermissionHelper.batteryOptimizationIntent(context)) }
                    }
                    next()
                },
                onLater = { next() },
                onBack = { step-- },
                onSkip = { finish() },
            )
            3 -> OverlayStep(
                onAllow = {
                    if (!PermissionHelper.canDrawOverlays(context)) {
                        runCatching { context.startActivity(PermissionHelper.overlaySettingsIntent(context)) }
                    }
                    next()
                },
                onLater = { next() },
                onBack = { step-- },
                onSkip = { finish() },
            )
            4 -> UsageStep(
                onAllow = {
                    if (!PermissionHelper.hasUsageStatsPermission(context)) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    }
                    next()
                },
                onLater = { next() },
                onSkip = { finish() },
            )
            5 -> FocusStep(
                onStart = { finish() },
                onConfigureLater = { finish() },
                onBack = { step-- },
            )
        }
    }
}

// ---------------------------------------------------------------- Steps

/** Step 1 — Onboarding welcome screen.png. */
@Composable
private fun WelcomeStep(onContinue: () -> Unit, onSkip: () -> Unit) {
    StepColumn {
        OnboardingHeader(leading = { CloseWord(onSkip) }, wordmark = true, onSkip = onSkip)
        Spacer(Modifier.height(44.dp))
        HeroImage(R.drawable.img_onb_welcome)
        Spacer(Modifier.height(48.dp))
        StepTitle("Welcome to NIYYAH")
        Spacer(Modifier.height(16.dp))
        StepBody(
            "Begin every day with intention. Niyyah helps you organize your worship, " +
                "knowledge, and daily habits while keeping your focus on what truly matters.",
        )
        Spacer(Modifier.weight(1f))
        DotsRow(active = 0)
        Spacer(Modifier.height(48.dp))
        PrimaryButton(label = "CONTINUE", trailingArrow = true, onClick = onContinue)
        Spacer(Modifier.height(40.dp))
    }
}

/** Step 2 — Onboarding screen for permissions-5.png (Location). */
@Composable
private fun LocationStep(onAllow: () -> Unit, onLater: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    StepColumn {
        OnboardingHeader(
            leading = {
                HeaderIcon(R.drawable.ic_close, width = 14.dp, height = 14.dp, onClick = onBack)
            },
            wordmark = true,
            onSkip = onSkip,
        )
        Spacer(Modifier.height(44.dp))
        HeroImage(R.drawable.img_onb_location)
        Spacer(Modifier.height(32.dp))
        StepTitle("Prayer Times & Location")
        Spacer(Modifier.height(16.dp))
        StepBody(
            "Niyyah uses your location to calculate accurate prayer times and determine " +
                "the Qibla direction.",
        )
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(NiyyahColors.Surface)
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconCircle(R.drawable.ic_set_location, circle = NiyyahColors.TextPrimary, tint = Color.White)
                Column {
                    Text(
                        text = "Location Access",
                        style = NiyyahType.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NiyyahColors.TextPrimary,
                    )
                    Text(
                        text = "Required for prayer times",
                        style = NiyyahType.Badge,
                        color = NiyyahColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(label = "ALLOW LOCATION", onClick = onAllow)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(label = "LATER", onClick = onLater)
        }
        Spacer(Modifier.weight(1f))
        DotsRow(active = 1)
        Spacer(Modifier.height(40.dp))
    }
}

/** Step 3 — Onboarding screen for permissions-3.png (Background Activity). */
@Composable
private fun BackgroundStep(onAllow: () -> Unit, onLater: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    StepColumn {
        OnboardingHeader(
            leading = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(4.dp, CircleShape, ambientColor = Color(0x22071836))
                        .background(Color.White, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_left),
                        contentDescription = "Back",
                        tint = NiyyahColors.TextPrimary,
                        modifier = Modifier.width(16.dp).height(13.dp),
                    )
                }
            },
            wordmark = false,
            onSkip = onSkip,
        )
        Spacer(Modifier.height(56.dp))
        BackgroundHeroCluster()
        Spacer(Modifier.height(44.dp))
        StepTitle("Reliable Prayer Protection")
        Spacer(Modifier.height(16.dp))
        StepBody(
            "Allow Niyyah to stay active in the background so prayer reminders and " +
                "Focus Mode continue working reliably.",
        )
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x14071836))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(NiyyahColors.SoftFill, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_gear),
                    contentDescription = null,
                    tint = NiyyahColors.TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PERMISSION",
                    style = NiyyahType.LabelUppercase.copy(fontSize = 11.sp, letterSpacing = 1.1.sp),
                    color = NiyyahColors.TextSecondary,
                )
                Text(
                    text = "Background Activity",
                    style = NiyyahType.BodyMedium,
                    color = NiyyahColors.TextPrimary,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_info_circle),
                contentDescription = null,
                tint = GoldOutline,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        DotsRow(active = 2)
        Spacer(Modifier.height(28.dp))
        PrimaryButton(label = "ALLOW", onClick = onAllow)
        Spacer(Modifier.height(12.dp))
        SecondaryButton(label = "LATER", onClick = onLater)
        Spacer(Modifier.height(32.dp))
    }
}

/** Step 4 — Onboarding screen for permissions-1.png (Display over other apps). */
@Composable
private fun OverlayStep(onAllow: () -> Unit, onLater: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    StepColumn {
        OnboardingHeader(
            leading = {
                HeaderIcon(R.drawable.ic_arrow_left, width = 18.dp, height = 14.dp, onClick = onBack)
            },
            wordmark = false,
            onSkip = onSkip,
        )
        Spacer(Modifier.height(28.dp))
        HeroImage(R.drawable.img_onb_overlay, cornerRadius = 32.dp)
        Spacer(Modifier.height(36.dp))
        StepTitle("Stay Focused During Prayer")
        Spacer(Modifier.height(16.dp))
        StepBody(
            "Niyyah uses a secure overlay only during prayer to help reduce " +
                "distractions and keep you focused.",
        )
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x14071836))
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconCircle(R.drawable.ic_onb_layers, circle = LavenderCircle, tint = NiyyahColors.TextPrimary)
            Column {
                Text(
                    text = "Display Over Other Apps",
                    style = NiyyahType.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NiyyahColors.TextPrimary,
                )
                Text(
                    text = "Required to gently hide incoming calls and messages while you pray.",
                    style = NiyyahType.Badge.copy(lineHeight = 18.sp),
                    color = NiyyahColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(label = "Allow Access", uppercase = false, onClick = onAllow)
        Spacer(Modifier.height(14.dp))
        SecondaryButton(label = "Maybe Later", uppercase = false, onClick = onLater)
        Spacer(Modifier.height(28.dp))
        DotsRow(active = 3)
        Spacer(Modifier.height(28.dp))
    }
}

/** Step 5 — Onboarding screen for permissions-4.png (Usage Access). */
@Composable
private fun UsageStep(onAllow: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit) {
    StepColumn {
        OnboardingHeader(leading = { CloseWord(onSkip) }, wordmark = true, onSkip = onSkip)
        Spacer(Modifier.height(40.dp))
        HeroImage(R.drawable.img_onb_usage, cornerRadius = 40.dp)
        Spacer(Modifier.height(36.dp))
        StepTitle("Protect Your Time")
        Spacer(Modifier.height(16.dp))
        StepBody(
            "Niyyah needs Usage Access to temporarily block selected applications " +
                "during prayer.",
        )
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x14071836))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Usage Access",
                style = NiyyahType.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            StaticToggle(on = false)
        }
        Spacer(Modifier.height(20.dp))
        DotsRow(active = 4)
        Spacer(Modifier.weight(1f))
        PrimaryButton(label = "Allow", uppercase = false, onClick = onAllow)
        Spacer(Modifier.height(14.dp))
        SecondaryButton(label = "Later", uppercase = false, onClick = onLater)
        Spacer(Modifier.height(36.dp))
    }
}

/** Step 6 — Onboarding screen for permissions-2.png (Choose Your Focus). */
@Composable
private fun FocusStep(onStart: () -> Unit, onConfigureLater: () -> Unit, onBack: () -> Unit) {
    var focusMode by rememberSaveable { mutableIntStateOf(0) }
    StepColumn {
        OnboardingHeader(
            leading = {
                HeaderIcon(R.drawable.ic_arrow_left, width = 18.dp, height = 14.dp, onClick = onBack)
            },
            wordmark = true,
            onSkip = null,
        )
        Spacer(Modifier.height(40.dp))
        HeroImage(R.drawable.img_onb_focus, cornerRadius = 24.dp, borderColor = NiyyahColors.Border)
        Spacer(Modifier.height(36.dp))
        StepTitle("Choose Your Focus")
        Spacer(Modifier.height(16.dp))
        StepBody(
            "Introduce Focus Mode. Receive reminders or go deep into concentration " +
                "during your dedicated times.",
        )
        Spacer(Modifier.height(32.dp))
        ModeCard(
            iconRes = R.drawable.ic_emergency_lock,
            title = "Focus Mode",
            body = "Lock selected apps during prayer and dedicated reflection times.",
            selected = focusMode == 0,
            onClick = { focusMode = 0 },
        )
        Spacer(Modifier.height(16.dp))
        ModeCard(
            iconRes = R.drawable.ic_bell,
            title = "Gentle Mode",
            body = "Receive timely reminders without locking your applications.",
            selected = focusMode == 1,
            onClick = { focusMode = 1 },
        )
        Spacer(Modifier.weight(1f))
        DotsRow(active = 5)
        Spacer(Modifier.height(28.dp))
        PrimaryButton(label = "Start Using NIYYAH", uppercase = false, trailingArrow = true, onClick = onStart)
        Spacer(Modifier.height(14.dp))
        SecondaryButton(label = "Configure Later", uppercase = false, onClick = onConfigureLater)
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------- Pieces

@Composable
private fun StepColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun OnboardingHeader(
    leading: @Composable () -> Unit,
    wordmark: Boolean,
    onSkip: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) { leading() }
        if (wordmark) {
            Text(
                text = "NIYYAH",
                style = NiyyahType.Wordmark.copy(fontSize = 26.sp, letterSpacing = 2.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (onSkip != null) {
            Text(
                text = "SKIP",
                style = NiyyahType.LabelUppercase.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
                color = NiyyahColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onSkip),
            )
        }
    }
}

/** Serif lowercase "close" word used in the welcome / usage headers. */
@Composable
private fun CloseWord(onClick: () -> Unit) {
    Text(
        text = "close",
        fontFamily = EbGaramond,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        color = NiyyahColors.TextPrimary,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun HeaderIcon(
    iconRes: Int,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = NiyyahColors.TextPrimary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(width)
            .height(height),
    )
}

@Composable
private fun HeroImage(
    imageRes: Int,
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp,
    borderColor: Color? = null,
) {
    var modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(cornerRadius))
    if (borderColor != null) {
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
    }
    Image(
        painter = painterResource(imageRes),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = modifier,
    )
}

/** Floating icon cluster hero — permissions-3 frame. */
@Composable
private fun BackgroundHeroCluster() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ClusterChip(R.drawable.ic_set_security)
            ClusterChip(R.drawable.ic_onb_battery)
        }
        Box(
            modifier = Modifier
                .offset(y = (-14).dp)
                .size(76.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0x33071836))
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_set_darkmode),
                contentDescription = null,
                tint = GoldText,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun ClusterChip(iconRes: Int) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x22071836))
            .background(Color.White, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = NiyyahColors.TextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        fontFamily = EbGaramond,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        color = NiyyahColors.TextPrimary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepBody(text: String) {
    Text(
        text = text,
        style = NiyyahType.Body.copy(lineHeight = 26.sp),
        color = NiyyahColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun DotsRow(active: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(STEP_COUNT) { i ->
            if (i == active) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(6.dp)
                        .background(GoldBar, RoundedCornerShape(3.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(DotInactive, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    uppercase: Boolean = true,
    trailingArrow: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(NiyyahColors.TextPrimary)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (uppercase) {
                NiyyahType.ButtonLabel.copy(letterSpacing = 1.2.sp)
            } else {
                NiyyahType.BodyMedium.copy(fontWeight = FontWeight.SemiBold)
            },
            color = Color.White,
        )
        if (trailingArrow) {
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.width(16.dp).height(12.dp),
            )
        }
    }
}

@Composable
private fun SecondaryButton(label: String, uppercase: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, GoldOutline, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = if (uppercase) {
                NiyyahType.ButtonLabel.copy(letterSpacing = 1.2.sp)
            } else {
                NiyyahType.BodyMedium.copy(fontWeight = FontWeight.Medium)
            },
            color = GoldText,
        )
    }
}

/** Selectable mode card — permissions-2 frame. */
@Composable
private fun ModeCard(
    iconRes: Int,
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NiyyahColors.Surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) NiyyahColors.Navy else CardBorder,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconCircle(iconRes, circle = LavenderCircle, tint = NiyyahColors.TextPrimary)
        Column {
            Text(
                text = title,
                fontFamily = EbGaramond,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                color = NiyyahColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = NiyyahType.Body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = NiyyahColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun IconCircle(iconRes: Int, circle: Color, tint: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(circle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Static toggle as drawn in the usage frame. */
@Composable
private fun StaticToggle(on: Boolean) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(26.dp)
            .background(
                if (on) NiyyahColors.TextPrimary else Color(0xFFDDE3F0),
                RoundedCornerShape(13.dp),
            )
            .padding(3.dp),
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
