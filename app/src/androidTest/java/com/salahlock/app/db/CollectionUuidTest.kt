package com.salahlock.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.entity.UserCollectionEntity
import com.salahlock.app.data.model.BookmarkItem
import com.salahlock.app.data.model.BookmarkType
import com.salahlock.app.data.repository.CollectionsRepository
import com.salahlock.app.data.sync.ActiveOwnerProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BM-013 Checkpoint B — proves collections receive a stable, non-blank UUID at
 * creation and that it survives rename/reload, backfilled UUIDs are untouched, and
 * membership rows map to the correct collection UUID.
 */
@RunWith(AndroidJUnit4::class)
class CollectionUuidTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: CollectionsRepository
    private val owner = "user_A"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val provider = ActiveOwnerProvider().apply { onAuthenticated(owner) }
        repo = CollectionsRepository(db.collectionsDao(), provider)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun newCollections_getDistinctNonBlankUuids() = runBlocking {
        val id1 = repo.create("One")
        val id2 = repo.create("Two")
        val u1 = db.collectionsDao().getCollectionUuid(owner, id1)!!
        val u2 = db.collectionsDao().getCollectionUuid(owner, id2)!!
        assertTrue(u1.isNotBlank()); assertTrue(u2.isNotBlank())
        assertEquals(36, u1.length)
        assertNotEquals(u1, u2)
    }

    @Test
    fun uuid_unchangedAfterRename() = runBlocking {
        val id = repo.create("Original")
        val before = db.collectionsDao().getCollectionUuid(owner, id)
        repo.rename(id, "Renamed")
        assertEquals(before, db.collectionsDao().getCollectionUuid(owner, id))
        assertEquals("Renamed", db.collectionsDao().observeCollection(owner, id).first()!!.name)
    }

    @Test
    fun uuid_stableAcrossRepositoryReload() = runBlocking {
        val id = repo.create("Persist")
        val before = db.collectionsDao().getCollectionUuid(owner, id)
        val reloaded = CollectionsRepository(
            db.collectionsDao(),
            ActiveOwnerProvider().apply { onAuthenticated(owner) },
        )
        // A fresh repository over the same DB sees the same UUID (no re-minting).
        reloaded.rename(id, "Persist2")
        assertEquals(before, db.collectionsDao().getCollectionUuid(owner, id))
    }

    @Test
    fun backfilledUuid_isUntouched() = runBlocking {
        // Simulate a migration-backfilled row with a preset UUID + no minting path.
        db.collectionsDao().insertCollection(
            UserCollectionEntity(name = "Legacy", clientUuid = "legacy-uuid-xyz", ownerId = owner),
        )
        val legacy = db.collectionsDao().observeCollections(owner).first().first { it.name == "Legacy" }
        repo.rename(legacy.id, "Legacy Renamed")
        assertEquals("legacy-uuid-xyz", db.collectionsDao().getCollectionUuid(owner, legacy.id))
    }

    @Test
    fun collectionItems_mapToOwningCollectionUuid() = runBlocking {
        val id = repo.create("WithItems")
        val uuid = db.collectionsDao().getCollectionUuid(owner, id)
        repo.addItem(
            id,
            BookmarkItem(type = BookmarkType.QURAN, key = "2:255", meta = "", title = "", body = ""),
        )
        val item = db.collectionsDao().observeItems(owner, id).first().first()
        assertEquals(uuid, item.collectionUuid)
        assertEquals(owner, item.ownerId)
    }
}
