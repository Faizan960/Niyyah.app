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
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.theme.*
import com.salahlock.app.verification.AffirmationGenerator

/**
 * Text-based verification screen.
 *
 * Presents [confirmCount] affirmations one at a time.
 * User types each affirmation into a text field.
 * Validation is case-insensitive with whitespace normalisation.
 * No typed text is stored — only the boolean success count.
 */
@Composable
fun TextVerificationScreen(
    prayer: PrayerName,
    confirmCount: Int,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val affirmations = remember(prayer, confirmCount) {
        AffirmationGenerator.generate(prayer, confirmCount)
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val currentAffirmation = affirmations.getOrNull(currentIndex) ?: return

    LaunchedEffect(currentIndex) {
        inputText = ""
        showError = false
        focusRequester.requestFocus()
    }

    fun submit() {
        if (AffirmationGenerator.validate(inputText, currentAffirmation)) {
            showError = false
            if (currentIndex + 1 >= affirmations.size) {
                onSuccess()
            } else {
                currentIndex++
            }
        } else {
            showError = true
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
            text = "TYPE TO CONFIRM",
            style = MaterialTheme.typography.labelMedium,
            color = MutedSage,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(12.dp))
        VerificationProgressDots(current = currentIndex, total = affirmations.size)

        Spacer(Modifier.weight(0.8f))

        // Affirmation display — animated slide on change
        AnimatedContent(
            targetState = currentAffirmation,
            transitionSpec = {
                (slideInVertically(tween(250)) { it / 4 } + fadeIn(tween(250)))
                    .togetherWith(slideOutVertically(tween(150)) { -it / 4 } + fadeOut(tween(150)))
            },
            label = "affirmation_text",
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
            text = "Type the phrase above",
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
                { Text("That doesn't match — please try again.", color = MaterialTheme.colorScheme.error) }
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
                text = if (currentIndex + 1 < affirmations.size) "Confirm (${currentIndex + 1}/${affirmations.size})" else "Complete",
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
