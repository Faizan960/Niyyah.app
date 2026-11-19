# Niyyah Full QA and Release-Readiness Report

**Date:** 2026-11-16
**Repository baseline:** `main` at `aabd037` plus the current uncommitted Local Masjid and secure-preference recovery fixes
**Physical device:** Vivo I2012, Android 14 / API 34, 1080×2400
**Assessment:** **GO for continued feature development after preserving this baseline; NO-GO for public production release until the open major findings are resolved.**

## 1. Executive summary

The current application is substantially healthier than it was at the start of this audit.

- **203 automated tests passed** across Android JVM, physical-device instrumentation, and the Cloudflare Worker.
- A clean Android build passed for debug, minified release, lint, unit tests, and instrumented-test APK compilation.
- The physical-device black-box pass covered onboarding, Home, Local Masjid, alarms, app blocking configuration, Quran, Hadith, bookmarks, Qibla, Azkar, verification, appearance, backup/restore, offline behavior, rotation, and process death.
- No application crash or ANR occurred during the normal black-box journeys.
- A real startup crash was reproduced when encrypted preferences were transferred without their Android Keystore key. This matches the reported “app stopped working” class of failure. A defensive recovery was added and verified against the actual broken state and with a new physical-device regression test.
- The Local Masjid defect was verified fixed: editing Asr from 16:24 to 17:00 updated Home immediately, survived process death, and replaced OS alarms with 17:00 entries for subsequent days.

The app should **not** yet be described as perfectly working or production-ready. Major remaining blockers include a hardcoded portable backup-encryption key, unsigned/unversioned release delivery, Play policy-sensitive permissions, backend development-tool vulnerabilities, and several important end-to-end coverage gaps.

## 2. Automated test results

| Layer | Result | Evidence |
|---|---:|---|
| Android JVM unit tests | **125/125 passed** | 17 suites, 0 failures/errors/skips |
| Android physical-device instrumentation | **40/40 passed** | API 34; Room migrations, persistence, ownership, sync, emergency overrides, Local Masjid, Keystore-loss recovery |
| Cloudflare Worker + local D1 | **38/38 passed** | Auth, tenant isolation, validation, injection safety, pagination, tombstones |
| **Total** | **203/203 passed** | Zero test assertions failed |

### Android build gates

The following completed successfully:

```text
gradlew clean build assembleDebugAndroidTest
gradlew build assembleDebugAndroidTest  (re-run after final fixes)
```

Verified outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` — approximately 30.4 MB, debug-signed, APK Signature Scheme v2.
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk` — approximately 7.5 MB, minified, **unsigned**.
- Android lint: **0 errors, 131 warnings, 2 hints**.
- Instrumented test sources compile successfully.

Reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

### Backend gates

```text
npm ci
npm run typecheck
npm test -- --reporter=verbose
```

- TypeScript typecheck: passed.
- Worker tests in real local Workers/D1 runtime: 38 passed.
- Authentication tests reject missing, malformed, expired, wrong-issuer, and disallowed-party tokens.
- Sync tests verify user isolation, spoofed-user rejection, SQL-injection handling, validation, tombstones, cursors, and continuation pagination.

## 3. Physical-device black-box results

### First-install onboarding

**Passed** after explicitly resetting retained Android app-ops:

1. Welcome screen.
2. Android 13+ notification permission dialog.
3. Location grant/skip path.
4. Mandatory Usage Access settings round trip and auto-advance.
5. Mandatory Display Over Apps settings round trip and auto-advance.
6. Battery-optimization step.
7. App selection with installed-app inventory.
8. Verification-method selection.
9. Completion into Home.

### Home and prayer timing

**Passed:** calculated schedule rendering, current/next prayer state, offline warning, source label, timeline, and process-death recovery.

**Local Masjid regression passed:**

- Created `QA Masjid` through the real Material 3 time picker.
- Imported calculated values as a draft.
- Changed Asr to 17:00.
- Saved Local Masjid.
- Home immediately displayed Asr at 17:00 without waiting for the next day or periodic worker.
- Force-stop/relaunch preserved the masjid source and 17:00 schedule.
- `dumpsys alarm` showed exact `PrayerAlarmReceiver` entries at 17:00 on subsequent dates.
- Local schedule remained available offline.

### Prayer verification

**Passed:** Home → current prayer → reflection → method chooser → typed phrase → three confirmations → completion. Home then displayed the verified state and persisted the prayer record.

Voice verification was not exercised because it requires controlled microphone input and speech-recognition conditions.

### App blocker configuration

**Passed:** 92 installed applications loaded; profile selection worked; `Allow All` changed the blocked count from 8 to 0; Minimal restored the default blocked set.

