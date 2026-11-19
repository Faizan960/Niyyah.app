package com.salahlock.app.data.repository

import com.salahlock.app.data.db.dao.EmergencyOverrideDao
import com.salahlock.app.data.db.entity.EmergencyOverrideEvent
import com.salahlock.app.data.db.entity.OwnerIds
import com.salahlock.app.data.model.PrayerName
import java.util.UUID

enum class OverrideReason(val displayText: String) {
    MEDICAL("Medical emergency"),
    FAMILY_EMERGENCY("Family emergency"),
    TRAVELLING("Travelling / in transit"),
    WORK_CRITICAL("Critical work situation"),
    OTHER("Other reason"),
}

/**
 * A successfully persisted override. There is deliberately no allowance/denial outcome:
 * emergency overrides are unlimited.
 */
sealed interface OverrideResult {
    data class Recorded(val eventId: String) : OverrideResult
}

class EmergencyOverrideRepository(
    private val dao: EmergencyOverrideDao,
) {
    /**
     * Appends one local event and does not touch prayer_records or streaks. The UI owns its
     * rapid-tap latch; each repository call represents a distinct confirmed override.
     */
    suspend fun useOverrideForPrayer(
        date: String,
        prayer: PrayerName,
        reason: OverrideReason,
    ): OverrideResult {
        val eventId = UUID.randomUUID().toString()
        dao.insert(
            EmergencyOverrideEvent(
                eventId = eventId,
                prayerIdentifier = prayer.name,
                reason = reason.name,
                timestampMs = System.currentTimeMillis(),
                date = date,
                ownerId = OwnerIds.LOCAL,
            )
        )
        return OverrideResult.Recorded(eventId)
    }
}
