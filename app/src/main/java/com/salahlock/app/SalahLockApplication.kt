package com.salahlock.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.preferences.UserIdentityPreferences
import com.salahlock.app.data.preferences.UserPreferences
import com.salahlock.app.data.repository.AppBlacklistRepository
import com.salahlock.app.data.repository.EmergencyOverrideRepository
import com.salahlock.app.data.repository.KnowledgeRepository
import com.salahlock.app.data.backup.BackupRepository
import com.salahlock.app.data.repository.PrayerSourceRepository
import com.salahlock.app.data.repository.PrayerTimesRepository
import com.salahlock.app.data.repository.StreakRepository
import com.salahlock.app.data.api.ApiClient
import com.salahlock.app.util.NotificationHelper
import com.salahlock.app.work.AlarmRefreshWorker
import com.salahlock.app.util.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SalahLockApplication : Application(), Configuration.Provider {

    // Application-scoped coroutine scope for one-time init work
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Manual dependency injection — no Hilt for MVP
    val database by lazy { AppDatabase.getInstance(this) }
    val userPreferences by lazy { UserPreferences(this) }
    val userIdentity by lazy { UserIdentityPreferences(this) }
    val networkObserver by lazy { NetworkConnectivityObserver(this) }

    val prayerSourceRepository by lazy {
        PrayerSourceRepository(userPreferences, database.localMasjidDao())
    }

    val backupRepository by lazy {
        BackupRepository(this, database, userPreferences, spiritualReportRepository)
    }

    /** Frozen monthly reflections — generated locally, stored in filesDir/reflections. */
    val spiritualReportRepository by lazy {
        com.salahlock.app.data.repository.SpiritualReportRepository(
            this, database.prayerRecordDao(), database.emergencyOverrideDao(),
        )
    }

    val prayerTimesRepository by lazy {
        PrayerTimesRepository(
            this,
            userPreferences,
            database.prayerTimeCacheDao(),
            ApiClient.aladhanApiService,
            database.localMasjidDao(),
        )
    }
    val streakRepository by lazy {
        StreakRepository(database.prayerRecordDao(), database.streakDao())
    }
    val blacklistRepository by lazy {
        AppBlacklistRepository(this, database.appBlacklistDao())
    }
    val overrideRepository by lazy {
        EmergencyOverrideRepository(database.emergencyOverrideDao())
    }

    /** Singleton — prevents Hadith sync from re-running on every Knowledge screen visit. */
    val knowledgeRepository by lazy { KnowledgeRepository(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Register all notification channels at startup — MUST happen before any alarm fires
        NotificationHelper.createChannels(this)
        Log.d("SalahLockApp", "Notification channels registered.")

        // Initialize AdMob and preload the single daily interstitial (Sprint A.1).
        // Fails open — if init/load fails the app is unaffected.
        // SL-021: moved off the main thread — MobileAds.initialize() officially
        // supports background-thread init and costs 100ms+ of cold start otherwise.
        appScope.launch {
            runCatching {
                com.google.android.gms.ads.MobileAds.initialize(this@SalahLockApplication) {
                    com.salahlock.app.ads.DailyInterstitialManager.preload(this@SalahLockApplication)
                }
            }
        }

        // Schedule the periodic alarm refresh worker
        AlarmRefreshWorker.schedule(this)

        // On first launch: populate app blacklist with installed apps
        appScope.launch {
            try {
                val onboardingDone = userPreferences.onboardingDone.first()
                if (onboardingDone) {
                    // Sync installed apps (adds newly installed, preserves existing toggle states)
                    blacklistRepository.syncInstalledApps()
                }
            } catch (e: Exception) {
                Log.e("SalahLockApp", "Failed to sync installed apps: ${e.message}")
            }
        }

        // Generate any missing closed-month spiritual reports (idempotent, offline).
        // Running at every launch makes generation survive reboots and app updates.
        appScope.launch {
            try {
                spiritualReportRepository.ensureReportsUpToDate()
            } catch (e: Exception) {
                Log.e("SalahLockApp", "Monthly reflection generation failed: ${e.message}")
            }
        }
    }
}
