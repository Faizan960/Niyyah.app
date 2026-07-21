package com.salahlock.app.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BM-VERIFY-004 — one universal per-prayer phrase ("I prayed my {Prayer}"),
 * repeated N times. Acceptance anchor is the confirmation "I prayed": voice
 * needs only the verb (recognizers drop the leading "I"); typed needs "I" + verb.
 * The prayer name is always optional.
 */
class VerificationPhraseProviderTest {

    private val asr = "I prayed my Asr"
    private val dhuhr = "I prayed my Dhuhr"

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

    // ── Voice: "I prayed" is enough ──────────────────────────────────────────

    @Test
    fun `spoken full sentence passes`() {
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I prayed my Asr"), asr))
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I prayed my Dhuhr"), dhuhr))
    }

    @Test
    fun `spoken just I prayed passes regardless of prayer`() {
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I prayed"), asr))
        // Recognizer dropped the leading "I".
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("prayed"), asr))
        // Wrong / missing prayer name still passes — name is optional.
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I prayed my Fajr"), asr))
    }

    @Test
    fun `spoken near-homophones of prayed pass`() {
        // Common SpeechRecognizer variants of "prayed".
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
            VerificationPhraseProvider.matchesSpoken(
                listOf("I played guitar", "I prayed my Asr"), asr,
            )
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
            "I need my Asr coffee",     // has prayer-adjacent noise but no prayed verb
        ).forEach {
            assertFalse("should FAIL: \"$it\"", VerificationPhraseProvider.matchesSpoken(listOf(it), asr))
        }
    }

    @Test
    fun `spoken empty and blank candidates fail`() {
        assertFalse(VerificationPhraseProvider.matchesSpoken(emptyList(), asr))
        assertFalse(VerificationPhraseProvider.matchesSpoken(listOf("", "  "), asr))
    }

    // ── Typed: needs "I" + the verb ──────────────────────────────────────────

    @Test
    fun `typed full sentence passes`() {
        assertTrue(VerificationPhraseProvider.matchesTyped("I prayed my Asr", asr))
        assertTrue(VerificationPhraseProvider.matchesTyped("i prayed my asr", asr))
    }

    @Test
    fun `typed I prayed passes and tolerates a verb typo`() {
        assertTrue(VerificationPhraseProvider.matchesTyped("I prayed", asr))
        assertTrue(VerificationPhraseProvider.matchesTyped("I prayd my Asr", asr))
    }

    @Test
    fun `typed without the pronoun fails`() {
        // Typing is deliberate, so require the full "I prayed" confirmation.
        assertFalse(VerificationPhraseProvider.matchesTyped("prayed", asr))
        assertFalse(VerificationPhraseProvider.matchesTyped("my Asr", asr))
    }

    @Test
    fun `typed unrelated input fails`() {
        assertFalse(VerificationPhraseProvider.matchesTyped("I want to use Instagram", asr))
        assertFalse(VerificationPhraseProvider.matchesTyped("open instagram now", asr))
        assertFalse(VerificationPhraseProvider.matchesTyped("", asr))
        assertFalse(VerificationPhraseProvider.matchesTyped("   ", asr))
    }
}
