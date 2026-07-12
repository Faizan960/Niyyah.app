package com.salahlock.app.work

import android.content.Context
import android.util.Log
import androidx.work.*
import com.salahlock.app.SalahLockApplication
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that creates a local auto-backup on a user-configured schedule.
 *
 * Runs offline — no network required. Backup is written to the app's private
 * `filesDir/backups/` directory. The most recent 5 auto-backups are kept.
 *
 * Schedule:
 *  DISABLED — no periodic work
 *  DAILY    — every 1 day
 *  WEEKLY   — every 7 days
 *  MONTHLY  — every 30 days
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SalahLockApplication
        return try {
            val path = app.backupRepository.createAutoBackup()
            Log.d("AutoBackupWorker", "Auto-backup success: $path")
            app.userPreferences.setLastBackupMs(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            Log.e("AutoBackupWorker", "Auto-backup failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "salahlock_auto_backup"

        fun schedule(context: Context, frequency: String) {
            val wm = WorkManager.getInstance(context)
            when (frequency) {
                "DISABLED" -> {
                    wm.cancelUniqueWork(WORK_NAME)
                    Log.d("AutoBackupWorker", "Auto-backup disabled.")
                }
                else -> {
                    val intervalDays = when (frequency) {
                        "DAILY"   -> 1L
                        "WEEKLY"  -> 7L
                        "MONTHLY" -> 30L
                        else      -> { wm.cancelUniqueWork(WORK_NAME); return }
                    }
                    val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                        intervalDays, TimeUnit.DAYS
                    )
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiresBatteryNotLow(true)
                                .build()
                        )
                        .build()
                    wm.enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request,
                    )
                    Log.d("AutoBackupWorker", "Auto-backup scheduled: every $intervalDays day(s).")
                }
            }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
