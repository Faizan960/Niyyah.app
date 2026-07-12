package com.salahlock.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as GFont
import androidx.compose.ui.unit.sp
import com.salahlock.app.R

// ── Stitch V2 fonts ──────────────────────────────────────────────────────────
// Display/headlines: EB Garamond (both modes). Labels/body-sans: Hanken Grotesk
// (light system) / Inter (dark system) — Hanken Grotesk is used app-wide for
// sans roles so text doesn't reflow when switching modes; both are humanist
// sans at the sizes used and match the exports.
// Downloadable Google Fonts: fall back to system serif/sans when unavailable.

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val ebGaramond = GoogleFont("EB Garamond")
private val hankenGrotesk = GoogleFont("Hanken Grotesk")

val NiyyahSerif = FontFamily(
    GFont(ebGaramond, fontProvider, FontWeight.Normal),
    GFont(ebGaramond, fontProvider, FontWeight.Medium),
    GFont(ebGaramond, fontProvider, FontWeight.SemiBold),
    GFont(ebGaramond, fontProvider, FontWeight.Bold),
)

val NiyyahSans = FontFamily(
    GFont(hankenGrotesk, fontProvider, FontWeight.Normal),
    GFont(hankenGrotesk, fontProvider, FontWeight.Medium),
    GFont(hankenGrotesk, fontProvider, FontWeight.SemiBold),
    GFont(hankenGrotesk, fontProvider, FontWeight.Bold),
)

// ── Stitch V2 type scale ─────────────────────────────────────────────────────
// display-lg 48/56 · display-md 36/44 · headline-xl 32/40 · headline-lg 24/32
// body-lg 18/28 · body-md 16/24 · label-md 14/20 +0.05em · label-sm 12/16
val SalahLockTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NiyyahSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.96).sp, // -0.02em
    ),
    displayMedium = TextStyle(
        fontFamily = NiyyahSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.36).sp, // -0.01em
    ),
    displaySmall = TextStyle(
        fontFamily = NiyyahSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = NiyyahSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = NiyyahSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = NiyyahSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = NiyyahSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = NiyyahSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = NiyyahSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = NiyyahSans,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = NiyyahSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = NiyyahSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = NiyyahSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.7.sp, // 0.05em
    ),
    labelMedium = TextStyle(
        fontFamily = NiyyahSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.36.sp, // 0.03em
    ),
    labelSmall = TextStyle(
        fontFamily = NiyyahSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
