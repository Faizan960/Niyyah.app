package com.salahlock.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-entered local masjid Jamaat (congregation) timetable.
 *
 * Single-row table (id = 1 always). Times are stored as "HH:mm" strings so
 * they survive timezone changes without re-entry.
 *
 * Future extension points (no schema change needed):
 *  - Add `fridayJumah: String?` for Friday Jumah override
 *  - Add `ramadanFajr: String?` / `ramadanIftar: String?` for Ramadan tables
 *  - Add `communityId: String?` for community masjid database linking
 */
@Entity(tableName = "local_masjid")
data class LocalMasjidEntity(
    @PrimaryKey val id: Int = 1,
    val masjidName: String = "",
    /** "HH:mm" 24-hour format */
    val fajr: String = "",
    val dhuhr: String = "",
    val asr: String = "",
    val maghrib: String = "",
    val isha: String = "",
    /** Legacy display-selection flag. Never determines actual prayer starts. */
    val enabled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
