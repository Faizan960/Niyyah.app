package com.salahlock.app.work

import android.content.Context
import android.util.Log
import androidx.work.*
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.alarm.AlarmScheduler
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val TAG = "AlarmRefreshWorker"
private const val WORK_NAME = "salahlock_alarm_refresh"

/**
 * Periodic WorkManager worker that refreshes the next 30 days of prayer alarms.
 *
 * Why: AlarmManager alarms can be silently dropped by OEM battery savers (especially
 * Xiaomi/MIUI, Realme, Samsung with aggressive background limits). WorkManager
 * provides a guaranteed, OS-backed recurring job that reschedules alarms even after
 * they are killed.
 *
 * Schedule: Every 24 hours, with a 2-hour flex window.
 */
class AlarmRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SalahLockApplication
        return try {
            val lat = app.userPreferences.userLat.first()
            val lng = app.userPreferences.userLng.first()
            val method = app.userPreferences.calcMethod.first()
            val duration = app.userPreferences.lockDurationMin.first()

            if (lat == 0.0 && lng == 0.0) {
                Log.w(TAG, "No location set — skipping alarm refresh.")
                return Result.success()
            }

            val upcoming = app.prayerTimesRepository.calculateUpcoming(lat, lng, method)
            AlarmScheduler.scheduleAll(applicationContext, upcoming, duration)
            Log.d(TAG, "Refreshed ${upcoming.size * 5} prayer alarms.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Alarm refresh failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        /** Enqueue the periodic refresh. Safe to call multiple times — uses KEEP existing policy. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlarmRefreshWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 2,
                flexTimeIntervalUnit = TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .setRequiresCharging(false)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "Scheduled periodic alarm refresh worker.")
        }

        /** Run an immediate one-time refresh (e.g., after location change). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AlarmRefreshWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "Enqueued immediate alarm refresh.")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
