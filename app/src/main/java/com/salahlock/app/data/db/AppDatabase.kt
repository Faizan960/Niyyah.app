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
import com.salahlock.app.data.db.dao.CollectionsDao
import com.salahlock.app.data.db.entity.UserCollectionEntity
import com.salahlock.app.data.db.entity.CollectionItemEntity
import com.salahlock.app.data.db.dao.HadithUserStateDao
import com.salahlock.app.data.db.dao.AzkarUserStateDao
import com.salahlock.app.data.db.dao.LegacyOwnershipDao
import com.salahlock.app.data.db.entity.HadithUserStateEntity
import com.salahlock.app.data.db.entity.AzkarUserStateEntity
import com.salahlock.app.data.db.entity.LegacyOwnershipEntity

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
        UserCollectionEntity::class,
        CollectionItemEntity::class,
        // BM-013 Checkpoint B — user-owned state (normalized off the corpus) + adoption ledger.
        HadithUserStateEntity::class,
        AzkarUserStateEntity::class,
        LegacyOwnershipEntity::class,
        // BM-013 Checkpoint C — transactional sync outbox + per-user sync progress.
        com.salahlock.app.data.db.entity.SyncOutboxEntity::class,
        com.salahlock.app.data.db.entity.SyncStateEntity::class,
    ],
    version = 11,
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
    abstract fun collectionsDao(): CollectionsDao
    abstract fun hadithUserStateDao(): HadithUserStateDao
    abstract fun azkarUserStateDao(): AzkarUserStateDao
    abstract fun legacyOwnershipDao(): LegacyOwnershipDao
    abstract fun ownershipAdoptionDao(): com.salahlock.app.data.db.dao.OwnershipAdoptionDao
    abstract fun syncOutboxDao(): com.salahlock.app.data.db.dao.SyncOutboxDao
    abstract fun syncStateDao(): com.salahlock.app.data.db.dao.SyncStateDao

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

        /**
         * Migration 7 → 8 (BM-006.5/6 Bookmarks + Collections):
         * - Adds `bookmarkSource TEXT NOT NULL DEFAULT 'hadith'` to hadith_table so
         *   bookmarks made from the Knowledge topic reader are distinguishable.
         * - Adds user_collections + collection_items (user-created libraries that
         *   reference existing bookmarks — no bookmark data is duplicated).
         * User data untouched.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE hadith_table ADD COLUMN bookmarkSource TEXT NOT NULL DEFAULT 'hadith'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collection_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        collectionId INTEGER NOT NULL,
                        contentType TEXT NOT NULL,
                        contentKey TEXT NOT NULL,
                        addedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_collection_items_collectionId_contentType_contentKey " +
                        "ON collection_items (collectionId, contentType, contentKey)"
                )
            }
        }

        /**
         * Migration 8 → 9 (BM-013 Checkpoint B — cloud-sync LOCAL FOUNDATION).
         *
         * Non-destructive. Establishes multi-account ownership, normalizes user
         * state off the static corpus, and adds stable cross-device identities.
         * NO user data is dropped; static corpus text is preserved in place.
         *
         *  1. Adds `ownerId` (default `__local__` = legacy/unclaimed) + `updatedAt`
         *     to every genuinely user-owned table.
         *  2. Adds `clientUuid` to collections (backfilled with a fresh UUID per row)
         *     and mirrors it onto `collection_items.collectionUuid`.
         *  3. Adds the static, deterministic `azkarRef` to the azkar corpus.
         *  4. Creates `hadith_user_state` / `azkar_user_state` and MIGRATES the
         *     existing bookmark/progress flags off the corpus into them (owner
         *     `__local__`). Corpus flag columns are left dormant.
         *  5. Creates the one-time `legacy_ownership` adoption ledger (unclaimed).
         */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1a ─ ownership + sync bookkeeping via ALTER (tables keeping their PK)
                for (table in listOf("prayer_records", "emergency_overrides", "quran_bookmarks")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN ownerId TEXT NOT NULL DEFAULT '__local__'")
                    db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                }
                // 1b ─ make unique keys owner-aware so multiple accounts can hold the
                //      same natural key (date/prayer, month, ayah) without colliding.
                db.execSQL("DROP INDEX IF EXISTS index_prayer_records_date_prayerName")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_prayer_records_ownerId_date_prayerName ON prayer_records (ownerId, date, prayerName)")
                db.execSQL("DROP INDEX IF EXISTS index_quran_bookmarks_surahNumber_ayahNumber")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_quran_bookmarks_ownerId_surahNumber_ayahNumber ON quran_bookmarks (ownerId, surahNumber, ayahNumber)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_emergency_overrides_ownerId_monthYear ON emergency_overrides (ownerId, monthYear)")

                // 1c ─ rebuild tables whose PRIMARY KEY must become owner-composite.
                //      (SQLite can't alter a PK in place; copy → drop → rename.)
                db.execSQL(
                    "CREATE TABLE `quran_progress_new` (`surahNumber` INTEGER NOT NULL, " +
                        "`lastAyah` INTEGER NOT NULL, `maxAyah` INTEGER NOT NULL, " +
                        "`timestampMs` INTEGER NOT NULL, `ownerId` TEXT NOT NULL DEFAULT '__local__', " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`ownerId`, `surahNumber`))"
                )
                db.execSQL(
                    "INSERT INTO quran_progress_new (surahNumber, lastAyah, maxAyah, timestampMs, ownerId, updatedAt) " +
                        "SELECT surahNumber, lastAyah, maxAyah, timestampMs, '__local__', 0 FROM quran_progress"
                )
                db.execSQL("DROP TABLE quran_progress")
                db.execSQL("ALTER TABLE quran_progress_new RENAME TO quran_progress")

                db.execSQL(
                    "CREATE TABLE `streaks_new` (`id` INTEGER NOT NULL, " +
                        "`currentStreak` INTEGER NOT NULL, `bestStreak` INTEGER NOT NULL, " +
                        "`lastFullDay` TEXT NOT NULL, `lastMercyWeek` TEXT NOT NULL, " +
                        "`mercyUsedThisWeek` INTEGER NOT NULL, `ownerId` TEXT NOT NULL DEFAULT '__local__', " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`ownerId`))"
                )
                db.execSQL(
                    "INSERT INTO streaks_new (id, currentStreak, bestStreak, lastFullDay, lastMercyWeek, mercyUsedThisWeek, ownerId, updatedAt) " +
                        "SELECT id, currentStreak, bestStreak, lastFullDay, lastMercyWeek, mercyUsedThisWeek, '__local__', 0 FROM streaks"
                )
                db.execSQL("DROP TABLE streaks")
                db.execSQL("ALTER TABLE streaks_new RENAME TO streaks")

                // 2 ─ collections: stable UUID identity + scoping
                db.execSQL("ALTER TABLE user_collections ADD COLUMN clientUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_collections ADD COLUMN ownerId TEXT NOT NULL DEFAULT '__local__'")
                db.execSQL("ALTER TABLE user_collections ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                // Fresh random UUID (v4-shaped) per existing row — avoids cross-device
                // collisions that a deterministic `legacy-<id>` would cause if the same
                // user adopted legacy data on two devices.
                db.execSQL(
                    "UPDATE user_collections SET clientUuid = $UUID_SQL WHERE clientUuid = ''"
                )
                db.execSQL("ALTER TABLE collection_items ADD COLUMN collectionUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE collection_items ADD COLUMN ownerId TEXT NOT NULL DEFAULT '__local__'")
                db.execSQL("ALTER TABLE collection_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE collection_items SET collectionUuid = " +
                        "(SELECT clientUuid FROM user_collections WHERE user_collections.id = collection_items.collectionId) " +
                        "WHERE collectionUuid = ''"
                )

                // 3 ─ static deterministic azkar ref on the corpus (index within
                //     category, computed without window functions for old SQLite).
                db.execSQL("ALTER TABLE azkar_table ADD COLUMN azkarRef TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE azkar_table SET azkarRef = 'azkar:v1:' || replace(lower(trim(category)), ' ', '_') || ':' || " +
                        "((SELECT COUNT(*) FROM azkar_table a2 WHERE a2.category = azkar_table.category AND a2.id <= azkar_table.id) - 1)"
                )

                // 4 ─ user-state tables + migrate flags off the corpus
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hadith_user_state` (" +
                        "`ownerId` TEXT NOT NULL, `hadithId` TEXT NOT NULL, " +
                        "`isBookmarked` INTEGER NOT NULL, `bookmarkSource` TEXT NOT NULL, " +
                        "`lastReadTimestamp` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerId`, `hadithId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `azkar_user_state` (" +
                        "`ownerId` TEXT NOT NULL, `azkarRef` TEXT NOT NULL, " +
                        "`isBookmarked` INTEGER NOT NULL, `completedCount` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerId`, `azkarRef`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `legacy_ownership` (" +
                        "`id` INTEGER NOT NULL, `adoptedBy` TEXT, `adoptedAtMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO hadith_user_state (ownerId, hadithId, isBookmarked, bookmarkSource, lastReadTimestamp, updatedAt) " +
                        "SELECT '__local__', id, isBookmarked, bookmarkSource, lastReadTimestamp, 0 " +
                        "FROM hadith_table WHERE isBookmarked = 1 OR lastReadTimestamp > 0"
                )
                db.execSQL(
                    "INSERT INTO azkar_user_state (ownerId, azkarRef, isBookmarked, completedCount, updatedAt) " +
                        "SELECT '__local__', azkarRef, isBookmarked, completedCount, 0 " +
                        "FROM azkar_table WHERE (isBookmarked = 1 OR completedCount > 0) AND azkarRef <> ''"
                )

                // 5 ─ legacy-adoption ledger, unclaimed
                db.execSQL("INSERT OR IGNORE INTO legacy_ownership (id, adoptedBy, adoptedAtMs) VALUES (1, NULL, 0)")
            }
        }

        /**
         * Migration 9 → 10 (BM-013 Checkpoint C — Quran-bookmark cloud sync).
         * Adds the generic sync_outbox change journal and per-user sync_state.
         * Structure-only; NO existing user data touched.
         */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_outbox` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ownerId` TEXT NOT NULL, `domain` TEXT NOT NULL, " +
                        "`entityKey` TEXT NOT NULL, `operation` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL, " +
                        "`attemptCount` INTEGER NOT NULL, `lastAttemptMs` INTEGER NOT NULL, " +
                        "`lastError` TEXT)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_ownerId_domain_entityKey " +
                        "ON sync_outbox (ownerId, domain, entityKey)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` (" +
                        "`ownerId` TEXT NOT NULL, `firstSyncDone` INTEGER NOT NULL, " +
                        "`pullWatermark` TEXT NOT NULL, PRIMARY KEY(`ownerId`))"
                )
            }
        }

        /**
         * Migration 10 → 11 (fix/local-persistence-auth — device-local prayer recovery).
         *
         * Prayer tracking is DEVICE-LOCAL and auth-independent by product requirement:
         * prayer_records, streaks, and emergency_overrides must always live under
         * [OwnerIds.LOCAL][com.salahlock.app.data.db.entity.OwnerIds.LOCAL] ('__local__')
         * so they survive sign-in, sign-out, and every auth-state change. Earlier builds
         * (BM-013 Checkpoint B/C) adopted these tables to the Clerk account on first
         * sign-in and/or let signed-out writes land under '__none__' — which made prayer
         * history "disappear" after logout and left rows orphaned after re-login.
         *
         * This migration folds every prayer_records / streaks / emergency_overrides row
         * back to '__local__' so existing installs recover their data in place. It is
         * NON-DESTRUCTIVE (data-only, no schema change) and idempotent:
         *
         *  - prayer_records / emergency_overrides: re-stamp every non-local row to
         *    '__local__' (`UPDATE OR IGNORE` preserves an already-present local row on a
         *    natural-key collision — both represent the same completion), then drop any
         *    leftover colliding non-local duplicate. Full history is preserved.
         *  - streaks (single-row aggregate, PK = ownerId): keep the row with the greatest
         *    bestStreak (then currentStreak, then updatedAt) so the user's best achievement
         *    is never lost, re-stamp it '__local__', and drop the rest.
         *
         * Cloud-synced tables (quran_*, collections, hadith/azkar state) are intentionally
         * left under their adopted owner — they remain per-account.
         */
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // prayer_records — re-stamp all non-local rows to __local__, keeping full
                // history; a natural-key (ownerId, date, prayerName) collision keeps the
                // existing __local__ row and the duplicate is removed below.
                db.execSQL("UPDATE OR IGNORE prayer_records SET ownerId = '__local__' WHERE ownerId <> '__local__'")
                db.execSQL("DELETE FROM prayer_records WHERE ownerId <> '__local__'")

                // emergency_overrides — same fold on (ownerId, monthYear).
                db.execSQL("UPDATE OR IGNORE emergency_overrides SET ownerId = '__local__' WHERE ownerId <> '__local__'")
                db.execSQL("DELETE FROM emergency_overrides WHERE ownerId <> '__local__'")

                // streaks — one aggregate row. Keep the single best row (highest bestStreak,
                // then currentStreak, then most recently updated) so no achievement is lost,
                // then make it the local row. Uses rowid (table is not WITHOUT ROWID).
                db.execSQL(
                    "DELETE FROM streaks WHERE rowid NOT IN (" +
                        "SELECT rowid FROM streaks ORDER BY bestStreak DESC, currentStreak DESC, updatedAt DESC LIMIT 1)"
                )
                db.execSQL("UPDATE streaks SET ownerId = '__local__' WHERE ownerId <> '__local__'")
            }
        }

        /** SQLite expression producing a fresh v4-shaped UUID string per row. */
        private const val UUID_SQL =
            "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                "substr(hex(randomblob(2)), 2) || '-' || " +
                "substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || " +
                "hex(randomblob(6)))"

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "salahlock_db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .build().also { INSTANCE = it }
            }
    }
}
