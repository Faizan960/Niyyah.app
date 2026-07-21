# BM-013 — Cloud Database Integration: Architecture Audit

Status: **AUDIT / PROPOSAL ONLY — no implementation.** Awaiting approval of database choice + architecture.
Author: Claude (audit pass over actual source + graphify graph)
Date: 2026-07-21

---

## 1. Existing NIYYAH Storage Architecture (as-built)

Layers found in source:

```
UI (Compose)  →  ViewModels  →  Repositories  →  { Room (AppDatabase v8) | DataStore | EncryptedSharedPreferences | filesDir JSON }
                                             ↘  Retrofit (Aladhan, Hadith API) — read-only external content
```

Persistence surfaces that actually exist today:

| Surface | Mechanism | File / name | Purpose |
|---|---|---|---|
| Relational | Room `AppDatabase` v8, db name `salahlock_db` | 15 entities, 8 migrations | prayer records, streak, overrides, blacklist, quran bookmarks/progress, collections, hadith/azkar/knowledge cache, prayer-time cache, masjid caches |
| Key-value prefs | DataStore `user_prefs` | `UserPreferences.kt` | all settings, per-prayer lock toggles, sync guards, pause, jumma times, hadith scroll positions |
| Encrypted prefs | `EncryptedSharedPreferences` `secure_user_prefs` | `SecurePreferences.kt` | user lat/lng, city, location mode |
| Flat files | `filesDir/reflections/*.json` | `SpiritualReportRepository.kt` | frozen monthly reflection reports (one JSON/month) |
| Backup | AES-256-GCM ZIP | `BackupPackage.kt` / `BackupRepository` | existing device-migration export (prayer history, streak, overrides, local masjid, settings, blocked apps, reflections) |
| Auth | Clerk Android SDK 1.0.36 | `AuthRepository.kt` | sole identity authority; `Clerk.userFlow`, `Clerk.session` |

**Critical finding for cloud sync:** *No Room table, DataStore key, or file is scoped by user today.* Every table is single-user/device-global (`salahlock_db` is one blob). There is no `clerk_user_id` anywhere. This is the single biggest structural gap between today's app and a multi-account cloud model (see §6, §7).

The existing `BackupPackage` already encodes the app's own opinion of "user-owned data," which is a strong starting signal for what to cloud-sync — but note it currently bundles device-specific things (blocked apps, local masjid, all settings) because a ZIP restore targets *the same user re-setting-up a device*, which is a different problem from *continuous multi-device sync*.

---

## 2. Exact Data Classification Matrix

Derived from actual entities/DAOs/prefs, not assumptions.

### A. CLOUD-SYNC USER DATA (follows the Clerk identity)

| DATA | CURRENT STORAGE | OWNER | SYNC? | WHY | CONFLICT RISK | PROPOSED CLOUD TABLE |
|---|---|---|---|---|---|---|
| Prayer completion/history | Room `prayer_records` (`PrayerRecord`) | user | **YES** | spiritual history is the emotional core; must survive device loss | Medium — same (date,prayer) row edited on 2 devices | `prayer_records` |
| Streak state | Room `streaks` (`StreakEntity`, single row id=1) | user | **YES (derived-but-durable)** | best streak / mercy week must not reset on new device | Medium — recomputable but "bestStreak" is durable | `user_streak` (1 row / user) |
| Emergency overrides (mercy) | Room `emergency_overrides` | user | **YES** | monthly mercy counts feed rank + achievements | Low–Medium | `emergency_overrides` |
| Quran bookmarks | Room `quran_bookmarks` | user | **YES** | classic cross-device expectation | Medium — add/delete races | `quran_bookmarks` |
| Quran reading progress | Room `quran_progress` (per surah) | user | **YES** | "continue reading" across devices | **High** — position monotonicity | `quran_progress` |
| Hadith/Knowledge bookmarks | Room `hadith_table` rows where `isBookmarked=1` (+`bookmarkSource`) | user | **YES (the bookmark flag only, not the text)** | user-curated saves | Medium | `hadith_bookmarks` (id + source only) |
| Azkar bookmarks | Room `azkar_table.isBookmarked` | user | **YES (flag only)** | user-curated saves | Low | `azkar_bookmarks` |
| Collections | Room `user_collections` | user | **YES** | user-created libraries | Medium — rename races | `collections` |
| Collection membership | Room `collection_items` (contentType+contentKey ref) | user | **YES** | links saved items into libraries | Medium | `collection_items` |
| Monthly reflections (frozen) | `filesDir/reflections/*.json` (`StoredMonthlyReflection`) | user | **YES** | frozen scores/rank must persist forever | **Very low** (immutable once frozen) | `monthly_reflections` |
| Cross-device preferences (subset) | DataStore | user | **YES (selective)** | theme, madhab, calc method, verification method/count, reminder toggles should follow the user | Low (last-write-wins per field) | `user_preferences` (per-field) |

