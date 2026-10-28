-- Niyyah backend — initial schema (D1 / SQLite).
-- Migration 0001. Applied with: wrangler d1 migrations apply niyyah --local
--
-- SYNC MODEL (offline-first):
--   * Identity (`user_id`) is ALWAYS the Clerk JWT `sub`, set server-side. Clients
--     never write it, so it is deliberately NOT a client-supplied column anywhere.
--   * `updated_at` is a server-assigned epoch-millisecond stamp and is the pull
--     cursor. Pull uses `updated_at >= cursor` (gte) + idempotent upsert on apply,
--     so a row sitting exactly on a cursor boundary is re-delivered, never skipped.
--   * `deleted_at` NULL = live row; non-NULL = tombstone. Deletes are soft so other
--     devices converge on the delete through a normal pull.
--   * Natural key per table = (user_id, <domain key columns>) and is the PRIMARY KEY,
--     which is also the ON CONFLICT target for idempotent upserts.
--
-- ┌──────────────────────────────────────────────────────────────────────────┐
-- │ EMERGENCY OVERRIDES ARE INTENTIONALLY ABSENT FROM THIS BACKEND.            │
-- │ Emergency override allowances are LOCAL-ONLY and device-bound. There is no  │
-- │ emergency_overrides table, no sync domain, and no endpoint for them — by    │
-- │ design. Do not add one.                                                     │
-- └──────────────────────────────────────────────────────────────────────────┘

-- One row per Clerk user. Created/refreshed on every authenticated request.
CREATE TABLE IF NOT EXISTS users (
  user_id      TEXT PRIMARY KEY,
  created_at   INTEGER NOT NULL,
  last_seen_at INTEGER NOT NULL
);

-- Prayer completion records. Natural key (user_id, date, prayer_name).
CREATE TABLE IF NOT EXISTS prayer_records (
  user_id           TEXT    NOT NULL,
  date              TEXT    NOT NULL,            -- ISO yyyy-MM-dd
  prayer_name       TEXT    NOT NULL,            -- FAJR | DHUHR | ASR | MAGHRIB | ISHA
  verified          INTEGER NOT NULL DEFAULT 0,  -- bool
  override_used     INTEGER NOT NULL DEFAULT 0,  -- bool (an override was applied to THIS prayer)
  verification_type TEXT,
  timestamp_ms      INTEGER NOT NULL DEFAULT 0,
  updated_at        INTEGER NOT NULL,            -- server cursor
  deleted_at        INTEGER,                     -- tombstone
  PRIMARY KEY (user_id, date, prayer_name)
);
CREATE INDEX IF NOT EXISTS idx_prayer_records_cursor ON prayer_records (user_id, updated_at);

-- Streak state. One row per user.
CREATE TABLE IF NOT EXISTS streaks (
  user_id              TEXT    PRIMARY KEY,
  current_streak       INTEGER NOT NULL DEFAULT 0,
  best_streak          INTEGER NOT NULL DEFAULT 0,
  last_full_day        TEXT,
  last_mercy_week      TEXT,
  mercy_used_this_week INTEGER NOT NULL DEFAULT 0, -- bool
  updated_at           INTEGER NOT NULL,
  deleted_at           INTEGER
);
CREATE INDEX IF NOT EXISTS idx_streaks_cursor ON streaks (user_id, updated_at);

-- Per-surah reading position ("continue reading"). Natural key (user_id, surah_number).
CREATE TABLE IF NOT EXISTS quran_reader_state (
  user_id      TEXT    NOT NULL,
  surah_number INTEGER NOT NULL,
  last_ayah    INTEGER NOT NULL DEFAULT 1,
  max_ayah     INTEGER NOT NULL DEFAULT 1,
  timestamp_ms INTEGER NOT NULL DEFAULT 0,
  updated_at   INTEGER NOT NULL,
  deleted_at   INTEGER,
  PRIMARY KEY (user_id, surah_number)
);
CREATE INDEX IF NOT EXISTS idx_quran_reader_state_cursor ON quran_reader_state (user_id, updated_at);

