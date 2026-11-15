package com.salahlock.app.alarm

import com.salahlock.app.data.model.DailyPrayers
import com.salahlock.app.data.model.PrayerName
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId

data class ScheduledPrayer(
    val prayer: PrayerName,
    val time: LocalDateTime,
)

/** Pure schedule planning kept separate from Android so boundary behavior is testable. */
object AlarmSchedulePlanner {
    fun all(prayers: List<DailyPrayers>): List<ScheduledPrayer> =
        prayers
            .flatMap { daily -> daily.toList() }
            .distinctBy { it.name to it.time.toLocalDate() }
            .sortedBy { it.time }
            .map { ScheduledPrayer(it.name, it.time) }

    fun upcoming(prayers: List<DailyPrayers>, now: LocalDateTime): List<ScheduledPrayer> =
        all(prayers)
            .filter { it.time.isAfter(now) }

    fun fingerprint(
        schedule: List<ScheduledPrayer>,
        lockDurationMin: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val canonical = buildString {
            append(zoneId.id).append('|').append(lockDurationMin)
            schedule.forEach { item ->
                append('|').append(item.prayer.name).append('@').append(item.time)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
