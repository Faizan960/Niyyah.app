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
    private const val IDENTITY_VERSION = "v2"

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

    /**
     * Persistable identity used for schedule generations. Unlike a full-list hash,
     * this can distinguish a normal rolling-horizon change from a changed trigger.
     */
    fun generationIdentity(
        schedule: List<ScheduledPrayer>,
        lockDurationMin: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        append(IDENTITY_VERSION).append('|').append(zoneId.id).append('|').append(lockDurationMin)
        schedule.sortedBy { it.time }.forEach { item ->
            append('\n')
                .append(item.prayer.name)
                .append('|')
                .append(item.time.toLocalDate())
                .append('|')
                .append(item.time)
        }
    }

    /** True when only non-overlapping horizon edges changed. */
    fun sameGeneration(previousIdentity: String, newIdentity: String): Boolean {
        if (previousIdentity == newIdentity) return true
        val previous = parseGenerationIdentity(previousIdentity) ?: return false
        val new = parseGenerationIdentity(newIdentity) ?: return false
        if (previous.config != new.config) return false

        val previousDates = previous.triggers.keys.mapTo(mutableSetOf()) { it.substringAfter('|') }
        val newDates = new.triggers.keys.mapTo(mutableSetOf()) { it.substringAfter('|') }
        val overlapDates = previousDates intersect newDates
        return overlapDates.isNotEmpty() && overlapDates.all { date ->
            previous.triggers.filterKeys { it.endsWith("|$date") } ==
                new.triggers.filterKeys { it.endsWith("|$date") }
        }
    }

    private data class ParsedIdentity(
        val config: String,
        val triggers: Map<String, String>,
    )

    private fun parseGenerationIdentity(identity: String): ParsedIdentity? {
        val lines = identity.lineSequence().toList()
        val header = lines.firstOrNull()?.split('|') ?: return null
        if (header.size != 3 || header[0] != IDENTITY_VERSION) return null
        val triggers = lines.drop(1).associate { line ->
            val parts = line.split('|')
            if (parts.size != 3) return null
            "${parts[0]}|${parts[1]}" to parts[2]
        }
        return ParsedIdentity(config = "${header[1]}|${header[2]}", triggers = triggers)
    }
}
