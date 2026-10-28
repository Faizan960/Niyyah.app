/**
 * The sync domain registry — the single source of truth for which domains are
 * syncable and what their columns are. Push/pull are generic over this registry;
 * every SQL identifier (table + column names) originates HERE, never from client
 * input, which is what keeps the generic query builder injection-safe.
 *
 * SECURITY INVARIANT: `emergency_overrides` (and any alias) is NOT in this registry
 * and is additionally hard-denied below. Emergency override allowances are
 * local-only and must never be synced.
 */

export type ColType = "int" | "text" | "bool";

export interface ColumnSpec {
  readonly name: string;
  readonly type: ColType;
  /** Natural-key columns are required unless they carry a default. */
  readonly required?: boolean;
  readonly default?: number | string;
}

export interface DomainSpec {
  readonly table: string;
  /** Natural-key columns in addition to the implicit, server-set `user_id`. */
  readonly keys: readonly ColumnSpec[];
  /** Client-writable payload columns. */
  readonly data: readonly ColumnSpec[];
}

export const DOMAINS: Readonly<Record<string, DomainSpec>> = {
  prayer_records: {
    table: "prayer_records",
    keys: [
      { name: "date", type: "text", required: true },
      { name: "prayer_name", type: "text", required: true },
    ],
    data: [
      { name: "verified", type: "bool", default: 0 },
      { name: "override_used", type: "bool", default: 0 },
      { name: "verification_type", type: "text" },
      { name: "timestamp_ms", type: "int", default: 0 },
    ],
  },
  streaks: {
    table: "streaks",
    keys: [], // one row per user; the natural key is user_id alone
    data: [
      { name: "current_streak", type: "int", default: 0 },
      { name: "best_streak", type: "int", default: 0 },
      { name: "last_full_day", type: "text" },
      { name: "last_mercy_week", type: "text" },
      { name: "mercy_used_this_week", type: "bool", default: 0 },
    ],
  },
  quran_reader_state: {
    table: "quran_reader_state",
    keys: [{ name: "surah_number", type: "int", required: true }],
    data: [
      { name: "last_ayah", type: "int", default: 1 },
      { name: "max_ayah", type: "int", default: 1 },
      { name: "timestamp_ms", type: "int", default: 0 },
    ],
  },
  quran_reading_sessions: {
    table: "quran_reading_sessions",
    keys: [{ name: "session_id", type: "text", required: true }],
    data: [
      { name: "surah_number", type: "int", default: 0 },
      { name: "start_ayah", type: "int", default: 1 },
      { name: "end_ayah", type: "int", default: 1 },
      { name: "started_at_ms", type: "int", default: 0 },
      { name: "ended_at_ms", type: "int", default: 0 },
      { name: "duration_ms", type: "int", default: 0 },
    ],
  },
  quran_surah_stats: {
    table: "quran_surah_stats",
    keys: [{ name: "surah_number", type: "int", required: true }],
    data: [
      { name: "times_read", type: "int", default: 0 },
      { name: "ayahs_read", type: "int", default: 0 },
      { name: "total_ms", type: "int", default: 0 },
      { name: "last_read_at_ms", type: "int", default: 0 },
    ],
  },
  quran_bookmarks: {
    table: "quran_bookmarks",
    keys: [
      { name: "surah_number", type: "int", required: true },
      // ayah_number 0 = whole-surah bookmark; defaulted so clients may omit it.
      { name: "ayah_number", type: "int", required: true, default: 0 },
    ],
    data: [
      { name: "collection_name", type: "text", default: "" },
      { name: "created_at_ms", type: "int", default: 0 },
    ],
  },
  hadith_reads: {
    table: "hadith_reads",
    keys: [
      { name: "collection", type: "text", required: true },
      { name: "hadith_number", type: "text", required: true },
    ],
    data: [{ name: "read_at_ms", type: "int", default: 0 }],
  },
  hadith_bookmarks: {
    table: "hadith_bookmarks",
    keys: [
      { name: "collection", type: "text", required: true },
      { name: "hadith_number", type: "text", required: true },
    ],
    data: [
      { name: "collection_name", type: "text", default: "" },
      { name: "created_at_ms", type: "int", default: 0 },
    ],
  },
};

/**
 * Hard denylist for a pointed, testable rejection — defense in depth on top of the
 * allowlist. Any attempt to sync emergency overrides is refused with a 400.
 */
export const FORBIDDEN_DOMAINS: ReadonlySet<string> = new Set([
  "emergency_overrides",
  "emergency_override",
  "emergency",
  "overrides",
]);
