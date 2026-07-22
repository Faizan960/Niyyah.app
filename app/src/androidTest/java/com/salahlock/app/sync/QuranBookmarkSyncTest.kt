package com.salahlock.app.sync

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salahlock.app.data.cloud.CloudBookmarkRow
import com.salahlock.app.data.cloud.CloudSyncException
import com.salahlock.app.data.cloud.QuranBookmarksCloud
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.LegacyOwnershipEntity
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.db.entity.SyncDomains
import com.salahlock.app.data.db.entity.SyncOps
import com.salahlock.app.data.repository.QuranRepository
import com.salahlock.app.data.sync.ActiveOwnerProvider
import com.salahlock.app.data.sync.LegacyAdoptionManager
import com.salahlock.app.data.sync.OwnerScope
import com.salahlock.app.data.sync.QuranBookmarkSyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BM-013 Checkpoint C — the 15 required sync-slice scenarios: outbox atomicity,
 * idempotency, first-sync merge, tombstone/non-resurrection, first-sync flag
 * discipline, owner isolation, logout scope, and one-time adoption.
 *
 * Cloud = [FakeCloud] (in-memory, natural-key merge + server-authoritative
 * monotonic updated_at, per-token row buckets to mimic RLS isolation).
 */
@RunWith(AndroidJUnit4::class)
class QuranBookmarkSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var cloud: FakeCloud
    private val A = "user_A"
    private val B = "user_B"

    /** In-memory PostgREST stand-in. Token == user id; rows bucketed per user (RLS). */
    private class FakeCloud : QuranBookmarksCloud {
        val store = mutableMapOf<String, MutableMap<Pair<Int, Int>, CloudBookmarkRow>>()
        private var serverTick = 0L
        var failNextUpsert = false
        var failNextPull = false
        var upsertCalls = 0

        private fun nextTime(): String = "2026-07-22T00:00:${(++serverTick).toString().padStart(2, '0')}.000000+00:00"

        /** Server-side write used to simulate another device's push. */
        fun seed(user: String, row: CloudBookmarkRow) {
            store.getOrPut(user) { mutableMapOf() }[row.surahNumber to row.ayahNumber] =
                row.copy(updatedAt = nextTime())
        }

        override suspend fun upsert(token: String, rows: List<CloudBookmarkRow>) {
            upsertCalls++
            if (failNextUpsert) { failNextUpsert = false; throw CloudSyncException("simulated upsert failure") }
            val bucket = store.getOrPut(token) { mutableMapOf() }
            rows.forEach { r ->
                val key = r.surahNumber to r.ayahNumber
                val existing = bucket[key]
                // merge-duplicates on the natural key; server stamps updated_at.
                bucket[key] = r.copy(
                    createdAtMs = existing?.createdAtMs?.takeIf { it > 0 } ?: r.createdAtMs,
                    updatedAt = nextTime(),
                )
            }
        }

        override suspend fun pull(token: String, sinceIso: String?): List<CloudBookmarkRow> {
            if (failNextPull) { failNextPull = false; throw CloudSyncException("simulated pull failure") }
            return store[token].orEmpty().values
                .filter { sinceIso == null || it.updatedAt >= sinceIso }
                .sortedBy { it.updatedAt }
        }
    }

    private fun engine(user: String) = QuranBookmarkSyncEngine(db, cloud, token = { user })

    private fun outboxCount(owner: String) = runBlocking { db.syncOutboxDao().countForOwner(owner) }

    @Before
    fun setUp() = runBlocking<Unit> {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        cloud = FakeCloud()
        db.legacyOwnershipDao().insertIfAbsent(LegacyOwnershipEntity())
    }

    @After
    fun tearDown() {
        db.close()
        // Restore the process-wide provider to its default state for other tests.
        ActiveOwnerProvider.shared.onSignedOut(legacyEverAdopted = false)
    }

    // ── 1 + 2: mutation + outbox atomicity ───────────────────────────────────

    @Test
    fun bookmarkWrite_andOutboxEvent_commitAtomically() = runBlocking {
        ActiveOwnerProvider.shared.onAuthenticated(A)
        val repo = repo()
        repo.toggleAyahBookmark(2, 255, true)

        assertEquals(1, db.quranDao().getAllBookmarksOnce(A).size)
        val events = db.syncOutboxDao().pending(A, SyncDomains.QURAN_BOOKMARK)
        assertEquals(1, events.size)
        assertEquals(SyncOps.UPSERT, events[0].operation)
        assertEquals("2:255", events[0].entityKey)

        // Rollback proof: a failure inside the same transaction persists NEITHER side.
        try {
            db.withTransaction {
                db.quranDao().insertBookmark(QuranBookmarkEntity(surahNumber = 3, ayahNumber = 1, ownerId = A))
                db.syncOutboxDao().enqueue(QuranBookmarkSyncEngine.deleteEvent(A, 3, 1, 1L))
                throw IllegalStateException("boom")
            }
        } catch (_: IllegalStateException) { }
        assertEquals(1, db.quranDao().getAllBookmarksOnce(A).size)
        assertEquals(1, outboxCount(A))
    }

    @Test
    fun delete_enqueuesDeleteAtomically_andCoalescesOverUpsert() = runBlocking {
        ActiveOwnerProvider.shared.onAuthenticated(A)
        val repo = repo()
        repo.toggleAyahBookmark(2, 255, true)   // UPSERT event
        repo.toggleAyahBookmark(2, 255, false)  // DELETE event replaces it

        assertEquals(0, db.quranDao().getAllBookmarksOnce(A).size)
        val events = db.syncOutboxDao().pending(A, SyncDomains.QURAN_BOOKMARK)
        assertEquals("coalesced to a single newest op", 1, events.size)
        assertEquals(SyncOps.DELETE, events[0].operation)
    }

    // ── 3: duplicate / upsert idempotency ────────────────────────────────────

    @Test
    fun repeatedUpsert_isIdempotent_locallyAndInCloud() = runBlocking {
        ActiveOwnerProvider.shared.onAuthenticated(A)
        val repo = repo()
        repo.toggleAyahBookmark(2, 255, true)
        repo.toggleAyahBookmark(2, 255, true)
        assertEquals(1, db.quranDao().getAllBookmarksOnce(A).size)
        assertEquals(1, outboxCount(A))

        engine(A).sync(A)
        engine(A).sync(A) // second full pass: no new rows, no duplicates
        assertEquals(1, cloud.store[A]!!.size)
        assertEquals(1, db.quranDao().getAllBookmarksOnce(A).size)
    }

    // ── 4: worker retry does not duplicate cloud rows ────────────────────────

    @Test
    fun retryAfterPushFailure_doesNotDuplicateCloudRows() = runBlocking {
        seedLocal(A, 2, 255)
        cloud.failNextUpsert = true
        try { engine(A).sync(A); fail("expected failure") } catch (_: CloudSyncException) { }
        assertEquals("event retained for retry", 1, outboxCount(A))
        val attempt = db.syncOutboxDao().pending(A, SyncDomains.QURAN_BOOKMARK)[0]
        assertTrue(attempt.attemptCount >= 1)
        assertNotNull(attempt.lastError)

        engine(A).sync(A) // retry succeeds
        engine(A).sync(A) // and re-running remains stable
        assertEquals(1, cloud.store[A]!!.size)
        assertEquals(0, outboxCount(A))
    }

    // ── 5 + 14: owner isolation locally ──────────────────────────────────────

    @Test
    fun ownerScoping_isolatesUserA_fromUserB_andFlowsSwitchOnOwnerChange() = runBlocking {
        ActiveOwnerProvider.shared.onAuthenticated(A)
        val repo = repo()
        repo.toggleAyahBookmark(2, 255, true)
        assertEquals(1, repo.getAllBookmarks().first().size)

        ActiveOwnerProvider.shared.onAuthenticated(B)
        assertEquals("User B must never see A's local rows", 0, repo.getAllBookmarks().first().size)
        assertEquals(0, db.quranDao().getAllBookmarksOnce(B).size)

        ActiveOwnerProvider.shared.onSignedOut(legacyEverAdopted = true)
        assertEquals("signed-out-after-adoption sees nothing", 0, repo.getAllBookmarks().first().size)
    }

    // ── 6/7/8: first-sync merge ──────────────────────────────────────────────

    @Test
    fun firstSync_pushesLocalOnlyBookmarks() = runBlocking {
        seedLocal(A, 2, 255)
        seedLocal(A, 18, null)
        engine(A).sync(A)

        assertEquals(2, cloud.store[A]!!.size)
        assertNull(cloud.store[A]!![2 to 255]!!.deletedAt)
        assertNotNull("whole-surah encodes ayah 0", cloud.store[A]!![18 to 0])
        assertTrue(db.syncStateDao().get(A)!!.firstSyncDone)
    }

    @Test
    fun firstSync_pullsCloudOnlyBookmarks() = runBlocking {
        cloud.seed(A, CloudBookmarkRow(surahNumber = 36, ayahNumber = 9, collectionName = "Fav", createdAtMs = 111))
        engine(A).sync(A)

        val local = db.quranDao().getAllBookmarksOnce(A)
        assertEquals(1, local.size)
        assertEquals(36, local[0].surahNumber)
        assertEquals(9, local[0].ayahNumber)
        assertEquals("Fav", local[0].collectionName)
        assertEquals(111L, local[0].createdAtMs)
    }

    @Test
    fun firstSync_mergesSameBookmarkOnBothSides_withoutDuplicates() = runBlocking {
        seedLocal(A, 2, 255)
        cloud.seed(A, CloudBookmarkRow(surahNumber = 2, ayahNumber = 255))
        engine(A).sync(A)

        assertEquals(1, cloud.store[A]!!.size)
        assertEquals(1, db.quranDao().getAllBookmarksOnce(A).size)
    }

    // ── 9 + 10: tombstones win; stale device cannot resurrect ────────────────

    @Test
    fun tombstone_winsOnIncrementalPull() = runBlocking {
        seedLocal(A, 2, 255)
        engine(A).sync(A) // first sync: pushed + firstSyncDone

        // Another device deletes the bookmark (server writes a newer tombstone).
        cloud.seed(A, cloud.store[A]!![2 to 255]!!.copy(deletedAt = "2026-07-22T09:00:00+00:00"))

        engine(A).sync(A) // incremental: tombstone applies
        assertEquals("deleted bookmark removed locally", 0, db.quranDao().getAllBookmarksOnce(A).size)
    }

    @Test
    fun staleDevice_cannotResurrectDeletedBookmark() = runBlocking {
        // Device B state: synced copy of the bookmark, nothing pending.
        seedLocal(A, 2, 255)
        engine(A).sync(A)
        assertEquals(1, db.quranDao().getAllBookmarksOnce(A).size)

        // Device A (elsewhere) deletes while B is offline → cloud holds tombstone.
        cloud.seed(A, cloud.store[A]!![2 to 255]!!.copy(deletedAt = "2026-07-22T09:00:00+00:00"))

        // B comes back and syncs (push: empty outbox → nothing to resurrect; pull: tombstone).
        engine(A).sync(A)
        assertEquals(0, db.quranDao().getAllBookmarksOnce(A).size)
        assertNotNull("cloud tombstone intact — nothing resurrected", cloud.store[A]!![2 to 255]!!.deletedAt)

        // Even repeated passes never bring it back.
        engine(A).sync(A)
        assertEquals(0, db.quranDao().getAllBookmarksOnce(A).size)
    }

    @Test
    fun localDelete_propagatesAsTombstone_andReAddClearsIt() = runBlocking {
        ActiveOwnerProvider.shared.onAuthenticated(A)
        val repo = repo()
        repo.toggleAyahBookmark(2, 255, true)
        engine(A).sync(A)
        assertNull(cloud.store[A]!![2 to 255]!!.deletedAt)

        repo.toggleAyahBookmark(2, 255, false)
        engine(A).sync(A)
        assertNotNull("delete becomes a server tombstone", cloud.store[A]!![2 to 255]!!.deletedAt)

        repo.toggleAyahBookmark(2, 255, true)
        engine(A).sync(A)
        assertNull("re-add clears the tombstone", cloud.store[A]!![2 to 255]!!.deletedAt)
        assertEquals(1, db.quranDao().getAllBookmarksOnce(A).size)
    }

    // ── 11 + 12: first_sync_done discipline ──────────────────────────────────

    @Test
    fun failedFirstSync_doesNotMarkFirstSyncDone_andKeepsLocalData() = runBlocking {
        seedLocal(A, 2, 255)
        cloud.failNextPull = true
        try { engine(A).sync(A); fail("expected failure") } catch (_: CloudSyncException) { }

        assertFalse(db.syncStateDao().get(A)?.firstSyncDone ?: false)
        assertEquals("local data untouched by failed first sync", 1, db.quranDao().getAllBookmarksOnce(A).size)

        engine(A).sync(A) // retry completes the first sync
        assertTrue(db.syncStateDao().get(A)!!.firstSyncDone)
        assertEquals(1, cloud.store[A]!!.size)
    }

    @Test
    fun emptyCloudFirstSync_neverDeletesLocalBookmarks() = runBlocking {
        seedLocal(A, 2, 255)
        engine(A).sync(A) // cloud starts empty
        assertEquals(1, db.quranDao().getAllBookmarksOnce(A).size)
        assertTrue(db.syncStateDao().get(A)!!.firstSyncDone)
    }

    // ── 13 + 15: logout scope + one-time adoption via coordinator logic ──────

    @Test
    fun signOutAfterAdoption_switchesToSignedOutNoUser_neverBackToLocal() = runBlocking {
        val provider = ActiveOwnerProvider()
        val manager = LegacyAdoptionManager(db, provider)
        seedLegacyLocal(2, 255)

        assertTrue(manager.adoptLegacyDataOnce(A, nowMs = 1000))
        assertEquals(OwnerScope.Authenticated(A), provider.scope())

        provider.onSignedOut(legacyEverAdopted = db.legacyOwnershipDao().get()?.adoptedBy != null)
        assertEquals(OwnerScope.SignedOutNoUser, provider.scope())
        // The __none__ scope matches no rows.
        assertEquals(0, db.quranDao().getAllBookmarksOnce(provider.ownerId()).size)
    }

    @Test
    fun userB_cannotClaimLegacyData_afterUserA_andSeesNothing() = runBlocking {
        val provider = ActiveOwnerProvider()
        val manager = LegacyAdoptionManager(db, provider)
        seedLegacyLocal(2, 255)

        assertTrue(manager.adoptLegacyDataOnce(A, nowMs = 1000))
        assertFalse("B cannot re-claim", manager.adoptLegacyDataOnce(B, nowMs = 2000))
        assertEquals(A, db.legacyOwnershipDao().get()!!.adoptedBy)

        provider.onAuthenticated(B)
        assertEquals("B sees no adopted rows", 0, db.quranDao().getAllBookmarksOnce(B).size)
        assertEquals(1, db.quranDao().getAllBookmarksOnce(A).size)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Repository bound to the in-memory DB; owner comes from the SHARED provider. */
    private fun repo(): QuranRepository = QuranRepository(
        ApplicationProvider.getApplicationContext(),
        db = db,
        scheduleSync = { },
    )

    private fun seedLocal(owner: String, surah: Int, ayah: Int?) = runBlocking {
        db.quranDao().insertBookmark(
            QuranBookmarkEntity(surahNumber = surah, ayahNumber = ayah, ownerId = owner, createdAtMs = 500),
        )
    }

    private fun seedLegacyLocal(surah: Int, ayah: Int?) = runBlocking {
        db.quranDao().insertBookmark(QuranBookmarkEntity(surahNumber = surah, ayahNumber = ayah))
    }
}
