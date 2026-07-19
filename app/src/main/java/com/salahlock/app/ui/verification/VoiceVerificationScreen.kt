package com.salahlock.app.ui.verification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardAlt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.theme.*
import com.salahlock.app.verification.AffirmationGenerator

private enum class VoiceState { IDLE, LISTENING, PROCESSING, ACCEPTED, ERROR_NO_MATCH, ERROR_UNAVAILABLE }

/**
 * Voice-based verification screen.
 *
 * Uses Android SpeechRecognizer for on-device recognition — no cloud API.
 * Falls back to text verification if speech is unavailable or permission denied.
 *
 * No audio data is stored. Only the boolean result (match / no match) is used.
 */
@Composable
fun VoiceVerificationScreen(
    prayer: PrayerName,
    confirmCount: Int,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    onFallbackToText: () -> Unit,
) {
    val context = LocalContext.current
    val affirmations = remember(prayer, confirmCount) {
        AffirmationGenerator.generate(prayer, confirmCount)
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    var lastHeard by remember { mutableStateOf("") }
    var startAfterGrant by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        startAfterGrant = granted
    }

    var speechRecognizer: SpeechRecognizer? by remember { mutableStateOf(null) }

    val currentAffirmation = affirmations.getOrNull(currentIndex) ?: return

    // Mic pulse animation when listening
    val micScale by rememberInfiniteTransition(label = "mic_pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOut), RepeatMode.Reverse),
        label = "mic_scale",
    )

    DisposableEffect(Unit) {
        onDispose { speechRecognizer?.destroy() }
    }

    fun startListening() {
        if (!speechAvailable) { voiceState = VoiceState.ERROR_UNAVAILABLE; return }
        if (!hasPermission) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return }

        voiceState = VoiceState.LISTENING
        lastHeard = ""

        speechRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { voiceState = VoiceState.PROCESSING }
            override fun onPartialResults(partial: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                lastHeard = matches.firstOrNull() ?: ""

                if (AffirmationGenerator.validateSpoken(matches, currentAffirmation)) {
                    voiceState = VoiceState.ACCEPTED
                } else {
                    voiceState = VoiceState.ERROR_NO_MATCH
                }
            }

            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        hasPermission = false
                        voiceState = VoiceState.IDLE
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> voiceState = VoiceState.ERROR_NO_MATCH
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> voiceState = VoiceState.ERROR_UNAVAILABLE
                    else -> voiceState = VoiceState.ERROR_NO_MATCH
                }
            }
        })

        // Affirmations are English, so force an English recognition model —
        // otherwise the recognizer uses the device locale (ur, ar, hi, …)
        // and never returns matching text. Keep the user's English variant
        // (en-IN, en-GB) when they already have one.
        val language = java.util.Locale.getDefault()
            .takeIf { it.language == "en" } ?: java.util.Locale.US
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        recognizer.startListening(intent)
    }

    // Start listening immediately after the user grants the mic permission —
    // without this the first tap appears to do nothing.
    LaunchedEffect(startAfterGrant) {
        if (startAfterGrant) {
            startAfterGrant = false
            startListening()
        }
    }

    // Auto-advance after ACCEPTED
    LaunchedEffect(voiceState) {
        if (voiceState == VoiceState.ACCEPTED) {
            kotlinx.coroutines.delay(800L)
            if (currentIndex + 1 >= affirmations.size) {
                onSuccess()
            } else {
                currentIndex++
                voiceState = VoiceState.IDLE
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.5f))

        // Progress
        Text(
            text = "SAY ALOUD",
            style = MaterialTheme.typography.labelMedium,
            color = MutedSage,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(12.dp))
        VerificationProgressDots(current = currentIndex, total = affirmations.size)

        Spacer(Modifier.weight(0.8f))

        // Affirmation
        AnimatedContent(
            targetState = currentAffirmation,
            transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 4 })
                    .togetherWith(fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 4 })
            },
            label = "voice_affirmation",
            modifier = Modifier.fillMaxWidth(),
        ) { text ->
            Text(
                text = "\"$text\"",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Please say:  ${currentAffirmation}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(0.8f))

        // Mic button
        val micColor = when (voiceState) {
            VoiceState.LISTENING -> EmeraldPrimary
            VoiceState.ACCEPTED -> SuccessGreen
            VoiceState.ERROR_NO_MATCH -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        Box(
            modifier = Modifier
                .scale(if (voiceState == VoiceState.LISTENING) micScale else 1f)
                .size(88.dp)
                .clip(CircleShape)
                .background(micColor.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, micColor, CircleShape)
                .clickable(
                    enabled = voiceState != VoiceState.LISTENING &&
                            voiceState != VoiceState.PROCESSING,
                    onClick = { startListening() }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = "Start listening",
                tint = micColor,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Status text
        val statusText = when (voiceState) {
            VoiceState.IDLE -> "Tap the mic to speak"
            VoiceState.LISTENING -> "Listening…"
            VoiceState.PROCESSING -> "Processing…"
            VoiceState.ACCEPTED -> "✓  Accepted"
            VoiceState.ERROR_NO_MATCH ->
                if (lastHeard.isNotBlank()) "Heard: \"$lastHeard\" — tap to try again"
                else "Didn't catch that — tap to try again"
            VoiceState.ERROR_UNAVAILABLE -> "Speech unavailable — use text instead"
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = when (voiceState) {
                VoiceState.ACCEPTED -> EmeraldPrimary
                VoiceState.ERROR_NO_MATCH, VoiceState.ERROR_UNAVAILABLE -> MaterialTheme.colorScheme.error
                VoiceState.LISTENING -> EmeraldPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(0.5f))

        // Use text instead
        TextButton(onClick = onFallbackToText) {
            Icon(Icons.Rounded.KeyboardAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("Use text instead", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        TextButton(onClick = onBack) {
            Text("← Back", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}
