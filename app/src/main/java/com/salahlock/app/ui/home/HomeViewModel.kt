package com.salahlock.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.model.DailyPrayers
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.data.model.PrayerTime
import com.salahlock.app.data.model.StreakInfo
import com.salahlock.app.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class LockSummary(
    val blockedAppCount: Int = 0,
    val todayBlockedCount: Int = 0,
    val remainingOverrides: Int = 3,
    val activeProfile: String = "Custom",
    val lastVerificationLabel: String = "None today",
)

data class HomeUiState(
    val todayPrayers: DailyPrayers? = null,
    val nextPrayer: PrayerTime? = null,
    val currentPrayer: PrayerTime? = null,
    val streakInfo: StreakInfo = StreakInfo(),
    val isLockActive: Boolean = false,
    val isOffline: Boolean = false,
    val currentTimeMs: Long = System.currentTimeMillis(),
    val locationMissing: Boolean = false,
    val cityName: String = "",
    val userName: String = "",
    /** Signed-in user's Google photo URL, or null when signed out (BM-008.1). */
    val userPhotoUrl: String? = null,
    val today: String = LocalDate.now().toString(),
    /** Pre-computed map of prayer name → isPast (avoids per-frame LocalDateTime.now()) */
    val isPastMap: Map<PrayerName, Boolean> = emptyMap(),
    /** Today's prayer completion records keyed by PrayerName */
    val todayRecords: Map<PrayerName, PrayerRecord> = emptyMap(),
    /** Missing critical permissions (overlay + usage stats) */
    val missingCriticalPermissions: Boolean = false,
    /** Missing battery optimization exemption */
    val batteryOptimizationNeeded: Boolean = false,
    /** Compact lock summary for the home dashboard card */
    val lockSummary: LockSummary = LockSummary(),
    /** Sprint E.1 — masjid name when LOCAL_MASJID source is active; blank otherwise */
    val activeMasjidName: String = "",
    /** Sprint N.3 — lock engine paused until this epoch-millis (0 = not paused) */
    val pauseUntilMs: Long = 0L,
    /** Sprint N.3 — Qibla bearing in whole degrees for the Home dashboard card */
    val qiblaBearing: Int = 0,
    /** SL-004 — Jumma 1 time "HH:mm" for the masjid card (blank = not set) */
    val jummaTime: String = "",
    /** BM-008.2 — the day's intention, rotated deterministically by date. */
    val dailyIntention: String = "",
) {
    val isPaused: Boolean get() = pauseUntilMs > currentTimeMs
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val locationHelper = com.salahlock.app.util.LocationHelper(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(dailyIntention = com.salahlock.app.spiritual.DailyIntention.forDate()) }
        observeCityName()
        observeTodayPrayers()
        observeStreak()
        observeTodayRecords()
        tickClock()
        checkLockStatus()
        checkPermissions()
        startLocationPolling()
        observeLockSummary()
        observeUserIdentity()
        observeMasjidName()
        observePause()
        observeQiblaBearing()
    }

    private fun observePause() {
        viewModelScope.launch {
            app.userPreferences.pauseUntil.collect { until ->
                _uiState.update { it.copy(pauseUntilMs = until) }
            }
        }
        viewModelScope.launch {
            app.userPreferences.jumma1.collect { j ->
                _uiState.update { it.copy(jummaTime = j) }
            }
        }
    }

    private fun observeQiblaBearing() {
        viewModelScope.launch {
            combine(app.userPreferences.userLat, app.userPreferences.userLng) { lat, lng ->
                lat to lng
            }.collect { (lat, lng) ->
                if (lat != 0.0 || lng != 0.0) {
                    val bearing = com.batoulapps.adhan2.Qibla(
                        com.batoulapps.adhan2.Coordinates(lat, lng)
                    ).direction.toInt()
                    _uiState.update { it.copy(qiblaBearing = bearing) }
                }
            }
        }
    }

    // ── Pause SalahLock (Sprint N.3) ──────────────────────────────────────────
    /** Pauses the lock engine for [minutes] from now. */
    fun pauseForMinutes(minutes: Int) {
        viewModelScope.launch {
            app.userPreferences.setPauseUntil(System.currentTimeMillis() + minutes * 60_000L)
        }
    }

    /** Pauses until the next prayer time (falls back to a 30-min pause if unknown). */
    fun pauseUntilNextPrayer() {
        viewModelScope.launch {
            val next = _uiState.value.nextPrayer?.time
            val untilMs = next
                ?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: (System.currentTimeMillis() + 30 * 60_000L)
            app.userPreferences.setPauseUntil(untilMs)
        }
    }

    /** Resumes the lock engine immediately. */
    fun resumePause() {
        viewModelScope.launch { app.userPreferences.setPauseUntil(0L) }
    }

    /**
     * Marks [prayer] as prayed for today directly from Home.
     * Reuses [StreakRepository.recordVerification] — the exact same path the lock
     * overlay records through — so verification stays single-source-of-truth (Room).
     */
    fun verifyPrayer(prayer: PrayerName) {
        viewModelScope.launch {
            app.streakRepository.recordVerification(
                date = LocalDate.now().toString(),
                prayer = prayer,
                wasOverride = false,
            )
        }
    }

    private fun observeMasjidName() {
        viewModelScope.launch {
            app.prayerSourceRepository.getActiveMasjidName().collect { name ->
                _uiState.update { it.copy(activeMasjidName = name ?: "") }
            }
        }
    }

    private fun observeUserIdentity() {
        viewModelScope.launch {
            app.userIdentity.displayName.collect { name ->
                _uiState.update { it.copy(userName = name) }
            }
        }
        viewModelScope.launch {
            app.userIdentity.photoUrl.collect { url ->
                _uiState.update { it.copy(userPhotoUrl = url) }
            }
        }
    }

    private fun observeCityName() {
        viewModelScope.launch {
            app.networkObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOffline = !online) }
            }
        }
        viewModelScope.launch {
            app.userPreferences.cityName.collect { city ->
                _uiState.update { it.copy(cityName = city) }
            }
        }
    }

    private fun startLocationPolling() {
        viewModelScope.launch {
            while (true) {
                fetchLocationIfPermitted()
                delay(4 * 60 * 60 * 1000L) // 4 hours
            }
        }
    }

    fun triggerLocationFetch() {
        viewModelScope.launch {
            fetchLocationIfPermitted()
        }
    }

    private suspend fun fetchLocationIfPermitted() {
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCoarse || hasFine) {
            val locationData = locationHelper.getCurrentLocation()
            if (locationData != null) {
                app.userPreferences.setLocation(
                    lat = locationData.lat,
                    lng = locationData.lng,
                    city = locationData.cityName,
                    mode = "GPS"
                )
            }
        }
    }

    private fun observeTodayPrayers() {
        viewModelScope.launch {
            combine(
                app.prayerTimesRepository.getTodayPrayers(),
                app.prayerTimesRepository.getTomorrowFajr(),
            ) { daily, tomorrowFajr -> Pair(daily, tomorrowFajr) }
                .collect { (daily, tomorrowFajr) ->
                    val locationMissing = daily == null
                    val now = LocalDateTime.now()
                    val nextPrayer = daily?.nextPrayerWithTomorrow(now, tomorrowFajr)
                    val currentPrayer = daily?.currentPrayer(now)
                    val isPastMap = buildIsPastMap(daily, now)
                    _uiState.update {
                        it.copy(
                            todayPrayers = daily,
                            nextPrayer = nextPrayer,
                            currentPrayer = currentPrayer,
                            locationMissing = locationMissing,
                            isPastMap = isPastMap,
                        )
                    }
                }
        }
    }

    private fun observeStreak() {
        viewModelScope.launch {
            app.streakRepository.observeStreakInfo().collect { info ->
                _uiState.update { it.copy(streakInfo = info) }
            }
        }
    }

    private fun observeTodayRecords() {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            app.database.prayerRecordDao().observeRecordsForDate(today).collect { records ->
                val byName = records.associateBy { PrayerName.valueOf(it.prayerName) }
                _uiState.update { it.copy(todayRecords = byName) }
            }
        }
    }

    /**
     * SL-021: suspends until the UI is actually collecting [uiState]. HomeScreen
     * collects with collectAsStateWithLifecycle, so subscriptions drop to zero
     * when the app is backgrounded — parking every polling loop here stops all
     * timer work (and its battery cost) while nothing is on screen.
     */
    private suspend fun awaitUiVisible() {
        _uiState.subscriptionCount.first { it > 0 }
    }

    private fun tickClock() {
        viewModelScope.launch {
            while (true) {
                awaitUiVisible()
                val now = LocalDateTime.now()
                _uiState.update { state ->
                    val tomorrowFajr = state.nextPrayer?.takeIf {
                        it.name == PrayerName.FAJR && state.todayPrayers?.nextPrayer(now) == null
                    }?.time
                    state.copy(
                        currentTimeMs = System.currentTimeMillis(),
                        nextPrayer = state.todayPrayers?.nextPrayerWithTomorrow(now, tomorrowFajr)
                            ?: state.nextPrayer,
                        currentPrayer = state.todayPrayers?.currentPrayer(now),
                        isPastMap = buildIsPastMap(state.todayPrayers, now),
                    )
                }
                // SL-002/SL-014: countdowns display seconds, so the shared clock must
                // tick every second — 10s caused visible 00:50→00:30 jumps. This state
                // (currentTimeMs) is the single timer source for all Home countdowns.
                delay(1_000L)
            }
        }
    }

    private fun checkLockStatus() {
        viewModelScope.launch {
            while (true) {
                awaitUiVisible()
                _uiState.update {
                    it.copy(isLockActive = com.salahlock.app.service.UsageStatsPollingService.isRunning)
                }
                delay(5_000L)
            }
        }
    }

    private fun checkPermissions() {
        viewModelScope.launch {
            while (true) {
                awaitUiVisible()
                val ctx = app.applicationContext
                _uiState.update {
                    it.copy(
                        missingCriticalPermissions = !PermissionHelper.hasCriticalPermissions(ctx),
                        batteryOptimizationNeeded = !PermissionHelper.isBatteryOptimizationIgnored(ctx),
                    )
                }
                delay(30_000L) // Check every 30 seconds — infrequent enough not to be expensive
            }
        }
    }

    private fun observeLockSummary() {
        viewModelScope.launch {
            combine(
                app.blacklistRepository.observeBlockedCount(),
                app.blacklistRepository.observeBlockedPackages(),
                app.userPreferences.blockProfile,
            ) { blockedCount, _, profile -> Pair(blockedCount, profile) }
                .collect { (blockedCount, profileName) ->
                    val today = LocalDate.now().toString()
                    val todayRecords = app.database.prayerRecordDao().getRecordsForDate(today)
                    val verifiedToday = todayRecords.filter { it.verified || it.overrideUsed }
                    val lastVerifiedLabel = if (verifiedToday.isEmpty()) "None today"
                        else "${verifiedToday.last().prayerName.lowercase().replaceFirstChar { it.uppercase() }} verified"
                    val overrideState = app.overrideRepository.getOverrideState()

                    _uiState.update {
                        it.copy(
                            lockSummary = LockSummary(
                                blockedAppCount = blockedCount,
                                todayBlockedCount = verifiedToday.size,
                                remainingOverrides = overrideState.maxPerMonth - overrideState.usedThisMonth,
                                activeProfile = com.salahlock.app.data.model.BlockProfile.fromName(profileName).displayName,
                                lastVerificationLabel = lastVerifiedLabel,
                            )
                        )
                    }
                }
        }
    }

    private fun buildIsPastMap(daily: DailyPrayers?, now: LocalDateTime): Map<PrayerName, Boolean> {
        if (daily == null) return emptyMap()
        return daily.toList().associate { it.name to it.time.isBefore(now) }
    }
}
