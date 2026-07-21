package com.salahlock.app.ui.verification

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardAlt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.theme.*
import com.salahlock.app.verification.*
import com.salahlock.app.verification.VerificationMethod.Companion.effective
import kotlinx.coroutines.delay

private enum class VerificationStep { REMINDER, SELECTION, TEXT, VOICE, SUCCESS }

/**
 * Full-screen verification flow.
 *
 * State machine: REMINDER → SELECTION (if ASK_EVERY_TIME) → TEXT or VOICE → SUCCESS
 *
 * Shown inside [LockOverlayActivity] when the user taps "I Have Prayed".
 * [onSuccess] is called after the success screen auto-dismisses — the Activity
 * then calls [LockViewModel.recordPrayerCompleted] and finishes.
 */
@Composable
fun VerificationFlowScreen(
    prayer: PrayerName,
    verificationMethod: VerificationMethod,
    confirmCount: Int,
    reminderQuran: Boolean,
    reminderHadith: Boolean,
    reminderReflection: Boolean,
    currentStreak: Int,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val effective = verificationMethod.effective()
    val reminder = remember {
        ReminderRepository.getRandom(reminderQuran, reminderHadith, reminderReflection)
    }
    // BM-VERIFY-004 — one universal phrase for this session, personalised to the
    // prayer being confirmed ("I prayed my Asr"). Same phrase for text and voice.
    val phrase = remember(prayer) { VerificationPhraseProvider.phraseFor(prayer.displayName) }
    var step by remember { mutableStateOf(VerificationStep.REMINDER) }
    var chosenMethod by remember { mutableStateOf<VerificationMethod?>(null) }

    val resolvedMethod: VerificationMethod = when (effective) {
        VerificationMethod.ASK_EVERY_TIME -> chosenMethod ?: VerificationMethod.TEXT
        else -> effective
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.97f))
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "verification_step",
        ) { currentStep ->
            when (currentStep) {
                VerificationStep.REMINDER -> ReminderScreen(
                    reminder = reminder,
                    onContinue = {
                        step = when (effective) {
                            VerificationMethod.TEXT -> VerificationStep.TEXT
                            VerificationMethod.VOICE -> VerificationStep.VOICE
                            else -> VerificationStep.SELECTION
                        }
                    },
                    onBack = onBack,
                )

                VerificationStep.SELECTION -> VerificationSelectionScreen(
                    onText = { chosenMethod = VerificationMethod.TEXT; step = VerificationStep.TEXT },
                    onVoice = { chosenMethod = VerificationMethod.VOICE; step = VerificationStep.VOICE },
                    onBack = { step = VerificationStep.REMINDER },
                )

                VerificationStep.TEXT -> TextVerificationScreen(
                    confirmCount = confirmCount,
                    phrase = phrase,
                    onSuccess = { step = VerificationStep.SUCCESS },
                    onBack = {
                        step = if (effective == VerificationMethod.ASK_EVERY_TIME)
                            VerificationStep.SELECTION else VerificationStep.REMINDER
                    },
                )

                VerificationStep.VOICE -> VoiceVerificationScreen(
                    confirmCount = confirmCount,
                    phrase = phrase,
                    onSuccess = { step = VerificationStep.SUCCESS },
                    onBack = {
                        step = if (effective == VerificationMethod.ASK_EVERY_TIME)
                            VerificationStep.SELECTION else VerificationStep.REMINDER
                    },
                    onFallbackToText = { step = VerificationStep.TEXT },
                )

                VerificationStep.SUCCESS -> VerificationSuccessScreen(
                    prayer = prayer,
                    currentStreak = currentStreak,
                    onDismiss = onSuccess,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// REMINDER SCREEN
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReminderScreen(
    reminder: Reminder,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val typeLabel = when (reminder.type) {
        ReminderType.QURAN -> "QURAN"
        ReminderType.HADITH -> "HADITH"
        ReminderType.REFLECTION -> "REFLECTION"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))

        // Type chip
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when (reminder.type) {
                ReminderType.QURAN, ReminderType.HADITH -> GoldAccent.copy(alpha = 0.12f)
                ReminderType.REFLECTION -> EmeraldPrimary.copy(alpha = 0.12f)
            },
        ) {
            Text(
                text = typeLabel,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = when (reminder.type) {
                    ReminderType.QURAN, ReminderType.HADITH -> GoldAccent
                    ReminderType.REFLECTION -> EmeraldPrimary
                },
                letterSpacing = 1.5.sp,
            )
        }

        Spacer(Modifier.height(28.dp))

        // Arabic text (Quran / some Hadiths)
        if (!reminder.arabic.isNullOrBlank()) {
            Text(
                text = reminder.arabic,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 46.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.4f),
                thickness = 0.5.dp,
                color = GoldAccent.copy(alpha = 0.3f),
            )
            Spacer(Modifier.height(20.dp))
        }

        // Translation
        Text(
            text = reminder.translation,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
            fontStyle = if (reminder.arabic.isNullOrBlank()) FontStyle.Normal else FontStyle.Normal,
        )

        Spacer(Modifier.height(16.dp))

        // Reference
        Text(
            text = reminder.reference,
            style = MaterialTheme.typography.labelLarge,
            color = GoldAccent,
            letterSpacing = 0.5.sp,
        )

        Spacer(Modifier.height(40.dp))

        // Sincerity note
        Text(
            text = "This reminder is for reflection and sincerity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldPrimary,
                contentColor = Color.White,
            ),
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onBack) {
            Text(
                "← Back",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// SELECTION SCREEN
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun VerificationSelectionScreen(
    onText: () -> Unit,
    onVoice: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "Choose Verification",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "How would you like to confirm your prayer?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        VerificationOptionCard(
            icon = Icons.Rounded.KeyboardAlt,
            title = "Type",
            subtitle = "Type a short affirmation",
            onClick = onText,
        )

        Spacer(Modifier.height(16.dp))

        VerificationOptionCard(
            icon = Icons.Rounded.Mic,
            title = "Voice",
            subtitle = "Say your confirmation aloud",
            onClick = onVoice,
        )

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onBack) {
            Text(
                "← Back",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VerificationOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = ElevatedSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(EmeraldPrimary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// SUCCESS SCREEN
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun VerificationSuccessScreen(
    prayer: PrayerName,
    currentStreak: Int,
    onDismiss: () -> Unit,
) {
    val scale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        )
        delay(2000L)
        onDismiss()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))

        // Check circle
        Box(
            modifier = Modifier
                .scale(scale.value)
                .size(100.dp)
                .background(EmeraldPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Prayer Verified",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "May Allah accept your ${prayer.displayName} prayer.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))

        // Streak card
        if (currentStreak > 0) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ElevatedSurface,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "CURRENT STREAK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSage,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$currentStreak Days",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onDismiss) {
            Text(
                "Continue",
                style = MaterialTheme.typography.titleMedium,
                color = EmeraldPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// SHARED: Progress dots
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun VerificationProgressDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until total) {
            val filled = i < current
            val color by animateColorAsState(
                targetValue = if (filled) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(250),
                label = "dot_color_$i",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
            )
        }
    }
}
