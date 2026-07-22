package com.salahlock.app.quran

import com.salahlock.app.data.repository.QuranPageMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BM-QURAN-PAGES — proves the bundled 604-page mapping is canonical and complete,
 * independent of visual QA. Reads the REAL shipped asset
 * (app/src/main/assets/quran_pages.json) and cross-checks it against the canonical
 * 114 surah ayah-counts (Hafs), which are embedded here so the test does not depend
 * on quran.json parsing.
 */
class QuranPageMappingTest {

    // Canonical Hafs ayah counts per surah (1..114). Sums to 6236.
    private val ayahCounts = listOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111,
        110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83,
        182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96,
        29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31,
        50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5,
        8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
    )

    private fun loadAssetJson(): String {
        // Unit tests run with the module dir (app/) as the working directory.
        val f = File("src/main/assets/quran_pages.json")
        assertTrue("pages asset missing at ${f.absolutePath}", f.exists())
        return f.readText()
    }

    private fun mapping(): QuranPageMapping =
        QuranPageMapping(QuranPageMapping.parsePageStarts(loadAssetJson()), ayahCounts)

    @Test
    fun asset_has_exactly_604_page_starts() {
        val starts = QuranPageMapping.parsePageStarts(loadAssetJson())
        assertEquals(604, starts.size)
    }

    @Test
    fun mapping_reports_604_pages_and_6236_ayahs() {
        val m = mapping()
        assertEquals(604, m.pageCount)
        assertEquals(6236, m.totalAyahs)
        assertEquals(6236, ayahCounts.sum())
    }

    @Test
    fun page_starts_are_strictly_monotonic() {
        val m = mapping()
        var prev = -1
        for (p in 1..604) {
            val (s, a) = m.pageStart(p)
            val ord = m.ordinal(s, a)
            assertTrue("page $p ordinal not increasing (prev=$prev, ord=$ord)", ord > prev)
            prev = ord
        }
    }

    @Test
    fun known_canonical_boundaries() {
        val m = mapping()
        assertEquals(1 to 1, m.pageStart(1))     // Al-Fatiha
        assertEquals(2 to 1, m.pageStart(2))     // Al-Baqara
        assertEquals(2 to 6, m.pageStart(3))
        assertEquals(2 to 17, m.pageStart(4))
        assertEquals(109 to 1, m.pageStart(603)) // Al-Kafirun
        assertEquals(112 to 1, m.pageStart(604)) // Al-Ikhlas
    }

    @Test
    fun pageForAyah_resolves_known_ayahs() {
        val m = mapping()
        assertEquals(1, m.pageForAyah(1, 1))
        assertEquals(1, m.pageForAyah(1, 7))   // last ayah of Fatiha still page 1
        assertEquals(2, m.pageForAyah(2, 1))
        assertEquals(2, m.pageForAyah(2, 5))   // just before the page-3 boundary (2:6)
        assertEquals(3, m.pageForAyah(2, 6))   // page-3 boundary
        assertEquals(604, m.pageForAyah(114, 6)) // last ayah of the Quran
    }

    @Test
    fun every_ayah_maps_to_exactly_one_page_and_all_are_covered() {
        val m = mapping()
        // Walk pages in order; the concatenation of ayahRefsForPage(1..604) must equal
        // the full ordered ayah list with no gaps, no duplicates, exactly 6236.
        var expectedSurah = 1
        var expectedAyah = 1
        var total = 0
        for (p in 1..604) {
            for ((s, a) in m.ayahRefsForPage(p)) {
                assertEquals("gap/overlap at page $p", expectedSurah, s)
                assertEquals("gap/overlap at page $p", expectedAyah, a)
                // Round-trip: this ayah must resolve back to this page.
                assertEquals("pageForAyah($s:$a) != $p", p, m.pageForAyah(s, a))
                total++
                if (expectedAyah < ayahCounts[expectedSurah - 1]) {
                    expectedAyah++
                } else {
                    expectedSurah++
                    expectedAyah = 1
                }
            }
        }
        assertEquals(6236, total)
        assertEquals("did not consume every surah", 115, expectedSurah)
    }

    @Test
    fun pageEnd_is_consistent_with_next_page_start() {
        val m = mapping()
        for (p in 1..603) {
            val end = m.pageEnd(p)
            val nextStart = m.pageStart(p + 1)
            val endOrd = m.ordinal(end.first, end.second)
            val nextOrd = m.ordinal(nextStart.first, nextStart.second)
            assertEquals("page $p end not immediately before page ${p + 1} start", endOrd + 1, nextOrd)
        }
        assertEquals(114 to 6, m.pageEnd(604))
    }
}
