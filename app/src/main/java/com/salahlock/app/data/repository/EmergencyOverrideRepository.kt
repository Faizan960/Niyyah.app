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
    // BM-013 — active owner seam; resolves to __local__ during the local foundation.
    private val activeOwner: com.salahlock.app.data.sync.ActiveOwnerProvider =
        com.salahlock.app.data.sync.ActiveOwnerProvider.shared,
) {
    private val owner get() = activeOwner.ownerId()

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
