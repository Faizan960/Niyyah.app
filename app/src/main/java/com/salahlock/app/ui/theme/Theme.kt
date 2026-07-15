package com.salahlock.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Material schemes are built from the raw palette instances (non-composable
// context), mirroring the theme-aware NiyyahColors tokens so Material surfaces,
// dialogs and menus follow the same light/dark values as the custom UI.
private val LightColorScheme = lightColorScheme(
    primary = LightNiyyahColors.Navy,
    onPrimary = LightNiyyahColors.OnNavy,
    secondary = LightNiyyahColors.Gold,
    onSecondary = LightNiyyahColors.TextPrimary,
    background = LightNiyyahColors.Background,
    onBackground = LightNiyyahColors.TextPrimary,
    surface = LightNiyyahColors.Surface,
    onSurface = LightNiyyahColors.TextPrimary,
    surfaceVariant = LightNiyyahColors.SurfaceElevated,
    onSurfaceVariant = LightNiyyahColors.TextSecondary,
    outline = LightNiyyahColors.Border,
)

/** Dark palette from the Master Spec (CLAUDE.md § Color Palette → Dark Mode). */
private val DarkColorScheme = darkColorScheme(
    primary = DarkNiyyahColors.Green,
    onPrimary = DarkNiyyahColors.TextPrimary,
    secondary = DarkNiyyahColors.Gold,
    onSecondary = DarkNiyyahColors.Background,
    background = DarkNiyyahColors.Background,
    onBackground = DarkNiyyahColors.TextPrimary,
    surface = DarkNiyyahColors.Surface,
    onSurface = DarkNiyyahColors.TextPrimary,
    surfaceVariant = DarkNiyyahColors.SurfaceElevated,
    onSurfaceVariant = DarkNiyyahColors.TextSecondary,
    outline = DarkNiyyahColors.Border,
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
    val palette = if (darkTheme) DarkNiyyahColors else LightNiyyahColors
    CompositionLocalProvider(LocalNiyyahColors provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = NiyyahMaterialTypography,
            content = content,
        )
    }
}
