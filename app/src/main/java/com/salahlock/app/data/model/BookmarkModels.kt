package com.salahlock.app.data.model

/** The four content modules that support bookmarking. */
enum class BookmarkType(val label: String) {
    QURAN("Quran"),
    HADITH("Hadith"),
    KNOWLEDGE("Knowledge"),
    AZKAR("Azkar");

    companion object {
        fun fromName(name: String?): BookmarkType? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * A bookmark from any module, normalized for the Bookmarks screen and for
 * collection membership. [key] is the stable cross-module content key used
 * by collection_items (see CollectionItemEntity docs).
 */
data class BookmarkItem(
    val type: BookmarkType,
    val key: String,
    /** Small metadata line, e.g. "Al-Baqarah 2:286" or "Sahih al-Bukhari 657". */
    val meta: String,
    val title: String,
    val body: String,
    val titleIsArabic: Boolean = false,
    val createdAtMs: Long = 0L,
    // Navigation payload — only the fields relevant to [type] are set.
    val surah: Int? = null,
    val ayah: Int? = null,
    val hadithId: String? = null,
    val azkarId: Int? = null,
    val azkarCategory: String? = null,
)
