# Branch Audit & Consolidation Report — 2026-10-28

Audit of every branch in `Faizan960/Niyyah.app` against `origin/main` (`d056f29`), performed before integration on `integration/branch-consolidation`. No branches were deleted, rewritten, or force-pushed; no production Cloudflare resources were created or modified.

## Branch inventory (after full fetch + prune)

| Branch | Tip | Ahead/behind vs main | Verdict |
| --- | --- | --- | --- |
| `main` | `d056f29` | — | baseline |
| `fix/typing-verification-specific-prayer` | `96d614f` | +1 / 0 | **Merge** (done) |
| `fix/prayer-timeline-state-ui` | `a52a9e0` | +1 / 2 | **Cherry-pick** (done) |
| `New-Features-Testing` | `1eab7af` | +1 / 11 | **Selectively port** (done — 1 hunk) |
| `feat/cloudflare-backend` | `e6e7585` | +1 / 0 | **Merge + corrections** (done) |
| `fix/emergency-feature` | `beed380` | 0 / 1 | Already in main (merged via `d056f29`) — no action |
| `bm-verify-004-prayer-phrase` | `99d489f` | 0 / 12 | Already in main — no action |
| `fix-alarms` | `2cfd0a5` | +8 / 12 | All 8 commits patch-equivalent in main (`git cherry` clean) — no action |
| `fix/local-persistence-auth` | `5db909c` | 0 / 2 | Already in main — no action |
| `current-figma-ui-backup` (local only) | `ab69a95` | 0 / 23 | Backup branch, nothing unique — no action |

## What was reviewed per branch

- **`fix/typing-verification-specific-prayer` (`96d614f`)** — full diff of `VerificationPhraseProvider.kt` (+ prayer-name allowlist, whole-token matching), `TextVerificationScreen.kt` error copy, and the expanded `VerificationPhraseProviderTest.kt`. Typed verification now requires the prayer name; spoken stays lenient by design. Clean merge into integration.
- **`fix/prayer-timeline-state-ui` (`a52a9e0`)** — full diff: `HomeScreen.kt` (`TimelineNodeState` priority mapping, hollow ring for current-unverified, hero shows "CURRENT PRAYER") + new `TimelineNodeStateTest`. Verified main's `HomeScreen.kt` differs *only* by this fix, so the cherry-pick applied cleanly with no other drift.
- **`New-Features-Testing` (`1eab7af`)** — full diff of its 3 files:
  - `UsageStatsPollingService.kt`: **ported** — an immediate `checkForegroundApp()` (on `Dispatchers.Main`, matching the main-looper poll handler) before the poll loop starts, closing the first-check delay. Main did not have this.
  - `LockOverlayScreen.kt` override-dialog rework: **rejected** — main's `OverrideResult`-based dialog (from the `beed380` emergency fix) is strictly better (typed result handling, submitting latch, inline error).
  - `LockOverlayActivity.kt` `enableEdgeToEdge()` + deferred `finish()`: **rejected** — not demonstrably beneficial on current main; deferred finish is redundant since main's `useOverride` completes persistence before `onOverrideGranted()` fires. No camera-verification code exists in this branch's unique commit.
- **`feat/cloudflare-backend` (`e6e7585`)** — every file read: `index.ts`, `auth/clerk.ts`, `db/schema.ts`, `db/sync.ts`, `types.ts`, `util/http.ts`, migration, tests, wrangler/package configs, README. See "Backend defects found & fixed" below. Working tree's untracked `cloudflare/` contained **only** `node_modules` (leftover install; no divergent source) — safe to merge the branch's files over it.
- **`fix/emergency-feature`, `bm-verify-004-prayer-phrase`, `fix/local-persistence-auth`, `fix-alarms`, `current-figma-ui-backup`** — merge-base/`git cherry` verification confirmed every unique commit is patch-equivalent to commits already on main; no integration required.

## Backend defects found & fixed (commit `497db5b`)

1. **Auth: missing-`azp` bypass** — with `CLERK_AUTHORIZED_PARTIES` configured, a token *without* an `azp` claim passed the check. Now fail-closed: missing/empty/disallowed `azp` → 401. A missing `CLERK_ISSUER` is now a hard config error instead of silently skipping issuer validation.
2. **Pull pagination could skip records** — `updated_at >= cursor LIMIT 1000` per domain drops rows sharing the boundary timestamp once a page fills (a single push stamps all its rows with the same `updated_at`). Replaced with per-domain keyset pagination on `(updated_at, rowid)`: full pages return `hasMore` + opaque `continuation`; the next request resumes strictly after the last `(updated_at, rowid)`. Durable cursors keep gte semantics and advance only on the final page. Test: 600 same-timestamp rows → all delivered, zero skips/duplicates.
3. **No semantic validation** — added `prayer_name` enum (`FAJR|DHUHR|ASR|MAGHRIB|ISHA`), ISO-date check, surah 1–114 / ayah 0–286 ranges, per-column text length caps, non-object row rejection, JSON content-type enforcement (415).
4. **Removed-feature data in the cloud schema** — free-pass/mercy-week fields (`streaks.last_mercy_week`, `mercy_used_this_week`) removed from migration `0001` and the sync registry; pushes containing them are rejected (`forbidden_field`); mercy/free-passes/monthly-exception domain aliases added to the hard denylist. Migration edited in place because the backend has never been deployed (placeholder `database_id`) — no destructive migration of real data exists. Emergency override **allowances** remain unsyncable (tested); the per-prayer `override_used` boolean is kept as ordinary prayer history (documented decision).
5. **README** — provisional-Clerk notice (final provider not chosen; isolated in `auth/clerk.ts`), pagination contract, data-model decisions, and a schema/API gap report vs the intended MVP (profile/preferences, backup metadata, mosque directory/schedules/submissions/verification, azkar, mosque-admin auth: all absent and not invented).

## Deliberately excluded

- The rest of `New-Features-Testing` (superseded override dialog, edge-to-edge tweak).
- Any Android wiring to the Cloudflare backend (main already syncs Quran bookmarks via the live Supabase/Clerk path; adopting the D1 backend is a separate migration decision).
- Community feed (deferred by product), mosque directory/schedule schema (no grounded design yet).
- The three-free-passes removal in the *Android app itself* — note: mercy logic is **still live** in main's `StreakRepository`; the cloud schema is ahead of the app here, and the app-side removal remains open work.

## Verification (all actually executed)

| Check | Command | Result |
| --- | --- | --- |
| Backend deps | `npm ci` (in `cloudflare/`) | ✅ installed clean |
| Backend types | `npm run typecheck` | ✅ 0 errors |
| Backend tests | `npm test` | ✅ 38/38 (21 before fixes; 17 added for the fixes) |
| Android unit tests | `./gradlew testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| Android build | `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL |
