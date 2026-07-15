package com.salahlock.app.ui.verification

import androidx.compose.ui.graphics.Color

/**
 * Verification-flow color tokens. Dark and light are identical structurally —
 * only the token values differ (per the design brief's dark-mode rule). The
 * lock context is dark, so [dark] is the default; [light] backs the light
 * rendering of the typing screen and the app-wide dark-mode-off preference.
 */
data class VerifyPalette(
    val bgTop: Color,
    val bgBottom: Color,
    val glow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val cardFill: Color,
    val cardBorder: Color,
    val selectedFill: Color,
    val selectedBorder: Color,
    val disabledText: Color,
    val emerald: Color,
    val onEmerald: Color,
    val affirmation: Color,
    val wordmark: Color,
) {
    companion object {
        val dark = VerifyPalette(
            bgTop = Color(0xFF0E1C1F),
            bgBottom = Color(0xFF0A1315),
            glow = Color(0x1418A67A),
            textPrimary = Color(0xFFF5F5F5),
            textSecondary = Color(0xB3FFFFFF),
            textMuted = Color(0x73FFFFFF),
            cardFill = Color(0x0AFFFFFF),
            cardBorder = Color(0x1AFFFFFF),
            selectedFill = Color(0x1418A67A),
            selectedBorder = Color(0x9934D399),
            disabledText = Color(0x4DFFFFFF),
            emerald = Color(0xFF34D399),
            onEmerald = Color(0xFF06251A),
            affirmation = Color(0xF2FFFFFF),
            wordmark = Color(0xFF34D399),
        )

        val light = VerifyPalette(
            bgTop = Color(0xFFFFFFFF),
            bgBottom = Color(0xFFFDFCFA),
            glow = Color(0x2618A67A),
            textPrimary = Color(0xFF111111),
            textSecondary = Color(0xFF666666),
            textMuted = Color(0xFF8C8C8C),
            cardFill = Color(0xFFFFFFFF),
            cardBorder = Color(0xFFE7E2DA),
            selectedFill = Color(0x140F8F6A),
            selectedBorder = Color(0xFF0F8F6A),
            disabledText = Color(0xFFB8B4AE),
            emerald = Color(0xFF12B981),
            onEmerald = Color(0xFF06251A),
            affirmation = Color(0xFFB9BCD0),
            wordmark = Color(0xFF0F8F6A),
        )

        fun of(isDark: Boolean): VerifyPalette = if (isDark) dark else light
    }
}
