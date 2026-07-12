package com.salahlock.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Niyyah corner-radius scale (CLAUDE.md — Master Spec v1.0):
 *  Chips 12 · Buttons 16 · Cards 24 · Sheets/Dialogs 28 · Hero Cards 32
 *
 * M3 slot mapping: chips draw from small, buttons/small cards from medium,
 * standard cards from large, sheets and dialogs from extraLarge. Hero cards
 * (32dp) always set RoundedCornerShape(32.dp) explicitly at the call site.
 */
val NiyyahShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