### B. LOCAL-ONLY / DEVICE-SPECIFIC DATA (must NOT sync)

| DATA | CURRENT STORAGE | OWNER | SYNC? | WHY NOT | CONFLICT RISK | CLOUD TABLE |
|---|---|---|---|---|---|---|
| App blacklist / package selections | Room `app_blacklist` | device | **NO** | package names are per-device; installed apps differ across phones (see §9) | n/a | — |
| Per-prayer lock toggles, lock duration, block profile, pause-until | DataStore | device | **NO** | device Salah-Lock behavior config | n/a | — |
| User location (lat/lng/city/mode) | `SecurePreferences` (encrypted) | device | **NO** | device-current GPS; syncing stale coords breaks prayer times | n/a | — |
| Prayer-time cache | Room `prayer_time_cache` | device | **NO** | recomputable per device/location | n/a | — |
| Local masjid timetable + Jumma times | Room `local_masjid`, DataStore jumma keys | device | **NO (default)** | tied to the mosque near *this* device; optional future opt-in | n/a | — |
| Sync guards (last sync ms/lat/lng/month) | DataStore | device | **NO** | pure local scheduling state | n/a | — |
| Hadith scroll positions | DataStore `hadith_pos_*` | device | **NO** | ephemeral UI state | n/a | — |
| Last-backup ms, last-interstitial ms | DataStore | device | **NO** | device housekeeping | n/a | — |
| Onboarding done flag | DataStore | device | **NO** | per-install | n/a | — |
| Verification session state, mic/overlay/accessibility perms | transient / OS | device | **NO** | security-sensitive device state (see §9) | n/a | — |

### C. STATIC / REFERENCE DATA (bundled — never per-user in cloud)

| DATA | CURRENT STORAGE | SYNC? | WHY |
|---|---|---|---|
| Quran corpus (text/surah/ayah) | bundled/local assets | **NO** | identical for all users; reference by `surahNumber`/`ayahNumber` |
| Hadith corpus text | Room `hadith_table` (cached from API/assets) | **NO** | reference by stable id (`bukhari-657-eng`); sync only the *bookmark pointer* |
| Azkar content | Room `azkar_table` (seeded) | **NO** | seeded reference; sync only bookmark flag + (optionally) progress counts |
| Knowledge category mapping, collection books | Room `hadith_category_mapping`, `collection_book_entity` | **NO** | app-seeded reference (re-seeded by `KnowledgeRepository`) |
| Nearby masjid cache (OSM) | Room `masjids` (dead feature) | **NO** | transient cache; feature retired |

**Anti-duplication rule:** cloud rows for bookmarks store only `surah/ayah` or the stable hadith/azkar id + `source` — never the Arabic/translation text. This keeps per-user cloud footprint tiny (§10).

---

## 3. Free Database Options Compared

