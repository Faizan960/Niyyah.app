# Emergency-override allowance — product policy record

> Status: **investigative record, no product change made.** Written after the
> "Asr Focus shows 0/3 overrides remaining" field report was investigated
> (2026-10-29). Records what the code and history establish, so future
> contributors do not mistake the monthly override allowance for the removed
> weekly free-pass.

## The reported symptom

Asr Focus screen displayed:

- Prayer: Asr
- Overrides remaining: 0 / 3
- "No emergency overrides remaining this month."

## Conclusion

The three monthly emergency overrides are a **separate, intentional,
still-active emergency feature** — not an obsolete free-pass mechanism. The
reported screen state (`0/3`) is the correct display for an allowance already
exhausted in the current calendar month. No code defect was found in the
counter, reset, or rendering logic.

## Evidence

1. **Age and independence.** `MAX_OVERRIDES_PER_MONTH = 3` exists from the
   very first commit of the codebase
   (`verification/`–era `data/repository/EmergencyOverrideRepository.kt`),
   predating the weekly mercy/free-pass feature's removal by months. The two
   mechanisms were always distinct:
   - Weekly free-pass ("mercy"): lived in the `streaks` table
     (`lastMercyWeek`, `mercyUsedThisWeek`) and softened *streak counting*
     (a 4/5 day week once counted). **Removed** in
     `06a25c4 "refactor: remove weekly free-pass (mercy) feature; strict 5/5
     streaks"` together with Room migration 11→12, which drops those columns.
     That commit touched **zero** override files and explicitly states:
     *"Emergency-overridden prayers still count as completed (the override
     counter in emergency_overrides is untouched emergency functionality)."*
   - Monthly emergency override: lives in the `emergency_overrides` table,
     keyed `(ownerId, monthYear)`, and consumes one allowance to unlock during
     a live prayer lock window.

2. **Committed repair history.** The override flow has its own dedicated fix
   branch (`fix/emergency-feature`) and repair commit `beed380`
   ("fix: repair emergency override flow (atomic, idempotent, limit-safe)"),
   which preserved the 3/month calendar-month device-local limit unchanged.

3. **Instrumented behavioral contract.**
   `app/src/androidTest/java/.../emergency/EmergencyOverrideRepositoryTest.kt`
   pins: atomic consume inside a Room transaction, idempotent duplicate
   handling, limit enforcement at 3, and calendar-month reset by month key.

4. **Deliberate feature plumbing.** The allowance is backed by backup/restore
   round-trip (`BackupPackage` DTOs), counted in achievements
   (`no_mercy_month` — a *penalty* for using overrides, kept identifier-stable),
   reflected in monthly reports (`overridesUsed`, legacy JSON field name
   `mercyUsed` retained only for backup compatibility), and ranked with an
   override penalty in `RankCalculator`.

5. **Privacy / local-only guarantees.** Overrides are pinned to `OwnerIds.LOCAL`
   (stamped back in Room migration 10→11), excluded from legacy adoption, never
   pushed through the Supabase bookmark sync path (the only writer is
   `quran_bookmarks`), and the Cloudflare backend explicitly hard-denylists
   override/mercy domains (`cloudflare/src/db/schema.ts` `FORBIDDEN_DOMAINS`).

## Standing rules

- The allowance is **3 per calendar month, per device-local owner**. It resets
  by month-key recomputation (no worker needed); a new month simply has no
  row yet and therefore counts as 0 used.
- Overrides must remain **device-local**: never uploaded by any sync path.
- The weekly free-pass must **not** be reintroduced (product decision, Room
  migration 11→12 already shipped). Do not confuse "mercy" naming residuals
  (achievement id `no_mercy_month`, serialized field `mercyUsed`) with live
  free-pass logic — they exist only for identifier/backup stability.
