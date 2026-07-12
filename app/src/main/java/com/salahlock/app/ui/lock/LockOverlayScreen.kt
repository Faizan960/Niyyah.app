package com.salahlock.app.ui.lock

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.theme.*
import com.salahlock.app.verification.VerificationMethod
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockOverlayScreen(
    onVerifyPrayer: () -> Unit,
    onOverrideGranted: () -> Unit,
    onLockExpired: () -> Unit = {},
    viewModel: LockViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reactive — update whenever the ViewModel state changes (including via onNewIntent)
    val prayer = state.prayer
    val lockEndMs = state.lockEndMs

    val scope = rememberCoroutineScope()
    var showOverrideConfirm by remember { mutableStateOf(false) }
    var overrideCountdown by remember { mutableStateOf(5) }

    LaunchedEffect(showOverrideConfirm) {
        if (showOverrideConfirm) {
            overrideCountdown = 5
            while (overrideCountdown > 0) {
                delay(1000L)
                overrideCountdown--
            }
        }
    }

    // Countdown timer — re-executes whenever lockEndMs changes (e.g., after onNewIntent).
    // Dismisses the overlay when the lock window expires.
    val timeRemaining by produceState("", lockEndMs) {
        while (true) {
            val diff = lockEndMs - System.currentTimeMillis()
            if (diff <= 0) {
                value = "Unlocked"
                onLockExpired()
                return@produceState
            }
            val min = diff / 60_000
            val sec = (diff % 60_000) / 1_000
            value = "${min}:${sec.toString().padStart(2, '0')}"
            delay(1000L)
        }
    }

    // Pulsing aura animation
    val auraScale by rememberInfiniteTransition(label = "aura").animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(3000, easing = EaseInOut), RepeatMode.Reverse),
        label = "auraScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center,
    ) {
        // Subtle emerald radial glow — accent only, not a solid color fill.
        Box(
            modifier = Modifier
                .size((300 * auraScale).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            EmeraldPrimary.copy(alpha = 0.18f),
                            Color.Transparent,
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "CURRENT PRAYER",
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldPrimary,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = prayer.displayName,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "It's time to answer the call to prayer.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))

            // Timer
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = timeRemaining,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "REMAINING",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Session Details
            Card(
                modifier = Modifier.fillMaxWidth(0.85f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Lock Session", fontWeight = FontWeight.Bold, color = EmeraldPrimary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    DetailRow("Verification", when (state.verificationMethod) {
                        VerificationMethod.TEXT -> "Text Confirmation"
                        VerificationMethod.VOICE -> "Voice Confirmation"
                        VerificationMethod.ASK_EVERY_TIME, VerificationMethod.CAMERA_DISABLED -> "Your Choice"
                        VerificationMethod.CAMERA_AI_FUTURE -> "AI Verification"
                    })
                    DetailRow("Status", "Awaiting Verification")
                }
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onVerifyPrayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "I Have Prayed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = { showOverrideConfirm = true }) {
                Text(
                    text = "Emergency Override",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showOverrideConfirm) {
            AlertDialog(
                onDismissRequest = { showOverrideConfirm = false },
                title = { Text("Emergency Override") },
                text = { Text("Are you sure? Prayer protection will be temporarily disabled.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showOverrideConfirm = false
                            scope.launch {
                                viewModel.useOverride(com.salahlock.app.data.repository.OverrideReason.OTHER)
                                onOverrideGranted()
                            }
                        },
                        enabled = overrideCountdown == 0,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text(if (overrideCountdown > 0) "Wait ${overrideCountdown}s" else "Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOverrideConfirm = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
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
