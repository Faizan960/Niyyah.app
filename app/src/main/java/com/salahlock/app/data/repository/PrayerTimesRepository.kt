package com.salahlock.app.data.repository

import android.content.Context
import android.util.Log
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import com.salahlock.app.data.api.AladhanApiService
import com.salahlock.app.data.db.dao.LocalMasjidDao
import com.salahlock.app.data.db.dao.PrayerTimeCacheDao
import com.salahlock.app.data.db.entity.LocalMasjidEntity
import com.salahlock.app.data.db.entity.PrayerTimeCacheEntity
import com.salahlock.app.data.model.DailyPrayers
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.data.model.PrayerSource
import com.salahlock.app.data.model.PrayerTime
import com.salahlock.app.data.preferences.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PrayerTimesRepository(
    private val context: Context,
    private val prefs: UserPreferences,
    private val cacheDao: PrayerTimeCacheDao,
    private val apiService: AladhanApiService,
    private val localMasjidDao: LocalMasjidDao,
) {
    /**
     * Returns today's prayer times.
     *
     * Routing:
     *  - [PrayerSource.LOCAL_MASJID] — uses user-entered masjid timings from Room.
     *  - [PrayerSource.API] — uses Aladhan API cache, falls back to offline Adhan2 calculation.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTodayPrayers(): Flow<DailyPrayers?> =
        prefs.prayerSource.flatMapLatest { sourceStr ->
            when (PrayerSource.fromString(sourceStr)) {
                // The active SOURCE preference is the sole authority — a saved masjid is
                // used whenever LOCAL_MASJID is selected, regardless of the legacy
                // `enabled` flag. Existence of a masjid never decides the active source.
                PrayerSource.LOCAL_MASJID -> localMasjidDao.observe().map { masjid ->
                    masjid?.toTodayDailyPrayers()
                }
                PrayerSource.API -> getApiTodayPrayers()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTomorrowPrayers(): Flow<DailyPrayers?> =
        prefs.prayerSource.flatMapLatest { sourceStr ->
            when (PrayerSource.fromString(sourceStr)) {
                PrayerSource.LOCAL_MASJID -> localMasjidDao.observe().map { masjid ->
                    masjid?.toDailyPrayers(LocalDate.now().plusDays(1))
                }
                PrayerSource.API -> getApiTomorrowPrayers()
            }
        }

    fun getTomorrowFajr(): Flow<LocalDateTime?> =
        getTomorrowPrayers().map { it?.fajr }

    fun getNextPrayer(): Flow<PrayerTime?> =
        getTodayPrayers().map { daily ->
            daily?.nextPrayer(LocalDateTime.now())
        }

    // ── API / Offline paths ─────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getApiTodayPrayers(): Flow<DailyPrayers?> = combine(
        prefs.userLat, prefs.userLng, prefs.calcMethod, prefs.madhab
    ) { lat, lng, method, madhab ->
        PrayerPrefs(lat, lng, method, madhab)
    }.flatMapLatest { p ->
        if (p.lat == 0.0 && p.lng == 0.0) return@flatMapLatest flowOf(null)
        val today = LocalDate.now()
        cacheDao.getPrayerTimesForDate(today.toString()).map { cached ->
            if (cached != null) parseCached(cached, today)
            else calculateForDate(p.lat, p.lng, p.method, today)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getApiTomorrowPrayers(): Flow<DailyPrayers?> = combine(
        prefs.userLat, prefs.userLng, prefs.calcMethod, prefs.madhab
    ) { lat, lng, method, madhab ->
        PrayerPrefs(lat, lng, method, madhab)
    }.flatMapLatest { p ->
        if (p.lat == 0.0 && p.lng == 0.0) return@flatMapLatest flowOf(null)
        val tomorrow = LocalDate.now().plusDays(1)
        cacheDao.getPrayerTimesForDate(tomorrow.toString()).map { cached ->
            if (cached != null) parseCached(cached, tomorrow)
            else calculateForDate(p.lat, p.lng, p.method, tomorrow)
        }
    }

    /** Background sync that hits the Aladhan API and stores results in the Room DB */
    suspend fun syncPrayerTimes(lat: Double, lng: Double, method: String, madhab: String) {
        if (lat == 0.0 && lng == 0.0) return
        val methodInt = mapMethodToInt(method)
        val schoolInt = if (madhab == "HANAFI") 1 else 0
        val now = LocalDate.now()

        try {
            val response = apiService.getCalendar(
                latitude = lat,
                longitude = lng,
                method = methodInt,
                month = now.monthValue,
                year = now.year,
                school = schoolInt
            )

            val entities = response.data.map { aladhanData ->
                val dateStr = aladhanData.date.gregorian.date // "dd-MM-yyyy"
                val parsedDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                PrayerTimeCacheEntity(
                    dateString = parsedDate.toString(),
                    fajr = aladhanData.timings.Fajr,
                    sunrise = aladhanData.timings.Sunrise,
                    dhuhr = aladhanData.timings.Dhuhr,
                    asr = aladhanData.timings.Asr,
                    maghrib = aladhanData.timings.Maghrib,
                    isha = aladhanData.timings.Isha,
                    calculationMethod = methodInt,
                    lat = lat,
                    lng = lng,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            cacheDao.insertAll(entities)
            Log.d("PrayerTimesRepo", "Sync complete — cached ${entities.size} days for $method at ($lat,$lng).")
            prefs.recordPrayerSync(lat, lng)
        } catch (e: Exception) {
            Log.e("PrayerTimesRepo", "Sync failed: ${e.message}", e)
        }
    }

    // ── Local masjid parsing ────────────────────────────────────────────────

    private fun LocalMasjidEntity.toTodayDailyPrayers(): DailyPrayers =
        toDailyPrayers(LocalDate.now())

    private fun LocalMasjidEntity.toDailyPrayers(date: LocalDate): DailyPrayers {
        fun parse(hhmm: String): LocalDateTime {
            val time = runCatching {
                LocalTime.parse(hhmm.trim(), DateTimeFormatter.ofPattern("HH:mm"))
            }.getOrElse { LocalTime.MIDNIGHT }
            return LocalDateTime.of(date, time)
        }
        // Sunrise is not collected for local masjid — use a reasonable default (1 hour after Fajr)
        val fajrDt = parse(fajr)
        return DailyPrayers(
            date = date.toString(),
            fajr = fajrDt,
            sunrise = fajrDt.plusHours(1),
            dhuhr = parse(dhuhr),
            asr = parse(asr),
            maghrib = parse(maghrib),
            isha = parse(isha),
            isOfflineFallback = false,
        )
    }

    // ── API cache parsing ───────────────────────────────────────────────────

    private fun parseCached(entity: PrayerTimeCacheEntity, date: LocalDate): DailyPrayers {
        fun toLocal(timeStr: String): LocalDateTime {
            val clean = timeStr.split(" ")[0]
            val time = LocalTime.parse(clean, DateTimeFormatter.ofPattern("HH:mm"))
            return LocalDateTime.of(date, time)
        }
        return DailyPrayers(
            date = date.toString(),
            fajr = toLocal(entity.fajr),
            sunrise = toLocal(entity.sunrise),
            dhuhr = toLocal(entity.dhuhr),
            asr = toLocal(entity.asr),
            maghrib = toLocal(entity.maghrib),
            isha = toLocal(entity.isha)
        )
    }

    private fun calculateForDate(lat: Double, lng: Double, method: String, date: LocalDate): DailyPrayers {
        val coordinates = Coordinates(lat, lng)
        val dateComponents = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        val calcMethod = when (method) {
            "MWL"   -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            "ISNA"  -> CalculationMethod.NORTH_AMERICA.parameters
            "EGYPT" -> CalculationMethod.EGYPTIAN.parameters
            else    -> CalculationMethod.KARACHI.parameters
        }
        val prayerTimes = PrayerTimes(coordinates, dateComponents, calcMethod)
        val zone = ZoneId.systemDefault()

        @Suppress("DEPRECATION")
        fun toLocal(adhanInstant: kotlinx.datetime.Instant): LocalDateTime =
            Instant.ofEpochMilli(adhanInstant.toEpochMilliseconds()).atZone(zone).toLocalDateTime()

        return DailyPrayers(
            date    = date.toString(),
            fajr    = toLocal(prayerTimes.fajr),
            sunrise = toLocal(prayerTimes.sunrise),
            dhuhr   = toLocal(prayerTimes.dhuhr),
            asr     = toLocal(prayerTimes.asr),
            maghrib = toLocal(prayerTimes.maghrib),
            isha    = toLocal(prayerTimes.isha),
            isOfflineFallback = true
        )
    }

    private fun mapMethodToInt(method: String): Int = when (method) {
        "KARACHI" -> 1; "ISNA" -> 2; "MWL" -> 3; "MAKKAH" -> 4; "EGYPT" -> 5
        "QATAR" -> 11; "DUBAI" -> 16; "SINGAPORE" -> 15; "TURKEY" -> 13
        else -> 1
    }

    private data class PrayerPrefs(val lat: Double, val lng: Double, val method: String, val madhab: String)

    suspend fun calculateUpcoming(lat: Double, lng: Double, method: String): List<DailyPrayers> {
        val today = LocalDate.now()
        var cachedCount = 0
        var offlineCount = 0

        // Lock scheduling MUST consume the same active source as Home / Next Prayer.
        // Decide on the authoritative [PrayerSource] preference — NOT masjid.enabled —
        // so a user on GPS never gets lock alarms scheduled from stale masjid times
        // (and vice versa).
        val source = PrayerSource.fromString(prefs.prayerSource.first())
        val localMasjid = localMasjidDao.get()
        if (source == PrayerSource.LOCAL_MASJID && localMasjid != null) {
            Log.d("PrayerSync", "calculateUpcoming: using LOCAL_MASJID timings for all 30 days.")
            return (0 until 30).map { offset ->
                localMasjid.toDailyPrayers(today.plusDays(offset.toLong()))
            }
        }

        val result = (0 until 30).map { offset ->
            val date = today.plusDays(offset.toLong())
            val cached = cacheDao.getPrayerTimesForDateSync(date.toString())
            if (cached != null) { cachedCount++; parseCached(cached, date) }
            else { offlineCount++; calculateForDate(lat, lng, method, date) }
        }
        Log.d("PrayerSync", "calculateUpcoming: $cachedCount days from API cache, $offlineCount days from offline calc.")
        return result
    }
}
