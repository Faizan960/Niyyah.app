package com.salahlock.app.theme

import androidx.compose.ui.graphics.Color

// ── Niyyah palette (CLAUDE.md — Master Spec v1.0) ────────────────────────────

// Brand accents
val EmeraldPrimary = Color(0xFF0F8F6A)
val EmeraldSecondary = Color(0xFF18A67A)
val GoldAccent = Color(0xFFD4A84F)
val Navy = Color(0xFF1E2D4C)
val DeepNavy = Color(0xFF253B63)
val Sage = Color(0xFFACBDAA)
val MutedSage = Color(0xFF7D927C)
val WarmStoneLight = Color(0xFFCEC0BB)
val WarmStone = Color(0xFF8C7D76)

// Elevated dark surface (spec: Elevated #232E31)
val ElevatedSurface = Color(0xFF232E31)

// Light Theme
val LightBackground = Color(0xFFF7F6F3)
val LightBackgroundSecondary = Color(0xFFF2F0EC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFFCFBF9)
val LightSurfaceVariant = Color(0xFFF2F0EC)
val LightTextPrimary = Color(0xFF111111)
val LightTextSecondary = Color(0xFF666666)
val LightTextMuted = Color(0xFF8C8C8C)
val LightDivider = Color(0xFFE7E2DA)

// Dark Theme
val DarkBackground = Color(0xFF0B0F10)
val DarkBackgroundSecondary = Color(0xFF12181A)
val DarkSurface = Color(0xFF151B1C)
val DarkSurfaceVariant = Color(0xFF1C2426)
val DarkTextPrimary = Color(0xFFF5F5F5)
val DarkTextSecondary = Color(0xFFA7A7A7)
val DarkTextMuted = Color(0xFF808080)
val DarkDivider = Color(0xFF2A3335)

// AMOLED Theme
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0A)
val AmoledSurfaceVariant = Color(0xFF111111)

// Status Colors (spec: Success/Warning/Error/Information)
val RustDanger = Color(0xFFE5484D)
val RustLight  = Color(0xFFFAD4D6)
val SuccessGreen = Color(0xFF0F8F6A)
val SuccessLight = Color(0xFFD3E9E1)
val WarningAmber = Color(0xFFF5A524)
val InfoBlue = Color(0xFF2F6FEB)

// Reading Card surfaces (no glassmorphism on reading screens)
val ReadingLight = LightSurface
val ReadingDark = DarkSurface
val ReadingAmoled = AmoledSurface

// ── Stitch V2 tokens (design/stitch-v2) ─────────────────────────────────────
// Light ("Premium Spiritual Editorial"): navy primary, emerald secondary
val StitchLightSurface = Color(0xFFFCF9F8)     // cards sit on #F7F6F3 canvas
val StitchLightTextSecondary = Color(0xFF45474E)
val StitchLightOutline = Color(0xFF75777E)
// Dark ("Niyyah Dark"): emerald primary, luminance layering
val StitchDarkPrimaryBright = Color(0xFF61DCAC) // accents/countdowns on dark
val StitchDarkOnPrimary = Color(0xFF003827)
val StitchNavyChip = Color(0xFF31466F)          // active chip container (dark)
val StitchGold = Color(0xFFEEC064)              // tertiary in both modes

// Theme helper aliases — used in MaterialTheme color scheme assignments only
val EmeraldDim = EmeraldPrimary.copy(alpha = 0.2f)
val EmeraldSurface = EmeraldPrimary.copy(alpha = 0.1f)
val GoldLight = GoldAccent.copy(alpha = 0.4f)
val GoldDim = GoldAccent.copy(alpha = 0.8f)
