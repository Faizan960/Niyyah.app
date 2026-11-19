package com.salahlock.app.alarm

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.salahlock.app.data.preferences.UserPreferences
import com.salahlock.app.data.repository.PrayerTimesRepository
import com.salahlock.app.service.UsageStatsPollingService
import com.salahlock.app.util.NotificationHelper
import com.salahlock.app.work.AlarmRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val TAG = "PrayerAlarmCoordinator"
private const val PERIODIC_WORK_NAME = "salahlock_alarm_refresh"
private const val IMMEDIATE_WORK_NAME = "salahlock_alarm_refresh_now"

internal fun refreshTriggerKey(
    calculationInputs: String,
    displaySource: String,
    jamaatUpdatedAt: Long,
): String = "$calculationInputs|$displaySource|$jamaatUpdatedAt"

/** Single entry point for observing, requesting, calculating, and applying alarms. */
class PrayerAlarmCoordinator(
    private val context: Context,
    private val prefs: UserPreferences,
    private val prayerTimesRepository: PrayerTimesRepository,
) {
    private val refreshMutex = Mutex()

    fun startObservingChanges(scope: CoroutineScope) {
        val calculationInputs = combine(
            prefs.userLat,
            prefs.userLng,
            prefs.calcMethod,
            prefs.madhab,
            prefs.lockDurationMin,
        ) { lat, lng, method, madhab, duration ->
            "$lat|$lng|$method|$madhab|$duration"
        }
        scope.launch {
            combine(
                calculationInputs,
                prefs.prayerSource,
                prayerTimesRepository.getLocalJamaatTimes(),
            ) { calculated, displaySource, jamaat ->
                // Source and timetable changes must replace the active alarms immediately.
                refreshTriggerKey(calculated, displaySource, jamaat?.updatedAt ?: 0L)
            }
                .distinctUntilChanged()
                .collect {
                    runCatching { requestRefresh("timing_configuration_changed") }
                        .onFailure { error ->
                            Log.e(TAG, "Could not enqueue timing refresh", error)
                        }
                }
        }
    }

    fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<AlarmRefreshWorker>(
            24, TimeUnit.HOURS, 2, TimeUnit.HOURS,
        )
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun requestRefresh(reason: String) {
        val request = OneTimeWorkRequestBuilder<AlarmRefreshWorker>()
            .setInputData(Data.Builder().putString(AlarmRefreshWorker.KEY_REASON, reason).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.d(TAG, "[ALARM] Refresh requested reason=$reason")
    }

    suspend fun refreshNow(reason: String): Int = refreshMutex.withLock {
        val lat = prefs.userLat.first()
        val lng = prefs.userLng.first()
        val method = prefs.calcMethod.first()
        val madhab = prefs.madhab.first()
        val duration = prefs.lockDurationMin.first()
        val now = LocalDateTime.now()

        val upcoming = prayerTimesRepository.calculateUpcoming(lat, lng, method, madhab)
        val scheduleIdentity = AlarmSchedulePlanner.all(upcoming)
        val schedule = AlarmSchedulePlanner.upcoming(upcoming, now)
        val (generation, scheduleReplaced) = PrayerLifecycleSynchronizer.withLock {
            val previousGeneration = prefs.alarmScheduleGeneration.first()
            val activatedGeneration = prefs.activateAlarmSchedule(
                AlarmSchedulePlanner.generationIdentity(scheduleIdentity, duration),
                AlarmSchedulePlanner::sameGeneration,
            )
            val replaced = previousGeneration <= 0L || activatedGeneration != previousGeneration
            if (previousGeneration > 0L && replaced) {
                context.stopService(Intent(context, UsageStatsPollingService::class.java))
                NotificationHelper.cancelPrayerLifecycleNotifications(context)
                Log.d(TAG, "[NOTIFICATION] Cleared lifecycle from replaced schedule")
            }
            activatedGeneration to replaced
        }

        // Activate the new generation before cancellation: any old alarm racing
        // with this refresh is rejected by the receiver.
        if (scheduleReplaced) {
            AlarmScheduler.cancelAllLegacy(context)
        } else {
            AlarmScheduler.cancelPrayerStartAlarms(context)
        }
        AlarmScheduler.scheduleAll(context, schedule, duration, generation)
        Log.d(
            TAG,
            "[ALARM] Refreshed count=${schedule.size} generation=$generation reason=$reason",
        )
        schedule.size
    }
}
