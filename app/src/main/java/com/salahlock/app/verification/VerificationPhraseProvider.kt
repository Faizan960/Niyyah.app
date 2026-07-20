package com.salahlock.app.verification

/**
 * Single source of truth for verification phrases AND the matching strategy
 * used by both typed and spoken verification (BM-VERIFY-002).
 *
 * A verification SESSION selects ONE phrase via [randomPhrase] and the user
 * repeats that SAME phrase N times. The phrase never changes between attempts —
 * selection happens once and is held in stable session/UI state, so
 * recomposition, retries and attempt progression never reshuffle it.
 *
 * Matching is deliberately forgiving (case-, punctuation- and spacing-
 * insensitive, tolerant of small typos and recognizer noise) while an
 * "important word" that is missing or replaced always fails — so unrelated
 * input cannot slip through.
 */
object VerificationPhraseProvider {

    /** Short, calm affirmations (~3–7 words). Prayer-independent. */
    val phrases: List<String> = listOf(
        "I pray with intention",
        "Prayer brings me peace",
        "I choose prayer before distraction",
        "My time with Allah matters",
        "I return to what matters",
        "I pause for prayer",
        "My salah comes first",
        "I choose focus",
        "Prayer is my priority",
        "I remember my purpose",
    )

    /** Selects ONE phrase for a NEW verification session. */
    fun randomPhrase(): String = phrases.random()

    // ── Matching ─────────────────────────────────────────────────────────────

    // Typing is held to a stricter overall similarity than voice: a keyboard
    // makes exactness easy, whereas speech-to-text naturally drops/rewords
    // tokens. In both cases important-word coverage is the real safeguard.
    private const val TYPED_SIMILARITY = 0.85
    private const val SPOKEN_SIMILARITY = 0.70

    // Function words carry no meaning for verification; only content words are
    // required to be present. This keeps "My phone comes first" from matching
    // "My salah comes first".
    private val stopWords = setOf(
        "i", "me", "my", "we", "you", "he", "she", "it", "they",
        "a", "an", "the", "to", "of", "in", "on", "at", "for", "with",
        "and", "or", "is", "am", "are", "be", "that", "this", "what",
        "before", "after", "comes", "come", "brings", "bring",
    )

    /**
     * Validates typed input against the [expected] phrase.
     * Case-insensitive, ignores punctuation/spacing, tolerates minor typos.
     * Never uses raw equality.
     */
    fun matchesTyped(input: String, expected: String): Boolean =
        matches(input, expected, TYPED_SIMILARITY, "TYPED")

    /**
     * Validates spoken input against the [expected] phrase. Checks every
     * recognition [candidates] transcription and NEVER requires a perfect
     * transcription — natural accent/recognizer variation passes while
     * important-word coverage blocks false positives.
     */
    fun matchesSpoken(candidates: List<String>, expected: String): Boolean =
        candidates.any { it.isNotBlank() && matches(it, expected, SPOKEN_SIMILARITY, "SPOKEN") }

    private fun matches(input: String, expected: String, minSimilarity: Double, channel: String): Boolean {
        val a = normalize(input)
        val b = normalize(expected)
        val covered = a.isNotEmpty() && contentWordsCovered(a, b)
        val sim = if (a.isEmpty()) 0.0 else similarity(a, b)
        val pass = a.isNotEmpty() && (a == b || (covered && sim >= minSimilarity))
        // Debug-only: inspect the real decision (esp. live SpeechRecognizer
        // output). Logs the phrase/transcript text — not audio or PII — and
        // never runs in release builds.
        if (com.salahlock.app.BuildConfig.DEBUG) {
            android.util.Log.d(
                "VerifyMatch",
                "[$channel] expected=\"$b\" heard=\"$a\" sim=${"%.2f".format(sim)} " +
                        "min=$minSimilarity coverage=$covered -> ${if (pass) "PASS" else "FAIL"}",
            )
        }
        return pass
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace('’', '\'')                 // curly → straight apostrophe
            .replace("'", "")                        // collapse contractions
            .replace(Regex("[^a-z0-9\\s]"), " ")     // punctuation → space
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Every meaning-bearing word in [expected] must appear in [input] (typo-tolerant). */
    private fun contentWordsCovered(input: String, expected: String): Boolean {
        val inputWords = input.split(' ').filter { it.isNotEmpty() }
        val contentWords = expected.split(' ').filter { it.isNotEmpty() && it !in stopWords }
        if (contentWords.isEmpty()) return true
        return contentWords.all { target ->
            val tolerance = if (target.length <= 4) 1 else 2
            inputWords.any { levenshtein(it, target) <= tolerance }
        }
    }

    private fun similarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
