package com.salahlock.app.ui.verification

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.verification.AffirmationGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which method the user is verifying with inside the flow. */
enum class VerifyMethod { VOICE, TYPING, CAMERA }

/** State of a voice or typing verification attempt. */
enum class AttemptStatus { IDLE, LISTENING, SUCCESS, FAILED }

data class VerificationUiState(
    val prayer: PrayerName = PrayerName.FAJR,
    /** The exact affirmation the user must recite/type, e.g. "I answered the call to Dhuhr." */
    val affirmation: String = "",
    val selectedMethod: VerifyMethod = VerifyMethod.VOICE,
    val status: AttemptStatus = AttemptStatus.IDLE,
    /** 0f–1f microphone amplitude for the live waveform. */
    val amplitude: Float = 0f,
    /** Attempts used so far (drives "STEP n OF 3" and the gentle retry copy). */
    val attempts: Int = 0,
    val maxAttempts: Int = 3,
    /** Live typed text for the typing screen. */
    val typedText: String = "",
    /** True once the prayer has been recorded as verified in Room. */
    val recorded: Boolean = false,
)

/**
 * Drives the whole prayer-verification flow (method selection → voice / typing /
 * camera → success). Reuses [AffirmationGenerator] for affirmation text and
 * fuzzy matching, and [com.salahlock.app.data.repository.StreakRepository] to
 * record the verified prayer — which the UsageStatsPollingService observes to
 * end the lock window. No new repository or storage is introduced.
 */
class VerificationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication

    private val _uiState = MutableStateFlow(VerificationUiState())
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var started = false

    /** Called once by the activity with the prayer being verified. */
    fun start(prayer: PrayerName, confirmCount: Int) {
        if (started) return
        started = true
        val affirmation = AffirmationGenerator.generate(prayer, 1).firstOrNull()
            ?.let { affirmationSentence(it, prayer) }
            ?: affirmationSentence("I answered the call to ${prayer.displayName}", prayer)
        _uiState.update {
            it.copy(
                prayer = prayer,
                affirmation = affirmation,
                maxAttempts = confirmCount.coerceIn(1, 3),
            )
        }
    }

    /** Presentation form used on the reference cards: "I answered the call to Dhuhr." */
    private fun affirmationSentence(raw: String, prayer: PrayerName): String {
        val preferred = "I answered the call to ${prayer.displayName}"
        val base = if (raw.startsWith("I answered the call")) raw else preferred
        return "\"$base.\""
    }

    /** The comparable phrase without the display quotes/period. */
    private fun expectedPhrase(): String =
        _uiState.value.affirmation.trim().trim('"').trimEnd('.')

    fun selectMethod(method: VerifyMethod) {
        _uiState.update { it.copy(selectedMethod = method) }
    }

    // ── Voice ─────────────────────────────────────────────────────────────────

    /**
     * Starts listening. Caller must have already ensured RECORD_AUDIO is granted.
     * Forces English recognition and matches results with the fuzzy spoken matcher
     * (per the SL-020 fix) so recognizer noise still passes.
     */
    fun startListening() {
        val context = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            // No recognizer on device — fail gently so the user can switch to typing.
            _uiState.update { it.copy(status = AttemptStatus.FAILED, attempts = it.attempts + 1) }
            return
        }
        recognizer?.destroy()
        _uiState.update { it.copy(status = AttemptStatus.LISTENING, amplitude = 0f) }

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB is roughly -2..10; normalise to 0f–1f for the waveform.
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _uiState.update { it.copy(amplitude = level) }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                onSpokenResult(emptyList())
            }
            override fun onResults(results: Bundle?) {
                val candidates = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: arrayListOf()
                onSpokenResult(candidates)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        sr.startListening(voiceIntent())
    }

    private fun voiceIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // Force English so transliterated prayer names transcribe consistently.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

    private fun onSpokenResult(candidates: List<String>) {
        val matched = AffirmationGenerator.validateSpoken(candidates, expectedPhrase())
        if (matched) {
            onVerified()
        } else {
            _uiState.update {
                it.copy(status = AttemptStatus.FAILED, amplitude = 0f, attempts = it.attempts + 1)
            }
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        _uiState.update { if (it.status == AttemptStatus.LISTENING) it.copy(status = AttemptStatus.IDLE, amplitude = 0f) else it }
    }

    // ── Typing ──────────────────────────────────────────────────────────────

    fun onTypedTextChange(text: String) {
        _uiState.update { it.copy(typedText = text) }
    }

    fun verifyTyped() {
        val state = _uiState.value
        if (AffirmationGenerator.validate(state.typedText, expectedPhrase())) {
            onVerified()
        } else {
            _uiState.update {
                it.copy(status = AttemptStatus.FAILED, attempts = it.attempts + 1)
            }
        }
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    /** Resets a failed attempt back to idle so the user can retry. */
    fun retry() {
        _uiState.update { it.copy(status = AttemptStatus.IDLE, amplitude = 0f, typedText = "") }
    }

    private fun onVerified() {
        _uiState.update { it.copy(status = AttemptStatus.SUCCESS, amplitude = 0f) }
        viewModelScope.launch {
            app.streakRepository.recordVerification(
                date = LocalDate.now().toString(),
                prayer = _uiState.value.prayer,
                wasOverride = false,
            )
            _uiState.update { it.copy(recorded = true) }
        }
    }

    override fun onCleared() {
        recognizer?.destroy()
        recognizer = null
        super.onCleared()
    }
}
