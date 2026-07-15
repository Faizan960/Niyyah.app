package com.salahlock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = NiyyahColors.Navy,
    onPrimary = NiyyahColors.OnNavy,
    secondary = NiyyahColors.Gold,
    onSecondary = NiyyahColors.TextPrimary,
    background = NiyyahColors.Background,
    onBackground = NiyyahColors.TextPrimary,
    surface = NiyyahColors.Surface,
    onSurface = NiyyahColors.TextPrimary,
    surfaceVariant = NiyyahColors.SurfaceElevated,
    onSurfaceVariant = NiyyahColors.TextSecondary,
    outline = NiyyahColors.Border,
)

/** Dark palette from the Master Spec (CLAUDE.md § Color Palette → Dark Mode). */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF18A67A),
    onPrimary = Color(0xFFF5F5F5),
    secondary = NiyyahColors.Gold,
    onSecondary = Color(0xFF0B0F10),
    background = Color(0xFF0B0F10),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF151B1C),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF232E31),
    onSurfaceVariant = Color(0xFFA7A7A7),
    outline = Color(0xFF2A3335),
)

private val NiyyahMaterialTypography = Typography(
    displayLarge = NiyyahType.DisplayLarge,
    headlineMedium = NiyyahType.HeadingMedium,
    titleLarge = NiyyahType.Quote,
    bodyLarge = NiyyahType.Body,
    bodyMedium = NiyyahType.BodyMedium,
    labelLarge = NiyyahType.ButtonLabel,
    labelMedium = NiyyahType.LabelUppercase,
    labelSmall = NiyyahType.Badge,
)

/**
 * BM-006.9: the Dark Mode preference switches the Material color scheme.
 * NOTE: the BM-005 screens style themselves with static NiyyahColors, so a
 * full dark restyle awaits dark Figma frames (tracked as BM-007+ debt) —
 * Material surfaces, dialogs and menus do follow the dark scheme.
 */
@Composable
fun NiyyahTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    // isSystemInDarkTheme() intentionally not the default: the BM-005 frames
    // are light-only, so dark applies only when the user explicitly opts in.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = NiyyahMaterialTypography,
        content = content,
    )
}
