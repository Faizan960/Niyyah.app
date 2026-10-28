import type { Env } from "../types";
import {
  DOMAINS,
  DEFAULT_TEXT_MAX_LEN,
  FORBIDDEN_DOMAINS,
  type ColumnSpec,
  type DomainSpec,
} from "./schema";
import { HttpError } from "../util/http";

/**
 * Generic offline-first sync core: push (upsert) and pull (cursor read).
 *
 * All SQL identifiers come from the static {@link DOMAINS} registry; all client
 * values are bound as parameters. `user_id` is always the server-derived Clerk id
 * passed in by the router — it is never read from the request body.
 *
 * PULL PAGINATION (no-skip guarantee):
 *   Pull pages per domain with a KEYSET continuation on (updated_at, rowid).
 *   A page that fills the limit returns `hasMore[domain] = true` plus an opaque
 *   `continuation[domain]` token encoding the last returned (updated_at, rowid).
 *   The next page resumes STRICTLY AFTER that pair, so rows sharing an
 *   `updated_at` with the page boundary can never be skipped — a naive
 *   `updated_at >= lastSeen` LIMIT page would drop them. The client loops until
 *   `hasMore` is false; only then does it advance its durable (>=) cursor to
 *   `cursors[domain]`. Durable cursors keep gte semantics: the boundary row is
 *   re-delivered and upserts are idempotent, so re-delivery is harmless.
 */

const MAX_ROWS_PER_DOMAIN = 1000;
const PULL_PAGE_LIMIT = 500;

type Row = Record<string, unknown>;
type Bound = number | string | null;

function coerce(col: ColumnSpec, value: unknown): Bound {
  if (value === undefined || value === null) {
    if (col.default !== undefined) return col.default;
    if (col.required) throw new HttpError(400, `Missing required field '${col.name}'`, "bad_request");
    return null;
  }
  switch (col.type) {
    case "int":
      if (typeof value === "number" && Number.isFinite(value)) return Math.trunc(value);
      if (typeof value === "boolean") return value ? 1 : 0;
      throw new HttpError(400, `Field '${col.name}' must be a number`, "bad_request");
    case "bool":
      if (typeof value === "boolean") return value ? 1 : 0;
      if (value === 0 || value === 1) return value;
      throw new HttpError(400, `Field '${col.name}' must be a boolean`, "bad_request");
    case "text":
      if (typeof value === "string") {
        const maxLen = col.maxLen ?? DEFAULT_TEXT_MAX_LEN;
        if (value.length > maxLen) {
          throw new HttpError(
            400,
            `Field '${col.name}' exceeds ${maxLen} characters`,
            "bad_request",
          );
        }
        return value;
      }
      throw new HttpError(400, `Field '${col.name}' must be a string`, "bad_request");
  }
  // Unreachable: col.type is exhaustively handled above.
  throw new HttpError(400, `Unsupported column type for '${col.name}'`, "bad_request");
}

/** Resolves a domain name to its spec, enforcing allowlist + emergency denylist. */
function resolveDomain(name: string): DomainSpec {
  if (FORBIDDEN_DOMAINS.has(name)) {
    throw new HttpError(400, `Domain '${name}' is local-only and cannot be synced`, "forbidden_domain");
  }
  const spec = DOMAINS[name];
  if (!spec) {
    throw new HttpError(400, `Unknown or non-syncable domain '${name}'`, "unknown_domain");
  }
  return spec;
}

// --------------------------------------------------------------------------- push

export interface PushResult {
  applied: Record<string, number>;
  serverTime: number;
}

export async function handlePush(env: Env, userId: string, body: unknown): Promise<PushResult> {
  const changes = (body as { changes?: unknown })?.changes;
  if (!changes || typeof changes !== "object") {
    throw new HttpError(400, "Body must contain a 'changes' object", "bad_request");
  }

  const now = Date.now();
  const statements: D1PreparedStatement[] = [];
  const applied: Record<string, number> = {};

  for (const domainName of Object.keys(changes)) {
    const spec = resolveDomain(domainName);
    const rows = (changes as Record<string, unknown>)[domainName];
    if (!Array.isArray(rows)) {
      throw new HttpError(400, `changes.${domainName} must be an array`, "bad_request");
    }
    if (rows.length > MAX_ROWS_PER_DOMAIN) {
      throw new HttpError(413, `Too many rows for '${domainName}' (max ${MAX_ROWS_PER_DOMAIN})`, "too_large");
    }
    for (const raw of rows) {
      if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
        throw new HttpError(400, `Invalid row in '${domainName}'`, "bad_request");
      }
      // Domain-specific semantic validation (enums, date format, value ranges).
      spec.checkRow?.(raw as Row);
      const deleted = (raw as Row).deleted === true || (raw as Row)._deleted === true;
      statements.push(buildUpsert(env, spec, userId, raw as Row, now, deleted));
    }
    applied[domainName] = rows.length;
  }

  if (statements.length > 0) await env.DB.batch(statements);
  return { applied, serverTime: now };
}

