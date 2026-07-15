package com.salahlock.app.ui.verification

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahType

// ─────────────────────────────────────────────────────────────────────────────
// Shared chrome
// ─────────────────────────────────────────────────────────────────────────────

/** Full-screen background: soft vertical wash + a single low emerald glow. */
@Composable
private fun VerifyBackground(palette: VerifyPalette, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(palette.bgTop, palette.bgBottom)),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(360.dp)
                .background(palette.glow, CircleShape),
        )
        content()
    }
}

/** NIYYAH wordmark row, with an optional back arrow — matches every frame's header. */
@Composable
private fun VerifyTopBar(palette: VerifyPalette, onBack: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = "Back",
                tint = palette.emerald,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable { onBack() }
                    .size(20.dp),
            )
        }
        Text(
            text = "NIYYAH",
            style = NiyyahType.LabelUppercaseWide.copy(fontSize = 18.sp, letterSpacing = 3.sp),
            color = palette.wordmark,
        )
    }
}

/** Primary emerald pill button — the design system's one call-to-action shape. */
@Composable
private fun EmeraldButton(
    label: String,
    palette: VerifyPalette,
    modifier: Modifier = Modifier,
    trailingArrow: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.emerald, CircleShape)
            .clickable { onClick() }
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = NiyyahType.Body.copy(fontSize = 18.sp),
            color = palette.onEmerald,
        )
        if (trailingArrow) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = palette.onEmerald,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Quiet text link — "Cancel", "Use Typing Instead", "Choose another method". */
@Composable
private fun TextLink(
    label: String,
    palette: VerifyPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = NiyyahType.Body,
        color = palette.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 8.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 1 · Method selection
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VerificationMethodScreen(
    palette: VerifyPalette,
    selected: VerifyMethod,
    onSelect: (VerifyMethod) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    VerifyBackground(palette) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "NIYYAH",
                    style = NiyyahType.LabelUppercaseWide.copy(fontSize = 18.sp, letterSpacing = 3.sp),
                    color = palette.wordmark,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Verify Your Prayer",
                    style = NiyyahType.Quote.copy(fontSize = 24.sp, lineHeight = 32.sp),
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Choose how you would like to verify today's prayer.",
                    style = NiyyahType.Body,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                MethodCard(
                    palette = palette,
                    iconRes = R.drawable.ic_verify_mic,
                    title = "Voice Verification",
                    subtitle = "Recite your affirmation aloud.",
                    badge = "RECOMMENDED",
                    badgeEmerald = true,
                    selected = selected == VerifyMethod.VOICE,
                    enabled = true,
                    onClick = { onSelect(VerifyMethod.VOICE) },
                )
                Spacer(Modifier.height(16.dp))
                MethodCard(
                    palette = palette,
                    iconRes = R.drawable.ic_verify_keyboard,
                    title = "Typing Verification",
                    subtitle = "Type your affirmation with intention.",
                    badge = null,
                    badgeEmerald = false,
                    selected = selected == VerifyMethod.TYPING,
                    enabled = true,
                    onClick = { onSelect(VerifyMethod.TYPING) },
                )
                Spacer(Modifier.height(16.dp))
                MethodCard(
                    palette = palette,
                    iconRes = R.drawable.ic_verify_camera,
                    title = "Camera Verification",
                    subtitle = "Prayer posture recognition.",
                    badge = "COMING SOON",
                    badgeEmerald = false,
                    selected = selected == VerifyMethod.CAMERA,
                    enabled = false,
                    onClick = { onSelect(VerifyMethod.CAMERA) },
                )
            }
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmeraldButton(label = "Continue", palette = palette, onClick = onContinue)
                Spacer(Modifier.height(4.dp))
                TextLink(label = "Cancel", palette = palette, onClick = onCancel)
            }
        }
    }
}

