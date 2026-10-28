package com.salahlock.app.data.backup

import com.salahlock.app.data.repository.StoredMonthlyReflection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * chore/post-merge-verification-ci — pins backward compatibility of restore paths
 * after the weekly free-pass ("mercy") removal:
 *
 *  1. A backup produced by an older app version still contains the removed
 *     `lastMercyWeek` / `mercyUsedThisWeek` streak fields. Restore must ignore
 *     them (`Json.ignoreUnknownKeys`) and keep every surviving value.
 *  2. Frozen monthly reflections persist their serialized `mercyUsed` field
 *     (emergency-override count). Old stored reports must still parse and map
 *     through `toReport()` unchanged.
 */
class LegacyBackupCompatibilityTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `backup with removed mercy streak fields restores surviving values`() {
        val legacy = """
            {
              "version": 1,
              "streak": {
                "currentStreak": 10,
                "bestStreak": 30,
                "lastFullDay": "2026-10-27",
                "lastMercyWeek": "2026-W40",
                "mercyUsedThisWeek": true
              }
            }
        """.trimIndent()

        val pkg = json.decodeFromString<BackupPackage>(legacy)

        val streak = pkg.streak
        // The three surviving values round-trip; the removed mercy keys were
        // silently ignored (the BackupStreak model no longer even declares them).
        assertEquals(10, streak?.currentStreak)
        assertEquals(30, streak?.bestStreak)
        assertEquals("2026-10-27", streak?.lastFullDay)
    }

    @Test
    fun `stored reflection with legacy mercyUsed field parses and keeps the override count`() {
        val legacy = """
            {
              "version": 1,
              "month": "2026-06",
              "score": 72.5,
              "mercyUsed": 3
            }
        """.trimIndent()

        val stored = json.decodeFromString<StoredMonthlyReflection>(legacy)
        assertEquals(3, stored.mercyUsed)

        // toReport() rebuilds the in-memory MonthlyStats with the renamed property
        // while preserving the same emergency-override count.
        val report = stored.toReport()
        assertEquals(3, report.stats.overridesUsed)
    }
}
