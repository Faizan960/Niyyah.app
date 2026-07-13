package com.salahlock.app.ui.lock

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.salahlock.app.R
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.verification.VerificationMethod
import com.salahlock.app.ui.theme.EbGaramond
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType
import kotlinx.coroutines.delay
import java.util.Locale

/** Salah Lock — Figma frame 1:1008. Dark, focused, one purpose. */
private val Mint = Color(0xFF6FDAB0)
private val MintGlow = Color(0xFF8BF7CB)
private val EmergencyRose = Color(0xFFFFDAD6)

@Composable
fun SalahLockScreen(
    onEmergency: () -> Unit = {},
    onPause: () -> Unit = {},
    onVerify: () -> Unit = {},
    viewModel: LockViewModel,
) {
    val state by viewModel.state.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NiyyahColors.Navy)) {
        // Atmospheric tonal layers — node 1:1009 / 1:1010
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-300).dp)
                .size(600.dp)
                .blur(32.dp)
                .background(MintGlow.copy(alpha = 0.05f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 200.dp, y = 200.dp)
                .size(400.dp)
                .blur(32.dp)
                .background(Color(0xFFD8E2FF).copy(alpha = 0.05f), CircleShape),
        )

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header — node 1:1011: single EMERGENCY pill, right-aligned
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(
                    modifier = Modifier
                        .border(1.dp, EmergencyRose.copy(alpha = 0.3f), NiyyahShapes.Pill)
                        .clickable { onEmergency() }
                        .padding(horizontal = 17.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_emergency_lock),
                        contentDescription = null,
                        tint = EmergencyRose.copy(alpha = 0.8f),
                        modifier = Modifier.width(12.dp).height(15.75.dp),
                    )
                    Text(
                        text = "EMERGENCY",
                        style = NiyyahType.Badge.copy(letterSpacing = 1.2.sp),
                        color = EmergencyRose.copy(alpha = 0.8f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 19.dp, bottom = 67.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                FocusTimer(state, nowMs)

                // PAUSE SESSION — node 1:1036
                Row(
                    modifier = Modifier
                        .width(280.dp)
                        .background(Color.White, NiyyahShapes.Pill)
                        .clickable { onPause() }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = null,
                        tint = NiyyahColors.Navy,
                        modifier = Modifier.width(12.dp).height(14.dp),
                    )
                    Text(
                        text = "PAUSE SESSION",
                        style = NiyyahType.LabelUppercaseWide,
                        color = NiyyahColors.Navy,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    VerificationPrompt(state.verificationMethod, onVerify)
                    BlockedAppsStrip()
                }
            }
        }
    }
}

/** Focal point — node 1:1018. Concentric rings, prayer name, giant countdown. */
@Composable
private fun FocusTimer(state: LockUiState, nowMs: Long) {
    val remainingMs = (state.lockEndMs - nowMs).coerceAtLeast(0L)
    val totalSeconds = remainingMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val big = if (hours > 0) String.format(Locale.ENGLISH, "%02d:%02d", hours, minutes)
    else String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
    val small = if (hours > 0) String.format(Locale.ENGLISH, ":%02d", seconds) else null

    Box(contentAlignment = Alignment.Center) {
        // Decorative rings — node 1:1019
        Box(modifier = Modifier.alpha(0.1f), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(340.dp).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape))
            Box(modifier = Modifier.size(280.dp).border(1.dp, Color.White, CircleShape))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "CURRENT FOCUS",
                style = NiyyahType.Badge.copy(letterSpacing = 1.2.sp),
                color = Mint,
                modifier = Modifier.alpha(0.8f).padding(bottom = 8.dp),
            )
            Text(
                text = state.prayer.displayName,
                style = NiyyahType.Wordmark.copy(letterSpacing = (-0.36).sp),
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = big,
                    fontFamily = EbGaramond,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    fontSize = 80.sp,
                    lineHeight = 80.sp,
                    letterSpacing = (-4).sp,
                    color = Color.White,
                )
                if (small != null) {
                    Text(
                        text = small,
                        fontFamily = EbGaramond,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        fontSize = 36.sp,
                        lineHeight = 40.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            Text(
                text = "remaining until unlock",
                style = NiyyahType.Body,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** Verification prompt card — node 1:1042. */
@Composable
private fun VerificationPrompt(method: VerificationMethod, onVerify: () -> Unit) {
    val description = when (method) {
        VerificationMethod.VOICE ->
            "Recite the verification phrase to complete\nthis session mindfully."
        VerificationMethod.TEXT ->
            "Type the verification phrase to complete\nthis session mindfully."
        else ->
            "Verify your prayer to complete\nthis session mindfully."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFCF9F8).copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFFCF9F8).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable { onVerify() }
            .padding(25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MintGlow.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, MintGlow.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_verify_ring),
                contentDescription = null,
                tint = Mint,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Verify to Unlock",
                style = NiyyahType.Quote.copy(lineHeight = 30.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = description,
                style = NiyyahType.Body.copy(lineHeight = 26.sp),
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Restricted apps strip — node 1:1051. Real blocked apps with system icons. */
@Composable
private fun BlockedAppsStrip() {
    val context = LocalContext.current
    val app = context.applicationContext as SalahLockApplication
    val blockedPackages by remember { app.blacklistRepository.observeBlockedPackages() }
        .collectAsState(initial = emptyList())
    val pm = context.packageManager

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "RESTRICTED ACCESS",
                style = NiyyahType.Badge.copy(letterSpacing = 1.2.sp),
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text = "Focus Mode",
                style = NiyyahType.Badge,
                color = Color.White.copy(alpha = 0.3f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            blockedPackages.take(8).forEach { pkg ->
                val info = remember(pkg) {
                    try {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(ai).toString() to
                            pm.getApplicationIcon(ai).toBitmap(80, 80).asImageBitmap()
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                } ?: return@forEach
                Column(
                    modifier = Modifier
                        .widthIn(min = 88.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(vertical = 17.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        bitmap = info.second,
                        contentDescription = null,
                        modifier = Modifier.size(26.67.dp),
                    )
                    Text(
                        text = info.first,
                        style = NiyyahType.Badge,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
