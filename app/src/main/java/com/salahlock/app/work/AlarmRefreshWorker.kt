package com.salahlock.app.work

import android.content.Context
import androidx.work.*
import com.salahlock.app.SalahLockApplication

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
            app.prayerAlarmCoordinator.refreshNow(
                inputData.getString(KEY_REASON) ?: "periodic",
            )
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("AlarmRefreshWorker", "Alarm refresh failed", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_REASON = "refresh_reason"

        fun schedule(context: Context) {
            (context.applicationContext as SalahLockApplication)
                .prayerAlarmCoordinator.schedulePeriodicRefresh()
        }

        fun runNow(context: Context) {
            (context.applicationContext as SalahLockApplication)
                .prayerAlarmCoordinator.requestRefresh("explicit")
        }
    }
}
