package com.salahlock.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A full Niyyah color palette. Two instances exist — [LightNiyyahColors] and
 * [DarkNiyyahColors] — and the active one is provided through [LocalNiyyahColors]
 * by [NiyyahTheme]. Screens keep referencing `NiyyahColors.X`; those properties
 * are `@Composable` accessors that read whichever palette is in scope, so light
 * and dark differ only by these tokens — never by layout (BM-009 dark mode).
 */
@Immutable
data class NiyyahPalette(
    val Background: Color,
    val HeaderBackground: Color,
    val Surface: Color,
    val SurfaceElevated: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val Border: Color,
    val Navy: Color,
    val Gold: Color,
    val Green: Color,
    val SoftFill: Color,
    val OnNavy: Color,
    val OnNavySecondary: Color,
    val OnNavyTertiary: Color,
    val NavPillBackground: Color,
    val NavPillBorder: Color,
    val NavShadow: Color,
    val HeroBadgeBackground: Color,
    val HeroBadgeBorder: Color,
    /** Faint hairline used for header underlines, dividers and chip outlines. */
    val Hairline: Color,
)

/** Light tokens — extracted verbatim from the BM-005 Figma light frames. */
val LightNiyyahColors = NiyyahPalette(
    Background = Color(0xFFF7F6F3),
    HeaderBackground = Color(0xFFFCF9F8),
    Surface = Color(0xFFFFFFFF),
    SurfaceElevated = Color(0xFFFCFBF9),
    TextPrimary = Color(0xFF071836),
    TextSecondary = Color(0xFF75777E),
    Border = Color(0xFFE7E2DA),
    Navy = Color(0xFF1E2D4C),
    Gold = Color(0xFFD4A84F),
    Green = Color(0xFF006C4F),
    SoftFill = Color(0xFFF0EDEC),
    OnNavy = Color(0xFFFFFFFF),
    OnNavySecondary = Color(0xB3FFFFFF),
    OnNavyTertiary = Color(0xCCFFFFFF),
    NavPillBackground = Color(0xCCFFFFFF),
    NavPillBorder = Color(0x33FFFFFF),
    NavShadow = Color(0x1A1E2D4C),
    HeroBadgeBackground = Color(0x33FFFFFF),
    HeroBadgeBorder = Color(0x1AFFFFFF),
    Hairline = Color(0x4DC5C6CE),
)

/**
 * Dark tokens — from the Master Spec (CLAUDE.md § Color Palette → Dark Mode).
 * Same roles as light; only the values differ. The hero card stays navy in both
 * modes (a brand surface), so its on-navy + badge tokens are shared.
 */
val DarkNiyyahColors = NiyyahPalette(
    Background = Color(0xFF0B0F10),
    HeaderBackground = Color(0xFF12181A),
    Surface = Color(0xFF151B1C),
    SurfaceElevated = Color(0xFF232E31),
    TextPrimary = Color(0xFFF5F5F5),
    TextSecondary = Color(0xFFA7A7A7),
    Border = Color(0xFF2A3335),
    Navy = Color(0xFF1E2D4C),
    Gold = Color(0xFFD4A84F),
    Green = Color(0xFF18A67A),
    SoftFill = Color(0xFF232E31),
    OnNavy = Color(0xFFFFFFFF),
    OnNavySecondary = Color(0xB3FFFFFF),
    OnNavyTertiary = Color(0xCCFFFFFF),
    // Glass pill on a dark canvas: dark surface at 80% with a faint white rim.
    NavPillBackground = Color(0xCC151B1C),
    NavPillBorder = Color(0x26FFFFFF),
    NavShadow = Color(0x40000000),
    HeroBadgeBackground = Color(0x33FFFFFF),
    HeroBadgeBorder = Color(0x1AFFFFFF),
    Hairline = Color(0x1FFFFFFF),
)

/** The palette in scope; defaults to light. Set by [NiyyahTheme]. */
val LocalNiyyahColors = staticCompositionLocalOf { LightNiyyahColors }

/**
 * Backwards-compatible accessor: every `NiyyahColors.X` call site resolves to the
 * current [NiyyahPalette] (same pattern as `MaterialTheme.colorScheme`). Must be
 * read from a @Composable scope — the only non-composable consumers are the
 * Material schemes in Theme.kt, which use the raw palette instances instead.
 */
object NiyyahColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.Background
    val HeaderBackground: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.HeaderBackground
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.Surface
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.SurfaceElevated
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.TextPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.TextSecondary
    val Border: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.Border
    val Navy: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.Navy
    val Gold: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.Gold
    val Green: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.Green
    val SoftFill: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.SoftFill
    val OnNavy: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.OnNavy
    val OnNavySecondary: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.OnNavySecondary
    val OnNavyTertiary: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.OnNavyTertiary
    val NavPillBackground: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.NavPillBackground
    val NavPillBorder: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.NavPillBorder
    val NavShadow: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.NavShadow
    val HeroBadgeBackground: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.HeroBadgeBackground
    val HeroBadgeBorder: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.HeroBadgeBorder
    val Hairline: Color @Composable @ReadOnlyComposable get() = LocalNiyyahColors.current.Hairline
}
