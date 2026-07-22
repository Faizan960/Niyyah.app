package com.salahlock.app.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * BM-QURAN-PAGES — canonical Madani-Mushaf (604-page) mapping.
 *
 * Pure Kotlin, no Android/Room dependencies (fully unit-testable). Page boundaries
 * come from the bundled `assets/quran_pages.json` (provenance: Tanzil quran-data.xml,
 * the standard King Fahd Complex Hafs 604-page layout) — NOT computed by dividing
 * 6236 ayahs. Page number is navigation metadata only; the canonical ayah identity
 * remains (surahNumber, ayahNumber).
 *
 * Construction needs the 604 page-starts (from the asset) and the 114 surah ayah
 * counts (derived at runtime from the SAME quran.json corpus), so there is one
 * Quran data source.
 */
class QuranPageMapping(
    /** page-1 index → [surahNumber, ayahNumber] that STARTS that page. */
    private val pageStarts: List<IntArray>,
    /** surah-1 index → ayah count (114 entries). */
    surahAyahCounts: List<Int>,
) {
    val pageCount: Int = pageStarts.size

    // cumBefore[s] = number of ayahs before surah s (1-based s); cumBefore[surahs+1] = total.
    private val surahCount = surahAyahCounts.size
    private val cumBefore = IntArray(surahCount + 2)
    private val startOrdinals = IntArray(pageCount)

    init {
        require(pageCount == 604) { "expected 604 pages, got $pageCount" }
        require(surahCount == 114) { "expected 114 surahs, got $surahCount" }
        var run = 0
        for (s in 1..surahCount) {
            cumBefore[s] = run
            run += surahAyahCounts[s - 1]
        }
        cumBefore[surahCount + 1] = run
        for (i in 0 until pageCount) {
            val e = pageStarts[i]
            startOrdinals[i] = ordinal(e[0], e[1])
        }
    }

    /** Total ayahs across the corpus (6236 for the standard Hafs text). */
    val totalAyahs: Int get() = cumBefore[surahCount + 1]

    /** 0-based global ayah ordinal for [surah]:[ayah]. */
    fun ordinal(surah: Int, ayah: Int): Int = cumBefore[surah] + (ayah - 1)

    /** (surahNumber, ayahNumber) that starts [page] (1..604). */
    fun pageStart(page: Int): Pair<Int, Int> {
        val e = pageStarts[page - 1]
        return e[0] to e[1]
    }

    /** (surahNumber, ayahNumber) of the last ayah on [page]. */
    fun pageEnd(page: Int): Pair<Int, Int> {
        val endOrdinal = if (page < pageCount) startOrdinals[page] - 1 else totalAyahs - 1
        return ayahAtOrdinal(endOrdinal)
    }

    /** The page (1..604) that contains ayah [surah]:[ayah]. Deterministic. */
    fun pageForAyah(surah: Int, ayah: Int): Int {
        val ord = ordinal(surah, ayah)
        // Largest page whose start ordinal is <= ord (binary search on the sorted starts).
        var lo = 0
        var hi = pageCount - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (startOrdinals[mid] <= ord) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans + 1
    }

    /** Ordered (surahNumber, ayahNumber) references contained on [page]. */
    fun ayahRefsForPage(page: Int): List<Pair<Int, Int>> {
        val startOrd = startOrdinals[page - 1]
        val endOrdExcl = if (page < pageCount) startOrdinals[page] else totalAyahs
        return (startOrd until endOrdExcl).map { ayahAtOrdinal(it) }
    }

    private fun ayahAtOrdinal(ord: Int): Pair<Int, Int> {
        var lo = 1
        var hi = surahCount
        var s = 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cumBefore[mid] <= ord) { s = mid; lo = mid + 1 } else hi = mid - 1
        }
        return s to (ord - cumBefore[s] + 1)
    }

    companion object {
        /** Parses the bundled pages asset into 604 page-start pairs. */
        fun parsePageStarts(assetJson: String): List<IntArray> =
            Json { ignoreUnknownKeys = true }
                .decodeFromString<PagesAsset>(assetJson)
                .pageStarts
                .map { intArrayOf(it[0], it[1]) }
    }
}

@Serializable
private data class PagesAsset(val pageStarts: List<List<Int>>)
