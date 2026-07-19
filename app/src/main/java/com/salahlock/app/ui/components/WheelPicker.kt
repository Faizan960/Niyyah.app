package com.salahlock.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salahlock.app.theme.EmeraldPrimary
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * iOS-style snapping wheel picker (SL-011). Reusable — hours, minutes, AM/PM,
 * or any string list. AMOLED-friendly: theme surfaces only, emerald highlight band.
 *
 * Three rows visible; the center row is the selection. Fling snaps per item via
 * [rememberSnapFlingBehavior]; [onSelected] fires as the centered index changes.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 42.dp,
    wheelWidth: Dp = 72.dp,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    )
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // Centered item = first visible (list is padded by one itemHeight top/bottom).
    val centeredIndex by remember {
        derivedStateOf {
            val offsetAdjust = if (listState.firstVisibleItemScrollOffset > 0) 1 else 0
            (listState.firstVisibleItemIndex + offsetAdjust).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { centeredIndex }
            .distinctUntilChanged()
            .collect { onSelected(it) }
    }

    Box(
        modifier = modifier.width(wheelWidth).height(itemHeight * 3),
        contentAlignment = Alignment.Center,
    ) {
        // Selection band behind the center row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(EmeraldPrimary.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            modifier = Modifier.fillMaxWidth().height(itemHeight * 3),
            contentPadding = PaddingValues(vertical = itemHeight),
        ) {
            items(items.size) { index ->
                val selected = index == centeredIndex
                Box(
                    modifier = Modifier.fillMaxWidth().height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = items[index],
                        style = if (selected) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) EmeraldPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(if (selected) 1f else 0.45f),
                    )
                }
            }
        }
    }
}
