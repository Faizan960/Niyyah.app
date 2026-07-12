package com.salahlock.app.data.db.entity

/**
 * Extension properties for HadithEntity that derive correct display values
 * from existing persisted data — no DB migration required.
 *
 * The fawazahmed0 API assigns two numbers to each hadith:
 *   - hadithnumber (global): sequential across the entire collection (1 → 7563 for Bukhari)
 *   - reference.hadith (within-book): resets to 1 for each book
 *
 * The global number is stored inside the entity's primary key:
 *   id = "{collection}-{globalNumber}-{language}"  e.g. "bukhari-657-eng"
 *
 * entity.hadithNumber stores the within-book number — never use it for citation.
 */

/** The global sequential hadith number across the entire collection (e.g., "657"). */
val HadithEntity.globalNumber: String
    get() = id.split("-").getOrNull(1) ?: hadithNumber

/** Full human-readable collection name. */
val HadithEntity.collectionDisplayName: String
    get() = when (collection.lowercase()) {
        "bukhari" -> "Sahih al-Bukhari"
        "muslim"  -> "Sahih Muslim"
        "abudawud", "abu-dawud" -> "Sunan Abu Dawud"
        "tirmidhi" -> "Jami at-Tirmidhi"
        "nasai", "nasai" -> "Sunan an-Nasa'i"
        "ibnmajah", "ibn-majah" -> "Sunan Ibn Majah"
        else -> collection.replaceFirstChar { it.uppercase() }
    }

/**
 * Short, standard citation reference shown to users.
 * Example: "Bukhari 647" or "Muslim 178"
 */
val HadithEntity.formattedReference: String
    get() = "${collection.replaceFirstChar { it.uppercase() }} $globalNumber"

/**
 * Full citation for the back card and sharing.
 * Example: "Sahih al-Bukhari, Book 8, Hadith 647"
 */
val HadithEntity.fullCitation: String
    get() = "$collectionDisplayName, Book $bookNumber, Hadith $globalNumber"
