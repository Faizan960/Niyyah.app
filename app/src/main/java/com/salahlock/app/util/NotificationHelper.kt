package com.salahlock.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.salahlock.app.MainActivity
import com.salahlock.app.R
import com.salahlock.app.data.model.PrayerName

object NotificationHelper {

    // ── Channel IDs ───────────────────────────────────────────────────────────
    const val ADHAN_CHANNEL_ID       = "salahlock_adhan"
    const val REMINDER_CHANNEL_ID    = "salahlock_reminder"
    const val MISSED_PRAYER_CHANNEL_ID = "salahlock_missed_prayer"
    const val LOCK_CHANNEL_ID        = "salahlock_lock_channel"

    /** Default delay before firing a missed-prayer reminder (minutes). Configurable via Settings later. */
    const val DEFAULT_MISSED_PRAYER_DELAY_MIN = 20

    private const val TAG = "NotificationHelper"

    // ── Channel Registration ──────────────────────────────────────────────────
    /**
     * Must be called in Application.onCreate() before any alarm fires.
     * Safe to call multiple times — Android deduplicates existing channels.
     */
    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                ADHAN_CHANNEL_ID,
                "Prayer Time (Adhan)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifies you when it's time for prayer"
                enableVibration(true)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Prayer Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Gentle reminders before prayer time"
                enableVibration(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                MISSED_PRAYER_CHANNEL_ID,
                "Prayer Follow-ups",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "A gentle follow-up if a prayer window passes"
                enableVibration(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                LOCK_CHANNEL_ID,
                "Prayer Lock Active",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Shown while Niyyah is protecting your prayer time"
                setShowBadge(false)
                enableVibration(false)
            }
        )

        Log.d(TAG, "All 4 notification channels registered.")
    }

    // ── Adhan Notification ────────────────────────────────────────────────────
    fun showAdhanNotification(context: Context, prayer: PrayerName) {
        val tapIntent = PendingIntent.getActivity(
            context, prayer.ordinal,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ADHAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_salahlock_notification)
            .setContentTitle("${prayer.arabicName}  ·  ${prayer.displayName}")
            .setContentText("It is time for ${prayer.displayName}.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("The ${prayer.displayName} prayer window has begun. When you have prayed, confirm it to continue.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(tapIntent)
            .setAutoCancel(false)
            .setOngoing(false)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(prayer.ordinal + 100, notification)
        Log.d(TAG, "Adhan notification shown for ${prayer.displayName}")

        // Vibration pattern: long-short-long (respectful, not jarring)
        vibrate(context)
    }

    // ── Missed Prayer Notification ────────────────────────────────────────────
    fun showMissedPrayerReminder(context: Context, prayer: PrayerName) {
        val tapIntent = PendingIntent.getActivity(
            context, prayer.ordinal + 200,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, MISSED_PRAYER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_salahlock_notification)
            .setContentTitle("${prayer.displayName} has passed")
            .setContentText("May Allah make the next prayer easy for you. Tap to mark it if you prayed.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(prayer.ordinal + 200, notification)
        Log.d(TAG, "Missed prayer reminder shown for ${prayer.displayName}")
    }

    // ── Vibration ─────────────────────────────────────────────────────────────
    private fun vibrate(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 200, 500), -1)
        }
    }
}

