package com.salahlock.app.data.model

/**
 * Determines where prayer times originate.
 *
 * [API] — Aladhan REST API with offline Adhan2 fallback. Default.
 * [LOCAL_MASJID] — User-entered timings for their local masjid.
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
