package com.salahlock.app.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BM-VERIFY-002 — one selected phrase repeated N times, shared forgiving
 * matcher for both typed and spoken input.
 */
class VerificationPhraseProviderTest {

    // ── Phrase source ────────────────────────────────────────────────────────

    @Test
    fun `phrases are short affirmations of 3 to 7 words`() {
        assertTrue(VerificationPhraseProvider.phrases.isNotEmpty())
        VerificationPhraseProvider.phrases.forEach { phrase ->
            val words = phrase.trim().split(Regex("\\s+")).size
            assertTrue("'$phrase' has $words words", words in 3..7)
        }
    }

    @Test
    fun `randomPhrase always returns a phrase from the shared list`() {
        repeat(50) {
            assertTrue(VerificationPhraseProvider.randomPhrase() in VerificationPhraseProvider.phrases)
        }
    }

    // A verification session selects ONE phrase and repeats it. That selection
    // is the caller's stable state; the provider only exposes randomPhrase().
    // Simulate a session here to document the contract.
    @Test
    fun `a session locks one phrase and reuses it for every attempt`() {
        val sessionPhrase = VerificationPhraseProvider.randomPhrase()
        val attempts = List(3) { sessionPhrase } // same reference every attempt
        assertTrue(attempts.all { it == sessionPhrase })
        assertEquals(1, attempts.toSet().size)
    }

    // ── Typed matching: case-insensitivity ───────────────────────────────────

    @Test
    fun `typed matching is completely case-insensitive`() {
        val expected = "I Pray With Intention"
        assertTrue(VerificationPhraseProvider.matchesTyped("i pray with intention", expected))
        assertTrue(VerificationPhraseProvider.matchesTyped("I PRAY WITH INTENTION", expected))
        assertTrue(VerificationPhraseProvider.matchesTyped("i PRAY with intention", expected))
    }

    @Test
    fun `typed matching ignores punctuation`() {
        assertTrue(VerificationPhraseProvider.matchesTyped("I pray with intention!!!", "I pray with intention"))
        assertTrue(VerificationPhraseProvider.matchesTyped("I Choose Prayer Before Distraction!!!", "I choose prayer before distraction"))
    }

    @Test
    fun `typed matching ignores repeated whitespace`() {
        assertTrue(VerificationPhraseProvider.matchesTyped("I    pray   with intention", "I pray with intention"))
        assertTrue(VerificationPhraseProvider.matchesTyped("  i   choose prayer before distraction ", "I choose prayer before distraction"))
    }

    @Test
    fun `typed matching tolerates minor typos`() {
        assertTrue(VerificationPhraseProvider.matchesTyped("I pray with intension", "I pray with intention"))
        assertTrue(VerificationPhraseProvider.matchesTyped("i choose prayer before distrction", "I choose prayer before distraction"))
    }

    @Test
    fun `typed unrelated input fails`() {
        assertFalse(VerificationPhraseProvider.matchesTyped("I want to use Instagram", "I pray with intention"))
        assertFalse(VerificationPhraseProvider.matchesTyped("open instagram now", "I choose prayer before distraction"))
    }

    @Test
    fun `typed empty input fails`() {
        assertFalse(VerificationPhraseProvider.matchesTyped("", "I pray with intention"))
        assertFalse(VerificationPhraseProvider.matchesTyped("   ", "I pray with intention"))
    }

    // ── Spoken matching: never requires a perfect transcription ───────────────

    @Test
    fun `spoken exact transcript passes`() {
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I choose prayer before distraction"), "I choose prayer before distraction"))
    }

    @Test
    fun `spoken natural recognizer variation passes`() {
        // plural / trailing-s noise
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I choose prayers before distractions"), "I choose prayer before distraction"))
        // dropped leading filler word
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("choose prayer before distraction"), "I choose prayer before distraction"))
        // inserted filler word
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("I choose prayer before the distraction"), "I choose prayer before distraction"))
        // homophone + dropped verb
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("prayer brings me piece"), "Prayer brings me peace"))
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("prayer brings peace"), "Prayer brings me peace"))
    }

    @Test
    fun `spoken matching is case-insensitive`() {
        assertTrue(VerificationPhraseProvider.matchesSpoken(listOf("MY SALAH COMES FIRST"), "My salah comes first"))
    }

    @Test
    fun `spoken checks every candidate`() {
        assertTrue(
            VerificationPhraseProvider.matchesSpoken(
                listOf("my sofa comes worst", "my salah comes first"),
                "My salah comes first",
            )
        )
    }

    @Test
    fun `spoken unrelated speech fails`() {
        assertFalse(VerificationPhraseProvider.matchesSpoken(listOf("open my applications"), "I choose prayer before distraction"))
        assertFalse(VerificationPhraseProvider.matchesSpoken(listOf("hello how are you"), "I pray with intention"))
    }

    @Test
    fun `spoken empty and blank candidates fail`() {
        assertFalse(VerificationPhraseProvider.matchesSpoken(emptyList(), "My salah comes first"))
        assertFalse(VerificationPhraseProvider.matchesSpoken(listOf("", "  "), "My salah comes first"))
    }

    // ── Important-word safeguards (both paths) ────────────────────────────────

    @Test
    fun `replacing an important word fails despite overlap`() {
        assertFalse(VerificationPhraseProvider.matchesSpoken(listOf("My phone comes first"), "My salah comes first"))
        assertFalse(VerificationPhraseProvider.matchesTyped("My phone comes first", "My salah comes first"))
    }

    @Test
    fun `dropping important words fails`() {
        assertFalse(VerificationPhraseProvider.matchesTyped("I choose distraction", "I choose prayer before distraction"))
        assertFalse(VerificationPhraseProvider.matchesSpoken(listOf("I choose distraction"), "I choose prayer before distraction"))
    }
}
