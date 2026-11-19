package com.salahlock.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.salahlock.app.MainActivity
import com.salahlock.app.R
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.ui.lock.LockOverlayActivity
import com.salahlock.app.util.NotificationHelper
import com.salahlock.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val TAG = "UsageStatsPollingService"
private const val LOCK_STATE_PREFS = "salahlock_active_lock"
private const val KEY_PRAYER = "prayer_name"
private const val KEY_END_MS = "lock_end_ms"
private const val KEY_GENERATION = "schedule_generation"

internal fun isDuplicateLockStart(
    active: Boolean,
    currentPrayer: PrayerName,
    incomingPrayer: PrayerName,
    currentGeneration: Long,
    incomingGeneration: Long,
    currentEndMs: Long,
    nowMs: Long,
): Boolean = active && currentPrayer == incomingPrayer &&
    currentGeneration == incomingGeneration && nowMs < currentEndMs

/**
 * The core lock engine.
 *
 * Runs ONLY during active prayer lock windows — not 24/7.
 * Polls UsageStatsManager every 500ms to detect the foreground app.
 * If a blacklisted app is detected, launches LockOverlayActivity.
 * Automatically stops when the lock window duration expires.
 *
 * START_STICKY reliability: the active lock state (prayer name + end time) is
 * persisted to SharedPreferences so it can be recovered if the OS kills and
 * restarts the service with a null intent.
 */
class UsageStatsPollingService : Service() {

