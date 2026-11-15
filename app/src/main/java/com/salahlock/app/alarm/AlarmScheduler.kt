package com.salahlock.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.receiver.MissedPrayerReminderReceiver
import com.salahlock.app.receiver.PrayerAlarmReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {

    const val EXTRA_PRAYER_NAME = "prayer_name"
    const val EXTRA_LOCK_DURATION_MIN = "lock_duration_min"
    const val EXTRA_SCHEDULE_GENERATION = "schedule_generation"
    const val EXTRA_TRIGGER_AT_MS = "trigger_at_ms"
    const val EXTRA_PRAYER_DATE = "prayer_date"

    fun scheduleAll(
        context: Context,
        schedule: List<ScheduledPrayer>,
        lockDurationMin: Int,
        generation: Long,
    ) {
        schedule.forEach { item ->
            schedule(context, item.prayer, item.time, lockDurationMin, generation)
        }
    }

    fun schedule(
        context: Context,
        prayer: PrayerName,
        time: LocalDateTime,
        lockDurationMin: Int,
        generation: Long,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMs = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra(EXTRA_PRAYER_NAME, prayer.name)
            putExtra(EXTRA_LOCK_DURATION_MIN, lockDurationMin)
            putExtra(EXTRA_SCHEDULE_GENERATION, generation)
            putExtra(EXTRA_TRIGGER_AT_MS, triggerMs)
            putExtra(EXTRA_PRAYER_DATE, time.toLocalDate().toString())
        }

        val requestCode = buildRequestCode(prayer, time.toLocalDate())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // setAlarmClock is the most reliable for prayer times — shows clock icon in status bar
        // and signals high user intent to the OS (harder for OEMs to suppress)
        val clockInfo = AlarmManager.AlarmClockInfo(triggerMs, pendingIntent)
        try {
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
        } catch (e: SecurityException) {
            // Fallback if exact alarm permission not granted (should not happen in normal flow)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        }
        Log.d("AlarmScheduler", "[ALARM] Scheduled ${prayer.name} start=$time generation=$generation")
    }

    /**
     * Cancel all alarms for the provided list.
     * Uses [PendingIntent.FLAG_UPDATE_CURRENT] to ensure the PendingIntent is found
     * even if previously created — then cancels it explicitly.
     */
    /** Cancels every known start and reminder PendingIntent before replacement. */
    fun cancelAllLegacy(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val today = LocalDate.now()
        for (offset in -2..62) {
            val date = today.plusDays(offset.toLong())
            PrayerName.entries.forEach { prayer ->
                val requestCode = buildRequestCode(prayer, date)
                cancelExisting(context, alarmManager, PrayerAlarmReceiver::class.java, requestCode)
                cancelExisting(context, alarmManager, MissedPrayerReminderReceiver::class.java, requestCode)
            }
        }
        Log.d("AlarmScheduler", "[ALARM] Cancelled stale prayer and reminder alarms")
    }

    /**
     * Schedules a missed-prayer reminder for [prayer] to fire [lockDurationMin] minutes
     * from now. [MissedPrayerReminderReceiver] checks the DB at fire time and shows
     * the notification only if the prayer is still unverified.
     *
     * Uses setExactAndAllowWhileIdle so it fires even during doze mode.
     * The request code is the same as the prayer alarm but the receiver class differs,
     * so these are distinct PendingIntents in the OS.
     */
    fun scheduleMissedPrayerReminder(
        context: Context,
        prayer: PrayerName,
        prayerDate: LocalDate,
        lockDurationMin: Int,
        generation: Long,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMs = System.currentTimeMillis() + (lockDurationMin * 60_000L)

        val intent = Intent(context, MissedPrayerReminderReceiver::class.java).apply {
            putExtra(EXTRA_PRAYER_NAME, prayer.name)
            putExtra(EXTRA_PRAYER_DATE, prayerDate.toString())
            putExtra(EXTRA_SCHEDULE_GENERATION, generation)
        }
        val requestCode = buildRequestCode(prayer, prayerDate)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        }
    }

    /**
     * Build a stable, unique request code per prayer per date.
     * Uses year/day/prayer ordinals to avoid collisions.
     * Maximum value fits well within Int range for reasonable years.
     */
    internal fun buildRequestCode(prayer: PrayerName, date: LocalDate): Int {
        // dayOfYear is 1..366; prayer.ordinal is 0..4
        return (date.year * 366 * PrayerName.entries.size) +
                (date.dayOfYear * PrayerName.entries.size) +
                prayer.ordinal
    }

    /**
     * Reminder request codes use today's date so they are stable within a day.
     * The MissedPrayerReminderReceiver class makes these distinct from prayer alarm
     * PendingIntents even when the integer value matches.
     */
    private fun cancelExisting(
        context: Context,
        alarmManager: AlarmManager,
        receiver: Class<*>,
        requestCode: Int,
    ) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, receiver),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
