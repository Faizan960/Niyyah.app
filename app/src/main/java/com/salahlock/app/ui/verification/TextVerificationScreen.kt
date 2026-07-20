package com.salahlock.app.ui.verification

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.theme.*
import com.salahlock.app.verification.VerificationPhraseProvider

/**
 * Text-based verification screen.
 *
 * ONE phrase is selected for the session and the user types that SAME phrase
 * [confirmCount] times. The phrase is held in [rememberSaveable] so it survives
 * recomposition and configuration changes and never reshuffles between attempts
 * or after a failed attempt.
 *
 * Matching is case-insensitive with typo tolerance and important-word
 * safeguards (see [VerificationPhraseProvider]). No typed text is stored — only
 * the boolean success count.
 */
@Composable
fun TextVerificationScreen(
    confirmCount: Int,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val total = remember(confirmCount) { confirmCount.coerceAtLeast(1) }
    // Selected ONCE per session and locked for every attempt.
    val phrase = rememberSaveable {
        VerificationPhraseProvider.randomPhrase().also {
            if (com.salahlock.app.BuildConfig.DEBUG) android.util.Log.d("VerifySession", "TEXT session phrase selected: \"$it\"")
        }
    }

    var currentAttempt by rememberSaveable { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(currentAttempt) {
        showError = false
        focusRequester.requestFocus()
    }

    fun submit() {
        if (VerificationPhraseProvider.matchesTyped(inputText, phrase)) {
            showError = false
            inputText = ""
            if (currentAttempt + 1 >= total) {
                onSuccess()
            } else {
                currentAttempt++
            }
        } else {
            // Stay on the SAME attempt and phrase — let the user retry.
            showError = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // BM-012: keep the input + Confirm button above the keyboard. The
            // overlay is adjustResize, so this is a no-op double-pad guard when the
            // window already resizes, and a safety net if it pans instead.
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.5f))

        // Progress
        Text(
            text = "TYPE TO CONFIRM",
            style = MaterialTheme.typography.labelMedium,
            color = MutedSage,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(12.dp))
        VerificationProgressDots(current = currentAttempt, total = total)

        Spacer(Modifier.weight(0.8f))

        // The single locked phrase (identical every attempt)
        Text(
            text = "\"$phrase\"",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Type the phrase above  ·  Attempt ${currentAttempt + 1} of $total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(0.8f))

        // Input field
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it; showError = false },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    "Type here…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            },
            singleLine = true,
            isError = showError,
            supportingText = if (showError) {
                { Text("Almost there — check the sentence and try again.", color = MaterialTheme.colorScheme.error) }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor = MaterialTheme.colorScheme.error,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = ::submit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = inputText.isNotBlank(),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldPrimary,
                contentColor = Color.White,
                disabledContainerColor = EmeraldPrimary.copy(alpha = 0.3f),
                disabledContentColor = Color.White.copy(alpha = 0.5f),
            ),
        ) {
            Text(
                text = if (currentAttempt + 1 < total) "Confirm (${currentAttempt + 1}/$total)" else "Complete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.weight(0.5f))

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