    companion object {
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_LOCK_DURATION_MIN = "lock_duration_min"
        const val EXTRA_SCHEDULE_GENERATION = "schedule_generation"
        val CHANNEL_ID get() = NotificationHelper.LOCK_CHANNEL_ID
        const val NOTIFICATION_ID = 1001

        @Volatile var isRunning = false

        // --- Lock state persistence (SharedPreferences — synchronous, no coroutine needed) ---
        private fun saveLockState(context: Context, prayer: String, endMs: Long, generation: Long) {
            lockPrefs(context).edit()
                .putString(KEY_PRAYER, prayer)
                .putLong(KEY_END_MS, endMs)
                .putLong(KEY_GENERATION, generation)
                .apply()
        }

        private fun clearLockState(context: Context) {
            lockPrefs(context).edit().clear().apply()
        }

        /** Returns null if no valid saved lock state exists (expired or missing). */
        fun getSavedLockState(context: Context): Triple<String, Long, Long>? {
            val prefs = lockPrefs(context)
            val prayer = prefs.getString(KEY_PRAYER, null) ?: return null
            val endMs = prefs.getLong(KEY_END_MS, 0L)
            val generation = prefs.getLong(KEY_GENERATION, 0L)
            return if (endMs > System.currentTimeMillis() && generation > 0L) {
                Triple(prayer, endMs, generation)
            } else null
        }

        private fun lockPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(LOCK_STATE_PREFS, Context.MODE_PRIVATE)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val app get() = applicationContext as SalahLockApplication

    private var prayerName: PrayerName = PrayerName.FAJR
    private var lockEndMs: Long = 0L
    private var scheduleGeneration: Long = 0L
    private var sessionJob: Job? = null
    private var blockedPackages: Set<String> = emptySet()
    private var lockWindowActive = false
    private var lastLaunchedOverlayMs = 0L

    /**
     * True once the current prayer has been verified (or overridden) for today.
     * Read from Room — survives process death, service restart, and reboot — so a
     * verified prayer is never re-locked when the user reopens a blocked app.
     */
    @Volatile private var lockReleased = false

    /** Lock engine is paused until this epoch-millis (Sprint N.3). Read live from prefs. */
    @Volatile private var pauseUntilMs = 0L

    private val pollingRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (now > lockEndMs) {
                Log.d(TAG, "Lock window expired — stopping service.")
                stopSelf()
                return
            }
            if (!PermissionHelper.hasUsageStatsPermission(applicationContext)) {
                Log.w(TAG, "Usage stats permission revoked — stopping service.")
                stopSelf()
                return
            }
            checkForegroundApp()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        isRunning = true
        Log.d(TAG, "Service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val (prayerNameStr, resolvedLockEndMs, resolvedGeneration) = resolveStartParams(intent) ?: run {
            Log.w(TAG, "Cannot resolve lock state — stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        val incomingPrayer = try { PrayerName.valueOf(prayerNameStr) } catch (_: Exception) { PrayerName.FAJR }
        if (isDuplicateLockStart(
                lockWindowActive,
                prayerName,
                incomingPrayer,
                scheduleGeneration,
                resolvedGeneration,
                lockEndMs,
                System.currentTimeMillis(),
            )
        ) {
            startForeground(NOTIFICATION_ID, buildNotification(prayerName))
            Log.d(TAG, "[NOTIFICATION] Duplicate service start ignored for ${prayerName.name}")
            return START_STICKY
        }

        sessionJob?.cancel()
        handler.removeCallbacks(pollingRunnable)
        val currentSessionJob = SupervisorJob()
        sessionJob = currentSessionJob
        val sessionScope = CoroutineScope(currentSessionJob + Dispatchers.IO)

        prayerName = incomingPrayer
        lockEndMs = resolvedLockEndMs
        scheduleGeneration = resolvedGeneration

        // Persist so the service can resume if killed by OS
        saveLockState(applicationContext, prayerName.name, lockEndMs, scheduleGeneration)

        Log.d(TAG, "Starting lock window for ${prayerName.displayName}, ends at $lockEndMs")

        val notification = buildNotification(prayerName)
        startForeground(NOTIFICATION_ID, notification)

        // Reset for the prayer this start command represents. The observer below
        // immediately re-derives the true value from Room (handles START_STICKY
        // restarts that occur *after* the prayer was already verified).
        lockReleased = false

        sessionScope.launch {
            // Gate check — read verification state ONCE before the poller can run.
            // This establishes a happens-before so a restart (process death / START_STICKY)
            // that occurs AFTER the prayer was verified never shows the overlay: the
            // poller is only posted if this prayer is still unverified.
            val today = LocalDate.now().toString()
            // Prayer records are device-local & auth-independent — always read __local__.
            val owner = com.salahlock.app.data.db.entity.OwnerIds.LOCAL
            val existing = app.database.prayerRecordDao().getRecord(owner, today, prayerName.name)
            val overrideRecorded = app.database.emergencyOverrideDao()
                .countForPrayer(owner, today, prayerName.name) > 0
            if (existing?.verified == true || overrideRecorded) {
                lockReleased = true
                Log.d(TAG, "${prayerName.name} protection already satisfied — not locking.")
                stopSelf()
                return@launch
            }

            // Initial load
            blockedPackages = app.blacklistRepository.getBlockedPackages().toSet()
            lockWindowActive = true

            // Immediate foreground check before entering the recurring poll loop:
            // without it the first check is deferred until the main looper processes
            // the posted runnable, so a blocked app that is ALREADY foreground when
            // the lock window starts would not trigger the overlay until later.
            // checkForegroundApp() normally runs on the main-looper handler, so the
            // Main dispatcher here keeps identical threading.
            withContext(Dispatchers.Main) { checkForegroundApp() }

            // Now schedule the recurring poll (first tick fires after the interval).
            handler.post(pollingRunnable)
            Log.d(TAG, "Polling started. Blocked packages: ${blockedPackages.size}")

            // Keep blocked list in sync for the duration of the lock window.
            // If the user blocks a new app mid-session, it takes effect immediately.
            app.blacklistRepository.observeBlockedPackages().collect { packages ->
                blockedPackages = packages.toSet()
                Log.d(TAG, "Blocked packages updated: ${blockedPackages.size}")
            }
        }

        // Keep the pause state live — if the user pauses mid-window, locking stops at once.
        sessionScope.launch {
            app.userPreferences.pauseUntil.collect { pauseUntilMs = it }
        }

        // End the lock window on genuine verification OR a separately recorded override.
        // The two records remain distinct so an override can never become completion data.
        val lockedPrayer = prayerName
        sessionScope.launch {
            // Prayer records are device-local & auth-independent — always read __local__.
            val owner = com.salahlock.app.data.db.entity.OwnerIds.LOCAL
            val date = LocalDate.now().toString()
            combine(
                app.database.prayerRecordDao().observeRecordsForDate(owner, date),
                app.database.emergencyOverrideDao().observeCountForPrayer(owner, date, lockedPrayer.name),
            ) { records, overrideCount ->
                records.any { it.prayerName == lockedPrayer.name && it.verified } || overrideCount > 0
            }.collect { protectionSatisfied ->
                if (protectionSatisfied) {
                    handler.post {
                        if (sessionJob === currentSessionJob) {
                            lockReleased = true
                            Log.d(TAG, "${lockedPrayer.name} protection released — ending lock window.")
                            stopSelf()
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    /**
     * Resolves prayer name and lock-end-ms from either:
     * 1. A normal intent with EXTRA_PRAYER_NAME + EXTRA_LOCK_DURATION_MIN
     * 2. A null intent (OS restart) — reads from persisted SharedPreferences
     *
     * Returns null if no valid lock state can be resolved (service should stop).
     */
    private fun resolveStartParams(intent: Intent?): Triple<String, Long, Long>? {
        if (intent != null) {
            val name = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return null
            val durationMin = intent.getIntExtra(EXTRA_LOCK_DURATION_MIN, 30)
            val generation = intent.getLongExtra(EXTRA_SCHEDULE_GENERATION, 0L)
            if (generation <= 0L) return null
            val endMs = System.currentTimeMillis() + (durationMin * 60_000L)
            return Triple(name, endMs, generation)
        }

        // Null intent = OS killed and restarted via START_STICKY — attempt recovery
        Log.d(TAG, "Null intent on onStartCommand — attempting lock state recovery.")
        return getSavedLockState(applicationContext)
    }

    private fun checkForegroundApp() {
        if (!lockWindowActive) return

        // Prayer already done — never lock again this window. The Room observer
        // normally stops the service first; this is the synchronous safety net for
        // the brief race between verification and that callback.
        if (lockReleased) {
            stopSelf()
            return
        }

        // Lock engine temporarily paused by the user — don't show the overlay.
        // Prayer calculation / streak / tracking are unaffected (handled elsewhere).
        if (System.currentTimeMillis() < pauseUntilMs) return

        // SL-021: with the screen off nobody can be using a blocked app — skip the
        // UsageStats query (the expensive part of each 500ms tick). Identical lock
        // behavior; polling resumes the instant the screen turns on.
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!pm.isInteractive) return

        val usageStats = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // 7s window: MIUI can lag lastTimeUsed by 3-5s; the extra margin prevents
        // false negatives on aggressive background-management ROMs (MIUI, ColorOS).
        val windowMs = 7_000L
        val stats = usageStats.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - windowMs,
            now,
        )

        val foregroundPkg = stats
            .filter { it.lastTimeUsed > now - windowMs }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName ?: return

        if (foregroundPkg == packageName) return
        if (foregroundPkg == "com.android.systemui" || foregroundPkg == "android") return

        if (blockedPackages.contains(foregroundPkg)) {
            if (now - lastLaunchedOverlayMs < 1000L) return
            lastLaunchedOverlayMs = now
            Log.d("LockVerification", "Blocked package detected: $foregroundPkg — launching overlay.")
            launchLockOverlay()
        }
    }

    private fun launchLockOverlay() {
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra(EXTRA_PRAYER_NAME, prayerName.name)
            putExtra("lock_end_ms", lockEndMs)
        }
        startActivity(intent)
    }

    private fun buildNotification(prayer: PrayerName) = run {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_salahlock_notification)
            .setContentTitle("${prayer.arabicName}  ·  ${prayer.displayName}")
            .setContentText("Niyyah is with you — prayer window open.")
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prayer Lock Active",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shown while Niyyah is protecting your prayer time"
            setShowBadge(false)
            enableVibration(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed — will restart via START_STICKY.")
    }

    override fun onDestroy() {
        isRunning = false
        lockWindowActive = false
        handler.removeCallbacks(pollingRunnable)
        serviceScope.cancel()
        sessionJob?.cancel()
        NotificationHelper.cancelAdhanNotification(applicationContext, prayerName)
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Clear persisted lock state so a stale state doesn't affect future restarts
        clearLockState(applicationContext)
        Log.d(TAG, "Service destroyed — lock state cleared.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
