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
    /** BM-HOME-PRAYER-UX — the active prayer-time source preference */
    val prayerSource: com.salahlock.app.data.model.PrayerSource =
        com.salahlock.app.data.model.PrayerSource.API,
    /** BM-HOME-PRAYER-UX — saved masjid name regardless of active source (blank = none) */
    val savedMasjidName: String = "",
    /** BM-HOME-PRAYER-UX — whether a Local Masjid timetable has been configured */
    val localMasjidConfigured: Boolean = false,
) {
    val isPaused: Boolean get() = pauseUntilMs > currentTimeMs
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val locationHelper = com.salahlock.app.util.LocationHelper(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
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

    // ── Pause SalahLock ───────────────────────────────────────────────────────
    // BM-011: pause *initiation* moved to the Lock Apps tab (BlacklistViewModel).
    // Home only needs to resume, surfaced via the "paused" hairline alert.
    /** Resumes the lock engine immediately. */
    fun resumePause() {
        viewModelScope.launch { app.userPreferences.setPauseUntil(0L) }
    }

    /**
     * BM-HOME-PRAYER-UX — switches the active prayer-time source.
     *
     * One authoritative preference ([PrayerSourceRepository]); switching never deletes
     * the saved masjid or the GPS/calculation config. Lock alarms are refreshed
     * immediately so Home, Next Prayer, Today's Prayers and Salah Lock scheduling all
     * consume the same effective schedule (no GPS-here / Masjid-there mismatch).
     */
    fun setPrayerSource(source: com.salahlock.app.data.model.PrayerSource) {
        viewModelScope.launch {
            app.prayerSourceRepository.setPrayerSource(source)
            com.salahlock.app.work.AlarmRefreshWorker.runNow(app)
        }
    }

    private fun observeMasjidName() {
        // Home header / hero location line — masjid name only when LOCAL_MASJID is active.
        viewModelScope.launch {
            app.prayerSourceRepository.getActiveMasjidName().collect { name ->
                _uiState.update { it.copy(activeMasjidName = name ?: "") }
            }
        }
        // Saved masjid + configured flag — independent of the active source, so the
        // Local Masjid card and source sheet can show it even while GPS is active.
        viewModelScope.launch {
            app.prayerSourceRepository.getLocalMasjid().collect { masjid ->
                _uiState.update {
                    it.copy(
                        savedMasjidName = masjid?.masjidName ?: "",
                        localMasjidConfigured = !masjid?.masjidName.isNullOrBlank(),
                    )
                }
            }
        }
        // The active source preference itself.
        viewModelScope.launch {
            app.prayerSourceRepository.getPrayerSource().collect { source ->
                _uiState.update { it.copy(prayerSource = source) }
            }
        }
    }

    private fun observeUserIdentity() {
        // BM-AUTH-001: greeting name comes from the Clerk-backed single auth state.
        viewModelScope.launch {
            app.authRepository.state.collect { state ->
                val name = (state as? com.salahlock.app.auth.AuthState.SignedIn)?.user?.name ?: ""
                _uiState.update { it.copy(userName = name) }
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
            val owner = com.salahlock.app.data.sync.ActiveOwnerProvider.shared.ownerId()
            app.database.prayerRecordDao().observeRecordsForDate(owner, today).collect { records ->
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
                    val owner = com.salahlock.app.data.sync.ActiveOwnerProvider.shared.ownerId()
                    val todayRecords = app.database.prayerRecordDao().getRecordsForDate(owner, today)
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