The actual foreground-app interception/overlay was not time-triggered end-to-end. Usage Access and overlay app-ops were verified available, but an actual prayer alarm firing while opening a blocked third-party app remains a required dedicated test.

### Quran and shared bookmarks

**Passed:** Surah listing, search, reader navigation, Arabic/translation rendering, verse bookmark, and shared Bookmarks view.

Observation: searching the common spelling `Yasin` returned no result, while `Yaseen` worked. This is a minor search-alias/normalization issue.

### Hadith

**Passed:** daily hadith, reader, bookmark action, and shared bookmark aggregation with Quran bookmarks.

### Qibla and Azkar

- Qibla rendered Mumbai, 308°, and distance to Kaaba.
- Morning Azkar opened and the repetition counter advanced from 0/1 to 1/1.

### Appearance

**Passed:** theme screen opened and Light theme applied correctly. Main activity survived landscape recreation and returned to portrait configuration without a crash.

### Backup and restore

**Functionally passed:** backup creation opened the system file picker, wrote a ZIP, displayed date/size, selected the ZIP for restore, showed a correct preview, and completed restore.

**Security status: failed for production readiness.** See Major Finding M1.

### Offline, lifecycle, crash, and performance probes

- Wi-Fi and mobile data disabled: app remained usable and displayed the offline warning; Local Masjid values remained available.
- Wi-Fi/mobile data were restored after the test.
- Three cold starts: 548 ms, 514 ms, and 503 ms (`am start -W`).
- No normal-journey `AndroidRuntime` fatal exception or ANR.
- Debug-process memory during onboarding: approximately 244 MB PSS / 419 MB RSS, including a WebView process and debug overhead. Release profiling is recommended.

## 4. Confirmed defect fixed during this audit

### F1 — Startup crash after device transfer/reinstall

**Severity before fix:** Critical
**Status:** Fixed locally and regression-tested; not yet committed/pushed.

Reproduced exception:

```text
Unable to create application SalahLockApplication
Caused by: javax.crypto.AEADBadTagException
EncryptedSharedPreferences.create(...)
```

Cause: encrypted preference files can survive a transfer/restore while their device-bound Android Keystore key does not. `SecurePreferences` previously constructed at application startup without recovery, so the process crashed before rendering UI.

Fix:

- Catch secure-preference decryption/keyset failures.
- Delete only the unreadable device-bound secure preference file.
- Recreate it with the current device's Keystore key.
- Preserve Room and normal DataStore data.
- Added `SecurePreferencesRecoveryTest`, which writes encrypted values, deletes the Keystore key, reconstructs preferences, verifies safe reset, and verifies subsequent writes.

Physical-device validation:

- The real broken restored state logged a controlled secure-preference reset.
- App process remained alive.
- Original Room/DataStore content, including Local Masjid data, survived.
- Regression test passed via AndroidJUnitRunner.

Expected tradeoff: only encrypted location values are reset; location permission allows them to be reacquired. This is safer than crashing or deleting the full application database.

## 5. Open findings

### M1 — Backup encryption uses a public hardcoded key

**Severity:** Major security blocker
**Location:** `app/src/main/java/com/salahlock/app/data/backup/BackupRepository.kt`

The AES key is SHA-256-derived from a string embedded in the APK. Anyone with the app or source can derive the key and decrypt any exported backup. AES-GCM still detects tampering, but it does not provide meaningful confidentiality with a globally known key.

Required before public release:

- Move new portable backups to passphrase-derived encryption with a random salt and authenticated metadata, or another user-controlled portable secret.
- Keep legacy-key decryption only for backward compatibility.
- Use a device-bound Keystore key for unattended local auto-backups, with clear UI that those backups are not portable.
- Add malformed archive, wrong passphrase, tamper, zip-bomb/size-limit, and transactional rollback tests.

### M2 — Release delivery is not production-ready

**Severity:** Major release blocker

- `versionCode = 1` and `versionName = 1.0` are static.
- Release APK is unsigned on this machine/CI when upload-keystore properties are absent.
- CI uploads only a debug APK.
- Debug APKs are signed with a debug certificate; CI-run certificates are not a stable production update channel.

This can cause update/install conflicts between APKs downloaded from different build environments. Establish a protected signing key, monotonically increasing version codes, and a signed release/AAB pipeline before distributing builds broadly.

### M3 — Play policy-sensitive permissions

**Severity:** Major release/policy risk

The manifest requests:

- `QUERY_ALL_PACKAGES`
- `USE_EXACT_ALARM`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

