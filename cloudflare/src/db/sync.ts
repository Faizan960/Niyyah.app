import type { Env } from "../types";
import { DOMAINS, FORBIDDEN_DOMAINS, type ColumnSpec, type DomainSpec } from "./schema";
import { HttpError } from "../util/http";

/**
 * Generic offline-first sync core: push (upsert) and pull (cursor read).
 *
 * All SQL identifiers come from the static {@link DOMAINS} registry; all client
 * values are bound as parameters. `user_id` is always the server-derived Clerk id
 * passed in by the router — it is never read from the request body.
 */

const MAX_ROWS_PER_DOMAIN = 1000;
const PULL_PAGE_LIMIT = 1000;

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
      if (typeof value === "string") return value;
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
      if (!raw || typeof raw !== "object") {
        throw new HttpError(400, `Invalid row in '${domainName}'`, "bad_request");
      }
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
  cursors: Record<string, number>;
  serverTime: number;
}

export async function handlePull(env: Env, userId: string, body: unknown): Promise<PullResult> {
  const b = (body ?? {}) as { domains?: unknown; cursors?: unknown };
  const requested: string[] =
    Array.isArray(b.domains) && b.domains.length > 0
      ? (b.domains as string[])
      : Object.keys(DOMAINS);
  const inCursors: Record<string, unknown> =
    b.cursors && typeof b.cursors === "object" ? (b.cursors as Record<string, unknown>) : {};

  const now = Date.now();
  const changes: Record<string, Row[]> = {};
  const cursors: Record<string, number> = {};

  for (const domainName of requested) {
    const spec = resolveDomain(domainName);
    const rawCursor = inCursors[domainName];
    const since = typeof rawCursor === "number" && Number.isFinite(rawCursor) ? Math.trunc(rawCursor) : 0;

    // Return only the user's own rows. `user_id` is omitted from the projection
    // (the caller already knows it); keys + data + updated_at + deleted_at are sent.
    const selectCols = [
      ...spec.keys.map((c) => c.name),
      ...spec.data.map((c) => c.name),
      "updated_at",
      "deleted_at",
    ];
    const sql =
      `SELECT ${selectCols.join(", ")} FROM ${spec.table} ` +
      `WHERE user_id = ? AND updated_at >= ? ORDER BY updated_at ASC LIMIT ${PULL_PAGE_LIMIT}`;
    const res = await env.DB.prepare(sql).bind(userId, since).all<Row>();
    const rows = res.results ?? [];
    changes[domainName] = rows;
    cursors[domainName] = rows.reduce((mx, r) => Math.max(mx, Number(r.updated_at) || 0), since);
  }

  return { changes, cursors, serverTime: now };
}
