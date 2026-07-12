# Niyyah (SalahLock) 🕌🔒

**Lock your distractions. Unlock your prayers.**

Niyyah (codename *SalahLock*) is a native Android app that blocks your most distracting apps during Islamic prayer windows — and only unblocks them once you verify that you've actually prayed. It combines a focus-lock system with accurate prayer times, Qibla direction, a Hadith & Azkar knowledge library, streaks, achievements, and private monthly spiritual reports.

Built entirely with **Kotlin + Jetpack Compose**, offline-first, with no account required.

---

## ✨ Features

### 🔒 Prayer Lock System
- **App blocking during prayer windows** — pick which apps get locked (per-prayer configuration supported, including Jumu'ah overrides).
- **Full-screen lock overlay** (`LockOverlayActivity`) appears when you open a blocked app during an active prayer window.
- **Usage-stats polling service** (`UsageStatsPollingService`) detects foreground apps in real time and stops automatically once the prayer is verified.
- **Pause SalahLock** — temporary pause option for travel, illness, etc.
- **Emergency override** — a deliberate, rate-limited escape hatch so the lock never traps you.

### ✅ Prayer Verification
Multiple ways to prove you prayed before the lock lifts:
- **Camera verification** — CameraX capture with a pluggable `VerificationProvider` (rule-based image checks, with an ML provider behind a factory).
- **Voice verification** — speech recognition with fuzzy matching, tolerant of recognizer mis-transcriptions.
- Configurable confirmation count per user.

### 🕋 Prayer Times & Qibla
- **Aladhan API** integration for location-based prayer times (with Room caching for offline use).
- **Local Masjid mode** — manually enter your mosque's iqamah times instead of calculated times.
- **Qibla compass** — a luxury watch-face-inspired compass dial with live sensor heading.
- Exact-alarm scheduling (`AlarmScheduler`) that survives reboots.

### 📚 Knowledge Library
- **Hadith reader** — collections and books fetched from a Hadith API, cached locally, with flashcard mode and search.
- **Azkar reader** — bundled azkar collections (morning/evening/post-prayer) shipped as JSON assets.
- Editorial, reading-first UI inspired by classical Islamic libraries.

### 📊 Spiritual Progress
- **Streaks & achievements** — with rarity tiers and a motivation engine.
- **Monthly Reflection reports** — frozen JSON reports generated on-device, ranked by a `MonthlyRank` formula. **Strictly private by design** — never leaves your phone except in your own backups.
- Daily progress ring and prayer hero card on the Home screen.

### ☁️ Backup & Restore
- Full local backup packages (prayer records, streaks, blocked apps, overrides, masjid times, preferences) via `BackupRepository` — your data stays yours.

---

## 🏗️ Architecture

Single-module MVVM app: **Compose UI → ViewModel → Repository → Room / DataStore / Retrofit**.

```
app/src/main/java/com/salahlock/app/
├── MainActivity.kt          # 3-tab pager: Home · Lock Apps · Knowledge
├── SalahLockApplication.kt
├── ads/                     # DailyInterstitialManager (1 ad/24h max, disabled by default)
├── alarm/                   # AlarmScheduler — exact alarms for prayer windows
├── auth/                    # Google auth config
├── camera/                  # CameraX capture + pluggable VerificationProviders
├── data/
│   ├── api/                 # Aladhan, Hadith, Overpass (Retrofit services)
│   ├── backup/              # BackupPackage + BackupRepository
│   ├── db/                  # Room AppDatabase (6 schema versions) + DAOs
│   ├── preferences/         # UserPreferences (DataStore) — central settings hub
│   └── repository/          # Prayer, Knowledge, Blacklist, Streak, Masjid repos
├── receiver/                # Boot / alarm broadcast receivers
├── service/                 # UsageStatsPollingService (foreground lock enforcement)
├── spiritual/               # MotivationEngine, RankCalculator, monthly reports
├── theme/                   # Design system (see CLAUDE.md style library)
├── ui/                      # Compose screens: home, blacklist, knowledge, qibla,
│                            #   lock, onboarding, profile, masjid, azkar, hadith
├── util/                    # Location helper, category detection, etc.
├── verification/            # Voice verification + validation logic
└── work/                    # WorkManager jobs (sync, reminders)
```

Key abstractions (the most-connected nodes in the codebase knowledge graph):

| Component | Role |
|---|---|
| `UserPreferences` | DataStore-backed settings hub — theme, madhab, lock toggles, pause state, verification config |
| `KnowledgeRepository` | Hadith/Azkar fetching, caching, and search |
| `HomeViewModel` | Prayer countdown, hero card, daily progress |
| `BlacklistViewModel` | App selection, categories, per-prayer lock profiles |
| `BackupRepository` | Full export/import of all user data |
| `SpiritualReportRepository` | Monthly reflection generation (file-based, no DB migration) |

---

## 🛠️ Tech Stack

- **Language:** Kotlin (JVM 17)
- **UI:** Jetpack Compose (Material 3)
- **Persistence:** Room (schema-versioned, migrations checked in) + DataStore + EncryptedSharedPreferences
- **Networking:** Retrofit + Kotlinx Serialization (Aladhan, Hadith API, Overpass)
- **Async:** Coroutines + Flow
- **Background:** WorkManager, foreground service, exact alarms
- **Camera/ML:** CameraX with pluggable verification providers
- **Ads:** AdMob interstitial (max one per day, `ADS_ENABLED=false` by default)
- **Min SDK 26 · Target/Compile SDK 36**

---

## 🚀 Building

```bash
git clone https://github.com/Faizan960/Niyyah.app.git
cd Niyyah.app
./gradlew assembleDebug
```

Open in Android Studio (Ladybug or newer recommended) and run on a device with API 26+.

### Release signing (optional)
Release builds read signing config from `local.properties` (never committed):

```properties
KEYSTORE_PATH=/abs/path/upload-keystore.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
# Optional — production AdMob IDs (test IDs used by default)
ADMOB_APP_ID=...
ADMOB_INTERSTITIAL_ID=...
```

Without a keystore configured, `assembleRelease` still succeeds (unsigned) so CI works out of the box.

---

## 🔐 Permissions

The lock system genuinely needs elevated permissions — all requested with in-app explanations during onboarding:

| Permission | Why |
|---|---|
| `PACKAGE_USAGE_STATS` | Detect which app is in the foreground during a prayer window |
| `SYSTEM_ALERT_WINDOW` | Show the lock overlay over blocked apps |
| `SCHEDULE_EXACT_ALARM` | Fire lock windows exactly at prayer times |
| `CAMERA` / `RECORD_AUDIO` | Optional prayer verification methods |
| `ACCESS_FINE_LOCATION` | Prayer times + Qibla direction |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule alarms after reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep the lock service alive on aggressive OEMs |

**Privacy:** no analytics SDK, no Firebase, no account. Prayer records, streaks, and monthly reports never leave the device except in your own backup files.

---

## 🎨 Design System

The UI follows a five-style visual library (documented in `CLAUDE.md`):

- **Style A — Premium Islamic Minimal** (default): reading screens, Home, Profile
- **Style B — Glass Spiritual**: floating navigation and utility panels only
- **Style C — Luxury Compass**: Qibla screen
- **Style D — Islamic Library**: Knowledge module
- **Style E — Focus Mode**: lock overlay and verification screens

> *"A screen should be identifiable by feeling alone."*

---

## 📄 License

[MIT](LICENSE) © 2026 Faizan Patel
