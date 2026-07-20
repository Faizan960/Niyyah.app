package com.salahlock.app.bookmarks

import com.salahlock.app.data.model.BookmarkItem
import com.salahlock.app.data.model.BookmarkType
import com.salahlock.app.ui.bookmarks.BookmarksUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BM-010.4 — visibleItems combines the module type filter with a
 * case-insensitive search over title/body/meta.
 */
class BookmarksFilterTest {

    private fun item(
        type: BookmarkType,
        key: String,
        title: String,
        body: String = "",
        meta: String = "",
    ) = BookmarkItem(type = type, key = key, meta = meta, title = title, body = body)

    private val items = listOf(
        item(BookmarkType.QURAN, "2:286", "Al-Baqarah", "Allah does not burden a soul", meta = "Al-Baqarah 2:286"),
        item(BookmarkType.HADITH, "bukhari-1", "Actions are by intentions", meta = "Sahih al-Bukhari 1"),
        item(BookmarkType.AZKAR, "42", "Morning remembrance", body = "Protection du'a", meta = "Morning Remembrance"),
    )

    @Test
    fun `no filter and no query shows everything`() {
        val state = BookmarksUiState(allItems = items)
        assertEquals(3, state.visibleItems.size)
    }

    @Test
    fun `type filter narrows to one module`() {
        val state = BookmarksUiState(allItems = items, typeFilter = BookmarkType.HADITH)
        assertEquals(listOf("bukhari-1"), state.visibleItems.map { it.key })
    }

    @Test
    fun `search is case-insensitive across title body and meta`() {
        assertEquals(
            listOf("2:286"),
            BookmarksUiState(allItems = items, searchQuery = "BURDEN").visibleItems.map { it.key },
        )
        assertEquals(
            listOf("bukhari-1"),
            BookmarksUiState(allItems = items, searchQuery = "bukhari").visibleItems.map { it.key },
        )
    }

    @Test
    fun `search composes with type filter`() {
        val state = BookmarksUiState(
            allItems = items,
            typeFilter = BookmarkType.QURAN,
            searchQuery = "intentions",
        )
        assertTrue(state.visibleItems.isEmpty())
    }

    @Test
    fun `blank query is ignored`() {
        val state = BookmarksUiState(allItems = items, searchQuery = "   ")
        assertEquals(3, state.visibleItems.size)
    }
}
