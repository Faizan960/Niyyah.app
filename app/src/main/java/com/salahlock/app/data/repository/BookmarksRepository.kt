package com.salahlock.app.data.repository

import com.salahlock.app.data.db.dao.CollectionsDao
import com.salahlock.app.data.db.entity.AzkarEntity
import com.salahlock.app.data.db.entity.HadithEntity
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.db.entity.collectionDisplayName
import com.salahlock.app.data.db.entity.globalNumber
import com.salahlock.app.data.model.BookmarkItem
import com.salahlock.app.data.model.BookmarkType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Aggregates the four per-module bookmark stores (quran_bookmarks,
 * hadith_table.isBookmarked, azkar_table.isBookmarked) into one unified
 * [BookmarkItem] stream for the Bookmarks screen and Collections.
 *
 * This layer does NOT own any storage — each module keeps its own bookmark
 * persistence and this repository only reads/normalizes it. Removing a
 * bookmark also removes its membership rows from every collection so the
 * two screens never drift apart.
 */
class BookmarksRepository(
    private val quranRepository: QuranRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val collectionsDao: CollectionsDao,
) {

    /** All bookmarks across the four modules, newest first. */
    fun observeAll(): Flow<List<BookmarkItem>> = combine(
        quranRepository.getAllBookmarks(),
        knowledgeRepository.getBookmarkedHadiths(),
        knowledgeRepository.getBookmarkedAzkar(),
    ) { quran, hadiths, azkar ->
        val surahs = quranRepository.getSurahs().associateBy { it.number }
        buildList {
            quran.forEach { b -> surahs[b.surahNumber]?.let { s -> add(quranItem(b, s)) } }
            hadiths.forEach { add(hadithItem(it)) }
            azkar.forEach { add(azkarItem(it)) }
        }.sortedByDescending { it.createdAtMs }
    }

    fun observeCounts(): Flow<Map<BookmarkType, Int>> = observeAll().map { items ->
        items.groupingBy { it.type }.eachCount()
    }

    /** Removes the bookmark in its home module and from every collection. */
    suspend fun remove(item: BookmarkItem) {
        when (item.type) {
            BookmarkType.QURAN -> {
                val surah = item.surah ?: return
                if (item.ayah != null) quranRepository.toggleAyahBookmark(surah, item.ayah, false)
                else quranRepository.toggleSurahBookmark(surah, false)
            }
            BookmarkType.HADITH, BookmarkType.KNOWLEDGE ->
                item.hadithId?.let { knowledgeRepository.toggleHadithBookmark(it, false) }
            BookmarkType.AZKAR ->
                item.azkarId?.let { knowledgeRepository.toggleAzkarBookmark(it, false) }
        }
        collectionsDao.deleteItemEverywhere(
            com.salahlock.app.data.sync.ActiveOwnerProvider.shared.ownerId(),
            item.type.name, item.key,
        )
    }

    // ------------------------------------------------------------- mapping

    private fun quranItem(b: QuranBookmarkEntity, s: Surah): BookmarkItem {
        val ayah = b.ayahNumber?.let { n -> s.ayahs.firstOrNull { it.numberInSurah == n } }
        return if (b.ayahNumber != null && ayah != null) {
            BookmarkItem(
                type = BookmarkType.QURAN,
                key = "${b.surahNumber}:${b.ayahNumber}",
                meta = "${s.transliteration} ${s.number}:${b.ayahNumber}",
                title = ayah.arabic,
                body = "\"${ayah.english}\"",
                titleIsArabic = true,
                createdAtMs = b.createdAtMs,
                surah = b.surahNumber,
                ayah = b.ayahNumber,
            )
        } else {
            BookmarkItem(
                type = BookmarkType.QURAN,
                key = "${b.surahNumber}:",
                meta = "Surah ${s.number} • ${s.ayahs.size} verses",
                title = s.transliteration,
                body = s.englishName,
                createdAtMs = b.createdAtMs,
                surah = b.surahNumber,
            )
        }
    }

    private fun hadithItem(h: HadithEntity): BookmarkItem {
        val type = if (h.bookmarkSource == KnowledgeRepository.BOOKMARK_SOURCE_KNOWLEDGE)
            BookmarkType.KNOWLEDGE else BookmarkType.HADITH
        return BookmarkItem(
            type = type,
            key = h.id,
            meta = "${h.collectionDisplayName} ${h.globalNumber}",
            title = snippet(h.translationText.trim().lineSequence().firstOrNull().orEmpty(), 64)
                .ifBlank { h.collectionDisplayName },
            body = "\"${snippet(h.translationText.trim(), 160)}\"",
            createdAtMs = h.lastReadTimestamp,
            hadithId = h.id,
        )
    }

    private fun azkarItem(a: AzkarEntity): BookmarkItem = BookmarkItem(
        type = BookmarkType.AZKAR,
        key = a.id.toString(),
        meta = "${a.category} Remembrance",
        title = a.transliteration.ifBlank { snippet(a.translation, 48) },
        body = "\"${snippet(a.translation, 160)}\"",
        createdAtMs = a.lastReadTimestamp,
        azkarId = a.id,
        azkarCategory = a.category,
    )

    private fun snippet(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max).trimEnd() + "…"
}
