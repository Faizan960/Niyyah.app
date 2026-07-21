# BM-013 Checkpoint B — LOCAL FOUNDATION: design notes

Status: foundation closeout. No cloud sync, no adoption-on-login, no production Supabase tables. These notes document decisions the review (items 4, 6, 7) asked to be written down before the Quran-bookmark cloud slice.

---

## 1. Hadith / Azkar two-authority window + eventual cutover algorithm (review item 4)

**Current state after v9.** The v8→v9 migration copied the legacy corpus flags
(`hadith_table.isBookmarked/bookmarkSource/lastReadTimestamp`,
`azkar_table.isBookmarked/completedCount`) into the new normalized, owner-scoped
tables `hadith_user_state` / `azkar_user_state` (owner `__local__`). To avoid a
high-risk UI refactor in the foundation, the **live Hadith/Azkar reader UI still reads
and writes the legacy corpus flag columns**. Therefore the one-time migrated
`*_user_state` snapshot can drift stale as the user toggles bookmarks/progress after
v9. This is a deliberate, temporary single-live-authority arrangement (corpus flags
are authoritative; `*_user_state` is a not-yet-consulted snapshot) — it is **not** a
live dual-authority conflict, because nothing reads `*_user_state` yet.

**Chosen strategy: deterministic re-import at domain activation (no dual-write).**
When the Hadith (or Azkar) domain is activated for cloud sync — a later, separately
approved slice — perform this atomic cutover ONCE per device, before the user-state
table becomes authoritative:

```
cutoverHadith(activeOwner):
  db.withTransaction {
    if (cutover_done_hadith flag already set) return          # idempotent guard
    for each corpus row where isBookmarked = 1 OR lastReadTimestamp > 0:
        existing = hadith_user_state[activeOwner, id]
        merged = merge(existing, legacyFlags)                 # see merge rule below
        upsert hadith_user_state[activeOwner, id] = merged
    set cutover_done_hadith flag
  }
  # from now on: repositories read/write ONLY hadith_user_state[activeOwner];
  # legacy corpus flags are never read or written again.
```

`merge(existing, legacy)` must **never clobber newer normalized/cloud state** with a
stale legacy flag:
- if `existing` is absent → take the legacy value (first import).
- if `existing` is present (already normalized/synced) → keep `existing`; the legacy
  flag is older by construction (it predates the cutover). Bookmark set = OR of the
  two; `lastReadTimestamp`/`completedCount` = max.
Azkar is identical, keyed by `azkarRef` with `completedCount = max`.

Cutover ordering rule: run cutover **before** the first cloud pull for that domain, so
the pull merges against already-imported local state (not against stale corpus flags).
After cutover, the corpus flag columns are dead and get dropped in a later
corpus-schema bump.

Not implemented now (Hadith/Azkar sync is not this checkpoint). Documented so the
slice implements exactly this, not an ad-hoc copy.

---

## 2. Azkar ordinal identity contract (review item 6)

`azkar:v1:<normalized-category>:<index-within-category>` (see `AzkarRef` KDoc for the
authoritative contract). Summary: appending to the end of a category is safe;
inserting/reordering in the middle, or renaming a category, shifts ordinals and
silently repoints existing bookmarks → requires a `v2` bump + remap. Preferred
long-term fix: add explicit immutable ids to `assets/azkar.json`. Determinism proven
by `AzkarRefTest` (JVM) and asserted again in `Migration8to9Test` (device).

---

## 3. Sync metadata: per-row columns vs. transactional outbox (review item 7)

**Recommendation for NIYYAH: a local change-journal (outbox), not per-row sync columns.**

Context: v9 currently carries a light `updatedAt` per user-owned row (cheap, useful as
a client event-time input). The open question is where the *sync* bookkeeping lives:
`is_dirty` / `deleted_at` / server-vs-client timestamps.

| | A. Per-row metadata | B. Local outbox / change journal |
|---|---|---|
| Domain entities | polluted with `is_dirty`, `deleted_at`, `server_updated_at` on every table | stay clean; sync fields isolated in one table |
| Offline writes | write row + set dirty (2 writes, must stay consistent) | write row + append one outbox event in the SAME Room transaction |
| Deletes | need tombstone rows (soft-delete columns) on every table | a `DELETE` event in the journal; local row can be hard-deleted |
| Idempotency / retry | must scan every table for dirty rows | replay journal entries in order; natural WorkManager retry + `attemptCount` |
| New domains | every new table re-implements the same 3 columns + queries | one journal serves all domains |

Proposed shape (design only — NOT implemented this checkpoint):

```
sync_outbox(
  id INTEGER PK AUTOINCREMENT,
  ownerId TEXT,            -- who the change belongs to (RLS scope on push)
  domain TEXT,             -- 'quran_bookmark' | 'quran_progress' | ...
  entityKey TEXT,          -- natural/stable key (e.g. '2:255', surah:18, uuid)
  operation TEXT,          -- UPSERT | DELETE
  payloadJson TEXT NULL,   -- minimal fields OR a reference to re-read from the domain row
  createdAt INTEGER,       -- client event time
  attemptCount INTEGER DEFAULT 0
)
```

Local write + `sync_outbox` append occur in one Room transaction (all-or-nothing).
A `SyncWorker` drains the outbox oldest-first, pushes each event with the Clerk JWT,
and deletes the event on 2xx (or bumps `attemptCount` for backoff). This gives:
clean domain tables, reliable offline writes, explicit deletes without local
tombstones, and simple idempotency.

**Cloud tombstones may still be required** on the Supabase side (so a delete
propagates to a second device that was offline) — that is a server-schema decision
for the Quran-bookmark slice, independent of the local outbox. The outbox removes the
need to scatter `is_dirty`/`deleted_at` across every local domain table.

Consequence: the previously-mentioned "v9→v10 to add is_dirty/deleted_at" is **not**
recommended. Instead the Quran-bookmark slice adds the single `sync_outbox` table (one
tracked migration) and the domain tables stay as they are.
