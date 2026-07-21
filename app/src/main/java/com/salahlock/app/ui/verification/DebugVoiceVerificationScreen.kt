package com.salahlock.app.ui.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salahlock.app.theme.EmeraldPrimary

/**
 * BM-VERIFY-003 — DEBUG-ONLY manual QA entry point for voice verification.
 *
 * Hosts the REAL [VoiceVerificationScreen] with the exact production logic
 * (VerificationPhraseProvider phrase selection, SpeechRecognizer, composite
 * scoreSpoken/0.45 matcher, 3× attempt progression, mic permissions,
 * VerifyMatch/VerifySession debug logging). It is only ever reachable from the
 * debug-only Developer row in Profile → Settings, and its route is registered
 * only under `BuildConfig.DEBUG`.
 *
 * CRITICAL: this is a pure test harness. On 3/3 success it shows a local
 * "test passed" panel and does NOTHING else — it does not touch prayer records,
 * streaks, verification status, or any blocked-app lock session. Real prayer
 * verification continues to flow through [VerificationFlowScreen] →
 * LockOverlayActivity → LockViewModel.recordPrayerCompleted() unchanged.
 *
 * A failed recognition stays on the same phrase and same attempt (handled inside
 * VoiceVerificationScreen), so QA can immediately tap the mic and try again.
 */
@Composable
fun DebugVoiceVerificationScreen(onExit: () -> Unit) {
    var passed by rememberSaveable { mutableStateOf(false) }
    // Pick a realistic per-prayer phrase for QA, e.g. "I prayed my Asr".
    val phrase = rememberSaveable {
        com.salahlock.app.verification.VerificationPhraseProvider.phraseFor(
            listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").random(),
        )
    }

    if (!passed) {
        VoiceVerificationScreen(
            confirmCount = 3,
            phrase = phrase,
            // No prayer data is written here — success only flips a local flag.
            onSuccess = { passed = true },
            onBack = onExit,
            onFallbackToText = onExit,
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(EmeraldPrimary, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Voice verification test passed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "DEBUG test only — no prayer, streak or lock data was changed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = { passed = false },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.White),
            ) { Text("Test again") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onExit) {
                Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
