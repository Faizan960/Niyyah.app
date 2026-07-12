package com.salahlock.app.data.backup

import com.salahlock.app.data.db.entity.AppBlacklistItem
import com.salahlock.app.data.db.entity.EmergencyOverride
import com.salahlock.app.data.db.entity.LocalMasjidEntity
import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.StreakEntity
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// ZIP entry: manifest.json  (plaintext — no decryption needed for preview)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class BackupManifest(
    val app: String = "SalahLock",
    val version: String = "",                // app versionName
    val backupVersion: Int = BACKUP_VERSION,
    val createdAt: String = "",              // ISO-8601 UTC
    val androidVersion: String = "",
    val device: String = "",
    val prayerRecordCount: Int = 0,          // for quick preview without decryption
    val currentStreak: Int = 0,
    val blockedAppCount: Int = 0,
) {
    companion object {
        const val BACKUP_VERSION = 1
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZIP entry: metadata.json  (plaintext)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class BackupMetadata(
    val encrypted: Boolean = true,
    val algorithm: String = "AES-256-GCM",
    val compression: String = "gzip",
    val checksumAlgorithm: String = "SHA-256",
    /** SHA-256 hex of the plaintext [BackupPackage] JSON (before gzip + encryption). */
    val checksum: String = "",
)

// ─────────────────────────────────────────────────────────────────────────────
// ZIP entry: backup.json  (JSON wrapper around encrypted payload)
// ─────────────────────────────────────────────────────────────────────────────

/** Wrapper stored as backup.json; `data` = Base64(IV + AES-256-GCM(GZIP(JSON(BackupPackage)))) */
@Serializable
data class BackupJsonEntry(val data: String)

// ─────────────────────────────────────────────────────────────────────────────
// BackupPackage  (the payload that goes inside backup.json after encryption)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Versioned container for all user-owned SalahLock data.
 *
 * Forward-compatibility rules:
 *  - All list fields default to [emptyList]; nullable fields default to null.
 *  - Unknown fields are silently ignored via [Json.ignoreUnknownKeys].
 *  - [version] lets future loaders detect and run migrations.
 *
 * Future extension points (no schema change needed — just add nullable/default fields):
 *  - achievements, AI verification history, community masjid link,
 *    ML dataset refs, cloud-sync token, analytics snapshot.
 */
@Serializable
data class BackupPackage(
    val version: Int = BACKUP_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersionName: String = "",

    val prayerHistory: List<BackupPrayerRecord> = emptyList(),
    val streak: BackupStreak? = null,
    val emergencyOverrides: List<BackupEmergencyOverride> = emptyList(),
    val localMasjid: BackupLocalMasjid? = null,
    val settings: BackupSettings = BackupSettings(),
    val blockedApps: List<BackupBlockedApp> = emptyList(),

    /**
     * Frozen monthly spiritual reflections (added after v1; defaults keep old
     * backups readable, ignoreUnknownKeys keeps old apps tolerant of new ones).
     */
    val monthlyReflections: List<com.salahlock.app.data.repository.StoredMonthlyReflection> = emptyList(),

    /** Reserved for future use — ignored by v1 restores. */
    val schemaExtras: String = "",
) {
    companion object {
        const val BACKUP_VERSION = 1
    }
}

@Serializable
data class BackupPrayerRecord(
    val id: Long = 0,
    val date: String,
    val prayerName: String,
    val verified: Boolean = false,
    val overrideUsed: Boolean = false,
    val verificationType: String = "NONE",
    val timestampMs: Long = 0,
)

@Serializable
data class BackupStreak(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastFullDay: String = "",
    val lastMercyWeek: String = "",
    val mercyUsedThisWeek: Boolean = false,
)

@Serializable
data class BackupEmergencyOverride(
    val id: Long = 0,
    val monthYear: String,
    val count: Int = 0,
    val lastReason: String = "",
    val lastUsedMs: Long = 0,
)

@Serializable
data class BackupLocalMasjid(
    val masjidName: String = "",
    val fajr: String = "",
    val dhuhr: String = "",
    val asr: String = "",
    val maghrib: String = "",
    val isha: String = "",
    val enabled: Boolean = false,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupBlockedApp(
    val packageName: String,
    val appLabel: String,
    val isBlocked: Boolean = true,
)

@Serializable
data class BackupSettings(
    val themePreference: String = "SYSTEM",
    val calcMethod: String = "KARACHI",
    val madhab: String = "HANAFI",
    val lockDurationMin: Int = 30,
    val adhanEnabled: Boolean = true,
    val lockFajr: Boolean = true,
    val lockDhuhr: Boolean = true,
    val lockAsr: Boolean = true,
    val lockMaghrib: Boolean = true,
    val lockIsha: Boolean = true,
    val blockProfile: String = "CUSTOM",
    val prayerSource: String = "API",
    val verificationMethod: String = "ASK_EVERY_TIME",
    val verificationConfirmCount: Int = 3,
    val reminderQuranEnabled: Boolean = true,
    val reminderHadithEnabled: Boolean = true,
    val reminderReflectionEnabled: Boolean = true,
    val autoBackupFrequency: String = "DISABLED",
)

// ─────────────────────────────────────────────────────────────────────────────
// Entity ↔ DTO conversions
// ─────────────────────────────────────────────────────────────────────────────

fun PrayerRecord.toBackup() = BackupPrayerRecord(
    id = id, date = date, prayerName = prayerName,
    verified = verified, overrideUsed = overrideUsed,
    verificationType = verificationType, timestampMs = timestampMs,
)
fun BackupPrayerRecord.toEntity() = PrayerRecord(
    id = 0, date = date, prayerName = prayerName,
    verified = verified, overrideUsed = overrideUsed,
    verificationType = verificationType, timestampMs = timestampMs,
)

fun StreakEntity.toBackup() = BackupStreak(
    currentStreak = currentStreak, bestStreak = bestStreak,
    lastFullDay = lastFullDay, lastMercyWeek = lastMercyWeek,
    mercyUsedThisWeek = mercyUsedThisWeek,
)
fun BackupStreak.toEntity() = StreakEntity(
    id = 1, currentStreak = currentStreak, bestStreak = bestStreak,
    lastFullDay = lastFullDay, lastMercyWeek = lastMercyWeek,
    mercyUsedThisWeek = mercyUsedThisWeek,
)

fun EmergencyOverride.toBackup() = BackupEmergencyOverride(
    id = id, monthYear = monthYear, count = count,
    lastReason = lastReason, lastUsedMs = lastUsedMs,
)
fun BackupEmergencyOverride.toEntity() = EmergencyOverride(
    id = 0, monthYear = monthYear, count = count,
    lastReason = lastReason, lastUsedMs = lastUsedMs,
)

fun LocalMasjidEntity.toBackup() = BackupLocalMasjid(
    masjidName = masjidName, fajr = fajr, dhuhr = dhuhr, asr = asr,
    maghrib = maghrib, isha = isha, enabled = enabled, updatedAt = updatedAt,
)
fun BackupLocalMasjid.toEntity() = LocalMasjidEntity(
    id = 1, masjidName = masjidName, fajr = fajr, dhuhr = dhuhr, asr = asr,
    maghrib = maghrib, isha = isha, enabled = enabled, updatedAt = updatedAt,
)

fun AppBlacklistItem.toBackup() = BackupBlockedApp(
    packageName = packageName, appLabel = appLabel, isBlocked = isBlocked,
)
fun BackupBlockedApp.toEntity() = AppBlacklistItem(
    id = 0, packageName = packageName, appLabel = appLabel, isBlocked = isBlocked,
)