| Criterion | **Supabase (Postgres)** | Neon (Postgres) | Firebase Firestore | Appwrite Cloud |
|---|---|---|---|---|
| Clerk integration | **Native 1st-class** third-party auth provider (RLS reads `auth.jwt()->>'sub'` = Clerk user id) | None built-in — you build your own JWT verification / backend | Requires custom-token bridge or Cloud Function verifying Clerk JWT | Custom JWT provider; workable but less documented |
| Android/Kotlin access | REST (PostgREST) + Kotlin SDK (`supabase-kt`) | Raw Postgres (needs your own API tier) or HTTP driver | Official Firebase Android SDK | Appwrite Kotlin SDK |
| Client-safe keys | **Yes** — anon/publishable key is public-safe; RLS enforces isolation | No public client model — a raw DB URL can't ship in an APK | Yes — API key public-safe, security via rules | Yes — project id public-safe |
| Offline-first + Room fit | Great — REST pull/push, you keep Room as source | You own everything (more work) | Firestore has own offline cache — competes with Room, awkward two-cache | OK |
| Row-level security | **Postgres RLS** (declarative, per-row) | Manual (your backend) | Security Rules (separate language) | Appwrite permissions |
| REST/API | PostgREST auto REST + realtime | Needs a backend | REST + SDK | REST + SDK |
| Realtime | Optional (websockets) — not required for us | No | Yes | Yes |
| Migrations | SQL migrations (versioned, portable) | SQL (portable) | Schemaless (no migrations, risk drift) | Console/JSON |
| Vendor lock-in | **Low** — plain Postgres, exportable | Low (Postgres) | **High** (proprietary) | Medium |
| Free tier | 2 projects, 500 MB DB, 5 GB egress, 50k MAU (auth unused by us), pauses after 7 days inactivity on free | 0.5 GB storage, generous compute, autosuspends | 1 GB stored, 50k reads/20k writes/day, 10 GB/mo egress | 5 GB, limited bandwidth |
| Operational complexity | Low (managed) | Medium (bring backend) | Low | Low–Medium |

---

## 4. Recommended Backend: **Supabase (Postgres) as a database only, Clerk as third-party auth provider**

Not Supabase Auth. Not Firebase Auth. Clerk stays the sole identity authority. Supabase is used purely as the cloud Postgres + RLS + auto REST layer, configured to *trust Clerk-signed session tokens*.

Why it wins for THIS app:
- It is the only option with a **documented, secretless, native Clerk integration** where the DB itself verifies the Clerk JWT and enforces per-user isolation — no custom backend to write, no Clerk secret leaving Clerk.
- **Room stays untouched and remains the fast source.** Supabase is a sync target reached over REST from a background layer, not a replacement cache (unlike Firestore, whose own offline cache would fight Room).
- **Plain Postgres = low lock-in**: schema is portable, exportable, migratable; if Supabase's free tier ever fails us we can lift-and-shift to Neon/any Postgres with the same SQL.
- Public **anon key is safe to ship in the APK**; isolation is enforced server-side by RLS, satisfying the "APK contains only public-safe credentials" requirement (§6).

Runner-up: Neon (cleaner Postgres, but you must build+host your own authenticated API tier — more moving parts for a solo project). Firestore is rejected on lock-in + the double-cache clash with Room.

---

## 5. Why It Fits Clerk + Room

- **Clerk:** Supabase's third-party auth integration (GA since 2025-04-01; the old Clerk JWT template is deprecated) accepts the Clerk session token directly. RLS policies key off `auth.jwt()->>'sub'`, which is the Clerk user id (`user_xxxxx`). Identity mapping is 1:1 and enforced by the database, not the client.
- **Room:** Repositories keep writing to Room synchronously (UI stays instant). A new sync layer mirrors the *cloud-eligible subset* to Supabase in the background. Room's existing DAOs/entities are reused; we add sync-bookkeeping columns and a `clerk_user_id` scoping column (§7, §15).

---

## 6. Authentication / Security Architecture

**Principle: the client never asserts its own identity; the token does.**

Flow:
```
Android app  --(Clerk.session.fetchToken())-->  short-lived Clerk JWT (sub = user_xxxxx)
     |
     |  every Supabase REST call: Authorization: Bearer <clerk jwt> + apikey: <supabase ANON key>
     v
Supabase PostgREST verifies the JWT signature against Clerk's JWKS
     v
Postgres RLS: policy USING ( clerk_user_id = auth.jwt() ->> 'sub' )
     v
Row returned ONLY if it belongs to the authenticated Clerk user
```

