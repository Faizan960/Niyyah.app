package com.salahlock.app.data.repository

import com.salahlock.app.data.db.dao.EmergencyOverrideDao
import com.salahlock.app.data.db.entity.EmergencyOverride
import com.salahlock.app.data.model.OverrideState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val MAX_OVERRIDES_PER_MONTH = 3

enum class OverrideReason(val displayText: String) {
    MEDICAL("Medical emergency"),
    FAMILY_EMERGENCY("Family emergency"),
    TRAVELLING("Travelling / in transit"),
    WORK_CRITICAL("Critical work situation"),
    OTHER("Other reason"),
}

class EmergencyOverrideRepository(
    private val dao: EmergencyOverrideDao,
) {
    // Emergency overrides are part of DEVICE-LOCAL prayer tracking: auth-independent,
    // must survive sign-in/out. Always scoped to the fixed local owner (never the
    // Clerk-driven ActiveOwnerProvider, whose signed-out scope hides all rows).
    private val owner = com.salahlock.app.data.db.entity.OwnerIds.LOCAL

    private fun currentMonthKey(): String {
        val now = LocalDate.now()
        return "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
    }

    suspend fun getOverrideState(): OverrideState {
        val monthKey = currentMonthKey()
        val record = dao.getForMonth(owner, monthKey)
        val used = record?.count ?: 0
        return OverrideState(
            usedThisMonth = used,
            maxPerMonth = MAX_OVERRIDES_PER_MONTH,
            canOverride = used < MAX_OVERRIDES_PER_MONTH,
        )
    }

    /** Records an override use and returns true if successful, false if limit reached. */
    suspend fun useOverride(reason: OverrideReason): Boolean {
        val monthKey = currentMonthKey()
        val existing = dao.getForMonth(owner, monthKey)
        val currentCount = existing?.count ?: 0

        if (currentCount >= MAX_OVERRIDES_PER_MONTH) return false

        dao.upsert(
            EmergencyOverride(
                id = existing?.id ?: 0,
                monthYear = monthKey,
                count = currentCount + 1,
                lastReason = reason.name,
                lastUsedMs = System.currentTimeMillis(),
                ownerId = owner,
            )
        )
        return true
    }
}
