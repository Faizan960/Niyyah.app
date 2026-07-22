package com.salahlock.app.data.sync

import androidx.room.withTransaction
import com.salahlock.app.data.cloud.CloudBookmarkRow
import com.salahlock.app.data.cloud.CloudSyncException
import com.salahlock.app.data.cloud.QuranBookmarksCloud
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.db.entity.SyncDomains
import com.salahlock.app.data.db.entity.SyncOps
import com.salahlock.app.data.db.entity.SyncOutboxEntity
import com.salahlock.app.data.db.entity.SyncStateEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * BM-013 Checkpoint C — Quran-bookmark sync algorithm. Pure orchestration over
 * Room + a [QuranBookmarksCloud] transport; no Android framework types, so tests
 * drive it with an in-memory DB and a fake cloud.
 *
 * ## Conflict / delete semantics (Phase 5, decided before coding)
 * A bookmark is set membership on the natural key `(owner, surah, ayah|0)`.
 *  - The cloud row is never hard-deleted by clients: delete = tombstone
 *    (`deleted_at` non-null) via the same natural-key upsert. `updated_at` is
 *    SERVER-authoritative (trigger), so ordering never trusts a device clock.
 *  - Pulls apply cloud state per key **except** keys with a pending outbox op —
 *    a local unpushed mutation always survives the pull and is pushed after
 *    (deterministic local-wins-until-pushed; the push then becomes the newest
 *    server write and propagates to other devices).
 *  - A stale device with NO pending op for a key simply applies the tombstone —
 *    it cannot resurrect a deleted bookmark because it has nothing to push.
 *  - Re-adding after a delete is an UPSERT that clears `deleted_at`.
 *
 * ## First sync (Phase 4)
 *  1. Snapshot ALL local bookmarks of this owner and enqueue them as UPSERT ops
 *     (one atomic Room txn). Adopted-legacy data thereby becomes "pending local
 *     mutations", so the subsequent pull can never destroy it.
 *  2. Push the outbox (upserts merge on natural key; also clears any cloud
 *     tombstones for keys this device genuinely holds).
 *  3. Full pull; apply per key (skipping pending ops — normally none remain);
 *     then, in the SAME txn, persist the watermark and `firstSyncDone = true`.
 *  Any failure before step 3's txn leaves `firstSyncDone = false` and all local
 *  data intact; the whole procedure is idempotent and safe to re-run.
 *
 * ## Incremental sync
 *  push pending outbox → pull `updated_at >= watermark` (gte + idempotent
 *  re-application closes the equal-timestamp gap) → apply + advance watermark.
 */
