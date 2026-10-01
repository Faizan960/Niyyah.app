# Niyyah Backend (Cloudflare)

A standalone cloud backend for **Niyyah** built on **Cloudflare Workers + D1**, authenticated with **Clerk**, exposing a versioned JSON API (`/api/v1`) for **offline-first synchronization**.

> **This backend does not replace Room.** Niyyah stays offline-first: the device's Room database remains the source of truth for the user experience. This service is an optional cloud mirror that lets a signed-in user's data converge across devices.

> **Status: isolated / not wired into the app.** This project lives entirely under `cloudflare/`. It does **not** modify the Android app, its sync code, or the existing Supabase backend. Whether this eventually *replaces* or *coexists with* Supabase is an open decision (see [Relationship to the existing backend](#relationship-to-the-existing-backend)).

---

## Contents

- [Architecture](#architecture)
- [What syncs (and what never will)](#what-syncs-and-what-never-will)
- [Project structure](#project-structure)
- [Local development](#local-development)
- [API reference](#api-reference)
- [Sync model](#sync-model)
- [Authentication](#authentication)
- [Security](#security)
- [Privacy](#privacy)
- [Testing](#testing)
- [Deploying for real](#deploying-for-real)
- [Relationship to the existing backend](#relationship-to-the-existing-backend)

---

## Architecture

```
Android app (Room = source of truth)
        │  HTTPS + Clerk session JWT (Bearer)
        ▼
Cloudflare Worker  (src/index.ts)
        │  verify JWT (Clerk JWKS)  →  user_id = token.sub
        ▼
   /api/v1/sync/push   ──►  upsert into D1 (server-set updated_at)
   /api/v1/sync/pull   ◄──  rows where updated_at >= cursor
        │
        ▼
Cloudflare D1 (SQLite) — one logical dataset per Clerk user
```

- **Stateless Worker.** Identity is derived from the verified token on every request. Nothing is cached between requests except the JWKS key set.
- **Generic sync core.** `src/db/sync.ts` builds every query from a static registry (`src/db/schema.ts`). Clients never choose table or column names, which is what keeps the dynamic SQL injection-safe.
- **Per-user isolation.** Every table is keyed by `user_id` first; every read is `WHERE user_id = ?` with the server-derived id.

### Tech stack

| Concern        | Choice                                             |
| -------------- | -------------------------------------------------- |
| Compute        | Cloudflare Workers                                 |
| Database       | Cloudflare D1 (SQLite)                             |
| Auth           | Clerk session JWT, verified via public JWKS (RS256)|
| Language       | TypeScript (ESM)                                   |
| JWT library    | [`jose`](https://github.com/panva/jose)            |
| Tests          | Vitest + `@cloudflare/vitest-plugin` (real Worker runtime + real local D1) |
| Tooling        | Wrangler                                           |

---

## What syncs (and what never will)

**Syncable domains** (D1 tables, all per-user):

| Domain                   | Natural key (besides `user_id`)       | Purpose                               |
| ------------------------ | ------------------------------------- | ------------------------------------- |
| `prayer_records`         | `date`, `prayer_name`                 | Prayer completion / verification      |
| `streaks`                | — (one row per user)                  | Streak + mercy-week state             |
| `quran_reader_state`     | `surah_number`                        | Continue-reading position per surah   |
| `quran_reading_sessions` | `session_id`                          | Reading-session log                   |
| `quran_surah_stats`      | `surah_number`                        | Per-surah aggregate stats             |
| `quran_bookmarks`        | `surah_number`, `ayah_number`         | Bookmarks (`ayah_number = 0` = surah) |
| `hadith_reads`           | `collection`, `hadith_number`         | Hadith read tracking                  |
| `hadith_bookmarks`       | `collection`, `hadith_number`         | Hadith bookmarks                      |

Plus `users` (one row per Clerk user, maintained server-side).

### 🚫 Emergency overrides are never syncable

There is **no** `emergency_overrides` table, **no** sync domain, and **no** endpoint for emergency overrides — by design. Emergency override allowances are **local-only and device-bound**. The backend hard-rejects any push/pull that names `emergency_overrides` (or an alias) with HTTP `400 forbidden_domain`, and a test asserts the table does not exist. Do not add one.

---

## Project structure

```
cloudflare/
├── package.json
├── wrangler.jsonc            # Worker + D1 binding + non-secret vars
├── tsconfig.json
├── vitest.config.ts          # generates a test keypair; wires test env + migrations
├── .dev.vars.example         # copy to .dev.vars for local Clerk config (gitignored)
├── migrations/
│   └── 0001_initial.sql       # all 9 tables + users (NO emergency_overrides)
├── src/
│   ├── index.ts              # router: /health, /me, /sync/push, /sync/pull
│   ├── types.ts              # Env bindings
│   ├── auth/clerk.ts         # Clerk JWT verification (JWKS, RS256)
│   ├── db/
│   │   ├── schema.ts         # domain registry (allowlist) + emergency denylist
│   │   └── sync.ts           # generic push/pull (parameterized SQL)
│   └── util/http.ts          # JSON + error helpers
└── test/
    ├── helpers.ts            # token minting + request helpers + migration setup
    ├── health.test.ts
    ├── auth.test.ts
    └── sync.test.ts
```

---

## Local development

Prerequisites: Node 18+ and npm. Wrangler is used via `npx` (a dev dependency).

```bash
cd cloudflare
npm install
```

Configure Clerk for `wrangler dev` (optional for tests — tests use a throwaway keypair):

```bash
cp .dev.vars.example .dev.vars
# edit .dev.vars with your Clerk issuer + JWKS URL
```

Apply migrations to the **local** D1 and start the dev server:

```bash
npm run migrate:local      # wrangler d1 migrations apply niyyah --local
npm run dev                # wrangler dev  (http://localhost:8787)
```

Smoke test:

```bash
curl http://localhost:8787/api/v1/health
# {"ok":true,"service":"niyyah-backend","time":...}
```

> No Clerk **secret** key is ever required. JWT verification uses only the **public** JWKS.

---

## API reference

Base path: `/api/v1`. All bodies are JSON. All routes except `/health` require
`Authorization: Bearer <clerk_session_jwt>`.

### `GET /api/v1/health`
Liveness. No auth.
```json
{ "ok": true, "service": "niyyah-backend", "time": 1730000000000 }
```

### `GET /api/v1/me`
Returns the authenticated Clerk user id (the token `sub`).
```json
{ "userId": "user_2abc..." }
```

### `POST /api/v1/sync/push`
Upserts client changes. Identity comes from the token; any `user_id` in the body is ignored.

Request:
```json
{
  "changes": {
    "prayer_records": [
      { "date": "2026-10-01", "prayer_name": "FAJR", "verified": true, "timestamp_ms": 1730000000000 }
    ],
    "quran_bookmarks": [
      { "surah_number": 2, "ayah_number": 255, "collection_name": "Favourites" },
      { "surah_number": 18, "ayah_number": 10, "deleted": true }
    ]
  }
}
```

Response:
```json
{ "applied": { "prayer_records": 1, "quran_bookmarks": 2 }, "serverTime": 1730000000123 }
```

- Set `"deleted": true` on a row to tombstone it.
- Unknown / non-syncable domains → `400`. `emergency_overrides` → `400 forbidden_domain`.
- Missing required key column or wrong type → `400 bad_request`.

### `POST /api/v1/sync/pull`
Returns rows changed at or after the per-domain cursor.

Request (omit `domains` to pull all; omit a cursor to start at 0):
```json
{ "domains": ["prayer_records", "quran_bookmarks"], "cursors": { "prayer_records": 1730000000000 } }
```

Response:
```json
{
  "changes": {
    "prayer_records": [
      { "date": "2026-10-01", "prayer_name": "FAJR", "verified": 1, "override_used": 0,
        "verification_type": null, "timestamp_ms": 1730000000000,
        "updated_at": 1730000000500, "deleted_at": null }
    ],
    "quran_bookmarks": []
  },
  "cursors": { "prayer_records": 1730000000500, "quran_bookmarks": 1730000000000 },
  "serverTime": 1730000000999
}
```

The client persists each returned `cursors[domain]` and sends it back on the next pull.

---

## Sync model

Offline-first, mirroring the algorithm the Android app already uses for Quran bookmarks (push → pull → advance cursor), so the two stay conceptually aligned.

- **Server-authoritative `updated_at`.** Every write stamps `updated_at = Date.now()` on the server. Device clocks are never trusted for ordering. `updated_at` **is** the pull cursor.
- **Pull is `updated_at >= cursor` (gte).** The row sitting exactly on the cursor boundary is re-delivered, never skipped. Because apply is an idempotent upsert on the natural key, re-delivery is harmless. Clients advance the cursor to `max(updated_at)` seen (or `+1` to skip the boundary).
- **Soft deletes (tombstones).** `deleted_at` non-null marks a delete. Deletes propagate like any other change, so other devices converge on them through a normal pull. Re-adding a key is an upsert that clears `deleted_at`.
- **Conflict resolution.** Last write wins on the natural key: the most recent `push` for a key becomes the newest server row and propagates. The Android engine's rule — a locally pending (unpushed) mutation always wins over an incoming pull for the same key, then is pushed — remains the client's responsibility; the server stays a simple convergent store.
- **Idempotency.** `push` is `INSERT ... ON CONFLICT(natural key) DO UPDATE`. Pushing the same row twice yields one row.

---

## Authentication

- The Worker expects a Clerk **session JWT** as `Authorization: Bearer <jwt>`.
- The token is verified with `jose` against Clerk's **public JWKS** (RS256), checking signature, `exp`/`nbf`, and `iss` (`CLERK_ISSUER`). An optional `azp` allowlist (`CLERK_AUTHORIZED_PARTIES`) is enforced when set.
- **Identity = `sub` only.** The request body is never trusted for identity. A spoofed `user_id` in a payload is ignored; the row is written under the token subject.
- Tokens are never logged, persisted, or echoed in errors.

Config (`wrangler.jsonc` vars / `.dev.vars`):

| Var                        | Required | Meaning                                        |
| -------------------------- | -------- | ---------------------------------------------- |
| `CLERK_ISSUER`             | yes      | Clerk Frontend API origin (issuer check)       |
| `CLERK_JWKS_URL`           | yes\*    | Public JWKS endpoint                            |
| `CLERK_AUTHORIZED_PARTIES` | no       | Comma-separated `azp` allowlist (empty = skip)  |

\* In tests, a literal JWKS is injected via `CLERK_JWKS_JSON`; production uses the URL.

---

## Security

- **No secrets committed.** Only public config (issuer, JWKS URL) lives in `wrangler.jsonc`. Real local config goes in `.dev.vars` (gitignored). There is no Clerk secret key in this project at all.
- **Injection-safe dynamic SQL.** Table/column names come only from the static registry; all values are bound parameters. A test pushes `1'); DROP TABLE prayer_records;--` as data and asserts the table survives.
- **Per-user authorization** on every read and write via the server-derived `user_id`.
- **Body size guard** (5 MB) and per-domain row cap (1000) to bound request cost.
- **Safe errors.** Unexpected failures return `500 internal` without leaking internals.

---

## Privacy

The backend stores only what is needed to converge a signed-in user's own worship/reading data:

- Prayer completion records, streak counters.
- Quran reading position, reading sessions, per-surah stats, bookmarks.
- Hadith reads and bookmarks.
- A `users` row: `user_id` (Clerk sub), `created_at`, `last_seen_at`.

It does **not** store: emergency overrides (local-only), app-blacklist/lock configuration, device identifiers, location, prayer *times* cache, or any Clerk profile fields beyond the subject id.

---

## Testing

Tests run against the **real Worker runtime** and a **real local D1** via `@cloudflare/vitest-plugin`. A throwaway RS256 keypair is generated per run (see `vitest.config.ts`): the public JWKS is given to the Worker and the private key to the tests, so tokens are genuinely signed and genuinely verified — no mocking of the auth path.

```bash
npm test
```

Covered scenarios include: health (no auth), missing/garbage/expired/wrong-issuer tokens rejected, valid token → `/me`, push+pull roundtrip, multi-domain push, idempotent upsert, cross-user isolation, spoofed `user_id` ignored, malformed payload rejected, wrong type rejected, unknown domain rejected, **emergency override push/pull rejected + table absent**, SQL-injection string stored as inert data, tombstone deletes, and incremental cursor (gte boundary + advance).

---

## Deploying for real

Not automated. When you choose to deploy:

```bash
npx wrangler login
npx wrangler d1 create niyyah          # copy the returned database_id into wrangler.jsonc
npm run migrate:remote                  # apply migrations to the remote D1
# set production vars:
npx wrangler deploy
```

Set `CLERK_ISSUER` / `CLERK_JWKS_URL` for your Clerk instance (vars or `wrangler secret`/dashboard as you prefer; they are not secrets). Replace the placeholder `database_id` in `wrangler.jsonc` first.

---

## Relationship to the existing backend

Niyyah already has a **Supabase** backend that syncs Quran bookmarks today (PostgREST + Clerk JWT via RLS), driven by the app's generic sync outbox. This Cloudflare project was built **standalone and is not wired into the app**, specifically to avoid creating two competing live backends for the same data.

The replace-vs-coexist decision is intentionally deferred. If this backend is later adopted, the Android boundary would be a new `QuranBookmarksCloud`-style transport (and sibling transports per domain) pointing at `/api/v1/sync/*` — a focused, reviewable change made on its own branch, not part of this scaffold.