Token retrieval on Android (SDK 1.0.36): `Clerk.session?.fetchToken()` → `ClerkResult<TokenResource, ClerkErrorResponse>` (JWT auto-refreshed ~60s; fetch per sync batch, do not cache long). *Exact call signature to be confirmed against the pinned SDK version at implementation time.*

Hard rules (all enforced):
- **APK ships only:** Supabase project URL + Supabase **anon/publishable** key + Clerk **publishable** key. All three are designed to be public.
- **Never in the APK:** Supabase `service_role` key, Postgres connection string/password, `CLERK_SECRET_KEY`.
- **Every private table:** `clerk_user_id text not null default (auth.jwt()->>'sub')`, RLS **enabled**, with select/insert/update/delete policies all gated on `clerk_user_id = auth.jwt()->>'sub'`. Insert policy uses `WITH CHECK` so a client cannot write rows under someone else's id.
- No public/anon-readable user tables. Reference/static data is *not* in Supabase at all (it's bundled), so there is nothing public to leak.
- Clerk configured in the Supabase dashboard as a third-party auth provider (Clerk domain / JWKS URL). No JWT secret shared with Clerk (native integration).

Result: even though the anon key is public, a malicious client cannot read or write another user's rows — the database rejects it. A forged `userId` is meaningless because the id is taken from the *verified* JWT, not from client input.

---

## 7. Proposed Schema (minimal, derived from real entities)

All private tables carry: `clerk_user_id text` (RLS key), `updated_at timestamptz`, `deleted_at timestamptz null` (soft delete / tombstone for sync). `id` strategy noted per table.

```sql
-- prayer history (maps PrayerRecord)
prayer_records(
  clerk_user_id text, date text, prayer_name text,
  verified bool, override_used bool, verification_type text,
  timestamp_ms bigint, updated_at timestamptz, deleted_at timestamptz,
  primary key (clerk_user_id, date, prayer_name)   -- natural key = idempotent upsert
)

user_streak(                                        -- StreakEntity, 1 row/user
  clerk_user_id text primary key, current_streak int, best_streak int,
  last_full_day text, last_mercy_week text, mercy_used_this_week bool, updated_at timestamptz
)

emergency_overrides(
  clerk_user_id text, month_year text, count int, last_reason text,
  last_used_ms bigint, updated_at timestamptz,
  primary key (clerk_user_id, month_year)
)

quran_bookmarks(
  clerk_user_id text, surah_number int, ayah_number int null,
  collection_name text, created_at_ms bigint, updated_at timestamptz, deleted_at timestamptz,
  unique (clerk_user_id, surah_number, ayah_number)
)

quran_progress(
  clerk_user_id text, surah_number int, last_ayah int, max_ayah int,
  timestamp_ms bigint, updated_at timestamptz,
  primary key (clerk_user_id, surah_number)
)

hadith_bookmarks(                                   -- pointer only, NOT hadith text
  clerk_user_id text, hadith_id text, bookmark_source text,
  created_at_ms bigint, updated_at timestamptz, deleted_at timestamptz,
  primary key (clerk_user_id, hadith_id)
)

azkar_bookmarks(
  clerk_user_id text, azkar_ref text,               -- stable ref, see note below
  updated_at timestamptz, deleted_at timestamptz,
  primary key (clerk_user_id, azkar_ref)
)

collections(
  clerk_user_id text, client_uuid text,             -- client-generated uuid (see §5/§15)
  name text, created_at_ms bigint, updated_at timestamptz, deleted_at timestamptz,
  primary key (clerk_user_id, client_uuid)
)

collection_items(
  clerk_user_id text, collection_uuid text, content_type text, content_key text,
  added_at_ms bigint, updated_at timestamptz, deleted_at timestamptz,
  unique (clerk_user_id, collection_uuid, content_type, content_key)
)

monthly_reflections(                                -- frozen, immutable
  clerk_user_id text, month text, payload jsonb,    -- StoredMonthlyReflection verbatim
  generated_at_ms bigint, updated_at timestamptz,
  primary key (clerk_user_id, month)
)

user_preferences(                                   -- selective, per-field last-write-wins
  clerk_user_id text primary key,
  theme_preference text, calc_method text, madhab text,
  verification_method text, verification_confirm_count int,
  reminder_quran bool, reminder_hadith bool, reminder_reflection bool,
  updated_at timestamptz
)
```

Notes:
- **Azkar `id` is `autoGenerate` (unstable across installs)** — it cannot be a cloud key directly. Before azkar bookmarks can sync we need a stable natural ref (e.g. `category + reference` hash). Flagged as a prerequisite, not a blocker for phase 1.
- **Collections use a client-generated UUID**, because today `user_collections.id` is a local autoincrement; two devices would both mint `id=1`. UUID is required to merge collections across devices (§15).
- Static Quran/Hadith/Azkar text is deliberately absent from the cloud.

---

## 8. Offline-First Sync Architecture

Room stays authoritative for reads. Cloud is a mirror.

**Write path (never blocks UI):**
```
User action → Repository writes Room (UI updates immediately via Flow)
            → mark row dirty (is_dirty=1, updated_at=now)
            → enqueue one-shot SyncWorker (WorkManager, network + backoff constraints)
SyncWorker  → fetchToken() → push dirty rows (upsert) → on success clear is_dirty
```

**Read/pull path:**
```
SyncWorker (or periodic WorkManager, ~ every few hours + on app foreground)
   → pull rows where updated_at > last_pulled_at (per table, per user)
   → merge into Room using conflict policy (§9)
   → Room Flow re-emits → UI updates automatically
```

Mechanics:
- **WorkManager** for reliability (survives process death, respects battery/network). One `CoroutineWorker` with `NetworkType.CONNECTED` + exponential backoff. No polling loops, no foreground service.
- Trigger points: on local write (debounced/coalesced), on app foreground, on connectivity regained (`NetworkConnectivityObserver` already exists), and a low-frequency periodic safety net.
- Room schema additions (new migration v8→v9): `is_dirty INTEGER`, `updated_at INTEGER`, `deleted_at INTEGER NULL`, and `clerk_user_id TEXT` on the cloud-eligible tables only. Local-only tables untouched.
- Fully offline: everything works against Room; sync is a no-op until a token + network exist. Fail-open, consistent with the existing `DailyInterstitialManager` philosophy.

---

## 9. Conflict-Resolution Strategy (per data type — deliberately simple)

| Data | Policy | Rationale |
|---|---|---|
| **Prayer completion** | **Union / OR-merge, never regress** — `verified = A.verified OR B.verified`; prefer `LOCK_VERIFIED > SELF_REPORTED > NONE`; keep earliest `timestamp_ms` | A verified prayer must never be lost to a stale device. Completion is monotonic. |
| **Streak** | Recompute from merged `prayer_records` after sync; `best_streak = max(local, cloud)` | Streak is derived; only `best_streak` is durable and takes the max. |
| **Emergency overrides** | `count = max(local, cloud)` per `month_year` | Never under-count mercy used (protects rank integrity). |
| **Quran reading position** | **Furthest-wins**: `last_ayah = max`, `max_ayah = max` per surah | Tablet at ayah 35 beats phone at ayah 20; reading only moves forward. |
| **Bookmarks (quran/hadith/azkar)** | **Add/delete via tombstone + LWW on `updated_at`** | A delete (with newer `deleted_at`) beats an older add and vice-versa; no lost-delete resurrection. |
| **Collections (rename)** | **Last-write-wins on `updated_at`** | Rename conflicts are rare and low-stakes; newest name wins. |
| **Collection membership** | Tombstone + LWW like bookmarks | Same add/delete race handling. |
| **Monthly reflections** | **First-write-wins / immutable** — never overwrite an existing `(user,month)` | Frozen reports must be preserved forever (matches existing `importAll` semantics). |
| **Preferences** | **Per-field last-write-wins on `updated_at`** | Acceptable per field; low stakes. |

No CRDTs. The only genuinely tricky cases (prayer completion, reading position) are handled by monotonic merges, which are simpler and safer than LWW.

---

## 10. Multi-Account Isolation Strategy

The core structural change: **Room becomes user-scoped.**

- Add `clerk_user_id` to every cloud-eligible Room table; every query filters by the currently signed-in Clerk id.
- On **login (User B)**: set the active `clerk_user_id`; Room reads only B's rows; a first pull fetches B's cloud data (§11).
- On **logout**: stop the sync layer; **purge (or lock) all user-scoped rows** so User B never sees User A's bookmarks/history/reflections/collections/prefs. Reflection JSON files in `filesDir/reflections/` must also be cleared/namespaced per user (today they're global — a real leak risk if untouched).
- **Do NOT wipe device-scoped data on logout**: `app_blacklist`, per-prayer lock toggles, block profile, location, prayer-time cache, local masjid. These are the device's Salah-Lock config and are intentionally account-independent.
- Decision to confirm with you: on logout, **clear** user-scoped local cache (simplest, safest, recommended) vs **retain encrypted per-account cache** for fast re-login. Recommendation: **clear** for v1 (privacy-first, matches the brand); re-pull on next login.

