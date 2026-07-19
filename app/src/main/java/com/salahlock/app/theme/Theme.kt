package com.salahlock.app.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

// Stitch V2 "Premium Spiritual Editorial": navy primary, emerald secondary,
// gold tertiary, warm paper surfaces, #E7E2DA hairlines.
private val LightThemeColors = lightColorScheme(
    primary = Navy,
    onPrimary = LightSurface,
    primaryContainer = DeepNavy,
    onPrimaryContainer = LightSurface,

    secondary = EmeraldPrimary,
    onSecondary = LightSurface,
    secondaryContainer = EmeraldPrimary.copy(alpha = 0.12f),
    onSecondaryContainer = EmeraldPrimary,

    tertiary = GoldAccent,
    onTertiary = LightSurface,
    tertiaryContainer = GoldLight,
    onTertiaryContainer = GoldDim,

    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = StitchLightTextSecondary,

    error = RustDanger,
    onError = LightSurface,
    errorContainer = RustLight,
    onErrorContainer = RustDanger,

    outline = LightDivider,
    outlineVariant = LightDivider
)

// Stitch V2 "Niyyah Dark": emerald primary on charcoal-teal luminance layers.
private val DarkThemeColors = darkColorScheme(
    primary = EmeraldSecondary,          // #18A67A
    onPrimary = DarkTextPrimary,
    primaryContainer = EmeraldSecondary,
    onPrimaryContainer = StitchDarkOnPrimary,

    secondary = StitchNavyChip,          // navy structural grounding
    onSecondary = DarkTextPrimary,
    secondaryContainer = DeepNavy,
    onSecondaryContainer = StitchDarkPrimaryBright,

    tertiary = StitchGold,
    onTertiary = DarkBackground,
    tertiaryContainer = GoldDim,
    onTertiaryContainer = GoldLight,

    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = ElevatedSurface,    // level 2 #232E31
    onSurfaceVariant = DarkTextSecondary,

    error = RustLight,
    onError = DarkBackground,
    errorContainer = RustDanger,
    onErrorContainer = RustLight,

    outline = DarkDivider,
    outlineVariant = DarkDivider
)

private val AmoledThemeColors = darkColorScheme(
    primary = EmeraldSecondary,
    onPrimary = DarkTextPrimary,
    primaryContainer = EmeraldSecondary,
    onPrimaryContainer = StitchDarkOnPrimary,

    secondary = StitchNavyChip,
    onSecondary = DarkTextPrimary,
    secondaryContainer = DeepNavy,
    onSecondaryContainer = StitchDarkPrimaryBright,

    tertiary = StitchGold,
    onTertiary = AmoledBackground,
    tertiaryContainer = GoldDim,
    onTertiaryContainer = GoldLight,
    
    background = AmoledBackground,
    onBackground = DarkTextPrimary,
    surface = AmoledSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    
    error = RustLight,
    onError = AmoledBackground,
    errorContainer = RustDanger,
    onErrorContainer = RustLight,
    
    outline = DarkDivider,
    outlineVariant = DarkDivider
)

enum class ThemeMode {
    LIGHT, DARK, AMOLED
}

@Composable
fun SalahLockTheme(
    themeMode: ThemeMode = if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val targetColorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightThemeColors
        ThemeMode.DARK -> DarkThemeColors
        ThemeMode.AMOLED -> AmoledThemeColors
    }

    val animatedColorScheme = ColorScheme(
        primary = animateColor(targetColorScheme.primary),
        onPrimary = animateColor(targetColorScheme.onPrimary),
        primaryContainer = animateColor(targetColorScheme.primaryContainer),
        onPrimaryContainer = animateColor(targetColorScheme.onPrimaryContainer),
        inversePrimary = animateColor(targetColorScheme.inversePrimary),
        secondary = animateColor(targetColorScheme.secondary),
        onSecondary = animateColor(targetColorScheme.onSecondary),
        secondaryContainer = animateColor(targetColorScheme.secondaryContainer),
        onSecondaryContainer = animateColor(targetColorScheme.onSecondaryContainer),
        tertiary = animateColor(targetColorScheme.tertiary),
        onTertiary = animateColor(targetColorScheme.onTertiary),
        tertiaryContainer = animateColor(targetColorScheme.tertiaryContainer),
        onTertiaryContainer = animateColor(targetColorScheme.onTertiaryContainer),
        background = animateColor(targetColorScheme.background),
        onBackground = animateColor(targetColorScheme.onBackground),
        surface = animateColor(targetColorScheme.surface),
        onSurface = animateColor(targetColorScheme.onSurface),
        surfaceVariant = animateColor(targetColorScheme.surfaceVariant),
        onSurfaceVariant = animateColor(targetColorScheme.onSurfaceVariant),
        surfaceTint = animateColor(targetColorScheme.surfaceTint),
        inverseSurface = animateColor(targetColorScheme.inverseSurface),
        inverseOnSurface = animateColor(targetColorScheme.inverseOnSurface),
        error = animateColor(targetColorScheme.error),
        onError = animateColor(targetColorScheme.onError),
        errorContainer = animateColor(targetColorScheme.errorContainer),
        onErrorContainer = animateColor(targetColorScheme.onErrorContainer),
        outline = animateColor(targetColorScheme.outline),
        outlineVariant = animateColor(targetColorScheme.outlineVariant),
        scrim = animateColor(targetColorScheme.scrim),
        surfaceBright = animateColor(targetColorScheme.surfaceBright),
        surfaceContainer = animateColor(targetColorScheme.surfaceContainer),
        surfaceContainerHigh = animateColor(targetColorScheme.surfaceContainerHigh),
        surfaceContainerHighest = animateColor(targetColorScheme.surfaceContainerHighest),
        surfaceContainerLow = animateColor(targetColorScheme.surfaceContainerLow),
        surfaceContainerLowest = animateColor(targetColorScheme.surfaceContainerLowest),
        surfaceDim = animateColor(targetColorScheme.surfaceDim),
    )

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = SalahLockTypography,
        shapes = NiyyahShapes,
        content = content,
    )
}

@Composable
private fun animateColor(targetValue: Color): Color {
    val animatedColor by animateColorAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = MotionTokens.Slow, easing = MotionTokens.StandardEasing),
        label = "themeColorAnimation"
    )
    return animatedColor
}
