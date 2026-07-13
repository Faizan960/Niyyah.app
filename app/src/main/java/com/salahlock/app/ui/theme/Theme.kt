package com.salahlock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
 * BM-005: the Figma light frames are the single design. Dark mode is deferred,
 * so the light scheme is applied regardless of system setting.
 */
@Composable
fun NiyyahTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme() intentionally unused until dark frames are redesigned.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = NiyyahMaterialTypography,
        content = content,
    )
}
