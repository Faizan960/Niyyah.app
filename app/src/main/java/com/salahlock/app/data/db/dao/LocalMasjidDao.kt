package com.salahlock.app.data.db.dao

import androidx.room.*
import com.salahlock.app.data.db.entity.LocalMasjidEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMasjidDao {

    /** Observe the single local masjid row (id = 1). Emits null if not yet configured. */
    @Query("SELECT * FROM local_masjid WHERE id = 1 LIMIT 1")
    fun observe(): Flow<LocalMasjidEntity?>

    /** One-shot read — used by alarm scheduler and migration checks. */
    @Query("SELECT * FROM local_masjid WHERE id = 1 LIMIT 1")
    suspend fun get(): LocalMasjidEntity?

    /** Insert or fully replace the local masjid row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalMasjidEntity)

    /** Delete the local masjid configuration. */
    @Query("DELETE FROM local_masjid WHERE id = 1")
    suspend fun delete()
}