class QuranBookmarkSyncEngine(
    private val db: AppDatabase,
    private val cloud: QuranBookmarksCloud,
    /** Fresh Clerk session JWT per call — never cached or persisted by the engine. */
    private val token: suspend () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val quranDao = db.quranDao()
    private val outbox = db.syncOutboxDao()
    private val stateDao = db.syncStateDao()

    /** Runs one full sync pass for [ownerId] (a Clerk user id). Throws to signal retry. */
    suspend fun sync(ownerId: String) {
        require(ownerId.isNotBlank())
        val state = stateDao.get(ownerId)
        if (state?.firstSyncDone != true) {
            firstSync(ownerId)
        } else {
            push(ownerId)
            pull(ownerId)
        }
    }

    // ------------------------------------------------------------ first sync

    private suspend fun firstSync(ownerId: String) {
        // Step 1 — atomically turn the current local dataset into pending mutations.
        db.withTransaction {
            quranDao.getAllBookmarksOnce(ownerId).forEach { b ->
                outbox.enqueue(upsertEvent(ownerId, b, clock()))
            }
        }
        // Step 2 — push local intent first so a cloud tombstone can never delete
        // an unsynced local bookmark (our upsert clears it before the pull).
        push(ownerId)
        // Step 3 — full pull + merge + mark first sync done, atomically.
        val rows = cloud.pull(requireToken(), sinceIso = null)
        db.withTransaction {
            applyCloudRows(ownerId, rows)
            stateDao.upsert(
                SyncStateEntity(
                    ownerId = ownerId,
                    firstSyncDone = true,
                    pullWatermark = rows.maxOfOrNull { it.updatedAt } ?: "",
                ),
            )
        }
    }

    // ------------------------------------------------------------ push

    private suspend fun push(ownerId: String) {
        while (true) {
            val batch = outbox.pending(ownerId, SyncDomains.QURAN_BOOKMARK)
            if (batch.isEmpty()) return
            val rows = batch.map { it.toCloudRow() }
            try {
                cloud.upsert(requireToken(), rows)
            } catch (e: Exception) {
                outbox.recordAttempt(batch.map { it.id }, clock(), e.message?.take(200))
                throw e
            }
            // Delete strictly by id: an entity re-mutated during the push got a
            // REPLACEd row with a NEW id, which therefore survives for the next pass.
            outbox.deleteByIds(batch.map { it.id })
            if (batch.size < 200) return
        }
    }

    // ------------------------------------------------------------ pull

    private suspend fun pull(ownerId: String) {
        val since = stateDao.get(ownerId)?.pullWatermark?.takeIf { it.isNotBlank() }
        val rows = cloud.pull(requireToken(), since)
        if (rows.isEmpty()) return
        db.withTransaction {
            applyCloudRows(ownerId, rows)
            val prev = stateDao.get(ownerId) ?: SyncStateEntity(ownerId)
            stateDao.upsert(
                prev.copy(pullWatermark = maxOf(prev.pullWatermark, rows.maxOf { it.updatedAt })),
            )
        }
    }

    /**
     * Applies cloud rows per natural key. MUST run inside a Room transaction.
     * Keys with a pending outbox op are skipped (local unpushed intent wins until
     * its push). Tombstones remove the local row; live rows upsert it. Never
     * deletes anything merely because a pull was empty.
     */
    private suspend fun applyCloudRows(ownerId: String, rows: List<CloudBookmarkRow>) {
        rows.forEach { row ->
            val key = entityKey(row.surahNumber, row.ayahNumber)
            if (outbox.hasPending(ownerId, SyncDomains.QURAN_BOOKMARK, key)) return@forEach
            val ayahOrNull = row.ayahNumber.takeIf { it != 0 }
            if (row.deletedAt != null) {
                if (ayahOrNull != null) quranDao.deleteAyahBookmark(ownerId, row.surahNumber, ayahOrNull)
                else quranDao.deleteSurahBookmark(ownerId, row.surahNumber)
            } else {
                quranDao.insertBookmark(
                    QuranBookmarkEntity(
                        surahNumber = row.surahNumber,
                        ayahNumber = ayahOrNull,
                        collectionName = row.collectionName,
                        createdAtMs = if (row.createdAtMs > 0) row.createdAtMs else clock(),
                        ownerId = ownerId,
                        updatedAt = clock(),
                    ),
                )
            }
        }
    }

    private suspend fun requireToken(): String =
        token() ?: throw CloudSyncException("No Clerk session token available", code = 401)

    private fun SyncOutboxEntity.toCloudRow(): CloudBookmarkRow {
        val (surah, ayah) = parseKey(entityKey)
        return if (operation == SyncOps.DELETE) {
            CloudBookmarkRow(
                surahNumber = surah,
                ayahNumber = ayah,
                // Tombstone timestamp is informational; conflict ordering uses the
                // server-side updated_at trigger, never this device clock.
                deletedAt = java.time.Instant.ofEpochMilli(createdAtMs).toString(),
            )
        } else {
            val meta = Json.decodeFromString(UpsertPayload.serializer(), payload)
            CloudBookmarkRow(
                surahNumber = surah,
                ayahNumber = ayah,
                collectionName = meta.collectionName,
                createdAtMs = meta.createdAtMs,
                deletedAt = null, // explicit: an upsert always clears a tombstone
            )
        }
    }

    companion object {
        /** Natural key inside the outbox: `"<surah>:<ayah|0>"` (0 = whole surah). */
        fun entityKey(surah: Int, ayah: Int?): String = "$surah:${ayah ?: 0}"

        private fun parseKey(key: String): Pair<Int, Int> {
            val (s, a) = key.split(":")
            return s.toInt() to a.toInt()
        }

        /** UPSERT outbox event carrying the bookmark's sync-relevant metadata. */
        fun upsertEvent(ownerId: String, b: QuranBookmarkEntity, nowMs: Long): SyncOutboxEntity =
            SyncOutboxEntity(
                ownerId = ownerId,
                domain = SyncDomains.QURAN_BOOKMARK,
                entityKey = entityKey(b.surahNumber, b.ayahNumber),
                operation = SyncOps.UPSERT,
                payload = Json.encodeToString(
                    UpsertPayload.serializer(),
                    UpsertPayload(b.collectionName, b.createdAtMs),
                ),
                createdAtMs = nowMs,
            )

        /** DELETE outbox event for a natural key. */
        fun deleteEvent(ownerId: String, surah: Int, ayah: Int?, nowMs: Long): SyncOutboxEntity =
            SyncOutboxEntity(
                ownerId = ownerId,
                domain = SyncDomains.QURAN_BOOKMARK,
                entityKey = entityKey(surah, ayah),
                operation = SyncOps.DELETE,
                payload = "",
                createdAtMs = nowMs,
            )
    }

    @Serializable
    data class UpsertPayload(val collectionName: String = "", val createdAtMs: Long = 0L)
}
