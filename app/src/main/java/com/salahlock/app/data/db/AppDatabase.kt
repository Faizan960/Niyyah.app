package com.salahlock.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.salahlock.app.data.db.dao.AppBlacklistDao
import com.salahlock.app.data.db.dao.EmergencyOverrideDao
import com.salahlock.app.data.db.dao.PrayerRecordDao
import com.salahlock.app.data.db.dao.StreakDao
import com.salahlock.app.data.db.entity.AppBlacklistItem
import com.salahlock.app.data.db.entity.EmergencyOverride
import com.salahlock.app.data.db.entity.MasjidEntity
import com.salahlock.app.data.db.entity.PrayerRecord
import com.salahlock.app.data.db.entity.StreakEntity
import com.salahlock.app.data.db.dao.PrayerTimeCacheDao
import com.salahlock.app.data.db.entity.PrayerTimeCacheEntity
import com.salahlock.app.data.db.entity.HadithEntity
import com.salahlock.app.data.db.entity.CategoryMappingEntity
import com.salahlock.app.data.db.entity.CollectionBookEntity
import com.salahlock.app.data.db.entity.AzkarEntity
import com.salahlock.app.data.db.dao.HadithDao
import com.salahlock.app.data.db.dao.AzkarDao
import com.salahlock.app.data.db.dao.LocalMasjidDao
import com.salahlock.app.data.db.entity.LocalMasjidEntity
import com.salahlock.app.data.db.dao.QuranDao
import com.salahlock.app.data.db.entity.QuranBookmarkEntity
import com.salahlock.app.data.db.entity.QuranProgressEntity

@Database(
    entities = [
        PrayerRecord::class,
        StreakEntity::class,
        EmergencyOverride::class,
        AppBlacklistItem::class,
        PrayerTimeCacheEntity::class,
        HadithEntity::class,
        CategoryMappingEntity::class,
        CollectionBookEntity::class,
        AzkarEntity::class,
        MasjidEntity::class,
        LocalMasjidEntity::class,
        QuranBookmarkEntity::class,
        QuranProgressEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prayerRecordDao(): PrayerRecordDao
    abstract fun streakDao(): StreakDao
    abstract fun emergencyOverrideDao(): EmergencyOverrideDao
    abstract fun appBlacklistDao(): AppBlacklistDao
    abstract fun prayerTimeCacheDao(): PrayerTimeCacheDao
    abstract fun hadithDao(): HadithDao
    abstract fun azkarDao(): AzkarDao
    // SL-021: masjidDao() removed with the dead nearby-masjid feature. MasjidEntity
    // stays in the entities list — dropping it would be a schema change (migration).
    abstract fun localMasjidDao(): LocalMasjidDao
    abstract fun quranDao(): QuranDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * Migration 1 → 2: Added prayer_time_cache table.
         * User data (prayer_records, streaks, overrides, blacklist) untouched.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS prayer_time_cache (
                        dateString TEXT NOT NULL,
                        fajr TEXT NOT NULL,
                        sunrise TEXT NOT NULL,
                        dhuhr TEXT NOT NULL,
                        asr TEXT NOT NULL,
                        maghrib TEXT NOT NULL,
                        isha TEXT NOT NULL,
                        calculationMethod INTEGER NOT NULL,
                        lat REAL NOT NULL,
                        lng REAL NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        PRIMARY KEY(dateString)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration 2 → 3: Added hadith_table, hadith_category_mapping (v3 schema),
         * and azkar_table. Knowledge data is synced from network/assets — not user data.
         * User data untouched.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hadith_table (
                        id TEXT NOT NULL,
                        collection TEXT NOT NULL,
                        bookNumber TEXT NOT NULL,
                        hadithNumber TEXT NOT NULL,
                        arabicText TEXT NOT NULL,
                        translationText TEXT NOT NULL,
                        language TEXT NOT NULL,
                        isBookmarked INTEGER NOT NULL DEFAULT 0,
                        lastReadTimestamp INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                // v3 category mapping schema (hadithId/category/subcategory columns).
                // This schema is completely replaced in MIGRATION_3_4.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hadith_category_mapping (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        hadithId TEXT NOT NULL,
                        collection TEXT NOT NULL,
                        category TEXT NOT NULL,
                        subcategory TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS azkar_table (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        category TEXT NOT NULL,
                        arabic TEXT NOT NULL,
                        transliteration TEXT NOT NULL,
                        translation TEXT NOT NULL,
                        reference TEXT NOT NULL,
                        targetCount INTEGER NOT NULL,
                        completedCount INTEGER NOT NULL DEFAULT 0,
                        isBookmarked INTEGER NOT NULL DEFAULT 0,
                        lastReadTimestamp INTEGER NOT NULL DEFAULT 0,
                        language TEXT NOT NULL DEFAULT 'ara'
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration 3 → 4: Restructures hadith_category_mapping (v3 schema had
         * hadithId/category/subcategory; v4 uses topic/collection/bookNumber).
         * Adds collection_book_entity. Old category data is dropped — it will be
         * re-seeded by KnowledgeRepository.seedHadithCategories() on next launch.
         * User data untouched.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop the v3 schema — columns are completely incompatible with current code.
                db.execSQL("DROP TABLE IF EXISTS hadith_category_mapping")
                // Recreate with the v4 schema (topic/collection/bookNumber).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hadith_category_mapping (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        topic TEXT NOT NULL,
                        collection TEXT NOT NULL,
                        bookNumber TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collection_book_entity (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        collectionName TEXT NOT NULL,
                        bookNumber TEXT NOT NULL,
                        title TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration 4 → 5:
         * - Adds `verificationType TEXT NOT NULL DEFAULT 'NONE'` to prayer_records.
         * - Creates the `masjids` table for nearby masjid caching.
         * User prayer history is preserved — no destructive migration.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE prayer_records ADD COLUMN verificationType TEXT NOT NULL DEFAULT 'NONE'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS masjids (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        osmId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        lat REAL NOT NULL,
                        lng REAL NOT NULL,
                        address TEXT NOT NULL DEFAULT '',
                        distanceMeters INTEGER NOT NULL DEFAULT 0,
                        fetchedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_masjids_osmId ON masjids (osmId)")
            }
        }

        /**
         * Migration 5 → 6: Adds `local_masjid` table for user-entered masjid timetables.
         * Single-row table (id = 1). All prayer time columns stored as "HH:mm" strings.
         * User data untouched.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_masjid (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        masjidName TEXT NOT NULL DEFAULT '',
                        fajr TEXT NOT NULL DEFAULT '',
                        dhuhr TEXT NOT NULL DEFAULT '',
                        asr TEXT NOT NULL DEFAULT '',
                        maghrib TEXT NOT NULL DEFAULT '',
                        isha TEXT NOT NULL DEFAULT '',
                        enabled INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration 6 → 7: Adds quran_bookmarks and quran_progress tables
         * (BM-006 Quran module). User data untouched.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quran_bookmarks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        surahNumber INTEGER NOT NULL,
                        ayahNumber INTEGER,
                        collectionName TEXT NOT NULL DEFAULT '',
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_quran_bookmarks_surahNumber_ayahNumber " +
                        "ON quran_bookmarks (surahNumber, ayahNumber)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quran_progress (
                        surahNumber INTEGER PRIMARY KEY NOT NULL,
                        lastAyah INTEGER NOT NULL DEFAULT 1,
                        maxAyah INTEGER NOT NULL DEFAULT 1,
                        timestampMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "salahlock_db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build().also { INSTANCE = it }
            }
    }
}
