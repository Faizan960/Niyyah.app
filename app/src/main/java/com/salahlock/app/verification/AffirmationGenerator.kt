package com.salahlock.app.verification

import com.salahlock.app.data.model.PrayerName

object AffirmationGenerator {

    private val affirmationsByPrayer: Map<PrayerName, List<String>> = mapOf(
        PrayerName.FAJR to listOf(
            "I have completed Fajr prayer",
            "I prayed Fajr today",
            "I have fulfilled my Fajr salah",
            "Fajr prayer is complete",
            "I answered the call to Fajr",
        ),
        PrayerName.DHUHR to listOf(
            "I have completed Dhuhr prayer",
            "I prayed Dhuhr today",
            "I have fulfilled my Dhuhr salah",
            "Dhuhr prayer is complete",
            "I answered the call to Dhuhr",
        ),
        PrayerName.ASR to listOf(
            "I have completed Asr prayer",
            "I prayed Asr today",
            "I have fulfilled my Asr salah",
            "Asr prayer is complete",
            "I answered the call to Asr",
        ),
        PrayerName.MAGHRIB to listOf(
            "I have completed Maghrib prayer",
            "I prayed Maghrib today",
            "I have fulfilled my Maghrib salah",
            "Maghrib prayer is complete",
            "I answered the call to Maghrib",
        ),
        PrayerName.ISHA to listOf(
            "I have completed Isha prayer",
            "I prayed Isha today",
            "I have fulfilled my Isha salah",
            "Isha prayer is complete",
            "I answered the call to Isha",
        ),
        PrayerName.SUNRISE to listOf(
            "I have completed my prayer",
            "I have fulfilled my salah",
            "My prayer is complete",
        ),
    )

    /**
     * Returns [count] unique affirmations for the given prayer, shuffled.
     * Affirmations within a single verification session never repeat.
     */
    fun generate(prayer: PrayerName, count: Int): List<String> {
        val pool = affirmationsByPrayer[prayer] ?: affirmationsByPrayer[PrayerName.FAJR]!!
        return pool.shuffled().take(count.coerceAtMost(pool.size))
    }

    /**
     * Validates typed or spoken input against the expected affirmation.
     * Case-insensitive, trims whitespace, collapses internal spaces,
     * and ignores trailing punctuation — so "I have completed Fajr prayer."
     * equals "i have completed fajr prayer".
     */
    fun validate(input: String, expected: String): Boolean {
        val normalize = { s: String ->
            s.lowercase()
                .trim()
                .replace(Regex("\\s+"), " ")
                .trimEnd('.', ',', '!', '?', '،', '؟')
        }
        return normalize(input) == normalize(expected)
    }

    // Speech recognizers rarely transcribe transliterated prayer names
    // verbatim ("Asr" → "answer", "Fajr" → "father"). These aliases map
    // common mis-transcriptions back to the canonical word before comparing.
    // Multi-word aliases are replaced on the whole sentence, so they are
    // listed longest-first within each pattern.
    private val spokenAliases: List<Pair<Regex, String>> = listOf(
        "fajar|fajir|fajor|fudger|fudge|father|budge" to "fajr",
        "the her|do her|duhr|dhur|dohr|zuhr|zohr|zuhur|zohar|zuher|juhr|dhuhar|door" to "dhuhr",
        "us are|asar|asir|aser|usur|answer|acer|asra|us" to "asr",
        "mag rib|magrib|mughrib|maghreb|magreb|mcgrib" to "maghrib",
        "e sha|esha|aisha|isya|easha|isa" to "isha",
        "salat|salaat|swalah|solah|sala" to "salah",
    ).map { (pattern, canon) -> Regex("\\b($pattern)\\b") to canon }

    /**
     * Validates spoken input against the expected affirmation.
     *
     * More forgiving than [validate]: checks every recognition candidate,
     * canonicalizes common mis-transcriptions of prayer names, and allows
     * small per-word and whole-phrase edit distances so natural recognizer
     * noise ("I prayed answer today") still passes while unrelated speech
     * does not.
     */
    fun validateSpoken(candidates: List<String>, expected: String): Boolean {
        val target = canonicalize(expected)
        return candidates.any { it.isNotBlank() && spokenMatches(canonicalize(it), target) }
    }

    private fun canonicalize(s: String): String {
        var out = s.lowercase()
            .replace(Regex("[.,!?،؟'’\"-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        for ((regex, canon) in spokenAliases) out = out.replace(regex, canon)
        return out
    }

    private fun spokenMatches(heard: String, target: String): Boolean {
        if (heard == target) return true

        // Word-by-word: each word may differ by a small edit distance
        val heardWords = heard.split(' ')
        val targetWords = target.split(' ')
        if (heardWords.size == targetWords.size) {
            val allClose = heardWords.zip(targetWords).all { (h, t) ->
                levenshtein(h, t) <= if (t.length <= 4) 1 else 2
            }
            if (allClose) return true
        }

        // Whole-phrase fallback: tolerates a dropped/inserted filler word
        return levenshtein(heard, target) <= maxOf(2, target.length / 5)
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
