package com.salahlock.app.data.repository

import android.content.Context
import android.util.Log
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
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
    private val clock: Clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
        override fun instant(): Instant = Instant.now()
    },
) {
    private val cacheMutex = Mutex()

    /** Returns today's schedule from the source selected by the user. */
    fun getTodayPrayers(): Flow<DailyPrayers?> = activeSchedule(
        date = { LocalDate.now(clock) },
        calculated = getCalculatedTodayPrayers(),
    )

    /** Returns tomorrow's schedule from the source selected by the user. */
    fun getTomorrowPrayers(): Flow<DailyPrayers?> = activeSchedule(
        date = { LocalDate.now(clock).plusDays(1) },
        calculated = getCalculatedTomorrowPrayers(),
    )

    /** Saved congregation schedule, exposed separately from actual prayer starts. */
    fun getLocalJamaatTimes(): Flow<LocalMasjidEntity?> = localMasjidDao.observe()

    fun getTomorrowFajr(): Flow<LocalDateTime?> =
        getTomorrowPrayers().map { it?.fajr }

    fun getNextPrayer(): Flow<PrayerTime?> =
        getTodayPrayers().map { daily ->
            daily?.nextPrayer(LocalDateTime.now())
        }

    private fun activeSchedule(
        date: () -> LocalDate,
        calculated: Flow<DailyPrayers?>,
    ): Flow<DailyPrayers?> = combine(
        prefs.prayerSource,
        localMasjidDao.observe(),
        calculated,
    ) { source, masjid, calculatedSchedule ->
        if (PrayerSource.fromString(source) == PrayerSource.LOCAL_MASJID && masjid != null) {
            masjid.toDailyPrayers(date(), calculatedSchedule?.sunrise) ?: calculatedSchedule
        } else {
            calculatedSchedule
        }
    }

    // ── API / Offline paths ─────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getCalculatedTodayPrayers(): Flow<DailyPrayers?> = combine(
        prefs.userLat,
        prefs.userLng,
        prefs.calcMethod,
        prefs.madhab,
        combine(prefs.lastPrayerSyncMethod, prefs.lastPrayerSyncMadhab) { method, madhab -> method to madhab },
    ) { lat, lng, method, madhab, cachedConfig ->
        PrayerPrefs(lat, lng, method, madhab, cachedConfig.first, cachedConfig.second)
    }.flatMapLatest { p ->
        if (p.lat == 0.0 && p.lng == 0.0) return@flatMapLatest flowOf(null)
        val today = LocalDate.now(clock)
        cacheDao.getPrayerTimesForDate(today.toString()).map { cached ->
            if (cached != null && cached.matches(p)) parseCached(cached, today)
            else calculateForDate(p.lat, p.lng, p.method, p.madhab, today)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getCalculatedTomorrowPrayers(): Flow<DailyPrayers?> = combine(
        prefs.userLat,
        prefs.userLng,
        prefs.calcMethod,
        prefs.madhab,
        combine(prefs.lastPrayerSyncMethod, prefs.lastPrayerSyncMadhab) { method, madhab -> method to madhab },
    ) { lat, lng, method, madhab, cachedConfig ->
        PrayerPrefs(lat, lng, method, madhab, cachedConfig.first, cachedConfig.second)
    }.flatMapLatest { p ->
        if (p.lat == 0.0 && p.lng == 0.0) return@flatMapLatest flowOf(null)
        val tomorrow = LocalDate.now(clock).plusDays(1)
        cacheDao.getPrayerTimesForDate(tomorrow.toString()).map { cached ->
            if (cached != null && cached.matches(p)) parseCached(cached, tomorrow)
            else calculateForDate(p.lat, p.lng, p.method, p.madhab, tomorrow)
        }
    }

    /** Background sync that hits the Aladhan API and stores results in the Room DB */
    suspend fun syncPrayerTimes(lat: Double, lng: Double, method: String, madhab: String) {
        if (lat == 0.0 && lng == 0.0) return
        val methodInt = mapMethodToInt(method)
        val schoolInt = if (madhab == "HANAFI") 1 else 0
        val now = LocalDate.now(clock)

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
            cacheMutex.withLock {
                cacheDao.insertAll(entities)
                prefs.recordPrayerSync(lat, lng, method, madhab)
            }
            Log.d("PrayerTimesRepo", "[TIMING] API sync cached ${entities.size} days method=$method madhab=$madhab")
        } catch (e: Exception) {
            Log.e("PrayerTimesRepo", "Sync failed: ${e.message}", e)
        }
    }

    // ── Local masjid parsing ────────────────────────────────────────────────

    internal fun LocalMasjidEntity.toDailyPrayers(
        date: LocalDate,
        calculatedSunrise: LocalDateTime? = null,
    ): DailyPrayers? {
        fun parse(value: String): LocalDateTime? = runCatching {
            LocalTime.parse(value.trim(), DateTimeFormatter.ofPattern("H:mm"))
        }.getOrNull()?.let(date::atTime)

        val fajrTime = parse(fajr) ?: return null
        val dhuhrTime = parse(dhuhr) ?: return null
        val asrTime = parse(asr) ?: return null
        val maghribTime = parse(maghrib) ?: return null
        val ishaTime = parse(isha) ?: return null
        val sunriseTime = calculatedSunrise
            ?.takeIf { it.toLocalDate() == date }
            ?: fajrTime.plusHours(1)

        return DailyPrayers(
            date = date.toString(),
            fajr = fajrTime,
            sunrise = sunriseTime,
            dhuhr = dhuhrTime,
            asr = asrTime,
            maghrib = maghribTime,
            isha = ishaTime,
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

    private fun calculateForDate(
        lat: Double,
        lng: Double,
        method: String,
        madhab: String,
        date: LocalDate,
    ): DailyPrayers {
        val coordinates = Coordinates(lat, lng)
        val dateComponents = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        val baseParameters = when (method) {
            "MWL"   -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            "ISNA"  -> CalculationMethod.NORTH_AMERICA.parameters
            "EGYPT" -> CalculationMethod.EGYPTIAN.parameters
            "MAKKAH" -> CalculationMethod.UMM_AL_QURA.parameters
            "QATAR" -> CalculationMethod.QATAR.parameters
            "DUBAI" -> CalculationMethod.DUBAI.parameters
            "SINGAPORE" -> CalculationMethod.SINGAPORE.parameters
            "TURKEY" -> CalculationMethod.TURKEY.parameters
            else    -> CalculationMethod.KARACHI.parameters
        }
        val parameters = baseParameters.copy(
            madhab = if (madhab == "HANAFI") Madhab.HANAFI else Madhab.SHAFI,
        )
        val prayerTimes = PrayerTimes(coordinates, dateComponents, parameters)
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

    private data class PrayerPrefs(
        val lat: Double,
        val lng: Double,
        val method: String,
        val madhab: String,
        val cachedMethod: String,
        val cachedMadhab: String,
    )

    private fun PrayerTimeCacheEntity.matches(prefs: PrayerPrefs): Boolean =
        calculationMethod == mapMethodToInt(prefs.method) &&
            prefs.cachedMethod == prefs.method &&
            prefs.cachedMadhab == prefs.madhab &&
            kotlin.math.abs(lat - prefs.lat) < 0.0001 &&
            kotlin.math.abs(lng - prefs.lng) < 0.0001

    suspend fun calculateUpcoming(
        lat: Double,
        lng: Double,
        method: String,
        madhab: String,
        today: LocalDate = LocalDate.now(clock),
    ): List<DailyPrayers> {
        if (PrayerSource.fromString(prefs.prayerSource.first()) == PrayerSource.LOCAL_MASJID) {
            val masjid = localMasjidDao.get()
            if (masjid != null) {
                val localSchedule = (0 until 30).mapNotNull { offset ->
                    masjid.toDailyPrayers(today.plusDays(offset.toLong()))
                }
                if (localSchedule.size == 30) {
                    Log.d("PrayerSync", "[TIMING] Source=LOCAL_MASJID name=${masjid.masjidName}")
                    return localSchedule
                }
                Log.e("PrayerSync", "Invalid Local Masjid timetable; falling back to calculated schedule")
            }
        }
        if (lat == 0.0 && lng == 0.0) return emptyList()

        var cachedCount = 0
        var offlineCount = 0
        val (prayerPrefs, cachedByDate) = cacheMutex.withLock {
            val snapshotPrefs = PrayerPrefs(
                lat,
                lng,
                method,
                madhab,
                prefs.lastPrayerSyncMethod.first(),
                prefs.lastPrayerSyncMadhab.first(),
            )
            val cachedRows = (0 until 30).associate { offset ->
                val date = today.plusDays(offset.toLong())
                date to cacheDao.getPrayerTimesForDateSync(date.toString())
            }
            snapshotPrefs to cachedRows
        }
        Log.d("PrayerSync", "[TIMING] Source=CALCULATED_API_OR_OFFLINE method=$method madhab=$madhab")

        val result = (0 until 30).map { offset ->
            val date = today.plusDays(offset.toLong())
            val cached = cachedByDate[date]
            if (cached != null && cached.matches(prayerPrefs)) {
                cachedCount++
                parseCached(cached, date)
            } else {
                offlineCount++
                calculateForDate(lat, lng, method, madhab, date)
            }
        }
        result.firstOrNull()?.toList()?.forEach { prayer ->
            Log.d("PrayerSync", "[TIMING] ${prayer.name.name}Start=${prayer.time}")
        }
        Log.d("PrayerSync", "calculateUpcoming: $cachedCount days from API cache, $offlineCount days from offline calc.")
        return result
    }
}