Because RLS also enforces isolation server-side, even a bug in the client scoping cannot expose another user's cloud rows.

---

## 11. First-Login / Existing-Local-Data Migration

Existing installs have local data created **before** any `clerk_user_id` existed and before cloud sync.

Strategy (runs once per account, guarded by a `first_sync_done` flag per user):
1. On first sign-in with cloud enabled, **stamp all existing unowned local rows** with the signed-in `clerk_user_id` (adopt the local data as this user's) — *only if the local data is currently unclaimed*.
2. Pull the user's cloud state.
3. **Merge, never overwrite**, using the §9 policies:
   - Cloud empty → upload the adopted local data (association step).
   - Cloud has data → merge (union prayer history, furthest-wins reading position, tombstone-aware bookmarks, first-write-wins reflections).
4. Mark `first_sync_done` for that user.

Edge case: if a device already has User A's local data and User B logs in fresh, the adopt step must **not** hand A's data to B — hence adoption only applies to unclaimed rows during the very first cloud enablement, and thereafter data is already `clerk_user_id`-stamped. This is why §10's logout purge matters.

---

## 12. Data That Must Remain Local-Only (explicit)

Never cloud-sync (security/device-specific):
- Blocked application package names (`app_blacklist`) — installed-app set differs per device; also privacy-sensitive.
- Salah-Lock config: per-prayer lock toggles, lock duration, block profile, pause-until.
- Accessibility / overlay / notification / microphone permission state — OS-owned, device-specific.
- Verification session state (transient).
- User GPS location, city, location mode (`SecurePreferences`).
- Prayer-time cache, sync guards, hadith scroll positions, onboarding flag, last-backup/interstitial timestamps.
- Nearby-masjid OSM cache (dead feature).
- Local masjid timetable + Jumma times — **device-default local**; only a deliberate future opt-in ("share my masjid across devices") would change this.

---

## 13. Free-Tier Limits & Cost Risk

Estimated per-user cloud footprint is tiny (pointers + counters, no corpus text): a heavy user ≈ a few hundred prayer_records/yr + tens of bookmarks + ~12 reflections/yr ≈ well under **~1 MB/user/year**.

| Scale | Est. DB size | Est. sync ops | Est. egress/mo | Verdict on Supabase free tier |
|---|---|---|---|---|
| 1,000 users | ~1–5 MB | low (batched, foreground + few/day) | << 1 GB | Comfortable |
| 10,000 users | ~50 MB | moderate | ~1–3 GB | Fits (free: 500 MB DB, 5 GB egress) |
| 100,000 users | ~0.5 GB | high | likely > 5 GB egress | **Exceeds free tier — plan for Pro (~$25/mo) or self-host Postgres** |

Key free-tier risks (state honestly — nothing is "free forever"):
- Supabase free projects **pause after ~7 days of inactivity** — fine during dev, unacceptable for production; production needs a paid or always-on plan even at low scale.
- 500 MB DB + 5 GB egress caps are reached around the **10k–50k active-user** range depending on sync frequency; batching + delta pulls (only `updated_at > last_pull`) are what keep us under.
- Mitigation for a limit hit: throttle pull frequency, harden delta sync, then upgrade to Pro or lift-and-shift the Postgres schema to Neon/self-host (low lock-in was chosen precisely for this).

---

## 14. Implementation Roadmap (for a later ticket — not now)

1. **Schema prep (local):** Room v8→v9 migration adding `clerk_user_id`, `updated_at`, `is_dirty`, `deleted_at` to cloud-eligible tables; stabilize azkar ref; add `client_uuid` to collections. *No behavior change yet.*
2. **User scoping:** make repositories filter Room by active `clerk_user_id`; wire logout purge of user-scoped data + reflection files.
3. **Supabase project + schema + RLS** (you create it after approval); configure Clerk as third-party auth provider.
4. **Sync core:** `CloudSyncClient` (REST + `fetchToken()`), `SyncWorker` (WorkManager), dirty-tracking, delta pull, conflict mergers (§9).
5. **First-sync migration** (§11) behind a per-user flag.
6. **Rollout:** ship dark (sync code present, disabled) → internal test on 2 devices → enable.

Ordering rule: 1→2 can land and be verified with zero cloud dependency; 3+ is the actual cloud switch-on.

---

## 15. Existing Files/Classes Likely to Change

- `data/db/AppDatabase.kt` — new v8→v9 migration; bump version.
- `data/db/entity/Entities.kt`, `QuranEntities.kt`, `CollectionEntities.kt`, `HadithEntity.kt`, `AzkarEntity.kt` — add sync/scoping columns (cloud-eligible only).
- `data/db/dao/*` (`PrayerRecordDao`, `QuranDao`, `CollectionsDao`, `StreakDao`, `HadithDao`, `AzkarDao`) — add `clerk_user_id` filters, dirty/tombstone queries.
- Repositories: `BookmarksRepository`, `CollectionsRepository`, `StreakRepository`, `SpiritualReportRepository` (per-user reflection dir), `KnowledgeRepository` (bookmark pointer), prayer-record repo — user scoping + mark-dirty on write.
- `auth/AuthRepository.kt` — expose `fetchToken()` and current `clerk_user_id`; drive purge/first-sync on state transitions.
- `SalahLockApplication.kt` — wire sync layer init + login/logout hooks.
- `data/preferences/UserPreferences.kt` — split "syncable" vs "device-only" pref accessors; add `first_sync_done` flag.
- `work/` — new worker alongside `AutoBackupWorker`/`AlarmRefreshWorker`.

## 16. New Components Genuinely Required

- `data/cloud/CloudSyncClient.kt` — authenticated REST calls to Supabase (Bearer Clerk JWT + anon key).
- `data/cloud/SyncWorker.kt` (WorkManager `CoroutineWorker`).
- `data/cloud/ConflictResolver.kt` — the §9 merge rules.
- `data/cloud/SyncRepository.kt` / `SyncManager` — orchestrates push/pull, dirty tracking, first-sync migration, logout purge.
- `data/cloud/dto/*` — Supabase row DTOs ↔ Room entity mappers (mirrors the existing `BackupPackage` DTO pattern).
- DataStore keys: `last_pulled_at_<table>`, `first_sync_done_<user>`, `active_clerk_user_id`.
- Supabase-side: SQL schema + RLS policies + Clerk third-party auth config (created by you, out of app repo).

---

## STOP — awaiting your approval

No Supabase/Firebase project created. No Clerk change. No Room change. No code written. Approve the **database choice (Supabase-as-DB + Clerk-as-auth)** and this architecture, or tell me what to adjust, before any implementation ticket begins.
