package com.salahlock.app.data.repository

import androidx.room.withTransaction
import com.salahlock.app.data.db.AppDatabase
import com.salahlock.app.data.db.dao.EmergencyOverrideDao
import com.salahlock.app.data.db.dao.PrayerRecordDao
import com.salahlock.app.data.db.entity.EmergencyOverride
import com.salahlock.app.data.model.OverrideState
import com.salahlock.app.data.model.PrayerName
import java.time.LocalDate

const val MAX_OVERRIDES_PER_MONTH = 3

enum class OverrideReason(val displayText: String) {
    MEDICAL("Medical emergency"),
    FAMILY_EMERGENCY("Family emergency"),
    TRAVELLING("Travelling / in transit"),
    WORK_CRITICAL("Critical work situation"),
    OTHER("Other reason"),
}

/**
 * Outcome of an emergency-override attempt for a specific prayer. Carries the resulting
 * [OverrideState] so the caller can update the UI without a second read. Only [Consumed]
 * and [AlreadyHandled] permit unlocking; [NoAllowance] must NOT unlock.
 */
sealed interface OverrideResult {
    val state: OverrideState

    /** Exactly one override consumed; the prayer was marked and the streak recomputed atomically. */
    data class Consumed(override val state: OverrideState) : OverrideResult

    /** This prayer was already overridden/verified — nothing consumed; safe to dismiss the lock. */
    data class AlreadyHandled(override val state: OverrideState) : OverrideResult

    /** Monthly allowance exhausted — nothing consumed and the lock must remain. */
    data class NoAllowance(override val state: OverrideState) : OverrideResult
}

class EmergencyOverrideRepository(
    private val db: AppDatabase,
    private val dao: EmergencyOverrideDao,
    private val prayerRecordDao: PrayerRecordDao,
    private val streakRepository: StreakRepository,
) {
    // Emergency overrides are part of DEVICE-LOCAL prayer tracking: auth-independent,
    // must survive sign-in/out. Always scoped to the fixed local owner (never the
    // Clerk-driven ActiveOwnerProvider, whose signed-out scope hides all rows).
    private val owner = com.salahlock.app.data.db.entity.OwnerIds.LOCAL

    private fun currentMonthKey(): String {
        val now = LocalDate.now()
        return "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
    }

    suspend fun getOverrideState(): OverrideState = readState(currentMonthKey())

    private suspend fun readState(monthKey: String): OverrideState {
        val used = dao.getForMonth(owner, monthKey)?.count ?: 0
        return OverrideState(
            usedThisMonth = used,
            maxPerMonth = MAX_OVERRIDES_PER_MONTH,
            canOverride = used < MAX_OVERRIDES_PER_MONTH,
        )
    }

    /**
     * Atomically consumes exactly one monthly emergency override for [prayer] on [date].
     *
     * Everything below happens inside a single Room transaction so the counter increment,
     * the prayer's `overrideUsed` mark, and the streak recompute succeed or fail together —
     * there is no window where an override is charged but the prayer is unrecorded (or vice
     * versa).
     *
     * Idempotent / duplicate-safe: if the prayer is already overridden or already verified,
     * nothing is consumed (repeated taps, UI lag, or re-opening the overlay cost no
     * allowance). When the monthly limit is reached, nothing is written and the caller must
     * keep the lock in place.
     */
    suspend fun useOverrideForPrayer(
        date: String,
        prayer: PrayerName,
        reason: OverrideReason,
    ): OverrideResult = db.withTransaction {
        val monthKey = currentMonthKey()

        // Duplicate protection keyed on the exact (owner, date, prayer): an already
        // handled prayer never consumes another allowance and never downgrades a
        // genuine verification into an override.
        val existing = prayerRecordDao.getRecord(owner, date, prayer.name)
        if (existing != null && (existing.overrideUsed || existing.verified)) {
            return@withTransaction OverrideResult.AlreadyHandled(readState(monthKey))
        }

        val monthRow = dao.getForMonth(owner, monthKey)
        val used = monthRow?.count ?: 0
        if (used >= MAX_OVERRIDES_PER_MONTH) {
            return@withTransaction OverrideResult.NoAllowance(
                OverrideState(used, MAX_OVERRIDES_PER_MONTH, canOverride = false)
            )
        }

        dao.upsert(
            EmergencyOverride(
                id = monthRow?.id ?: 0,
                monthYear = monthKey,
                count = used + 1,
                lastReason = reason.name,
                lastUsedMs = System.currentTimeMillis(),
                ownerId = owner,
            )
        )
        // recordVerification marks overrideUsed=true and recomputes the streak. Because we
        // are inside db.withTransaction, its DAO writes join THIS transaction.
        streakRepository.recordVerification(date, prayer, wasOverride = true)

        OverrideResult.Consumed(
            OverrideState(
                usedThisMonth = used + 1,
                maxPerMonth = MAX_OVERRIDES_PER_MONTH,
                canOverride = used + 1 < MAX_OVERRIDES_PER_MONTH,
            )
        )
    }
}
