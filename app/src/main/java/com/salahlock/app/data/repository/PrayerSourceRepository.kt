package com.salahlock.app.data.repository

import com.salahlock.app.data.db.dao.LocalMasjidDao
import com.salahlock.app.data.db.entity.LocalMasjidEntity
import com.salahlock.app.data.model.PrayerSource
import com.salahlock.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for the prayer time source preference
 * and local masjid configuration.
 *
 * Future support:
 *  - Community masjid database: add a `communityMasjidDao` here
 *  - Friday overrides: add `getFridayJumahTime()` delegating to `LocalMasjidEntity.fridayJumah`
 *  - Ramadan timetable: add `getRamadanTimings()` delegating to seasonal fields
 */
class PrayerSourceRepository(
    private val prefs: UserPreferences,
    private val localMasjidDao: LocalMasjidDao,
) {
    /** The currently active prayer time source. Defaults to [PrayerSource.API]. */
    fun getPrayerSource(): Flow<PrayerSource> =
        prefs.prayerSource.map { PrayerSource.fromString(it) }

    suspend fun setPrayerSource(source: PrayerSource) {
        prefs.setPrayerSource(source.name)
    }

    /** Observes the local masjid configuration. Emits null if not yet set up. */
    fun getLocalMasjid(): Flow<LocalMasjidEntity?> = localMasjidDao.observe()

    suspend fun getLocalMasjidOnce(): LocalMasjidEntity? = localMasjidDao.get()

    /** Save or update the local masjid timetable. Always uses id = 1 (single-row). */
    suspend fun saveLocalMasjid(entity: LocalMasjidEntity) {
        localMasjidDao.upsert(entity.copy(id = 1, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Returns the display name for the home screen header.
     * Shows masjid name when LOCAL_MASJID is active; null otherwise (city name is used).
     */
    fun getActiveMasjidName(): Flow<String?> =
        localMasjidDao.observe().map { masjid ->
            if (masjid?.enabled == true) masjid.masjidName.ifBlank { null } else null
        }

    /** Deletes the local masjid configuration and resets source to API. */
    suspend fun clearLocalMasjid() {
        localMasjidDao.delete()
        setPrayerSource(PrayerSource.API)
    }
}
