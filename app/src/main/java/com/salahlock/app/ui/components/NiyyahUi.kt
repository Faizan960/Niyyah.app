package com.salahlock.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Stitch V2 shared components (design/stitch-v2).
 *
 * Cards: surface background, 1px hairline border (outline), no shadow,
 * 12–16px radius, internal padding ≥ 24dp ("lg").
 * Section headers: small uppercase sans label with wide tracking.
 * Chips: 32dp height, small radius, surfaceVariant container.
 */

@Composable
fun NiyyahCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    contentPadding: Dp = 24.dp,
    container: Color = MaterialTheme.colorScheme.surface,
    border: Color = MaterialTheme.colorScheme.outline,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun NiyyahSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = if (onTrailingClick != null) {
                    Modifier.clickable(onClick = onTrailingClick)
                } else Modifier,
            )
        }
    }
}

@Composable
fun NiyyahChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val container = if (active) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val label = if (active) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .height(32.dp)
            .background(container, MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = label,
        )
    }
}

/** Thin hairline divider matching the Stitch outline color. */
@Composable
fun NiyyahHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}
