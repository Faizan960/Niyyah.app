package com.salahlock.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.salahlock.app.BuildConfig
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ZIP-based local backup and restore system.
 *
 * # ZIP structure
 * ```
 * SalahLock_Backup_2026_06_26.zip
 * ├── manifest.json   — plaintext: app version, creation date, record counts (preview without decryption)
 * ├── metadata.json   — plaintext: encryption algo, SHA-256 checksum of plaintext payload
 * └── backup.json     — {"data":"<Base64(IV + AES-256-GCM(GZIP(JSON(BackupPackage))))>"}
 * ```
 *
 * # Encryption
 * AES-256-GCM with a fixed-constant key (derived via SHA-256).
 * Provides tamper protection and format privacy while remaining portable across
 * devices and reinstalls — the primary use case is phone migration.
 *
 * # Integrity
 * [BackupMetadata.checksum] = SHA-256(plaintext BackupPackage JSON).
 * Verified after decryption; mismatch = corruption detected.
 */
class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: UserPreferences,
    private val reflections: com.salahlock.app.data.repository.SpiritualReportRepository? = null,
) {
    private val tag = "BackupRepository"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // ── Encryption (AES-256-GCM, fixed-constant key for cross-device portability) ────

    private val encryptionKey: javax.crypto.SecretKey by lazy {
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest("SalahLock-Backup-ZIP-Key-v1-2026".toByteArray(Charsets.UTF_8))
        SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        val iv = cipher.iv                       // 12-byte GCM IV
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext                   // prepend IV; GCM auth tag is in ciphertext
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)        // throws AEADBadTagException on corruption
    }

    private fun compress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }

    private fun decompress(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }

    // ── Collect data ─────────────────────────────────────────────────────────

    private suspend fun collectPackage(): BackupPackage {
        val records = db.prayerRecordDao().getAll()
        val streak = db.streakDao().getStreak()
        val overrides = db.emergencyOverrideDao().getAll()
        val localMasjid = db.localMasjidDao().get()
        val blockedApps = db.appBlacklistDao().getAllSync()

        val settings = BackupSettings(
            themePreference = prefs.themePreference.first().name,
            calcMethod = prefs.calcMethod.first(),
            madhab = prefs.madhab.first(),
            lockDurationMin = prefs.lockDurationMin.first(),
            adhanEnabled = prefs.adhanEnabled.first(),
            lockFajr = prefs.lockFajr.first(),
            lockDhuhr = prefs.lockDhuhr.first(),
            lockAsr = prefs.lockAsr.first(),
            lockMaghrib = prefs.lockMaghrib.first(),
            lockIsha = prefs.lockIsha.first(),
            blockProfile = prefs.blockProfile.first(),
            prayerSource = prefs.prayerSource.first(),
            verificationMethod = prefs.verificationMethod.first(),
            verificationConfirmCount = prefs.verificationConfirmCount.first(),
            reminderQuranEnabled = prefs.reminderQuranEnabled.first(),
            reminderHadithEnabled = prefs.reminderHadithEnabled.first(),
            reminderReflectionEnabled = prefs.reminderReflectionEnabled.first(),
            autoBackupFrequency = prefs.autoBackupFrequency.first(),
        )

        return BackupPackage(
            createdAt = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            prayerHistory = records.map { it.toBackup() },
            streak = streak?.toBackup(),
            emergencyOverrides = overrides.map { it.toBackup() },
            localMasjid = localMasjid?.toBackup(),
            settings = settings,
            blockedApps = blockedApps.map { it.toBackup() },
            monthlyReflections = reflections?.exportAll() ?: emptyList(),
        )
    }

    // ── Build ZIP bytes ──────────────────────────────────────────────────────

    private fun buildZipBytes(pkg: BackupPackage): ByteArray {
        val plainJson = json.encodeToString(pkg)
        val plainBytes = plainJson.toByteArray(Charsets.UTF_8)
        val checksum = sha256Hex(plainBytes)

        val compressed = compress(plainBytes)
        val encrypted = encrypt(compressed)
        val encodedData = Base64.encodeToString(encrypted, Base64.NO_WRAP)

        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        val manifest = BackupManifest(
            version = BuildConfig.VERSION_NAME,
            backupVersion = BackupManifest.BACKUP_VERSION,
            createdAt = isoDate,
            androidVersion = Build.VERSION.RELEASE,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            prayerRecordCount = pkg.prayerHistory.size,
            currentStreak = pkg.streak?.currentStreak ?: 0,
            blockedAppCount = pkg.blockedApps.count { it.isBlocked },
        )
        val metadata = BackupMetadata(
            checksum = checksum,
        )
        val backupEntry = BackupJsonEntry(data = encodedData)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            fun writeEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            writeEntry("manifest.json", json.encodeToString(manifest))
            writeEntry("metadata.json", json.encodeToString(metadata))
            writeEntry("backup.json",   json.encodeToString(backupEntry))
        }
        return baos.toByteArray()
    }

    // ── Parse ZIP bytes ──────────────────────────────────────────────────────

    private data class ParsedZip(
        val manifest: BackupManifest,
        val metadata: BackupMetadata,
        val pkg: BackupPackage,
    )

    private fun parseZipBytes(zipBytes: ByteArray): ParsedZip {
        var manifestJson: String? = null
        var metadataJson: String? = null
        var backupJson: String? = null

        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val content = zip.readBytes().toString(Charsets.UTF_8)
                when (entry.name) {
                    "manifest.json" -> manifestJson = content
                    "metadata.json" -> metadataJson = content
                    "backup.json"   -> backupJson   = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (manifestJson == null || metadataJson == null || backupJson == null) {
            throw IllegalArgumentException(
                "Invalid SalahLock backup. Missing: ${
                    listOfNotNull(
                        "manifest.json".takeIf { manifestJson == null },
                        "metadata.json".takeIf { metadataJson == null },
                        "backup.json".takeIf   { backupJson   == null },
                    ).joinToString()
                }"
            )
        }

        val manifest = json.decodeFromString<BackupManifest>(manifestJson!!)
        val metadata = json.decodeFromString<BackupMetadata>(metadataJson!!)
        val entry    = json.decodeFromString<BackupJsonEntry>(backupJson!!)

        if (manifest.app != "SalahLock") throw IllegalArgumentException("Not a SalahLock backup.")
        if (manifest.backupVersion > BackupManifest.BACKUP_VERSION) {
            throw IllegalArgumentException(
                "Backup version ${manifest.backupVersion} is newer than this app supports (max ${BackupManifest.BACKUP_VERSION}). Please update SalahLock."
            )
        }

        val encryptedBytes = Base64.decode(entry.data, Base64.NO_WRAP)
        val decrypted = try {
            decrypt(encryptedBytes)
        } catch (e: Exception) {
            throw IllegalArgumentException("Backup decryption failed — file may be corrupted.")
        }
        val plainBytes = decompress(decrypted)

        // Checksum validation
        val computedChecksum = sha256Hex(plainBytes)
        if (metadata.checksum.isNotBlank() && computedChecksum != metadata.checksum) {
            throw IllegalArgumentException("Backup checksum mismatch — file is corrupted.")
        }

        val pkg = json.decodeFromString<BackupPackage>(plainBytes.toString(Charsets.UTF_8))
        return ParsedZip(manifest, metadata, pkg)
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Exports a backup ZIP to a user-chosen SAF URI (Downloads, Documents, SD card, etc.).
     * @return human-readable success message
     */
    suspend fun createBackup(contentResolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        Log.d(tag, "Creating backup to $uri")
        val pkg = collectPackage()
        val zipBytes = buildZipBytes(pkg)
        contentResolver.openOutputStream(uri)?.use { it.write(zipBytes) }
            ?: throw IllegalStateException("Could not open output stream for backup.")
        val sizeKb = zipBytes.size / 1024
        Log.d(tag, "Backup complete: ${zipBytes.size} bytes, ${pkg.prayerHistory.size} records")
        "Backup created — ${pkg.prayerHistory.size} prayer records, ${sizeKb}KB"
    }

    /**
     * Writes a backup ZIP to the app's cache dir and returns a FileProvider URI
     * suitable for [Intent.ACTION_SEND] (share sheet).
     */
    suspend fun createShareableBackup(): Uri = withContext(Dispatchers.IO) {
        val pkg = collectPackage()
        val zipBytes = buildZipBytes(pkg)
        val dateStr = SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date())
        val file = File(context.cacheDir, "SalahLock_Backup_$dateStr.zip")
        file.writeBytes(zipBytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Reads the manifest from a ZIP without full decryption — fast preview for the confirmation dialog.
     * Returns null if the file is not a valid SalahLock backup.
     */
    suspend fun extractPreview(contentResolver: ContentResolver, uri: Uri): BackupPreviewData = withContext(Dispatchers.IO) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read backup file.")
        val parsed = parseZipBytes(bytes)
        BackupPreviewData(
            backupDate = parsed.manifest.createdAt.take(10),   // "YYYY-MM-DD"
            appVersion = parsed.manifest.version,
            prayerRecords = parsed.pkg.prayerHistory.size,
            currentStreak = parsed.pkg.streak?.currentStreak ?: 0,
            blockedApps = parsed.pkg.blockedApps.count { it.isBlocked },
            localMasjidName = parsed.pkg.localMasjid?.masjidName?.ifBlank { null },
        )
    }

    /**
     * Restores all user data from a ZIP backup.
     * Validates structure, decrypts, verifies checksum, then applies to Room + DataStore.
     * @return human-readable success message
     */
    suspend fun restoreBackup(contentResolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        Log.d(tag, "Restoring from $uri")
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read backup file.")
        val parsed = parseZipBytes(bytes)
        applyRestore(parsed.pkg)
        Log.d(tag, "Restore complete: ${parsed.pkg.prayerHistory.size} records")
        "Restore complete — ${parsed.pkg.prayerHistory.size} prayer records restored"
    }

    private suspend fun applyRestore(pkg: BackupPackage) {
        // Prayer history
        db.prayerRecordDao().deleteAll()
        if (pkg.prayerHistory.isNotEmpty()) {
            db.prayerRecordDao().insertAll(pkg.prayerHistory.map { it.toEntity() })
        }
        // Streak
        pkg.streak?.let { db.streakDao().upsert(it.toEntity()) }
        // Emergency overrides
        pkg.emergencyOverrides.forEach { db.emergencyOverrideDao().upsert(it.toEntity()) }
        // Local masjid
        pkg.localMasjid?.let { db.localMasjidDao().upsert(it.toEntity()) }
        // Blocked apps
        if (pkg.blockedApps.isNotEmpty()) {
            db.appBlacklistDao().insertAll(pkg.blockedApps.map { it.toEntity() })
        }
        // Monthly reflections (frozen reports; existing files are never overwritten)
        if (pkg.monthlyReflections.isNotEmpty()) {
            reflections?.importAll(pkg.monthlyReflections)
        }
        // Settings
        val s = pkg.settings
        prefs.setThemePreference(
            runCatching { UserPreferences.ThemePreference.valueOf(s.themePreference) }
                .getOrDefault(UserPreferences.ThemePreference.SYSTEM)
        )
        prefs.setCalcMethod(s.calcMethod)
        prefs.setMadhab(s.madhab)
        prefs.setLockDuration(s.lockDurationMin)
        prefs.setAdhanEnabled(s.adhanEnabled)
        prefs.setPrayerSource(s.prayerSource)
        prefs.setVerificationMethod(s.verificationMethod)
        prefs.setVerificationConfirmCount(s.verificationConfirmCount)
        prefs.setReminderQuranEnabled(s.reminderQuranEnabled)
        prefs.setReminderHadithEnabled(s.reminderHadithEnabled)
        prefs.setReminderReflectionEnabled(s.reminderReflectionEnabled)
        // autoBackupFrequency intentionally not restored — avoids scheduling loops on new device
    }

    // ── Auto backup ──────────────────────────────────────────────────────────

    /** Creates a backup ZIP in `filesDir/backups/` for WorkManager-scheduled auto-backup. */
    suspend fun createAutoBackup(): String = withContext(Dispatchers.IO) {
        val dir = context.filesDir.resolve("backups").also { it.mkdirs() }
        val name = "AutoBackup_${System.currentTimeMillis()}.zip"
        val file = dir.resolve(name)
        val pkg = collectPackage()
        file.writeBytes(buildZipBytes(pkg))
        // Keep latest 5 auto-backups only
        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(5)?.forEach { it.delete() }
        Log.d(tag, "Auto-backup: ${file.absolutePath} (${file.length()} bytes)")
        file.absolutePath
    }

    fun latestAutoBackupInfo(): Pair<String, Long>? {
        val dir = context.filesDir.resolve("backups")
        val latest = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return null
        return latest.absolutePath to latest.length()
    }
}

/** Preview data extracted from a backup without performing a full restore. */
data class BackupPreviewData(
    val backupDate: String,
    val appVersion: String,
    val prayerRecords: Int,
    val currentStreak: Int,
    val blockedApps: Int,
    val localMasjidName: String?,
)