function buildUpsert(
  env: Env,
  spec: DomainSpec,
  userId: string,
  raw: Row,
  now: number,
  deleted: boolean,
): D1PreparedStatement {
  // Identifiers below are all from the static registry — safe to interpolate.
  const cols = [
    "user_id",
    ...spec.keys.map((c) => c.name),
    ...spec.data.map((c) => c.name),
    "updated_at",
    "deleted_at",
  ];
  const values: Bound[] = [
    userId, // server-derived identity; body user_id is ignored entirely
    ...spec.keys.map((c) => coerce(c, raw[c.name])),
    ...spec.data.map((c) => coerce(c, raw[c.name])),
    now,
    deleted ? now : null,
  ];
  const placeholders = cols.map(() => "?").join(", ");
  const conflictTarget = ["user_id", ...spec.keys.map((c) => c.name)].join(", ");
  const updates = [...spec.data.map((c) => c.name), "updated_at", "deleted_at"]
    .map((name) => `${name} = excluded.${name}`)
    .join(", ");

  const sql =
    `INSERT INTO ${spec.table} (${cols.join(", ")}) VALUES (${placeholders}) ` +
    `ON CONFLICT(${conflictTarget}) DO UPDATE SET ${updates}`;
  return env.DB.prepare(sql).bind(...values);
}

// --------------------------------------------------------------------------- pull

export interface PullResult {
  changes: Record<string, Row[]>;
  /** Durable per-domain cursors (updated_at, gte semantics). Advance ONLY when hasMore is false. */
  cursors: Record<string, number>;
  /** Per-domain flag: true when more rows remain behind the continuation token. */
  hasMore: Record<string, boolean>;
  /** Opaque keyset continuation per domain; present only when hasMore[domain] is true. */
  continuation: Record<string, string>;
  serverTime: number;
}

/** Opaque continuation token: keyset position (updated_at, rowid) as "u.r". */
interface Keyset {
  u: number;
  r: number;
}

function encodeKeyset(k: Keyset): string {
  // Tokens are treated as opaque by clients; the format is server-internal and
  // re-validated on decode. Kept dependency-free (no Buffer/atob needed).
  return `${k.u}.${k.r}`;
}

function decodeKeyset(raw: unknown, domainName: string): Keyset {
  if (typeof raw !== "string" || !/^\d{1,15}\.\d{1,15}$/.test(raw)) {
    throw new HttpError(400, `Invalid continuation token for '${domainName}'`, "bad_request");
  }
  const [u, r] = raw.split(".").map(Number) as [number, number];
  return { u, r };
}

export async function handlePull(env: Env, userId: string, body: unknown): Promise<PullResult> {
  const b = (body ?? {}) as { domains?: unknown; cursors?: unknown; continuation?: unknown };
  const requested: string[] =
    Array.isArray(b.domains) && b.domains.length > 0
      ? (b.domains as string[])
      : Object.keys(DOMAINS);
  const inCursors: Record<string, unknown> =
    b.cursors && typeof b.cursors === "object" ? (b.cursors as Record<string, unknown>) : {};
  const inContinuations: Record<string, unknown> =
    b.continuation && typeof b.continuation === "object"
      ? (b.continuation as Record<string, unknown>)
      : {};

  const now = Date.now();
  const changes: Record<string, Row[]> = {};
  const cursors: Record<string, number> = {};
  const hasMore: Record<string, boolean> = {};
  const continuation: Record<string, string> = {};

  for (const domainName of requested) {
    const spec = resolveDomain(domainName);
    const rawCursor = inCursors[domainName];
    const since = typeof rawCursor === "number" && Number.isFinite(rawCursor) ? Math.trunc(rawCursor) : 0;
    // A continuation token takes precedence: we are mid-paginate for this domain
    // and must resume strictly after the last delivered (updated_at, rowid).
    const keyset =
      domainName in inContinuations ? decodeKeyset(inContinuations[domainName], domainName) : null;

    // Return only the user's own rows. `user_id` is omitted from the projection
    // (the caller already knows it); keys + data + updated_at + deleted_at are sent.
    // `rowid` is selected only to build the continuation keyset and is stripped
    // from the returned rows below.
    const selectCols = [
      "rowid",
      ...spec.keys.map((c) => c.name),
      ...spec.data.map((c) => c.name),
      "updated_at",
      "deleted_at",
    ];
    const sql = keyset
      ? `SELECT ${selectCols.join(", ")} FROM ${spec.table} ` +
        `WHERE user_id = ? AND (updated_at > ? OR (updated_at = ? AND rowid > ?)) ` +
        `ORDER BY updated_at ASC, rowid ASC LIMIT ${PULL_PAGE_LIMIT}`
      : `SELECT ${selectCols.join(", ")} FROM ${spec.table} ` +
        `WHERE user_id = ? AND updated_at >= ? ` +
        `ORDER BY updated_at ASC, rowid ASC LIMIT ${PULL_PAGE_LIMIT}`;
    const binds: Bound[] = keyset ? [userId, keyset.u, keyset.u, keyset.r] : [userId, since];
    const res = await env.DB.prepare(sql).bind(...binds).all<Row>();
    const rows = (res.results ?? []) as Row[];

    if (rows.length === PULL_PAGE_LIMIT) {
      // Full page — another page may exist (or more rows may share the final
      // updated_at). The keyset continuation resumes strictly after the last
      // (updated_at, rowid), so a shared timestamp can never be skipped.
      const last = rows[rows.length - 1];
      const key: Keyset = {
        u: Number(last.updated_at) || 0,
        r: Number(last.rowid) || 0,
      };
      for (const r of rows) delete r.rowid;
      changes[domainName] = rows;
      hasMore[domainName] = true;
      continuation[domainName] = encodeKeyset(key);
      cursors[domainName] = since;
    } else {
      for (const r of rows) delete r.rowid;
      changes[domainName] = rows;
      hasMore[domainName] = false;
      cursors[domainName] = rows.reduce(
        (mx, r) => Math.max(mx, Number(r.updated_at) || 0),
        since,
      );
    }
  }

  return { changes, cursors, hasMore, continuation, serverTime: now };
}