Lint explicitly flags direct battery-optimization exemption requests in three locations. Each permission needs a valid Play policy declaration and user-facing justification. Prefer the general battery optimization settings page or WorkManager where possible.

### M4 — Backend development toolchain has known vulnerabilities

**Severity:** Major supply-chain/CI risk, not currently a Worker runtime dependency issue

`npm audit` reports **9 vulnerabilities: 7 high, 2 moderate**. Affected packages include Cloudflare Vitest tooling, Wrangler/Miniflare, Vitest/Vite, Undici, Sharp, and Zod. The production dependency list is small (`jose`), but vulnerable dev tooling still processes repository code and runs in CI.

Upgrade the Cloudflare test plugin, Wrangler, and Vitest stack together and rerun all 38 backend tests.

### M5 — Critical lock behavior lacks a true alarm-to-overlay E2E test

**Severity:** Major coverage gap

Alarm registration and permission paths were verified, and receiver/service logic has unit/integration coverage. The audit did not wait for a real exact alarm and then launch a blocked third-party app to confirm the overlay, service ownership, verification completion, and reminder cleanup as one black-box scenario.

Add a debug-only injectable clock/alarm trigger or an instrumentation test seam so this path can be tested deterministically without waiting for wall-clock prayer times.

### R1 — Android lint debt

**Severity:** Moderate

Lint reports 131 warnings and 2 hints. The highest-value categories are:

- battery-policy warnings;
- locked-orientation warnings for lock/verification activities, especially Android 16/large screens;
- oversized vector asset;
- stale dependency versions;
- window-size API usage;
- font certificate typo warnings.

No lint errors were reported.

### R2 — Debug network logging

**Severity:** Moderate development/privacy risk

The Aladhan client logs full HTTP bodies in debug builds. Release logging is disabled. Continue to ensure debug builds are not distributed as production artifacts and avoid adding authentication-bearing APIs to this BODY-logging client.

### R3 — Memory footprint

**Severity:** Moderate performance risk

The debug process reached roughly 244 MB PSS / 419 MB RSS during onboarding. This includes debug tooling, ads/WebView, and mapped code, so it is not a release measurement. Profile the minified release build on low-memory API 26 hardware before adding two large features.

### R4 — Local development credential hygiene

**Severity:** Moderate local-security note

An ignored local agent-settings file contains a hardcoded API-key-like value. It is not tracked by Git, but it should be restricted and rotated if it is active. Never copy it into repository files, logs, reports, or CI configuration.

### N1 — Search normalization

**Severity:** Minor

Quran search recognizes `Yaseen` but not the common spelling `Yasin`. Add aliases or transliteration normalization.

### N2 — Product messaging

**Severity:** Minor

The signed-out Profile invites the user to sign in and sync progress while also stating that cloud sync is in development. Clarify whether sign-in currently provides sync or is preview-only.

## 6. Test coverage gaps

The following were not proven end-to-end and should not be represented as fully verified:

- Voice verification with real microphone/speech recognition and denial fallback.
- Real Clerk sign-in and Supabase synchronization with production-like credentials.
- Exact alarm delivery at wall-clock time followed by blocked-app overlay.
- Boot-completed and timezone/date-change behavior on an actual reboot/time change.
- API 26–33 device matrix; only Android 14/API 34 was physically tested.
- Foldable/tablet/ChromeOS adaptive layouts and accessibility/TalkBack.
- Low-memory process death while readers, backup, or verification are active.
- Very large backup/restore, corrupted ZIP, truncated ciphertext, and storage-full behavior.
- Poor GPS, denied-forever permissions, airplane mode during onboarding, and captive portal behavior.
- AdMob/UMP production consent and ad delivery.
- Release signing, Play App Bundle installation, and Play policy review.

## 7. Recommended baseline before adding two major features

1. Commit the Local Masjid and secure-preference crash fixes on a dedicated baseline branch.
2. Replace the hardcoded backup key before touching backup schemas or migration features.
3. Establish signed, versioned release/AAB builds.
4. Upgrade the vulnerable Cloudflare development stack and rerun backend tests.
5. Add deterministic alarm-to-overlay integration coverage.
6. Add Compose UI automation for onboarding, Local Masjid save/source switching, verification, and backup preview.
7. Add API 26 and one Android 16 test target.
8. Keep the 203-test baseline mandatory in CI while implementing the two new features.

## 8. Final disposition

**Feature-development baseline:** Conditional GO. The current behavior is stable enough to branch from once the local fixes are committed and protected by CI.
**Public production release:** NO-GO. Resolve M1–M5 first.
**“Perfectly working” claim:** Not supportable yet; the verified surface is broad, but security, release engineering, policy, and true alarm-overlay E2E gaps remain.
