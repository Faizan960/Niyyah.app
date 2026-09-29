package com.salahlock.app.data.repository

import com.salahlock.app.data.db.dao.PrayerRecordDao
import com.salahlock.app.data.db.dao.StreakDao
import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.StreakEntity
import com.salahlock.app.data.db.entity.VerificationType
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.data.model.SpiritualRank
import com.salahlock.app.data.model.StreakInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

class StreakRepository(
    private val prayerRecordDao: PrayerRecordDao,
    private val streakDao: StreakDao,
) {
    // Prayer tracking is DEVICE-LOCAL and auth-independent by design: it must survive
    // sign-in, sign-out, and every auth-state change. So streaks/records are always
    // scoped to the fixed local owner — never to the Clerk-driven ActiveOwnerProvider
    // (whose SignedOutNoUser scope resolves to "__none__" and would hide all rows).
    // Only cloud-synced data (Quran bookmarks/collections) is per-account.
    private val owner = com.salahlock.app.data.db.entity.OwnerIds.LOCAL

    fun observeStreakInfo(): Flow<StreakInfo> =
        streakDao.observeStreak(owner).map { entity ->
            val e = entity ?: StreakEntity()
            StreakInfo(
                currentStreak = e.currentStreak,
                bestStreak = e.bestStreak,
                rank = SpiritualRank.fromStreak(e.currentStreak),
                mercyAvailableThisWeek = !e.mercyUsedThisWeek,
            )
        }

    suspend fun recordVerification(
        date: String,
        prayer: PrayerName,
        wasOverride: Boolean,
        verificationType: VerificationType = if (wasOverride) VerificationType.SELF_REPORTED else VerificationType.LOCK_VERIFIED,
    ) {
        val record = PrayerRecord(
            date = date,
            prayerName = prayer.name,
            verified = !wasOverride,
            overrideUsed = wasOverride,
            verificationType = verificationType.name,
            ownerId = owner,
        )
        prayerRecordDao.upsert(record)
        android.util.Log.d("StreakRepo", "Recorded $prayer on $date as ${verificationType.name}")
        recalculateStreak()
    }

    private suspend fun recalculateStreak() {
        val today = LocalDate.now()
        val fromDate = today.minusDays(35).toString()
        val records = prayerRecordDao.getRecentRecords(owner, fromDate)
        val byDay = records.groupBy { it.date }

        val currentStreak = calculateStreak(byDay, today)
        val existingStreak = streakDao.getStreak(owner) ?: StreakEntity(ownerId = owner)
        val bestStreak = maxOf(existingStreak.bestStreak, currentStreak)

        val weekKey = isoWeekKey(today)
        val mercyUsed = existingStreak.lastMercyWeek == weekKey && existingStreak.mercyUsedThisWeek

        streakDao.upsert(
            existingStreak.copy(
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                lastFullDay = today.toString(),
                mercyUsedThisWeek = mercyUsed,
                ownerId = owner,
            )
        )
    }

    /** Counts consecutive days where 5/5 prayers verified (or 1 mercy day allowed per week). */
    private fun calculateStreak(byDay: Map<String, List<PrayerRecord>>, today: LocalDate): Int {
        var streak = 0
        var mercyUsedThisWeek = false

        for (i in 0 until 30) {
            val date = today.minusDays(i.toLong())
            val dayRecords = byDay[date.toString()] ?: emptyList()
            val verifiedCount = dayRecords.count { it.verified || it.overrideUsed }

            val weekKey = isoWeekKey(date)
            val isCurrentWeek = weekKey == isoWeekKey(today)

            when {
                verifiedCount >= 5 -> streak++
                verifiedCount >= 4 && !mercyUsedThisWeek -> {
                    // Mercy: 1 missed prayer allowed per week
                    streak++
                    if (!isCurrentWeek) mercyUsedThisWeek = true
                }
                i == 0 -> continue // Don't break on today (day might still be incomplete)
                else -> break
            }
        }
        return streak
    }

    /**
     * Returns the ISO 8601 week identifier for a date, e.g. "2025-W23".
     * Uses WeekFields.ISO (Monday start, minimum 4 days) — fixes the previous
     * dayOfYear/7 approximation which produced incorrect week boundaries.
     */
    private fun isoWeekKey(date: LocalDate): String {
        val weekFields = WeekFields.of(Locale.getDefault()).let { WeekFields.ISO }
        val year = date.get(weekFields.weekBasedYear())
        val week = date.get(weekFields.weekOfWeekBasedYear())
        return "$year-W${week.toString().padStart(2, '0')}"
    }
}
