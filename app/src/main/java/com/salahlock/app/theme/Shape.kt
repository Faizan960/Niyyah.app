package com.salahlock.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Niyyah corner-radius scale (Stitch V2 — design/stitch-v2).
 * Tailwind usage in the exports: rounded (4) · rounded-lg (8) · rounded-xl (12)
 * · rounded-2xl (16) · rounded-full (pills). Buttons and chips are 4px or
 * pill; cards are 12–16px. Pills use RoundedCornerShape(50) at call sites.
 */
val NiyyahShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
