package com.salahlock.app.sync

import com.salahlock.app.data.sync.identity.AzkarRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BM-013 Checkpoint B — proves [AzkarRef] is deterministic across reseeding /
 * recreation and never leaks display-text or autoincrement-id dependence.
 */
class AzkarRefTest {

    /** The canonical category sequence of the bundled `azkar.json` (array order). */
    private val canonicalCategories = listOf(
        "Morning", "Morning", "Morning", "Morning", "Morning", "Morning", "Morning", "Morning",
        "Evening", "Evening", "Evening", "Evening", "Evening",
        "After Prayer", "After Prayer", "After Prayer", "After Prayer", "After Prayer", "After Prayer", "After Prayer",
        "Sleep", "Sleep", "Sleep", "Sleep", "Sleep",
        "Travel", "Travel", "Travel",
        "Food", "Food", "Food",
        "Anxiety", "Anxiety", "Anxiety", "Anxiety",
        "Waking Up", "Waking Up",
        "Entering Mosque", "Entering Mosque",
        "Leaving Mosque",
        "Before Wudu",
        "After Wudu", "After Wudu",
    )

    @Test
    fun `assign is stable across identical reseeds`() {
        val first = AzkarRef.assign(canonicalCategories)
        val second = AzkarRef.assign(canonicalCategories)
        assertEquals("same canonical order must yield identical refs", first, second)
    }

    @Test
    fun `index is per-category, zero-based, in canonical order`() {
        val refs = AzkarRef.assign(canonicalCategories)
        assertEquals("azkar:v1:morning:0", refs[0])
        assertEquals("azkar:v1:morning:7", refs[7])   // 8th Morning entry
        assertEquals("azkar:v1:evening:0", refs[8])    // first Evening entry
        assertEquals("azkar:v1:after_prayer:0", refs[13])
        assertEquals("azkar:v1:leaving_mosque:0", refs[39])
        assertEquals("azkar:v1:before_wudu:0", refs[40])
    }

    @Test
    fun `every ref is unique for the canonical corpus`() {
        val refs = AzkarRef.assign(canonicalCategories)
        assertEquals(canonicalCategories.size, refs.size)
        assertEquals("no collisions", refs.size, refs.toSet().size)
    }

    @Test
    fun `ref does not depend on autoincrement id or list identity`() {
        // Same logical entry (3rd Morning) computed directly must match the assigned one.
        val refs = AzkarRef.assign(canonicalCategories)
        assertEquals(AzkarRef.of("Morning", 2), refs[2])
    }

    @Test
    fun `category normalization is whitespace and case insensitive`() {
        assertEquals(AzkarRef.of("After Prayer", 0), AzkarRef.of("  after   prayer ", 0))
        assertEquals("azkar:v1:after_prayer:0", AzkarRef.of("After Prayer", 0))
    }

    @Test
    fun `different category or index yields different ref`() {
        assertNotEquals(AzkarRef.of("Morning", 0), AzkarRef.of("Evening", 0))
        assertNotEquals(AzkarRef.of("Morning", 0), AzkarRef.of("Morning", 1))
    }

    @Test
    fun `ref is versioned`() {
        assertTrue(AzkarRef.of("Morning", 0).startsWith("azkar:${AzkarRef.VERSION}:"))
    }
}
