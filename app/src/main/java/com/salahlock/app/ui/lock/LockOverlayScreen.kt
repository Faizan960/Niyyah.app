package com.salahlock.app.ui.lock

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.repository.OverrideReason
import com.salahlock.app.data.repository.OverrideResult
import com.salahlock.app.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stitch V2 — dark/salah-lock: ambient emerald glow, "<Prayer> Focus" serif
 * header, 80sp EB Garamond emerald countdown, quick-actions pill (call /
 * reading / emergency), translucent 32dp-radius "Verify to Unlock" card.
 */
@Composable
fun LockOverlayScreen(
    onVerifyPrayer: () -> Unit,
    onOverrideGranted: () -> Unit,
    onLockExpired: () -> Unit = {},
    viewModel: LockViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prayer = state.prayer
    val lockEndMs = state.lockEndMs
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    var showOverrideConfirm by remember { mutableStateOf(false) }
    var overrideCountdown by remember { mutableStateOf(5) }
    var selectedReason by remember { mutableStateOf(OverrideReason.OTHER) }
    var submitting by remember { mutableStateOf(false) }
    var overrideError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showOverrideConfirm) {
        if (showOverrideConfirm) {
            // Reset the sheet each time it opens so a prior attempt can't leak state.
            overrideCountdown = 5
            submitting = false
            overrideError = null
            selectedReason = OverrideReason.OTHER
            while (overrideCountdown > 0) {
                delay(1000L)
                overrideCountdown--
            }
        }
    }

    var minutesText by remember { mutableStateOf("00") }
    var secondsText by remember { mutableStateOf("00") }
    LaunchedEffect(lockEndMs) {
        while (true) {
            val diff = lockEndMs - System.currentTimeMillis()
            if (diff <= 0) {
                onLockExpired()
                return@LaunchedEffect
            }
            minutesText = (diff / 60_000).toString().padStart(2, '0')
            secondsText = ((diff % 60_000) / 1_000).toString().padStart(2, '0')
            delay(1000L)
        }
    }

    // Ambient background glow — 8s ease-in-out breathing (Stitch .bg-glow)
    val glow = rememberInfiniteTransition(label = "glow")
    val glowScale by glow.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOut), RepeatMode.Reverse),
        label = "glowScale",
    )
    val glowAlpha by glow.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOut), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    // Pulsing verify ring (Stitch animate-ping, 3s)
    val ring = rememberInfiniteTransition(label = "ring")
    val ringScale by ring.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(3000, easing = EaseOut), RepeatMode.Restart),
        label = "ringScale",
    )
    val ringAlpha by ring.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(3000, easing = EaseOut), RepeatMode.Restart),
        label = "ringAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // Centered ambient emerald glow
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
                .scale(glowScale)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        listOf(EmeraldSecondary.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // ── Focus header ──────────────────────────────────────────────
            Icon(
                Icons.Filled.Mosque,
                contentDescription = null,
                tint = EmeraldSecondary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${prayer.displayName} Focus",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = "DEVICE LOCKED",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(32.dp))

            // ── Countdown — 80sp serif, dimmed colon ──────────────────────
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = minutesText,
                    fontFamily = NiyyahSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 80.sp,
                    lineHeight = 80.sp,
                    letterSpacing = (-2).sp,
                    color = EmeraldSecondary,
                )
                Text(
                    text = ":",
                    fontFamily = NiyyahSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 80.sp,
                    lineHeight = 80.sp,
                    color = EmeraldSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                    text = secondsText,
                    fontFamily = NiyyahSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 80.sp,
                    lineHeight = 80.sp,
                    letterSpacing = (-2).sp,
                    color = EmeraldSecondary,
                )
            }
            Text(
                text = "Remaining",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 12.dp),
            )

            Spacer(Modifier.height(64.dp))

            // ── Quick actions pill: call / reading / emergency ────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        RoundedCornerShape(50),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 32.dp, vertical = 12.dp),
            ) {
                QuickActionCircle(Icons.Outlined.Call, "Phone") {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                PillDivider()
                QuickActionCircle(Icons.Outlined.AutoStories, "Azkar") {
                    context.startActivity(
                        Intent(context, com.salahlock.app.MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                PillDivider()
                QuickActionCircle(Icons.Outlined.Emergency, "Emergency override") {
                    showOverrideConfirm = true
                }
            }
            Text(
                text = "Emergency & Prayer Apps Only",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 12.dp),
            )

            Spacer(Modifier.weight(1f))

            // ── Verify to Unlock card ─────────────────────────────────────
            Surface(
                onClick = onVerifyPrayer,
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .scale(ringScale)
                                .alpha(ringAlpha)
                                .border(2.dp, EmeraldSecondary.copy(alpha = 0.4f), CircleShape)
                        )
                        Box(
                            Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.TaskAlt,
                                contentDescription = null,
                                tint = EmeraldSecondary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Verify to Unlock",
                        fontFamily = NiyyahSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 20.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Confirm your prayer to exit focus mode.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                }
            }
        }

        if (showOverrideConfirm) {
            val override = state.overrideState
            val remaining = (override.maxPerMonth - override.usedThisMonth).coerceAtLeast(0)
            val hasAllowance = override.canOverride && remaining > 0

            AlertDialog(
                onDismissRequest = { if (!submitting) showOverrideConfirm = false },
                title = { Text("Emergency Override") },
                text = {
                    Column {
                        DetailRow("Prayer", prayer.displayName)
                        DetailRow("Overrides remaining", "$remaining / ${override.maxPerMonth}")
                        Spacer(Modifier.height(12.dp))

                        if (hasAllowance) {
                            Text(
                                "Prayer protection will pause for this prayer only. Use this " +
                                    "just when you truly cannot verify normally.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "REASON",
                                style = MaterialTheme.typography.labelMedium,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            OverrideReason.entries.forEach { reason ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = !submitting) { selectedReason = reason }
                                        .padding(vertical = 2.dp),
                                ) {
                                    RadioButton(
                                        selected = selectedReason == reason,
                                        onClick = { selectedReason = reason },
                                        enabled = !submitting,
                                        colors = RadioButtonDefaults.colors(selectedColor = EmeraldSecondary),
                                    )
                                    Text(
                                        reason.displayText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        } else {
                            Text(
                                "No emergency overrides remaining this month. Prayer protection " +
                                    "will stay active — please verify your prayer to continue.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        overrideError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    if (hasAllowance) {
                        Button(
                            // Gated on the countdown (accidental-use guard) AND a submitting
                            // latch so a rapid double-tap can only ever launch ONE attempt.
                            onClick = {
                                if (submitting) return@Button
                                submitting = true
                                overrideError = null
                                scope.launch {
                                    when (viewModel.useOverride(selectedReason)) {
                                        is OverrideResult.Consumed,
                                        is OverrideResult.AlreadyHandled -> {
                                            showOverrideConfirm = false
                                            onOverrideGranted()
                                        }
                                        is OverrideResult.NoAllowance -> {
                                            // Allowance ran out (e.g. used elsewhere) — do NOT
                                            // unlock; surface the state and let the user verify.
                                            submitting = false
                                            overrideError =
                                                "No emergency overrides remaining this month."
                                        }
                                    }
                                }
                            },
                            enabled = overrideCountdown == 0 && !submitting,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary),
                        ) {
                            Text(
                                when {
                                    submitting -> "Please wait…"
                                    overrideCountdown > 0 -> "Wait ${overrideCountdown}s"
                                    else -> "Confirm Emergency Override"
                                }
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showOverrideConfirm = false },
                        enabled = !submitting,
                    ) {
                        Text(
                            if (hasAllowance) "Cancel" else "Close",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun QuickActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PillDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
