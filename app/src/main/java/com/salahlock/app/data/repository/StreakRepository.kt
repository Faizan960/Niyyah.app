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

        val currentStreak = StreakCalculator.calculate(byDay, today)
        val existingStreak = streakDao.getStreak(owner) ?: StreakEntity(ownerId = owner)
        val bestStreak = maxOf(existingStreak.bestStreak, currentStreak)

        streakDao.upsert(
            existingStreak.copy(
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                lastFullDay = today.toString(),
                ownerId = owner,
            )
        )
    }
}
