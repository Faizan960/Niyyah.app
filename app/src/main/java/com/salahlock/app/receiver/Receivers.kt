package com.salahlock.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.alarm.AlarmScheduler
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.service.UsageStatsPollingService
import com.salahlock.app.util.NotificationHelper
import com.salahlock.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "SalahLockReceiver"

/** Fires when a prayer time alarm triggers. Starts the lock window service. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME) ?: return
        val lockDurationMin = intent.getIntExtra(AlarmScheduler.EXTRA_LOCK_DURATION_MIN, 30)

        val prayer = try {
            PrayerName.valueOf(prayerName)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unknown prayer name in alarm: $prayerName")
            return
        }

        // SUNRISE is not an obligatory prayer — never lock for it
        if (prayer == PrayerName.SUNRISE) {
            Log.d(TAG, "Ignoring alarm for SUNRISE — not a lockable prayer.")
            return
        }

        // Guard: only start service if critical permissions are present
        if (!PermissionHelper.hasCriticalPermissions(context)) {
            Log.w(TAG, "Critical permissions missing — skipping lock window for ${prayer.displayName}")
            return
        }

        val pending = goAsync()
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val app = context.applicationContext as SalahLockApplication

                // Sprint N.3 — if the lock engine is paused, suppress the lock window,
                // reminder, and notifications for this prayer. Prayer tracking/streak
                // (driven by user verification) are unaffected.
                val pauseUntil = app.userPreferences.pauseUntil.first()
                if (System.currentTimeMillis() < pauseUntil) {
                    Log.d(TAG, "SalahLock paused — skipping ${prayer.displayName}.")
                    return@launch
                }

                // Check per-prayer lock enabled flag (user may have disabled specific prayers)
                val isEnabled = app.userPreferences.isPrayerLockEnabled(prayer).first()
                if (!isEnabled) {
                    Log.d(TAG, "Lock disabled by user for ${prayer.displayName} — skipping.")
                    // Still show the Adhan notification even if locking is disabled
                    NotificationHelper.showAdhanNotification(context, prayer)
                    return@launch
                }

                val serviceIntent = Intent(context, UsageStatsPollingService::class.java).apply {
                    putExtra(UsageStatsPollingService.EXTRA_PRAYER_NAME, prayer.name)
                    putExtra(UsageStatsPollingService.EXTRA_LOCK_DURATION_MIN, lockDurationMin)
                }
                context.startForegroundService(serviceIntent)
                NotificationHelper.showAdhanNotification(context, prayer)
                AlarmScheduler.scheduleMissedPrayerReminder(context, prayer, lockDurationMin)
                Log.d(TAG, "Prayer alarm fired: ${prayer.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error in PrayerAlarmReceiver: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Fires at prayer_time + lockDurationMin (i.e., when the lock window closes).
 * Checks whether the prayer was verified; shows a reminder notification only if not.
 * Uses goAsync() for the DB read — receiver's time budget is 10s but the query is fast.
 */
class MissedPrayerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME) ?: return
        val prayer = try {
            PrayerName.valueOf(prayerName)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unknown prayer name in reminder: $prayerName")
            return
        }

        val pending = goAsync()
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val app = context.applicationContext as SalahLockApplication
                val today = LocalDate.now().toString()
                // Prayer records are device-local & auth-independent — always read __local__.
                val owner = com.salahlock.app.data.db.entity.OwnerIds.LOCAL
                val record = app.database.prayerRecordDao().getRecord(owner, today, prayer.name)
                val wasVerified = record != null && (record.verified || record.overrideUsed)
                if (!wasVerified) {
                    NotificationHelper.showMissedPrayerReminder(context, prayer)
                    Log.d(TAG, "Missed prayer reminder shown for ${prayer.displayName}")
                } else {
                    Log.d(TAG, "Prayer already verified — skipping reminder for ${prayer.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check prayer record for reminder: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}

/** Reschedules all alarms after device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) {
            Log.d(TAG, "Boot completed — rescheduling prayer alarms.")
            val pending = goAsync()
            rescheduleAlarms(context) { pending.finish() }
        }
    }
}

/** Reschedules alarms after the app itself is updated. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Package replaced — rescheduling prayer alarms.")
            val pending = goAsync()
            rescheduleAlarms(context) { pending.finish() }
        }
    }
}

/**
 * Reschedules all upcoming prayer alarms using the stored location.
 * Calls [onFinish] when complete so the BroadcastReceiver's [PendingResult] can be released.
 * Uses GlobalScope intentionally — this is a fire-and-forget operation scoped to app lifetime.
 */
@Suppress("OPT_IN_USAGE")
private fun rescheduleAlarms(context: Context, onFinish: () -> Unit) {
    val app = context.applicationContext as SalahLockApplication
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val lat = app.userPreferences.userLat.first()
            val lng = app.userPreferences.userLng.first()
            val method = app.userPreferences.calcMethod.first()
            val duration = app.userPreferences.lockDurationMin.first()

            if (lat != 0.0 && lng != 0.0) {
                val upcoming = app.prayerTimesRepository.calculateUpcoming(lat, lng, method)
                AlarmScheduler.scheduleAll(context, upcoming, duration)
                Log.d(TAG, "Rescheduled ${upcoming.size * 5} prayer alarms.")
            } else {
                Log.w(TAG, "No location set — cannot reschedule alarms.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule alarms: ${e.message}")
        } finally {
            onFinish()
        }
    }
}
