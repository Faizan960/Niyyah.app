/**
 * The sync domain registry — the single source of truth for which domains are
 * syncable and what their columns are. Push/pull are generic over this registry;
 * every SQL identifier (table + column names) originates HERE, never from client
 * input, which is what keeps the generic query builder injection-safe.
 *
 * SECURITY INVARIANT: `emergency_overrides` (and any alias) is NOT in this registry
 * and is additionally hard-denied below. Emergency override allowances and monthly
 * exception records are local-only and must never be synced.
 *
 * FREE-PASS/ mercy-week state (`last_mercy_week`, `mercy_used_this_week`) was
 * REMOVED from the streaks domain: the three-free-passes feature is being removed
 * from the product and its state must not be carried into the cloud backend. The
 * Android side still holds legacy columns (kept only for Room migration safety);
 * the streak sync client must not send them. See README "Data model decisions".
 */

export type ColType = "int" | "text" | "bool";

import { HttpError } from "../util/http";

export interface ColumnSpec {
  readonly name: string;
  readonly type: ColType;
  /** Natural-key columns are required unless they carry a default. */
  readonly required?: boolean;
  readonly default?: number | string;
  /** Max accepted character length for text columns (text only). */
  readonly maxLen?: number;
}

export interface DomainSpec {
  readonly table: string;
  /** Natural-key columns in addition to the implicit, server-set `user_id`. */
  readonly keys: readonly ColumnSpec[];
  /** Client-writable payload columns. */
  readonly data: readonly ColumnSpec[];
  /**
   * Domain-specific semantic validation for one raw row (after generic column
   * coercion is NOT yet applied — this sees the raw client object). Throw an
   * HttpError to reject the row.
   */
  readonly checkRow?: (row: Record<string, unknown>) => void;
}

/** Default cap for any text column without an explicit maxLen. */
export const DEFAULT_TEXT_MAX_LEN = 256;

const PRAYER_NAMES: ReadonlySet<string> = new Set([
  "FAJR",
  "DHUHR",
  "ASR",
  "MAGHRIB",
  "ISHA",
]);

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function requireIsoDate(row: Record<string, unknown>, field: string): void {
  const v = row[field];
  if (typeof v === "string" && !ISO_DATE.test(v)) {
    throw new HttpError(400, `Field '${field}' must be an ISO yyyy-MM-dd date`, "bad_request");
  }
}

function requireRange(
  row: Record<string, unknown>,
  field: string,
  min: number,
  max: number,
): void {
  const v = row[field];
  if (v === undefined || v === null) return; // defaulted/optional at coercion stage
  if (typeof v !== "number" || !Number.isFinite(v) || v < min || v > max) {
    throw new HttpError(400, `Field '${field}' must be between ${min} and ${max}`, "bad_request");
  }
}

export const DOMAINS: Readonly<Record<string, DomainSpec>> = {
  prayer_records: {
    table: "prayer_records",
    keys: [
      { name: "date", type: "text", required: true, maxLen: 10 },
      { name: "prayer_name", type: "text", required: true, maxLen: 16 },
    ],
    data: [
      { name: "verified", type: "bool", default: 0 },
      { name: "override_used", type: "bool", default: 0 },
      { name: "verification_type", type: "text", maxLen: 32 },
      { name: "timestamp_ms", type: "int", default: 0 },
    ],
    checkRow: (row) => {
      requireIsoDate(row, "date");
      const name = row["prayer_name"];
      if (typeof name === "string" && !PRAYER_NAMES.has(name)) {
        throw new HttpError(400, `Unknown prayer_name '${name}'`, "bad_request");
      }
    },
  },
  streaks: {
    table: "streaks",
    keys: [], // one row per user; the natural key is user_id alone
    data: [
      { name: "current_streak", type: "int", default: 0 },
      { name: "best_streak", type: "int", default: 0 },
      { name: "last_full_day", type: "text", maxLen: 10 },
    ],
    checkRow: (row) => {
      for (const banned of ["last_mercy_week", "mercy_used_this_week"]) {
        if (row[banned] !== undefined) {
          throw new HttpError(
            400,
            `Field '${banned}' belongs to the removed free-pass feature and cannot be synced`,
            "forbidden_field",
          );
        }
      }
    },
  },
  quran_reader_state: {
    table: "quran_reader_state",
    keys: [{ name: "surah_number", type: "int", required: true }],
    data: [
      { name: "last_ayah", type: "int", default: 1 },
      { name: "max_ayah", type: "int", default: 1 },
      { name: "timestamp_ms", type: "int", default: 0 },
    ],
    checkRow: (row) => {
      requireRange(row, "surah_number", 1, 114);
      requireRange(row, "last_ayah", 0, 286);
      requireRange(row, "max_ayah", 0, 286);
    },
  },
  quran_reading_sessions: {
    table: "quran_reading_sessions",
    keys: [{ name: "session_id", type: "text", required: true, maxLen: 64 }],
    data: [
      { name: "surah_number", type: "int", default: 0 },
      { name: "start_ayah", type: "int", default: 1 },
      { name: "end_ayah", type: "int", default: 1 },
      { name: "started_at_ms", type: "int", default: 0 },
      { name: "ended_at_ms", type: "int", default: 0 },
      { name: "duration_ms", type: "int", default: 0 },
    ],
    checkRow: (row) => {
      requireRange(row, "surah_number", 0, 114);
      requireRange(row, "start_ayah", 0, 286);
      requireRange(row, "end_ayah", 0, 286);
    },
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
    checkRow: (row) => {
      requireRange(row, "surah_number", 1, 114);
    },
  },
  quran_bookmarks: {
    table: "quran_bookmarks",
    keys: [
      { name: "surah_number", type: "int", required: true },
      // ayah_number 0 = whole-surah bookmark; defaulted so clients may omit it.
      { name: "ayah_number", type: "int", required: true, default: 0 },
    ],
    data: [
      { name: "collection_name", type: "text", default: "", maxLen: 64 },
      { name: "created_at_ms", type: "int", default: 0 },
    ],
    checkRow: (row) => {
      requireRange(row, "surah_number", 1, 114);
      requireRange(row, "ayah_number", 0, 286);
    },
  },
  hadith_reads: {
    table: "hadith_reads",
    keys: [
      { name: "collection", type: "text", required: true, maxLen: 64 },
      { name: "hadith_number", type: "text", required: true, maxLen: 64 },
    ],
    data: [{ name: "read_at_ms", type: "int", default: 0 }],
  },
  hadith_bookmarks: {
    table: "hadith_bookmarks",
    keys: [
      { name: "collection", type: "text", required: true, maxLen: 64 },
      { name: "hadith_number", type: "text", required: true, maxLen: 64 },
    ],
    data: [
      { name: "collection_name", type: "text", default: "", maxLen: 64 },
      { name: "created_at_ms", type: "int", default: 0 },
    ],
  },
};

/**
 * Hard denylist for a pointed, testable rejection — defense in depth on top of the
 * allowlist. Emergency override / monthly exception / mercy (removed free passes)
 * state is refused with a 400 under any plausible client-side naming.
 */
export const FORBIDDEN_DOMAINS: ReadonlySet<string> = new Set([
  "emergency_overrides",
  "emergency_override",
  "emergency",
  "overrides",
  "monthly_exceptions",
  "exception_records",
  "mercy_weeks",
  "mercy",
  "free_passes",
]);
