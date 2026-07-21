package com.salahlock.app.sync

import com.salahlock.app.data.sync.ReflectionFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * BM-013 Checkpoint B — proves crash-safe, idempotent reflection adoption/recovery.
 */
class ReflectionFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(dir: File, name: String, body: String) {
        dir.mkdirs(); File(dir, name).writeText(body)
    }

    private fun read(root: File, owner: String, name: String): String? =
        File(File(root, owner), name).takeIf { it.exists() }?.readText()

    @Test
    fun migrateLooseToLocal_movesTopLevelJsonIntoLocal() {
        val root = tmp.newFolder("reflections")
        write(root, "2026-06.json", "A")
        ReflectionFiles.migrateLooseToLocal(root)
        assertFalse(File(root, "2026-06.json").exists())
        assertEquals("A", read(root, "__local__", "2026-06.json"))
    }

    @Test
    fun adoptLocalTo_movesLocalIntoOwner_andIsIdempotent() {
        val root = tmp.newFolder("reflections")
        write(File(root, "__local__"), "2026-06.json", "A")
        write(File(root, "__local__"), "2026-07.json", "B")
        ReflectionFiles.adoptLocalTo(root, "user_A")
        assertEquals("A", read(root, "user_A", "2026-06.json"))
        assertEquals("B", read(root, "user_A", "2026-07.json"))
        assertFalse("__local__ dir removed once emptied", File(root, "__local__").exists())
        // Second call is a no-op (no crash, nothing to move).
        ReflectionFiles.adoptLocalTo(root, "user_A")
        assertEquals("A", read(root, "user_A", "2026-06.json"))
    }

    @Test
    fun recovery_completesPartialMove_withoutOverwritingDestination() {
        val root = tmp.newFolder("reflections")
        // Simulate a crash mid-move: 2026-06 already in owner dir (newer), 2026-07 still legacy.
        write(File(root, "user_A"), "2026-06.json", "NEWER")
        write(File(root, "__local__"), "2026-06.json", "OLDER")
        write(File(root, "__local__"), "2026-07.json", "B")

        ReflectionFiles.recoverPendingAdoption(root, "user_A")

        assertEquals("existing dest not overwritten", "NEWER", read(root, "user_A", "2026-06.json"))
        assertEquals("B", read(root, "user_A", "2026-07.json"))
        assertFalse(File(root, "__local__").exists())
    }

    @Test
    fun recovery_noopWhenUnclaimed() {
        val root = tmp.newFolder("reflections")
        write(File(root, "__local__"), "2026-06.json", "A")
        ReflectionFiles.recoverPendingAdoption(root, null)
        assertEquals("legacy data untouched while unclaimed", "A", read(root, "__local__", "2026-06.json"))
    }

    @Test
    fun adopt_neverTouchesAnotherOwnersDirectory() {
        val root = tmp.newFolder("reflections")
        write(File(root, "user_B"), "2026-05.json", "B-private")
        write(File(root, "__local__"), "2026-06.json", "legacy")
        ReflectionFiles.adoptLocalTo(root, "user_A")
        assertEquals("user_B data untouched", "B-private", read(root, "user_B", "2026-05.json"))
        assertEquals("legacy", read(root, "user_A", "2026-06.json"))
    }
}
