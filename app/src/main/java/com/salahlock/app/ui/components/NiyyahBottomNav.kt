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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.salahlock.app.R
import com.salahlock.app.ui.navigation.NiyyahRoutes
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes

/**
 * Floating pill bottom navigation — Figma node 1:3 (Home).
 * 351dp wide pill, 19.5dp side margins, 25dp above the bottom edge;
 * white 80% fill, white 20% border, 0/4/20 navy-10% shadow;
 * 25dp horizontal / 13dp vertical inner padding; gold 4dp dot under the active tab.
 */
data class NiyyahTab(val route: String, val iconRes: Int, val label: String)

val NiyyahTabs = listOf(
    NiyyahTab(NiyyahRoutes.HOME, R.drawable.ic_nav_home, "Home"),
    NiyyahTab(NiyyahRoutes.PRAYER, R.drawable.ic_nav_prayer, "Prayer"),
    NiyyahTab(NiyyahRoutes.QURAN, R.drawable.ic_nav_quran, "Quran"),
    NiyyahTab(NiyyahRoutes.KNOWLEDGE, R.drawable.ic_nav_knowledge, "Knowledge"),
    NiyyahTab(NiyyahRoutes.PROFILE, R.drawable.ic_nav_profile, "Profile"),
)

@Composable
fun NiyyahBottomNav(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 19.5.dp)
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
        NiyyahTabs.forEach { tab ->
            val selected = tab.route == currentRoute
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onTabSelected(tab.route) },
            ) {
                Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = tab.label,
                    tint = if (selected) NiyyahColors.TextPrimary else NiyyahColors.TextSecondary,
                )
                // Figma: only the active tab reserves the 4dp-gap + 4dp gold dot below its icon
                if (selected) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(4.dp)
                            .background(NiyyahColors.Gold, NiyyahShapes.Pill),
                    )
                }
            }
        }
    }
}
