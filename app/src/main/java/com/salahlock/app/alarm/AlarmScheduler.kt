package com.salahlock.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.salahlock.app.data.model.DailyPrayers
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.receiver.MissedPrayerReminderReceiver
import com.salahlock.app.receiver.PrayerAlarmReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {

    const val EXTRA_PRAYER_NAME = "prayer_name"
    const val EXTRA_LOCK_DURATION_MIN = "lock_duration_min"

    fun scheduleAll(context: Context, dailyPrayersList: List<DailyPrayers>, lockDurationMin: Int) {
        // Cancel before re-scheduling by rebuilding each PendingIntent and cancelling it explicitly
        cancelAll(context, dailyPrayersList)
        val now = LocalDateTime.now()
        dailyPrayersList.forEach { daily ->
            daily.toList().forEach { prayer ->
                if (prayer.time.isAfter(now)) {
                    schedule(context, prayer.name, prayer.time, lockDurationMin)
                }
            }
        }
    }

    fun schedule(
        context: Context,
        prayer: PrayerName,
        time: LocalDateTime,
        lockDurationMin: Int,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMs = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra(EXTRA_PRAYER_NAME, prayer.name)
            putExtra(EXTRA_LOCK_DURATION_MIN, lockDurationMin)
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
    }

    /**
     * Cancel all alarms for the provided list.
     * Uses [PendingIntent.FLAG_UPDATE_CURRENT] to ensure the PendingIntent is found
     * even if previously created — then cancels it explicitly.
     */
    fun cancelAll(context: Context, dailyPrayersList: List<DailyPrayers>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        dailyPrayersList.forEach { daily ->
            val date = LocalDate.parse(daily.date)
            PrayerName.entries.forEach { prayer ->
                val requestCode = buildRequestCode(prayer, date)
                val intent = Intent(context, PrayerAlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    /**
     * Legacy cancel — used when the prayer list is not available (e.g., from BootReceiver).
     * Iterates a fixed range to cancel all known prayer alarms.
     */
    fun cancelAllLegacy(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val today = LocalDate.now()
        for (offset in -1..31) {
            val date = today.plusDays(offset.toLong())
            PrayerName.entries.forEach { prayer ->
                val requestCode = buildRequestCode(prayer, date)
                val intent = Intent(context, PrayerAlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
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
    fun scheduleMissedPrayerReminder(context: Context, prayer: PrayerName, lockDurationMin: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMs = System.currentTimeMillis() + (lockDurationMin * 60_000L)

        val intent = Intent(context, MissedPrayerReminderReceiver::class.java).apply {
            putExtra(EXTRA_PRAYER_NAME, prayer.name)
        }
        val requestCode = buildReminderRequestCode(prayer)
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
     * Uses year * 366 * 5 + dayOfYear * 5 + prayerOrdinal to avoid collisions.
     * Maximum value fits well within Int range for reasonable years.
     */
    private fun buildRequestCode(prayer: PrayerName, date: LocalDate): Int {
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
    private fun buildReminderRequestCode(prayer: PrayerName): Int {
        val today = LocalDate.now()
        return buildRequestCode(prayer, today)
    }
}
