package com.salahlock.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.salahlock.app.R

// ── BM-TYPOGRAPHY — Manrope (app-wide Latin UI type) ─────────────────────────
// Single bundled variable font (`manrope_variable.ttf`, OFL — see app/licenses/).
// One file supplies every weight, so it works offline, never depends on a runtime
// font download, renders identically on every device, and keeps APK impact minimal.
// (minSdk 26 → variable-font `wght` axis is honoured natively.)
//
// Weight vocabulary (disciplined — see the type scale below):
//   ExtraBold 800 → major display only     Bold 700 → headings / key numbers
//   SemiBold 600  → buttons, section titles, active nav
//   Medium 500    → secondary emphasis, controls, metadata
//   Regular 400   → body & descriptions

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    resId = R.font.manrope_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** The one canonical Latin UI family. */
val Manrope = FontFamily(
    manrope(400),
    manrope(500),
    manrope(600),
    manrope(700),
    manrope(800),
)

/**
 * Display/heading roles. Manrope now carries every Latin role — there is no serif in
 * NIYYAH. `NiyyahSerif` is kept as a legacy alias so existing call sites keep compiling
 * and simply render Manrope; prefer [NiyyahDisplay] in new code.
 */
val NiyyahDisplay = Manrope
val NiyyahSerif = Manrope
val NiyyahSans = Manrope

/**
 * CRITICAL (BM-TYPOGRAPHY §14/§15): Arabic content must NOT be tied to the Latin
 * Manrope family. Manrope has no Arabic glyphs, so Arabic falls back to the platform
 * Arabic font (Noto Naskh) exactly as before — but pinning [ArabicUi] on Arabic Text
 * composables also isolates them from the Manrope weight changes below, so Quran /
 * Hadith / Azkar / dhikr Arabic keeps its established rendering.
 */
val ArabicUi = FontFamily.Default

// ── Type scale ───────────────────────────────────────────────────────────────
// Sizes, line-heights and letter-spacing are UNCHANGED from the previous scale to
// preserve every layout; only the family (→ Manrope) and weights (→ modern
// hierarchy) change.
// display-lg 48/56 · display-md 36/44 · headline-xl 32/40 · headline-lg 24/32
// body-lg 18/28 · body-md 16/24 · label-md 14/20 +0.05em · label-sm 12/16
val SalahLockTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.96).sp, // -0.02em
    ),
    displayMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.36).sp, // -0.01em
    ),
    displaySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.7.sp, // 0.05em
    ),
    labelMedium = TextStyle(
        // §9 — uppercase structural section labels (TODAY'S PRAYERS, NEXT PRAYER, …).
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.36.sp, // 0.03em
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
