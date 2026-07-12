package com.salahlock.app.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.salahlock.app.data.db.dao.AppBlacklistDao
import com.salahlock.app.data.db.entity.AppBlacklistItem
import com.salahlock.app.data.model.AppCategory
import com.salahlock.app.data.model.BlockProfile
import com.salahlock.app.data.model.detectCategory
import kotlinx.coroutines.flow.Flow

// Packages that are never blocked (safety / Islamic utility apps)
val ALWAYS_WHITELISTED = setOf(
    "com.android.dialer",
    "com.samsung.android.dialer",
    "com.android.contacts",
    "com.google.android.contacts",
    "com.android.phone",
    "com.android.settings",
    "com.samsung.android.settings",
    "com.quran.labs.androidquran",
    "com.greentech.quran",
    "com.bitsmedia.android.muslimpro",
    "com.islamicfinder.app",
    "com.aedansoftware.quranpro",
    "com.salahlock.app",
    "com.android.emergency",
    "com.google.android.apps.emergency",
)

// Pre-suggested apps blocked on first install
private val DEFAULT_BLOCKED = setOf(
    "com.instagram.android",
    "com.google.android.youtube",
    "com.twitter.android",
    "com.x.android",
    "com.zhiliaoapp.musically",
    "com.ss.android.ugc.trill",
    "com.whatsapp",
    "com.android.chrome",
    "com.google.android.apps.chrome",
    "org.telegram.messenger",
    "com.facebook.katana",
    "com.snapchat.android",
    "com.netflix.mediaclient",
    "com.reddit.frontpage",
    "in.mohalla.sharechat",
    "com.jio.jioplay.tv",
    "com.spotify.music",
    "com.pinterest",
    "com.linkedin.android",
    "tv.twitch.android.app",
)

/** Enriched app metadata for the UI — not persisted in Room. */
data class InstalledAppInfo(
    val packageName: String,
    val appLabel: String,
    val category: AppCategory,
    val firstInstallTime: Long,
    val isBlocked: Boolean,
)

class AppBlacklistRepository(
    private val context: Context,
    private val dao: AppBlacklistDao,
) {
    fun observeAll(): Flow<List<AppBlacklistItem>> = dao.observeAll()
    fun observeBlockedPackages(): Flow<List<String>> = dao.observeBlockedPackageNames()
    fun observeBlockedCount(): Flow<Int> = dao.observeBlockedCount()

    suspend fun getBlockedPackages(): List<String> =
        dao.getBlockedPackages().map { it.packageName }

    suspend fun toggle(item: AppBlacklistItem) {
        if (ALWAYS_WHITELISTED.contains(item.packageName)) return
        // Use package-name SQL update — avoids relying on the entity's auto-generated id,
        // which may be 0 when called from the UI before the DB observer has synced ids.
        dao.setBlockedForPackages(listOf(item.packageName), !item.isBlocked)
    }

    suspend fun setBlocked(item: AppBlacklistItem, blocked: Boolean) {
        if (ALWAYS_WHITELISTED.contains(item.packageName)) return
        dao.setBlockedForPackages(listOf(item.packageName), blocked)
    }

    /** Block all installed apps that are not whitelisted. */
    suspend fun blockAll() {
        val allPackages = dao.getAllSync()
            .filter { !ALWAYS_WHITELISTED.contains(it.packageName) }
            .map { it.packageName }
        if (allPackages.isNotEmpty()) {
            dao.setBlockedForPackages(allPackages, true)
        }
    }

    /** Unblock all apps (except whitelisted). */
    suspend fun unblockAll() {
        dao.unblockAll()
    }

    /**
     * Blocks all installed apps in the given [category].
     * Only affects apps already in the DB — call [syncInstalledApps] first to ensure they exist.
     */
    suspend fun blockCategory(category: AppCategory) {
        val installed = getInstalledAppsWithCategory()
        val toBlock = installed
            .filter { it.category == category && !ALWAYS_WHITELISTED.contains(it.packageName) }
            .map { it.packageName }
        if (toBlock.isNotEmpty()) {
            dao.setBlockedForPackages(toBlock, true)
        }
    }

    /**
     * Applies a [BlockProfile] by blocking apps whose category matches the profile's
     * blockedCategories, and unblocking all others (except whitelisted).
     * CUSTOM profile is a no-op.
     */
    suspend fun applyProfile(profile: BlockProfile) {
        if (profile == BlockProfile.CUSTOM) return
        val installed = getInstalledAppsWithCategory()
        val toBlock = installed
            .filter { it.category in profile.blockedCategories && !ALWAYS_WHITELISTED.contains(it.packageName) }
            .map { it.packageName }
        val toUnblock = installed
            .filter { it.category !in profile.blockedCategories && !ALWAYS_WHITELISTED.contains(it.packageName) }
            .map { it.packageName }

        dao.unblockAll()
        if (toBlock.isNotEmpty()) {
            dao.setBlockedForPackages(toBlock, true)
        }
    }

    /**
     * Returns installed launchable apps enriched with category and install-time metadata.
     * Reads from PackageManager + compares against DB for current blocked state.
     */
    suspend fun getInstalledAppsWithCategory(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val blockedSet = dao.getBlockedPackages().map { it.packageName }.toSet()

        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { info ->
                pm.getLaunchIntentForPackage(info.packageName) != null &&
                        !ALWAYS_WHITELISTED.contains(info.packageName)
            }
            .map { info ->
                val pkgInfo: PackageInfo? = try {
                    pm.getPackageInfo(info.packageName, 0)
                } catch (_: PackageManager.NameNotFoundException) { null }

                InstalledAppInfo(
                    packageName = info.packageName,
                    appLabel = pm.getApplicationLabel(info).toString(),
                    category = detectCategory(info.packageName, info.category),
                    firstInstallTime = pkgInfo?.firstInstallTime ?: 0L,
                    isBlocked = blockedSet.contains(info.packageName),
                )
            }
    }

    /** Re-syncs installed apps, adding new ones without changing existing toggle states. */
    suspend fun syncInstalledApps() {
        val pm = context.packageManager
        val items = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { info ->
                pm.getLaunchIntentForPackage(info.packageName) != null &&
                        !ALWAYS_WHITELISTED.contains(info.packageName)
            }
            .map { info ->
                AppBlacklistItem(
                    packageName = info.packageName,
                    appLabel = pm.getApplicationLabel(info).toString(),
                    isBlocked = DEFAULT_BLOCKED.contains(info.packageName),
                )
            }
        // IGNORE strategy — existing rows with matching packageName are not touched.
        dao.insertAll(items)
    }

    suspend fun populateInstalledApps() = syncInstalledApps()

    /**
     * Replaces the current block selection with exactly [blockedPackages].
     * Used by onboarding's "Choose Apps To Lock" step — writes straight into the
     * same Room-backed blacklist the rest of the app reads, so no separate storage.
     * Ensures rows exist first, then blocks only the chosen (non-whitelisted) apps.
     */
    suspend fun applySelection(blockedPackages: Set<String>) {
        syncInstalledApps()
        dao.unblockAll()
        val toBlock = blockedPackages.filterNot { ALWAYS_WHITELISTED.contains(it) }
        if (toBlock.isNotEmpty()) {
            dao.setBlockedForPackages(toBlock, true)
        }
    }

    fun isWhitelisted(packageName: String): Boolean = ALWAYS_WHITELISTED.contains(packageName)
}
