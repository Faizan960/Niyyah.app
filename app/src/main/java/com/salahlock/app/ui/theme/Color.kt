package com.salahlock.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Niyyah color tokens — extracted verbatim from the BM-005 Figma light frames
 * (file oKuAeR81LnwPgcRySotxvl). Light frames are the single source of truth;
 * dark mode is deferred until dark frames are redesigned.
 */
object NiyyahColors {
    // Canvas & surfaces
    val Background = Color(0xFFF7F6F3)
    val HeaderBackground = Color(0xFFFCF9F8)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceElevated = Color(0xFFFCFBF9)

    // Text
    val TextPrimary = Color(0xFF071836)
    val TextSecondary = Color(0xFF75777E)

    // Lines
    val Border = Color(0xFFE7E2DA)

    // Brand
    val Navy = Color(0xFF1E2D4C)
    val Gold = Color(0xFFD4A84F)
    val Green = Color(0xFF006C4F)

    // On-navy (hero card) text
    val OnNavy = Color(0xFFFFFFFF)
    val OnNavySecondary = Color(0xB3FFFFFF)   // white 70%
    val OnNavyTertiary = Color(0xCCFFFFFF)    // white 80%

    // Bottom nav glass pill
    val NavPillBackground = Color(0xCCFFFFFF)          // white 80%
    val NavPillBorder = Color(0x33FFFFFF)              // white 20%
    val NavShadow = Color(0x1A1E2D4C)                  // navy 10%

    // Hero badge glass
    val HeroBadgeBackground = Color(0x33FFFFFF)        // white 20%
    val HeroBadgeBorder = Color(0x1AFFFFFF)            // white 10%
}
