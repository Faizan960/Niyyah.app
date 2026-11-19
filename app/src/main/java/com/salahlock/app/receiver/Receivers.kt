package com.salahlock.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.alarm.AlarmScheduler
import com.salahlock.app.alarm.AlarmEventValidator
import com.salahlock.app.alarm.PrayerLifecycleSynchronizer
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

private suspend fun runIfScheduleIsCurrent(
    app: SalahLockApplication,
    deliveredGeneration: Long,
    block: () -> Unit,
): Boolean = PrayerLifecycleSynchronizer.withLock {
    val activeGeneration = app.userPreferences.alarmScheduleGeneration.first()
    if (AlarmEventValidator.isCurrent(deliveredGeneration, activeGeneration)) {
        block()
        true
    } else {
        Log.w(
            TAG,
            "[ALARM] Rejected side effects generation=$deliveredGeneration active=$activeGeneration",
        )
        false
    }
}

/** Fires when a prayer time alarm triggers. Starts the lock window service. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME) ?: return
        val lockDurationMin = intent.getIntExtra(AlarmScheduler.EXTRA_LOCK_DURATION_MIN, 30)
        val scheduleGeneration = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_GENERATION, 0L)
        val triggerAtMs = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT_MS, 0L)
        val prayerDate = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return

        // Alarms created before schedule-versioning (or without a trigger identity)
        // are deliberately rejected after upgrade instead of posting stale events.
        if (scheduleGeneration <= 0L || triggerAtMs <= 0L) {
            Log.w(TAG, "[ALARM] Rejected unversioned alarm prayer=$prayerName")
            return
        }

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

        val pending = goAsync()
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val app = context.applicationContext as SalahLockApplication
                val activeGeneration = app.userPreferences.alarmScheduleGeneration.first()
                if (!AlarmEventValidator.isCurrent(scheduleGeneration, activeGeneration)) {
                    Log.w(
                        TAG,
                        "[ALARM] Rejected stale ${prayer.name} alarm generation=$scheduleGeneration active=$activeGeneration",
                    )
                    return@launch
                }
                if (!AlarmEventValidator.isTimely(triggerAtMs, System.currentTimeMillis())) {
                    Log.w(TAG, "[ALARM] Rejected outdated ${prayer.name} trigger=$triggerAtMs")
                    return@launch
                }
                Log.d(TAG, "[ALARM] Receiver accepted ${prayer.name} trigger=$triggerAtMs generation=$scheduleGeneration")

                val announcementsEnabled = app.userPreferences.notificationsEnabled.first() &&
                    app.userPreferences.adhanEnabled.first()

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
                    if (announcementsEnabled) {
                        runIfScheduleIsCurrent(app, scheduleGeneration) {
                            NotificationHelper.showAdhanNotification(context, prayer)
                        }
                    }
                    return@launch
                }

                if (!PermissionHelper.hasCriticalPermissions(context)) {
                    Log.w(TAG, "Critical permissions missing — skipping lock window for ${prayer.displayName}")
                    if (announcementsEnabled) {
                        runIfScheduleIsCurrent(app, scheduleGeneration) {
                            NotificationHelper.showAdhanNotification(context, prayer)
                        }
                    }
                    return@launch
                }

                val lifecycleStarted = runIfScheduleIsCurrent(app, scheduleGeneration) {
                    val serviceIntent = Intent(context, UsageStatsPollingService::class.java).apply {
                        putExtra(UsageStatsPollingService.EXTRA_PRAYER_NAME, prayer.name)
                        putExtra(UsageStatsPollingService.EXTRA_LOCK_DURATION_MIN, lockDurationMin)
                        putExtra(UsageStatsPollingService.EXTRA_SCHEDULE_GENERATION, scheduleGeneration)
                    }
                    context.startForegroundService(serviceIntent)
                    if (announcementsEnabled) NotificationHelper.showAdhanNotification(context, prayer)
                    AlarmScheduler.scheduleMissedPrayerReminder(
                        context, prayer, prayerDate, lockDurationMin, scheduleGeneration,
                    )
                }
                if (lifecycleStarted) {
                    Log.d(TAG, "[NOTIFICATION] ${prayer.name} prayer-start lifecycle started")
                }
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
        val scheduleGeneration = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_GENERATION, 0L)
        val prayerDate = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_DATE) ?: return
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
                val activeGeneration = app.userPreferences.alarmScheduleGeneration.first()
                if (!AlarmEventValidator.isCurrent(scheduleGeneration, activeGeneration)) {
                    Log.w(TAG, "[ALARM] Rejected stale missed reminder for ${prayer.name}")
                    return@launch
                }
                // Prayer records are device-local & auth-independent — always read __local__.
                val owner = com.salahlock.app.data.db.entity.OwnerIds.LOCAL
                val record = app.database.prayerRecordDao().getRecord(owner, prayerDate, prayer.name)
                val wasVerified = record?.verified == true
                if (!wasVerified && app.userPreferences.notificationsEnabled.first()) {
                    NotificationHelper.cancelAdhanNotification(context, prayer)
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
            (context.applicationContext as SalahLockApplication)
                .prayerAlarmCoordinator.requestRefresh("device_booted")
        }
    }
}

/** Reschedules alarms after the app itself is updated. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Package replaced — rescheduling prayer alarms.")
            (context.applicationContext as SalahLockApplication)
                .prayerAlarmCoordinator.requestRefresh("application_updated")
        }
    }
}

/** Reschedules after wall-clock, date, or timezone changes. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_DATE_CHANGED
        ) {
            (context.applicationContext as SalahLockApplication)
                .prayerAlarmCoordinator.requestRefresh("${intent.action}")
        }
    }
}
