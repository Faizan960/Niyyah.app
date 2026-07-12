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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "UsageStatsPollingService"
private const val LOCK_STATE_PREFS = "salahlock_active_lock"
private const val KEY_PRAYER = "prayer_name"
private const val KEY_END_MS = "lock_end_ms"

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
        val CHANNEL_ID get() = NotificationHelper.LOCK_CHANNEL_ID
        const val NOTIFICATION_ID = 1001

        @Volatile var isRunning = false

        // --- Lock state persistence (SharedPreferences — synchronous, no coroutine needed) ---
        private fun saveLockState(context: Context, prayer: String, endMs: Long) {
            lockPrefs(context).edit()
                .putString(KEY_PRAYER, prayer)
                .putLong(KEY_END_MS, endMs)
                .apply()
        }

        private fun clearLockState(context: Context) {
            lockPrefs(context).edit().clear().apply()
        }

        /** Returns null if no valid saved lock state exists (expired or missing). */
        fun getSavedLockState(context: Context): Pair<String, Long>? {
            val prefs = lockPrefs(context)
            val prayer = prefs.getString(KEY_PRAYER, null) ?: return null
            val endMs = prefs.getLong(KEY_END_MS, 0L)
            return if (endMs > System.currentTimeMillis()) Pair(prayer, endMs) else null
        }

        private fun lockPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(LOCK_STATE_PREFS, Context.MODE_PRIVATE)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val app get() = applicationContext as SalahLockApplication

    private var prayerName: PrayerName = PrayerName.FAJR
    private var lockEndMs: Long = 0L
    private var blockedPackages: Set<String> = emptySet()
    private var lockWindowActive = false
    private var lastLaunchedOverlayMs = 0L

    /**
     * True once the current prayer has been verified (or overridden) for today.
     * Read from Room — survives process death, service restart, and reboot — so a
     * verified prayer is never re-locked when the user reopens a blocked app.
     */
    @Volatile private var prayerVerified = false

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
        val (prayerNameStr, resolvedLockEndMs) = resolveStartParams(intent) ?: run {
            Log.w(TAG, "Cannot resolve lock state — stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        prayerName = try { PrayerName.valueOf(prayerNameStr) } catch (_: Exception) { PrayerName.FAJR }
        lockEndMs = resolvedLockEndMs

        // Persist so the service can resume if killed by OS
        saveLockState(applicationContext, prayerName.name, lockEndMs)

        Log.d(TAG, "Starting lock window for ${prayerName.displayName}, ends at $lockEndMs")

        val notification = buildNotification(prayerName)
        startForeground(NOTIFICATION_ID, notification)

        // Reset for the prayer this start command represents. The observer below
        // immediately re-derives the true value from Room (handles START_STICKY
        // restarts that occur *after* the prayer was already verified).
        prayerVerified = false

        serviceScope.launch {
            // Gate check — read verification state ONCE before the poller can run.
            // This establishes a happens-before so a restart (process death / START_STICKY)
            // that occurs AFTER the prayer was verified never shows the overlay: the
            // poller is only posted if this prayer is still unverified.
            val today = LocalDate.now().toString()
            val existing = app.database.prayerRecordDao().getRecord(today, prayerName.name)
            if (existing != null && (existing.verified || existing.overrideUsed)) {
                prayerVerified = true
                Log.d(TAG, "${prayerName.name} already verified on start — not locking.")
                stopSelf()
                return@launch
            }

            // Initial load
            blockedPackages = app.blacklistRepository.getBlockedPackages().toSet()
            lockWindowActive = true
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
        serviceScope.launch {
            app.userPreferences.pauseUntil.collect { pauseUntilMs = it }
        }

        // End the lock window as soon as THIS prayer is verified (or overridden).
        // Observing Room means a verification done in the overlay — or one that
        // already existed before an OS-triggered restart — stops the service and
        // prevents re-locking when the user reopens a blocked app.
        val lockedPrayer = prayerName
        serviceScope.launch {
            app.database.prayerRecordDao()
                .observeRecordsForDate(LocalDate.now().toString())
                .collect { records ->
                    val verified = records.any {
                        it.prayerName == lockedPrayer.name && (it.verified || it.overrideUsed)
                    }
                    if (verified) {
                        prayerVerified = true
                        Log.d(TAG, "${lockedPrayer.name} verified — ending lock window.")
                        stopSelf()
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
    private fun resolveStartParams(intent: Intent?): Pair<String, Long>? {
        if (intent != null) {
            val name = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return null
            val durationMin = intent.getIntExtra(EXTRA_LOCK_DURATION_MIN, 30)
            val endMs = System.currentTimeMillis() + (durationMin * 60_000L)
            return Pair(name, endMs)
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
        if (prayerVerified) {
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
        // Clear persisted lock state so a stale state doesn't affect future restarts
        clearLockState(applicationContext)
        Log.d(TAG, "Service destroyed — lock state cleared.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
