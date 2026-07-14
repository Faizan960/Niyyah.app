package com.salahlock.app.quran

import com.salahlock.app.data.repository.Ayah
import com.salahlock.app.data.repository.Surah
import com.salahlock.app.data.repository.filterSurahs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurahSearchTest {

    private fun surah(n: Int, ar: String, tr: String, en: String) = Surah(
        number = n,
        arabicName = ar,
        transliteration = tr,
        englishName = en,
        revelationType = "Meccan",
        ayahs = listOf(Ayah(1, "بِسْمِ اللَّهِ", "In the name of Allah", 1)),
    )

    private val surahs = listOf(
        surah(1, "سُورَةُ ٱلْفَاتِحَةِ", "Al-Faatiha", "The Opening"),
        surah(2, "سُورَةُ البَقَرَةِ", "Al-Baqara", "The Cow"),
        surah(36, "سُورَةُ يسٓ", "Yaseen", "Yaseen"),
        surah(112, "سُورَةُ الإِخْلَاصِ", "Al-Ikhlaas", "Sincerity"),
    )

    @Test
    fun `empty query returns everything`() {
        assertEquals(4, filterSurahs(surahs, "").size)
        assertEquals(4, filterSurahs(surahs, "   ").size)
    }

    @Test
    fun `matches english name case-insensitively`() {
        val result = filterSurahs(surahs, "the cow")
        assertEquals(listOf(2), result.map { it.number })
    }

    @Test
    fun `matches transliteration case-insensitively`() {
        val result = filterSurahs(surahs, "faatiha")
        assertEquals(listOf(1), result.map { it.number })
    }

    @Test
    fun `matches arabic name`() {
        val result = filterSurahs(surahs, "البَقَرَةِ")
        assertEquals(listOf(2), result.map { it.number })
    }

    @Test
    fun `matches exact surah number`() {
        val result = filterSurahs(surahs, "112")
        assertEquals(listOf(112), result.map { it.number })
    }

    @Test
    fun `partial match can hit several surahs`() {
        val result = filterSurahs(surahs, "al-")
        assertTrue(result.map { it.number }.containsAll(listOf(1, 2, 112)))
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(filterSurahs(surahs, "zzz").isEmpty())
    }
}