-- Reading sessions (append log). Natural key (user_id, session_id = client UUID).
CREATE TABLE IF NOT EXISTS quran_reading_sessions (
  user_id       TEXT    NOT NULL,
  session_id    TEXT    NOT NULL,
  surah_number  INTEGER NOT NULL DEFAULT 0,
  start_ayah    INTEGER NOT NULL DEFAULT 1,
  end_ayah      INTEGER NOT NULL DEFAULT 1,
  started_at_ms INTEGER NOT NULL DEFAULT 0,
  ended_at_ms   INTEGER NOT NULL DEFAULT 0,
  duration_ms   INTEGER NOT NULL DEFAULT 0,
  updated_at    INTEGER NOT NULL,
  deleted_at    INTEGER,
  PRIMARY KEY (user_id, session_id)
);
CREATE INDEX IF NOT EXISTS idx_quran_reading_sessions_cursor ON quran_reading_sessions (user_id, updated_at);

-- Per-surah aggregate stats. Natural key (user_id, surah_number).
CREATE TABLE IF NOT EXISTS quran_surah_stats (
  user_id         TEXT    NOT NULL,
  surah_number    INTEGER NOT NULL,
  times_read      INTEGER NOT NULL DEFAULT 0,
  ayahs_read      INTEGER NOT NULL DEFAULT 0,
  total_ms        INTEGER NOT NULL DEFAULT 0,
  last_read_at_ms INTEGER NOT NULL DEFAULT 0,
  updated_at      INTEGER NOT NULL,
  deleted_at      INTEGER,
  PRIMARY KEY (user_id, surah_number)
);
CREATE INDEX IF NOT EXISTS idx_quran_surah_stats_cursor ON quran_surah_stats (user_id, updated_at);

-- Quran bookmarks. ayah_number = 0 encodes a whole-surah bookmark (never a SQL NULL,
-- so the natural key is always concrete). Natural key (user_id, surah_number, ayah_number).
CREATE TABLE IF NOT EXISTS quran_bookmarks (
  user_id         TEXT    NOT NULL,
  surah_number    INTEGER NOT NULL,
  ayah_number     INTEGER NOT NULL DEFAULT 0,
  collection_name TEXT    NOT NULL DEFAULT '',
  created_at_ms   INTEGER NOT NULL DEFAULT 0,
  updated_at      INTEGER NOT NULL,
  deleted_at      INTEGER,
  PRIMARY KEY (user_id, surah_number, ayah_number)
);
CREATE INDEX IF NOT EXISTS idx_quran_bookmarks_cursor ON quran_bookmarks (user_id, updated_at);

-- Hadith read tracking. Natural key (user_id, collection, hadith_number).
CREATE TABLE IF NOT EXISTS hadith_reads (
  user_id       TEXT    NOT NULL,
  collection    TEXT    NOT NULL,
  hadith_number TEXT    NOT NULL,
  read_at_ms    INTEGER NOT NULL DEFAULT 0,
  updated_at    INTEGER NOT NULL,
  deleted_at    INTEGER,
  PRIMARY KEY (user_id, collection, hadith_number)
);
CREATE INDEX IF NOT EXISTS idx_hadith_reads_cursor ON hadith_reads (user_id, updated_at);

-- Hadith bookmarks. Natural key (user_id, collection, hadith_number).
CREATE TABLE IF NOT EXISTS hadith_bookmarks (
  user_id         TEXT    NOT NULL,
  collection      TEXT    NOT NULL,
  hadith_number   TEXT    NOT NULL,
  collection_name TEXT    NOT NULL DEFAULT '',
  created_at_ms   INTEGER NOT NULL DEFAULT 0,
  updated_at      INTEGER NOT NULL,
  deleted_at      INTEGER,
  PRIMARY KEY (user_id, collection, hadith_number)
);
CREATE INDEX IF NOT EXISTS idx_hadith_bookmarks_cursor ON hadith_bookmarks (user_id, updated_at);
