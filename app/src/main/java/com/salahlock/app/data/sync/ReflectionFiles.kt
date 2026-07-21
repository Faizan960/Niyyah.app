package com.salahlock.app.data.sync

import com.salahlock.app.data.db.entity.OwnerIds
import java.io.File

/**
 * BM-013 Checkpoint B — pure, crash-safe file operations for per-owner reflection
 * storage (`<root>/<ownerId>/<month>.json`). Extracted from SpiritualReportRepository
 * so the recovery logic is unit-testable without an Android Context.
 *
 * ## Crash-safe adoption ordering (review item 3)
 * Reflection files can't join the Room transaction. Adoption therefore orders as:
 *   1. Room transaction claims the legacy dataset (`legacy_ownership.adoptedBy = A`)
 *      and re-stamps DB rows — commits FIRST (the durable source of truth).
 *   2. [adoptLocalTo] moves the `<root>/__local__` json files into `<root>/A`.
 * If the process dies between (1) and (2), some reflection files remain under
 * `__local__`. On next start, [recoverPendingAdoption] reads the committed
 * `adoptedBy = A` and finishes the move — idempotently. Because step (1) already
 * committed, User B's claim can never succeed, so those leftover files can only ever
 * be completed to User A. There is no window where User B can adopt them.
 */
object ReflectionFiles {

    /** Relocates pre-BM-013 loose top-level json files into the `__local__` namespace. */
    fun migrateLooseToLocal(root: File) {
        val loose = root.listFiles { f -> f.isFile && f.extension == "json" } ?: return
        if (loose.isEmpty()) return
        val local = File(root, OwnerIds.LOCAL).also { it.mkdirs() }
        loose.forEach { f -> moveInto(local, f) }
    }

    /**
     * Moves every json file under `<root>/__local__` into `<root>/<owner>`. Idempotent: an
     * already-moved file (present at destination) is not overwritten — the stale
     * source copy is dropped. Safe to call repeatedly / after a crash.
     */
    fun adoptLocalTo(root: File, owner: String) {
        require(owner.isNotBlank() && owner != OwnerIds.LOCAL && owner != OwnerIds.NONE) {
            "adopt target must be a real owner id"
        }
        val src = File(root, OwnerIds.LOCAL)
        val files = src.listFiles { f -> f.isFile && f.extension == "json" } ?: return
        val dst = File(root, owner).also { it.mkdirs() }
        files.forEach { f -> moveInto(dst, f) }
        // Drop the source dir once emptied (only if nothing left).
        if ((src.listFiles()?.isEmpty() == true)) src.delete()
    }

    /**
     * Startup recovery: if the legacy dataset was already adopted (adoptedBy != null),
     * finish moving any leftover `__local__` reflections to that owner. No-op when
     * unclaimed. NEVER touches another owner's directory.
     */
    fun recoverPendingAdoption(root: File, adoptedBy: String?) {
        if (adoptedBy.isNullOrBlank()) return
        adoptLocalTo(root, adoptedBy)
    }

    /** Move [file] into [dir]; if a same-named file already exists there, drop the source. */
    private fun moveInto(dir: File, file: File) {
        val dest = File(dir, file.name)
        if (!dest.exists()) file.renameTo(dest) else file.delete()
    }
}
