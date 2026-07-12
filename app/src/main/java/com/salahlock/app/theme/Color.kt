package com.salahlock.app.theme

import androidx.compose.ui.graphics.Color

// Shared Accents
val EmeraldPrimary = Color(0xFF0F8F6A)
val EmeraldSecondary = Color(0xFF18A67A)
val GoldAccent = Color(0xFFD4A84F)

// Sprint D — new accent tokens
val ElevatedSurface = Color(0xFF232E31)
val MutedSage = Color(0xFF7D927C)
val DeepNavy = Color(0xFF253B63)
val WarmStone = Color(0xFF8C7D76)

// Light Theme
val LightBackground = Color(0xFFF7F6F2)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1EFE8)
val LightTextPrimary = Color(0xFF121212)
val LightTextSecondary = Color(0xFF6B6B6B)
val LightDivider = Color(0xFFE0DDCC)

// Dark Theme
val DarkBackground = Color(0xFF0B0F10)
val DarkSurface = Color(0xFF151B1C)
val DarkSurfaceVariant = Color(0xFF1C2426)
val DarkTextPrimary = Color(0xFFF5F5F5)
val DarkTextSecondary = Color(0xFFA7A7A7)
val DarkDivider = Color(0xFF2A3335)

// AMOLED Theme
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0A)
val AmoledSurfaceVariant = Color(0xFF111111)

// Status Colors
val RustDanger = Color(0xFFE53935)
val RustLight  = Color(0xFFFFCDD2)
val SuccessGreen = Color(0xFF43A047)
val SuccessLight = Color(0xFFC8E6C9)

// Reading Card surfaces (no glassmorphism on reading screens)
val ReadingLight = LightSurface
val ReadingDark = DarkSurface
val ReadingAmoled = AmoledSurface

// Theme helper aliases — used in MaterialTheme color scheme assignments only
val EmeraldDim = EmeraldPrimary.copy(alpha = 0.2f)
val EmeraldSurface = EmeraldPrimary.copy(alpha = 0.1f)
val GoldLight = GoldAccent.copy(alpha = 0.4f)
val GoldDim = GoldAccent.copy(alpha = 0.8f)

