package com.salahlock.app.data.model

/**
 * Determines which schedule context the UI highlights.
 *
 * Actual prayer starts always come from Aladhan/offline Adhan2. [LOCAL_MASJID]
 * highlights the saved congregation schedule without replacing prayer starts.
 *
 * Architecture is designed to support future sources (community DB, Friday overrides,
 * Ramadan timetables) without schema changes.
 */
enum class PrayerSource {
    API,
    LOCAL_MASJID;

    companion object {
        fun fromString(value: String): PrayerSource =
            entries.firstOrNull { it.name == value } ?: API
    }
}
