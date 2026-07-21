package com.salahlock.app.verification

/**
 * Single source of truth for the verification phrase AND the matching strategy
 * used by both typed and spoken verification.
 *
 * BM-VERIFY-004 — the phrase is now ONE universal, easy-to-pronounce sentence,
 * personalised per prayer:
 *
 *     "I prayed my {Prayer}"      e.g. "I prayed my Asr", "I prayed my Dhuhr"
 *
 * Every user can say it, in any accent. Verification is deliberately
 * low-friction: the acceptance anchor is simply the confirmation **"I prayed"**.
 * The prayer name and the word "my" are optional and NEVER required to match —
 * so natural recognizer variation (dropped words, accents, near-homophones like
 * "prayer" / "pray" / "played") still passes. This is an intentionality gate
 * before unlocking distracting apps, not voice authentication.
 *
 * A verification SESSION shows this ONE phrase and the user repeats it N times.
 */
object VerificationPhraseProvider {

    /** The universal phrase for a given prayer, e.g. "I prayed my Asr". */
    fun phraseFor(prayerDisplayName: String): String = "I prayed my $prayerDisplayName"

    /** Neutral phrase for contexts without a specific prayer (e.g. debug QA). */
    fun defaultPhrase(): String = phraseFor("prayer")

    // The confirmation verb. SpeechRecognizer commonly returns near variants
    // (prayer, pray, prays, played); an edit distance ≤ 2 to "prayed" accepts
    // all of them, while genuinely unrelated words stay out ("play" is
    // distance 3, "played" is distance 1).
    private const val PRAYED = "prayed"
    private const val PRAYED_TOLERANCE = 2

    /**
     * Validates typed input. Deliberate typing must contain the full
     * confirmation "I prayed" — both the pronoun and the verb (verb is
     * typo-tolerant). The prayer name is optional.
     */
    fun matchesTyped(input: String, expected: String): Boolean =
        matchesOne(input, expected, requirePronoun = true, channel = "TYPED")

    /**
     * Validates spoken input against EVERY recognizer [candidates] transcription;
     * passes if ANY candidate contains the confirmation verb. Recognizers
     * routinely drop the leading "I", so voice does not require the pronoun —
     * saying "I prayed" (or just "prayed") is enough.
     */
    fun matchesSpoken(candidates: List<String>, expected: String): Boolean {
        var pass = false
        candidates.forEach { candidate ->
            if (candidate.isNotBlank() &&
                matchesOne(candidate, expected, requirePronoun = false, channel = "SPOKEN")
            ) {
                pass = true
            }
        }
        return pass
    }

    private fun matchesOne(input: String, expected: String, requirePronoun: Boolean, channel: String): Boolean {
        val tokens = normalize(input).split(' ').filter { it.isNotEmpty() }
        val hasPrayed = tokens.any { levenshtein(it, PRAYED) <= PRAYED_TOLERANCE }
        val hasPronoun = tokens.any { it == "i" }
        val pass = hasPrayed && (!requirePronoun || hasPronoun)
        // Debug-only: inspect the real decision (esp. live SpeechRecognizer
        // output). Logs phrase/transcript text — never audio or PII — and is
        // compiled out of release builds.
        if (com.salahlock.app.BuildConfig.DEBUG) {
            android.util.Log.d(
                "VerifyMatch",
                "[$channel] expected=\"${normalize(expected)}\" heard=\"${normalize(input)}\" " +
                    "hasPrayed=$hasPrayed hasI=$hasPronoun requireI=$requirePronoun -> ${if (pass) "PASS" else "FAIL"}",
            )
        }
        return pass
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace('’', '\'')                      // curly → straight apostrophe
            .replace("'", "")                        // collapse contractions
            .replace(Regex("[^a-z0-9\\s]"), " ")     // punctuation → space
            .replace(Regex("\\s+"), " ")
            .trim()

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
