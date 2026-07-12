package com.salahlock.app.data.model

import java.time.LocalDateTime

enum class PrayerName(val displayName: String, val arabicName: String) {
    FAJR("Fajr", "الفجر"),
    SUNRISE("Sunrise", "الشروق"),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء"),
}

data class PrayerTime(
    val name: PrayerName,
    val time: LocalDateTime,
)

data class DailyPrayers(
    val date: String, // "2025-06-04"
    val fajr: LocalDateTime,
    val sunrise: LocalDateTime,
    val dhuhr: LocalDateTime,
    val asr: LocalDateTime,
    val maghrib: LocalDateTime,
    val isha: LocalDateTime,
    val isOfflineFallback: Boolean = false,
) {
    fun toList(): List<PrayerTime> = listOf(
        PrayerTime(PrayerName.FAJR, fajr),
        PrayerTime(PrayerName.DHUHR, dhuhr),
        PrayerTime(PrayerName.ASR, asr),
        PrayerTime(PrayerName.MAGHRIB, maghrib),
        PrayerTime(PrayerName.ISHA, isha),
    )

    fun toFullList(): List<PrayerTime> = listOf(
        PrayerTime(PrayerName.FAJR, fajr),
        PrayerTime(PrayerName.SUNRISE, sunrise),
        PrayerTime(PrayerName.DHUHR, dhuhr),
        PrayerTime(PrayerName.ASR, asr),
        PrayerTime(PrayerName.MAGHRIB, maghrib),
        PrayerTime(PrayerName.ISHA, isha),
    )

    /**
     * Returns the next upcoming prayer for today.
     * Returns null if all today's prayers have passed (use [nextPrayerWithTomorrow] instead).
     */
    fun nextPrayer(now: LocalDateTime): PrayerTime? =
        toList().firstOrNull { it.time.isAfter(now) }

    /**
     * Returns the next upcoming prayer; if all today's prayers have passed,
     * [tomorrowFajr] is returned so the UI always has a "next prayer".
     */
    fun nextPrayerWithTomorrow(now: LocalDateTime, tomorrowFajr: LocalDateTime?): PrayerTime? =
        nextPrayer(now) ?: tomorrowFajr?.let { PrayerTime(PrayerName.FAJR, it) }

    /**
     * Returns the currently active prayer (the last one whose adhan has called).
     * Returns null before Fajr.
     */
    fun currentPrayer(now: LocalDateTime): PrayerTime? =
        toList().lastOrNull { !it.time.isAfter(now) }

    /**
     * Returns true if [now] is within the prayer window for [prayer].
     * Window ends when the next prayer begins.
     */
    fun isWithinWindow(prayer: PrayerName, now: LocalDateTime): Boolean {
        val prayers = toList()
        val idx = prayers.indexOfFirst { it.name == prayer }
        if (idx < 0) return false
        val start = prayers[idx].time
        val end = if (idx + 1 < prayers.size) prayers[idx + 1].time else isha.plusHours(2)
        return !now.isBefore(start) && now.isBefore(end)
    }
}

data class LockWindowState(
    val isActive: Boolean = false,
    val prayer: PrayerName? = null,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
)

/** Result from camera/image verifier — kept for future ML integration */
sealed class VerificationResult {
    data object Verified : VerificationResult()
    data object TooDark : VerificationResult()
    data object NotPointingDown : VerificationResult()
    data object NoTexture : VerificationResult()
    data class Error(val message: String) : VerificationResult()
}

data class StreakInfo(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val rank: SpiritualRank = SpiritualRank.AL_MUBTADI,
    val mercyAvailableThisWeek: Boolean = true,
)

enum class SpiritualRank(
    val arabicName: String,
    val translation: String,
    val minDays: Int,
    val icon: String,
) {
    AL_MUBTADI("المبتدئ", "The Beginner", 0, "🌱"),
    AL_MUNTAZIM("المنتظم", "The Regular", 7, "🌙"),
    AL_MUWAZZAF("الموظف", "The Committed", 30, "⭐"),
    AL_HAFIZ("الحافظ", "The Guardian", 90, "✨");

    companion object {
        fun fromStreak(days: Int): SpiritualRank =
            values().reversed().firstOrNull { days >= it.minDays } ?: AL_MUBTADI
    }
}

data class OverrideState(
    val usedThisMonth: Int = 0,
    val maxPerMonth: Int = 3,
    val canOverride: Boolean = true,
)