@Composable
private fun MethodCard(
    palette: VerifyPalette,
    iconRes: Int,
    title: String,
    subtitle: String,
    badge: String?,
    badgeEmerald: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val fill = if (selected) palette.selectedFill else palette.cardFill
    val border = if (selected) palette.selectedBorder else palette.cardBorder
    val titleColor = if (enabled) palette.textPrimary else palette.disabledText
    val subtitleColor = if (enabled) palette.textSecondary else palette.disabledText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(if (selected) 1.5.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (selected) palette.emerald.copy(alpha = 0.12f) else palette.cardBorder,
                    CircleShape,
                )
                .border(1.dp, if (selected) palette.emerald.copy(alpha = 0.4f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (enabled) (if (selected) palette.emerald else palette.textSecondary) else palette.disabledText,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = NiyyahType.Body.copy(fontSize = 18.sp),
                    color = titleColor,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    val badgeColor = if (badgeEmerald) palette.emerald else palette.textMuted
                    Box(
                        modifier = Modifier
                            .border(1.dp, badgeColor.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = badge,
                            style = NiyyahType.Badge.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
                            color = badgeColor,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = NiyyahType.Body,
                color = subtitleColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2 · Voice verification (idle / listening / failed)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VoiceVerificationScreen(
    palette: VerifyPalette,
    state: VerificationUiState,
    onBack: () -> Unit,
    onTapToSpeak: () -> Unit,
    onRetry: () -> Unit,
    onSwitchToTyping: () -> Unit,
) {
    val listening = state.status == AttemptStatus.LISTENING
    val failed = state.status == AttemptStatus.FAILED
    VerifyBackground(palette) {
        Column(modifier = Modifier.fillMaxSize()) {
            VerifyTopBar(palette, onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                PulsingMic(palette, active = listening)
                Spacer(Modifier.height(24.dp))
                Text(
                    text = state.prayer.displayName,
                    style = NiyyahType.DisplayLarge.copy(fontSize = 44.sp, lineHeight = 52.sp),
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Recite the following affirmation.",
                    style = NiyyahType.Body,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                AffirmationCard(palette, state.affirmation)
                Spacer(Modifier.height(40.dp))
                when {
                    failed -> GentleFailure(palette)
                    listening -> ListeningIndicator(palette, state.amplitude)
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmeraldButton(
                    label = when {
                        failed -> "Try Again"
                        listening -> "Listening…"
                        else -> "Tap to Speak"
                    },
                    palette = palette,
                    onClick = { if (failed) onRetry() else onTapToSpeak() },
                )
                Spacer(Modifier.height(8.dp))
                TextLink(
                    label = if (failed) "Choose another method" else "Use Typing Instead",
                    palette = palette,
                    onClick = onSwitchToTyping,
                )
            }
        }
    }
}

/** Emerald mic with a soft breathing halo — calm, never flashy. */
@Composable
private fun PulsingMic(palette: VerifyPalette, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "mic")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.18f else 1.06f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "micScale",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .background(palette.emerald.copy(alpha = 0.12f), CircleShape),
        )
        Icon(
            painter = painterResource(R.drawable.ic_verify_mic),
            contentDescription = null,
            tint = palette.emerald,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun AffirmationCard(palette: VerifyPalette, affirmation: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.cardFill)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = affirmation,
            style = NiyyahType.Quote.copy(
                fontSize = 28.sp,
                lineHeight = 40.sp,
                fontStyle = FontStyle.Italic,
            ),
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Green dot + LISTENING… + a small animated emerald waveform. */
@Composable
private fun ListeningIndicator(palette: VerifyPalette, amplitude: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(8.dp).background(palette.emerald, CircleShape))
            Text(
                text = "LISTENING…",
                style = NiyyahType.LabelUppercase.copy(letterSpacing = 1.4.sp),
                color = palette.emerald,
            )
        }
        Spacer(Modifier.height(24.dp))
        Waveform(palette, amplitude)
    }
}

@Composable
private fun Waveform(palette: VerifyPalette, amplitude: Float) {
    val transition = rememberInfiniteTransition(label = "wave")
    val bars = 5
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.height(40.dp),
    ) {
        repeat(bars) { i ->
            val phase by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(500 + i * 90, delayMillis = i * 60),
                    RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            val h = (8f + 28f * phase * (0.4f + amplitude)).coerceIn(6f, 40f)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.emerald),
            )
        }
    }
}

/** Gentle, guilt-free failure copy — no harsh warnings. */
@Composable
private fun GentleFailure(palette: VerifyPalette) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "That didn't quite match.",
            style = NiyyahType.Quote.copy(fontSize = 20.sp, lineHeight = 28.sp),
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Take a breath and recite it once more, gently.",
            style = NiyyahType.Body,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3 · Typing verification
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TypingVerificationScreen(
    palette: VerifyPalette,
    state: VerificationUiState,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onVerify: () -> Unit,
    onSwitchToVoice: () -> Unit,
) {
    val target = state.affirmation.trim().trim('"').trimEnd('.')
    VerifyBackground(palette) {
        Column(modifier = Modifier.fillMaxSize()) {
            VerifyTopBar(palette, onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.size(8.dp).background(palette.emerald, CircleShape))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "STEP ${(state.attempts + 1).coerceAtMost(state.maxAttempts)} OF ${state.maxAttempts}",
                    style = NiyyahType.LabelUppercase.copy(letterSpacing = 1.4.sp),
                    color = palette.textMuted,
                )
                Spacer(Modifier.height(40.dp))
                Text(
                    text = "Type the affirmation exactly as shown.",
                    style = NiyyahType.Body,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                // Faded target affirmation (reference "as shown").
                Text(
                    text = state.affirmation,
                    style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 40.sp),
                    color = palette.affirmation,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                BasicTextField(
                    value = state.typedText,
                    onValueChange = onTextChange,
                    textStyle = NiyyahType.Quote.copy(
                        fontSize = 24.sp,
                        lineHeight = 34.sp,
                        color = palette.textPrimary,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(palette.emerald),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${state.typedText.length} / ${target.length}",
                    style = NiyyahType.Badge,
                    color = palette.textMuted,
                    modifier = Modifier.align(Alignment.End),
                )
                if (state.status == AttemptStatus.FAILED) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Not an exact match yet — check the words and try again.",
                        style = NiyyahType.Body,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmeraldButton(label = "Verify Intention", palette = palette, onClick = onVerify)
                Spacer(Modifier.height(8.dp))
                TextLink(label = "Use Voice Instead", palette = palette, onClick = onSwitchToVoice)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4 · Camera — coming soon
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CameraComingSoonScreen(
    palette: VerifyPalette,
    onBack: () -> Unit,
    onNotifyMe: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    VerifyBackground(palette) {
        Column(modifier = Modifier.fillMaxSize()) {
            VerifyTopBar(palette, onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(palette.emerald.copy(alpha = 0.10f), CircleShape)
                        .border(1.dp, palette.emerald.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_verify_camera),
                        contentDescription = null,
                        tint = palette.emerald,
                        modifier = Modifier.size(56.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    text = "Camera Verification",
                    style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Prayer posture recognition is on its way, insha'Allah. We'll let you know the moment it arrives.",
                    style = NiyyahType.Body,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmeraldButton(label = "Notify Me", palette = palette, onClick = onNotifyMe)
                Spacer(Modifier.height(8.dp))
                TextLink(label = "Choose Another Method", palette = palette, onClick = onChooseAnother)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5 · Unlock success
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UnlockSuccessScreen(
    palette: VerifyPalette,
    onContinue: () -> Unit,
) {
    VerifyBackground(palette) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SuccessSeal(palette)
                Spacer(Modifier.height(32.dp))
                Text(
                    text = "Focus Mode\nCompleted.",
                    style = NiyyahType.Quote.copy(fontSize = 32.sp, lineHeight = 40.sp),
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "May Allah accept your worship.",
                    style = NiyyahType.Body.copy(fontSize = 18.sp),
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                EmeraldButton(
                    label = "Continue",
                    palette = palette,
                    trailingArrow = true,
                    onClick = onContinue,
                )
            }
        }
    }
}

/** Ringed emerald check with a soft glow — the emerald "success" moment. */
@Composable
private fun SuccessSeal(palette: VerifyPalette) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .border(1.dp, palette.emerald.copy(alpha = 0.3f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(palette.emerald.copy(alpha = 0.10f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(palette.emerald, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_verify_check),
                contentDescription = null,
                tint = palette.onEmerald,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
