# Niyyah Design System v2 — Stitch Export

Source: Google Stitch project **Niyyah Design System** (ID `284917472316415862`), fetched 2026-07-12. Supersedes the v1 export in `../stitch/` for screen design; the v1 folder still holds the Niyyah logo asset.

13 screens × 2 modes. Each screen: `.png` (render) + `.html` (generated Tailwind code — spec reference for Compose, not literal code) + `.json` (metadata).

**Screens** (in `light/` and `dark/`): home, prayer, quran, azkar, hadith, profile, knowledge, collections, salah-lock, qibla, bookmarks, monthly-reflection, settings.

**Design systems:**
- `design-system.md` / `.json` — "Niyyah Dark" (dark-mode tokens + guidelines)
- `design-system-2.md` / `.json` — "Premium Spiritual Editorial" (light-mode tokens + guidelines)

Notable design decisions visible in these screens:
- Light home: navy hero prayer card, "Today's Intention" quote card, daily prayer chips, Hijri + Gregorian date header
- Dark home: emerald countdown (`01:42:15 remaining`), prayer time chips, "Daily Intention" serif italic card, "Curated Knowledge" carousel
- New modules designed but not yet in the app: Quran, Collections, Bookmarks, dedicated Settings

The Master Product & Design Specification in the repo root `CLAUDE.md` remains the source of truth; where they disagree, CLAUDE.md wins.
