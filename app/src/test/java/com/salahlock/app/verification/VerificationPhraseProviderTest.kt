package com.salahlock.app.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typing verification must identify the SPECIFIC prayer being verified: the
 * confirmation "I <prayed>" PLUS that prayer's name (an explicit allowlist of
 * common spellings) as a complete token. Generic confirmations ("I pray",
 * "I prayed") and other prayers' names are rejected.
 *
 * Voice stays deliberately lenient (recognizers drop the "I" and mangle the
 * name); those tests are retained unchanged at the bottom.
 */
class VerificationPhraseProviderTest {

    private val asr = "I prayed my Asr"
    private val dhuhr = "I prayed my Dhuhr"

    private fun phrase(name: String) = VerificationPhraseProvider.phraseFor(name)
    private fun typedPass(input: String, forPrayer: String) =
        assertTrue(
            "typed \"$input\" should ACCEPT for $forPrayer",
            VerificationPhraseProvider.matchesTyped(input, phrase(forPrayer)),
        )
    private fun typedReject(input: String, forPrayer: String) =
        assertFalse(
            "typed \"$input\" should REJECT for $forPrayer",
            VerificationPhraseProvider.matchesTyped(input, phrase(forPrayer)),
        )

    // ── Phrase source ────────────────────────────────────────────────────────

    @Test
    fun `phraseFor builds I prayed my prayer`() {
        assertEquals("I prayed my Asr", VerificationPhraseProvider.phraseFor("Asr"))
        assertEquals("I prayed my Dhuhr", VerificationPhraseProvider.phraseFor("Dhuhr"))
    }

    @Test
    fun `defaultPhrase is a valid I prayed sentence`() {
        assertTrue(VerificationPhraseProvider.defaultPhrase().startsWith("I prayed"))
    }

    // ── Typed: the Maghrib acceptance table (spec) ───────────────────────────

    @Test
    fun `typed Maghrib acceptance table`() {
        typedReject("I pray", "Maghrib")
        typedReject("I prayed", "Maghrib")
        typedPass("I prayed Maghrib", "Maghrib")
        typedPass("I pray Maghrib", "Maghrib")
        typedPass("I prays Maghrib", "Maghrib")
        typedPass("I prayed Magrib", "Maghrib")
        typedPass("I prayed Maghrib salah", "Maghrib")
        typedReject("I prayed Isha", "Maghrib")
    }

    // ── Typed: every prayer accepts its own name + variants ──────────────────

    @Test
    fun `typed accepts each prayer with its own name`() {
        typedPass("I prayed Fajr", "Fajr")
        typedPass("I prayed my Fajr", "Fajr")
        typedPass("I prayed Dhuhr", "Dhuhr")
        typedPass("I prayed my Dhuhr", "Dhuhr")
        typedPass("I prayed Asr", "Asr")
        typedPass("I prayed my Asr", "Asr")
        typedPass("I prayed Maghrib", "Maghrib")
        typedPass("I prayed Isha", "Isha")
        typedPass("I prayed my Isha", "Isha")
    }

    @Test
    fun `typed accepts common spelling variants per prayer`() {
        typedPass("I prayed Fajar", "Fajr")
        typedPass("I prayed Fajer", "Fajr")
        typedPass("I prayed Zuhr", "Dhuhr")
        typedPass("I prayed Duhr", "Dhuhr")
        typedPass("I prayed Zohr", "Dhuhr")
        typedPass("I prayed Asar", "Asr")
        typedPass("I prayed Magrib", "Maghrib")
        typedPass("I prayed Maghreb", "Maghrib")
        typedPass("I prayed Ishaa", "Isha")
        typedPass("I prayed Esha", "Isha")
    }

    @Test
    fun `typed accepts verb typos and grammatical variants with the name`() {
        typedPass("I pray Fajr", "Fajr")
        typedPass("I prays Asr", "Asr")
        typedPass("I prayd my Maghrib", "Maghrib")      // verb typo
        typedPass("I prayed my Dhuhr prayer", "Dhuhr")  // trailing noise
    }

    // ── Typed: generic confirmations are rejected (no prayer named) ──────────

    @Test
    fun `typed rejects generic confirmations for every prayer`() {
        for (p in listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")) {
            typedReject("I pray", p)
            typedReject("I prayed", p)
            typedReject("I prayed my", p)
            typedReject("I pray my", p)
            typedReject("I have prayed", p)
        }
    }

    // ── Typed: the WRONG prayer name is rejected ─────────────────────────────

    @Test
    fun `typed rejects a different prayer name`() {
        typedReject("I prayed Isha", "Maghrib")
        typedReject("I prayed Fajr", "Maghrib")
        typedReject("I prayed Asr", "Dhuhr")
        typedReject("I prayed Maghrib", "Fajr")
        typedReject("I prayed Dhuhr", "Isha")
        typedReject("I prayed Fajr", "Asr")
    }

