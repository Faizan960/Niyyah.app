package com.salahlock.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * How a prayer was verified.
 * LOCK_VERIFIED = confirmed through the SalahLock overlay.
 * SELF_REPORTED = user manually marked it done from the Prayers screen.
 * NONE = not yet verified.
 */
enum class VerificationType { NONE, LOCK_VERIFIED, SELF_REPORTED }

/** One record per prayer per day. */
@Entity(
    tableName = "prayer_records",
    indices = [Index(value = ["date", "prayerName"], unique = true)],
)
data class PrayerRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO date string: "2025-06-04" */
    val date: String,
    /** FAJR | DHUHR | ASR | MAGHRIB | ISHA */
    val prayerName: String,
    val verified: Boolean = false,
    val overrideUsed: Boolean = false,
    /** Stored as string to survive Room migrations without losing data. Default = "NONE" */
    val verificationType: String = VerificationType.NONE.name,
    val timestampMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "streaks")
data class StreakEntity(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    /** ISO date of the last fully verified day */
    val lastFullDay: String = "",
    /** ISO week string of last mercy used: "2025-W23" */
    val lastMercyWeek: String = "",
    val mercyUsedThisWeek: Boolean = false,
)

@Entity(tableName = "emergency_overrides")
data class EmergencyOverride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "2025-06" — month+year for monthly reset */
    val monthYear: String,
    val count: Int = 0,
    val lastReason: String = "",
    val lastUsedMs: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "app_blacklist",
    indices = [Index(value = ["packageName"], unique = true)],
)
data class AppBlacklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val isBlocked: Boolean = true,
)

/**
 * Cached nearby masjid entry from OpenStreetMap Overpass API.
 * Refreshed in the background if older than 24 hours.
 */
@Entity(
    tableName = "masjids",
    indices = [Index(value = ["osmId"], unique = true)],
)
data class MasjidEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** OpenStreetMap node/way/relation ID */
    val osmId: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** Full address string, may be empty */
    val address: String = "",
    /** Distance in meters from user, computed at fetch time */
    val distanceMeters: Int = 0,
    /** Epoch millis when this entry was fetched */
    val fetchedAtMs: Long = System.currentTimeMillis(),
)

