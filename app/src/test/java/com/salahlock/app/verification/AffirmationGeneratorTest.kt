package com.salahlock.app.verification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffirmationGeneratorTest {

    // ── validate (typed input) ──────────────────────────────────────────────

    @Test
    fun `typed exact match passes`() {
        assertTrue(AffirmationGenerator.validate("I prayed Asr today", "I prayed Asr today"))
    }

    @Test
    fun `typed match ignores case punctuation and spacing`() {
        assertTrue(AffirmationGenerator.validate("  i prayed  asr today. ", "I prayed Asr today"))
    }

    @Test
    fun `typed near-miss fails`() {
        assertFalse(AffirmationGenerator.validate("I prayed answer today", "I prayed Asr today"))
    }

    // ── validateSpoken (voice input) ────────────────────────────────────────

    @Test
    fun `spoken exact transcript passes`() {
        assertTrue(AffirmationGenerator.validateSpoken(listOf("I prayed Asr today"), "I prayed Asr today"))
    }

    @Test
    fun `common prayer-name mis-transcriptions pass`() {
        assertTrue(AffirmationGenerator.validateSpoken(listOf("I prayed answer today"), "I prayed Asr today"))
        assertTrue(AffirmationGenerator.validateSpoken(listOf("I prayed us today"), "I prayed Asr today"))
        assertTrue(AffirmationGenerator.validateSpoken(listOf("I prayed father today"), "I prayed Fajr today"))
        assertTrue(AffirmationGenerator.validateSpoken(listOf("I prayed zuhr today"), "I prayed Dhuhr today"))
        assertTrue(AffirmationGenerator.validateSpoken(listOf("I prayed the her today"), "I prayed Dhuhr today"))
        assertTrue(AffirmationGenerator.validateSpoken(listOf("I prayed magrib today"), "I prayed Maghrib today"))
        assertTrue(AffirmationGenerator.validateSpoken(listOf("I prayed Aisha today"), "I prayed Isha today"))
    }

    @Test
    fun `salah variants pass`() {
        assertTrue(
            AffirmationGenerator.validateSpoken(
                listOf("I have fulfilled my Asr salat"),
                "I have fulfilled my Asr salah",
            )
        )
    }

    @Test
    fun `small per-word recognizer noise passes`() {
        assertTrue(
            AffirmationGenerator.validateSpoken(
                listOf("I have completed fajar prayers"),
                "I have completed Fajr prayer",
            )
        )
    }

    @Test
    fun `match found in a later recognition candidate passes`() {
        assertTrue(
            AffirmationGenerator.validateSpoken(
                listOf("I braved answer to say", "I prayed Asr today"),
                "I prayed Asr today",
            )
        )
    }

    @Test
    fun `punctuation and casing in transcript are ignored`() {
        assertTrue(AffirmationGenerator.validateSpoken(listOf("i prayed asr, today."), "I prayed Asr today"))
    }

    @Test
    fun `unrelated speech fails`() {
        assertFalse(AffirmationGenerator.validateSpoken(listOf("hello how are you"), "I prayed Asr today"))
        assertFalse(AffirmationGenerator.validateSpoken(listOf("open instagram now"), "I prayed Asr today"))
    }

    @Test
    fun `wrong prayer name fails`() {
        assertFalse(AffirmationGenerator.validateSpoken(listOf("I prayed Fajr today"), "I prayed Isha today"))
    }

    @Test
    fun `empty and blank candidates fail`() {
        assertFalse(AffirmationGenerator.validateSpoken(emptyList(), "I prayed Asr today"))
        assertFalse(AffirmationGenerator.validateSpoken(listOf("", "  "), "I prayed Asr today"))
    }
}