    // ── Typed: similar / partial names must NOT match (complete-token rule) ───

    @Test
    fun `typed rejects partial or glued prayer names`() {
        typedReject("I prayed Magh", "Maghrib")        // partial
        typedReject("I prayed Maghribi", "Maghrib")    // extra suffix, not an exact token
        typedReject("I prayed premaghrib", "Maghrib")  // name as a substring inside a word
        typedReject("I prayed Aisha", "Isha")          // similar but a different word
        typedReject("I prayed Duh", "Dhuhr")           // partial
    }

    // ── Typed: case, whitespace, punctuation are normalised ──────────────────

    @Test
    fun `typed is case-insensitive`() {
        typedPass("i prayed maghrib", "Maghrib")
        typedPass("I PRAYED MAGHRIB", "Maghrib")
        typedPass("I Prayed Maghrib", "Maghrib")
    }

    @Test
    fun `typed normalises whitespace`() {
        typedPass("   I   prayed    Maghrib   ", "Maghrib")
        typedPass("\tI prayed\nMaghrib", "Maghrib")
    }

    @Test
    fun `typed ignores harmless punctuation`() {
        typedPass("I prayed, Maghrib!", "Maghrib")
        typedPass("I prayed my Maghrib.", "Maghrib")
        typedPass("I prayed - Maghrib", "Maghrib")
    }

    // ── Typed: pronoun requirement (deliberate typing) ───────────────────────

    @Test
    fun `typed without the pronoun fails`() {
        typedReject("prayed Maghrib", "Maghrib")
        typedReject("Maghrib", "Maghrib")
        typedReject("my Maghrib", "Maghrib")
    }

    // ── Typed: empty / malformed input ───────────────────────────────────────

    @Test
    fun `typed empty and malformed input fails`() {
        typedReject("", "Maghrib")
        typedReject("   ", "Maghrib")
        typedReject("!!!", "Maghrib")
        typedReject("\n\t  \n", "Maghrib")
        typedReject("1234567890", "Maghrib")
    }

    @Test
    fun `typed unrelated input fails`() {
        typedReject("I want to use Instagram", "Maghrib")
        typedReject("open instagram now", "Maghrib")
        typedReject("let me use my phone", "Maghrib")
    }

    // ── Typed: neutral debug phrase relaxes the name requirement ─────────────

    @Test
    fun `typed against the neutral default phrase accepts a generic confirmation`() {
        val neutral = VerificationPhraseProvider.defaultPhrase() // "I prayed my prayer"
        assertTrue(VerificationPhraseProvider.matchesTyped("I prayed", neutral))
        assertTrue(VerificationPhraseProvider.matchesTyped("I pray", neutral))
        assertFalse(VerificationPhraseProvider.matchesTyped("open instagram", neutral))
    }

    // ── Voice: deliberately lenient (behaviour unchanged) ────────────────────

    @Test
    fun `spoken full sentence passes`() {
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I prayed my Asr"), asr))
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I prayed my Dhuhr"), dhuhr))
    }

    @Test
    fun `spoken just I prayed passes regardless of prayer`() {
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I prayed"), asr))
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("prayed"), asr))
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I prayed my Fajr"), asr))
    }

    @Test
    fun `spoken near-homophones of prayed pass`() {
        listOf("I prayer my Asr", "I pray my Asr", "I prays Asr", "I played my Asr").forEach {
            assertTrue("should PASS: \"$it\"", VerificationPhraseProvider.matchesSpoken(listOf(it), asr))
        }
    }

    @Test
    fun `spoken is case-insensitive and ignores punctuation`() {
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I PRAYED MY ASR"), asr))
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I prayed, my Asr!"), asr))
    }

    @Test
    fun `spoken checks every candidate and passes on the best`() {
        assertTrue(
            VerificationPhraseProvider.matchesSpoken(listOf("I played guitar", "I prayed my Asr"), asr),
        )
    }

    @Test
    fun `spoken unrelated speech fails`() {
        listOf(
            "Open Instagram",
            "I want to use Instagram",
            "Let me use my phone",
            "My phone comes first",
            "Play YouTube",
            "Hello how are you",
            "I need my Asr coffee",
        ).forEach {
            assertFalse("should FAIL: \"$it\"", VerificationPhraseProvider.matchesSpoken(listOf(it), asr))
        }
    }

    @Test
    fun `spoken empty and blank candidates fail`() {
        assertFalse(VerificationPhraseProvider.matchesSpoken(emptyList(), asr))
        assertFalse(VerificationPhraseProvider.matchesSpoken(listOf("", "  "), asr))
    }
}
