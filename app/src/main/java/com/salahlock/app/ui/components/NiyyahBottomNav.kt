package com.salahlock.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import com.salahlock.app.R
import com.salahlock.app.ui.navigation.NiyyahRoutes
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating pill bottom navigation — Figma node 1:3 (Home).
 * 351dp wide pill, 19.5dp side margins, 25dp above the bottom edge;
 * glass fill, glass border, navy-10% shadow; 25dp horizontal / 13dp vertical
 * inner padding; gold 4dp dot under the active tab.
 *
 * BM-007 polish: the Profile tab was removed (Profile now opens only from the
 * top-right avatar), and the active gold dot + icon tint now interpolate against
 * [pageOffset] so they glide with the pager instead of snapping.
 */
data class NiyyahTab(val route: String, val iconRes: Int, val label: String)

val NiyyahTabs = listOf(
    NiyyahTab(NiyyahRoutes.HOME, R.drawable.ic_nav_home, "Home"),
    NiyyahTab(NiyyahRoutes.PRAYER, R.drawable.ic_nav_prayer, "Prayer"),
    NiyyahTab(NiyyahRoutes.QURAN, R.drawable.ic_nav_quran, "Quran"),
    NiyyahTab(NiyyahRoutes.KNOWLEDGE, R.drawable.ic_nav_knowledge, "Knowledge"),
)

/**
 * @param pageOffset the pager's fractional page (currentPage + offsetFraction),
 *   e.g. 1.4 while swiping from Prayer toward Quran. Drives the sliding dot and
 *   the smooth active/inactive icon tint blend.
 */
@Composable
fun NiyyahBottomNav(
    pageOffset: Float,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Tab icon centers and the pill's left edge, in window pixels, captured on
    // layout so the single gold dot can be positioned between any two tabs.
    val centers = remember { mutableStateListOf<Float>().apply { repeat(NiyyahTabs.size) { add(0f) } } }
    var pillLeft by remember { mutableStateOf(0f) }
    var measured by remember { mutableStateOf(false) }

    val activeTint = NiyyahColors.TextPrimary
    val inactiveTint = NiyyahColors.TextSecondary
    val gold = NiyyahColors.Gold

    Box(
        modifier = modifier
            .padding(horizontal = 19.5.dp)
            .fillMaxWidth()
            .onGloballyPositioned { pillLeft = it.boundsInWindow().left },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = NiyyahShapes.Pill,
                    ambientColor = NiyyahColors.NavShadow,
                    spotColor = NiyyahColors.NavShadow,
                )
                .background(NiyyahColors.NavPillBackground, NiyyahShapes.Pill)
                .border(1.dp, NiyyahColors.NavPillBorder, NiyyahShapes.Pill)
                .padding(horizontal = 25.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NiyyahTabs.forEachIndexed { index, tab ->
                // 0f when this tab is dead-centre, 1f once a full page away.
                val distance = abs(index - pageOffset).coerceIn(0f, 1f)
                val tint = lerp(activeTint, inactiveTint, distance)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .onGloballyPositioned {
                            centers[index] = it.boundsInWindow().center.x
                            measured = true
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onTabSelected(index) },
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = tab.label,
                        tint = tint,
                    )
                    // Reserve the 4dp-gap + 4dp-dot slot on every tab so the pill
                    // height is constant; the real dot is drawn once in the overlay.
                    Box(modifier = Modifier.padding(top = 4.dp).size(4.dp))
                }
            }
        }

        // Single gold dot gliding between the two tabs pageOffset sits between.
        if (measured) {
            val lower = pageOffset.toInt().coerceIn(0, NiyyahTabs.lastIndex)
            val upper = (lower + 1).coerceAtMost(NiyyahTabs.lastIndex)
            val frac = (pageOffset - lower).coerceIn(0f, 1f)
            val dotCenterX = lerpFloat(centers[lower], centers[upper], frac) - pillLeft
            val dotRadiusPx = with(density) { 2.dp.toPx() }
            val bottomInsetPx = with(density) { 13.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset {
                        IntOffset(
                            x = (dotCenterX - dotRadiusPx).roundToInt(),
                            y = -bottomInsetPx.roundToInt(),
                        )
                    }
                    .size(4.dp)
                    .background(gold, NiyyahShapes.Pill),
            )
        }
    }
}
