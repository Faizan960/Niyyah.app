# Niyyah 🕌

**Live with intention. Worship with sincerity. Return to Allah.**

Niyyah (*intention*) — formerly **SalahLock** — is a calm companion for intentional Muslim living. It gently blocks your most distracting apps during Islamic prayer windows, unblocking them once you verify that you've prayed. Around that core it offers accurate prayer times, Qibla direction, a Quran reader, a Hadith & Azkar knowledge library, streaks, and private monthly spiritual reflections.

Niyyah is not designed to capture attention — it is designed to return attention to Allah. The perfect user journey: open the app, receive guidance, leave the app, pray.

It is **not** merely a prayer app. It is **not** a productivity app. It is **not** a habit tracker. It is a peaceful, premium Islamic companion for everyday worship.

> 🚧 **Development Status:** Niyyah is currently in active development and production-readiness preparation. The first public Google Play release is still being finalized.

---

## ✨ Features

### 🔒 Prayer Lock System
- **App blocking during prayer windows** — pick which apps get locked, with per-prayer configuration including Jumu'ah overrides.
- **5-minute pre-prayer buffer** — the lock becomes active shortly before the configured prayer time.
- **Full-screen lock overlay** appears when you open a blocked app during an active prayer window.
- **Immediate unlock after verification** — successfully verifying the matching prayer immediately ends that prayer's active lock instead of making you wait for the countdown.
- **Pause Salah Lock** — a temporary pause option for travel, illness, or genuine need.
- **Emergency override** — a deliberate, rate-limited escape hatch so the lock never traps you.

### ✅ Prayer Verification
A single, unified flow to gently confirm you've prayed before the lock lifts. Choose your preferred method:
- **Voice** — speech recognition with fuzzy matching, tolerant of reasonable mis-transcriptions.
- **Typing** — type a short confirmation.
- Clear success and try-again states, with configurable confirmation behavior.

### 🕋 Prayer Times & Qibla
- **GPS-based prayer times** with offline caching, so times remain available without a network connection.
- **Local Masjid mode** — save your mosque's prayer/iqamah timings and use them instead of calculated GPS timings.
- **Switch between GPS and Local Masjid timings** without losing your saved mosque schedule.
- Prayer-time alarms automatically reschedule when the active timing source changes and survive device reboots.
- **Qibla compass** — a calm, luxury watch-face-inspired dial with live sensor heading.

### 📖 Quran
- **Reading-first Quran experience** with large, calm typography and focused controls.
- Bookmarks and reading progress.
- Surah and Juz navigation.
- Full-text search.
- Multiple reading presentations, including the existing reader experience and a book-style reading direction.
- Supported Quran bookmark data can synchronize for signed-in users while remaining available locally first.

### 📚 Knowledge Library
- **Hadith reader** — collections and books with search and an editorial, reading-first layout.
- **Azkar** — morning, evening, and post-prayer remembrances with counters and favorites.
- Dedicated Quran and Hadith experiences with modern, calm interfaces.
- User-specific bookmarks and collections are kept separate from the shared religious corpus.

### 📊 Spiritual Progress
- **Streaks & achievements** — encouraging consistency over perfection, never shaming.
- **Monthly Reflection reports** — private and generated on-device.
- Reflections remain private to you and are not part of ordinary cloud synchronization.
- Daily progress ring and a prayer-focused hero card on a calm Home dashboard.

### 🗂️ Collections & Bookmarks
- Save verses, hadith, and azkar into your own collections.
- One unified bookmarks experience across supported Quran, Hadith, and Azkar content.
- Search through saved content.
- Account-scoped cloud synchronization for supported bookmark data.

### 👤 Profile & Personalization
- **Optional account** — use Niyyah without signing in.
- **Google/Clerk sign-in** — optional authentication for account-linked features and supported cloud synchronization.
- **Light & dark themes** — a fully theme-aware design that stays calm and legible in both.
- Personal statistics, reading history, and monthly reflections in a journal-like profile.
- User data remains isolated when switching between accounts.

### ☁️ Backup & Restore
- Create local backups of supported application data.
- Restore your data when needed.
- Local backups are separate from cloud synchronization.
- Supported cloud data can synchronize automatically when signed in.
- Niyyah remains local-first, so the core experience continues to work offline.

---

## 🔐 Privacy

Niyyah is designed with a **local-first and privacy-conscious approach**.

- Optional account — you can use Niyyah without signing in.
- Clerk is used for optional account authentication.
- Supabase is used for supported cloud-synchronized user data.
- Server-side Row Level Security keeps cloud data isolated between accounts.
- Prayer records and personal application data remain primarily local.
- Monthly spiritual reflections are generated and stored privately on-device.
- No privileged database credentials are shipped with the app.
- No analytics SDK is intentionally used by Niyyah.
- Google AdMob is used for limited monetization, with ads kept away from worship-critical experiences.

---

## 📄 License

[MIT](LICENSE) © 2026 Faizan Patel
