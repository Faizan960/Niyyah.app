package com.salahlock.app.verification

/**
 * Single source of truth for the verification phrase AND the matching strategy
 * used by both typed and spoken verification.
 *
 * The session phrase is one universal, easy sentence personalised per prayer:
 *
 *     "I prayed my {Prayer}"      e.g. "I prayed my Maghrib", "I prayed my Asr"
 *
 * TYPED verification (deliberate, on a keyboard) must identify the SPECIFIC
 * prayer being verified: the confirmation "I <prayed>" PLUS that prayer's name
 * (an explicit allowlist of common spellings) as a complete token. Generic
 * confirmations ("I pray", "I prayed") and other prayers' names are rejected.
 *
 * SPOKEN verification stays deliberately lenient (accent / recognizer variation):
 * any transcription carrying the confirmation verb passes; the prayer name is
 * optional. This asymmetry is intentional — see [matchesTyped] vs [matchesSpoken].
 * Voice is an intentionality gate before unlocking distracting apps, not voice
 * authentication.
 */
object VerificationPhraseProvider {

    /** The universal phrase for a given prayer, e.g. "I prayed my Maghrib". */
    fun phraseFor(prayerDisplayName: String): String = "I prayed my $prayerDisplayName"

    /** Neutral phrase for contexts without a specific prayer (e.g. debug QA). */
    fun defaultPhrase(): String = phraseFor("prayer")

    // The confirmation verb. SpeechRecognizer and typing commonly produce near
    // variants (prayer, pray, prays, played, prayd); an edit distance <= 2 to
    // "prayed" accepts all of them while genuinely unrelated words stay out
    // ("play" is distance 3, "played" is distance 1).
    private const val PRAYED = "prayed"
    private const val PRAYED_TOLERANCE = 2

    // Accepted spelling / romanisation variants per prayer, keyed by the canonical
    // lowercase display name (see data.model.PrayerName.displayName). This is a
    // deliberate EXPLICIT allowlist — never a fuzzy or substring match — so one
    // prayer's input can never satisfy a DIFFERENT prayer's gate. Add new accepted
    // spellings here; keep the sets disjoint across prayers.
    private val PRAYER_NAME_VARIANTS: Map<String, Set<String>> = mapOf(
        "fajr" to setOf("fajr", "fajar", "fajer"),
        "dhuhr" to setOf("dhuhr", "dhuhur", "duhr", "duhur", "zuhr", "zuhur", "zohr", "dhohr"),
        "asr" to setOf("asr", "asar", "aser"),
        "maghrib" to setOf("maghrib", "magrib", "maghreb", "magreb"),
        "isha" to setOf("isha", "ishaa", "esha", "eshaa", "ishah"),
    )

    /**
     * Validates TYPED input against the [expected] phrase. Requires, as whole
     * tokens: the pronoun "I", the confirmation verb (typo-tolerant), AND the
     * expected prayer's name (or an accepted spelling of it). The expected prayer
     * is derived from [expected] — never hardcoded. If [expected] names no known
     * prayer (e.g. the neutral debug phrase) the name requirement is relaxed.
     */
    fun matchesTyped(input: String, expected: String): Boolean {
        val tokens = tokensOf(input)
        val hasPronoun = tokens.any { it == "i" }
        val hasPrayed = hasPrayedVerb(tokens)
        val variants = expectedVariants(expected)
        val hasPrayerName = variants == null || tokens.any { it in variants }
        val pass = hasPronoun && hasPrayed && hasPrayerName
        debugLog("TYPED", expected, input, "hasI=$hasPronoun hasPrayed=$hasPrayed hasName=$hasPrayerName", pass)
        return pass
    }

    /**
     * Validates SPOKEN input against EVERY recognizer [candidates] transcription;
     * passes if ANY candidate carries the confirmation verb. Recognizers routinely
     * drop the leading "I" and mangle the prayer name, so voice requires NEITHER
     * the pronoun nor the name — saying "I prayed" (or just "prayed") is enough.
     */
    fun matchesSpoken(candidates: List<String>, expected: String): Boolean {
        var pass = false
        candidates.forEach { candidate ->
            val ok = candidate.isNotBlank() && hasPrayedVerb(tokensOf(candidate))
            if (ok) pass = true
            debugLog("SPOKEN", expected, candidate, "hasPrayed=$ok", pass)
        }
        return pass
    }

    /**
     * Resolves the accepted spellings for the prayer named in [expected], or null
     * when [expected] names no known prayer. Scans every token (not just the last
     * word) so it does not depend on the exact phrase layout.
     */
    private fun expectedVariants(expected: String): Set<String>? {
        val tokens = tokensOf(expected)
        for (variants in PRAYER_NAME_VARIANTS.values) {
            if (tokens.any { it in variants }) return variants
        }
        return null
    }

    private fun hasPrayedVerb(tokens: List<String>): Boolean =
        tokens.any { levenshtein(it, PRAYED) <= PRAYED_TOLERANCE }

    private fun tokensOf(s: String): List<String> =
        normalize(s).split(' ').filter { it.isNotEmpty() }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace('’', '\'')                 // curly -> straight apostrophe
            .replace("'", "")                        // collapse contractions
            .replace(Regex("[^a-z0-9\\s]"), " ")     // punctuation -> space
            .replace(Regex("\\s+"), " ")
            .trim()

    // Debug-only: inspect the real decision (esp. live SpeechRecognizer output).
    // Logs phrase / transcript text — never audio or PII — and is compiled out of
    // release builds.
    private fun debugLog(channel: String, expected: String, heard: String, detail: String, pass: Boolean) {
        if (com.salahlock.app.BuildConfig.DEBUG) {
            android.util.Log.d(
                "VerifyMatch",
                "[$channel] expected=\"${normalize(expected)}\" heard=\"${normalize(heard)}\" " +
                    "$detail -> ${if (pass) "PASS" else "FAIL"}",
            )
        }
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
