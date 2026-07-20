package com.salahlock.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.MotionTokens

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Outlined.Home)
    object Prayers : Screen("prayers", "Prayers", Icons.AutoMirrored.Outlined.List)
    object Qibla : Screen("qibla", "Qibla", Icons.Outlined.Explore)

    // Lock Apps — promoted to a primary tab in Sprint N.3 (reuses AppBlacklistScreen)
    object LockApps : Screen("lock_apps", "Lock Apps", Icons.Outlined.Lock)

    // BM-011 — Quran and Hadith are now first-class, separate main tabs.
    // Distinct book icons: open book (Quran) vs. library (Hadith collections).
    object Quran : Screen("quran", "Quran", Icons.AutoMirrored.Outlined.MenuBook)
    object Hadith : Screen("hadith", "Hadith", Icons.Outlined.LocalLibrary)

    // Screens not in bottom bar
    object Profile : Screen("profile", "Profile", Icons.Outlined.Person)
    object Blacklist : Screen("blacklist", "Blacklist", Icons.AutoMirrored.Outlined.List)
}

// BM-011 — four primary tabs: Home · Lock Apps · Quran · Hadith.
val BottomNavItems = listOf(
    Screen.Home,
    Screen.LockApps,
    Screen.Quran,
    Screen.Hadith,
)

/**
 * BM-011 bottom bar: full-width "dark lens" bar with a 1dp hairline top border
 * and four tabs. The selected tab animates into a soft emerald pill (filled
 * rounded container + icon + label), matching the Quran/Hadith reference.
 * Inactive tabs are icon-over-label in the muted onSurfaceVariant tint.
 * Signature unchanged (selectedIndex / onTabSelected) — pager contract intact.
 */
@Composable
fun FloatingBottomNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    // BM-011 reference: the selected tab is a SOLID emerald pill with white
    // icon + label; inactive tabs are muted icon-over-label.
    val activeTint = Color.White
    val pillColor = EmeraldPrimary
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.background.copy(alpha = if (isLightMode) 0.94f else 0.88f)

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(barColor)
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItems.forEachIndexed { index, screen ->
                val isSelected = selectedIndex == index
                val tint by animateColorAsState(
                    targetValue = if (isSelected) activeTint else inactiveTint,
                    animationSpec = MotionTokens.normalTween(),
                    label = "navTint_$index",
                )
                val pillAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = MotionTokens.normalTween(),
                    label = "navPill_$index",
                )
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(pillColor.copy(alpha = pillColor.alpha * pillAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(index) },
                        )
                        .padding(vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    // Label only appears on the selected tab (space-efficient pill).
                    if (pillAlpha > 0.05f) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer { alpha = pillAlpha },
                        )
                    }
                }
            }
        }
    }
}
