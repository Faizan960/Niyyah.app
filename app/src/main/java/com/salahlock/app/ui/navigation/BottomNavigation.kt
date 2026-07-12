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
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.MotionTokens

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Outlined.Home)
    object Prayers : Screen("prayers", "Prayers", Icons.AutoMirrored.Outlined.List)
    object Qibla : Screen("qibla", "Qibla", Icons.Outlined.Explore)
    object HadithAzkar : Screen("hadith_azkar", "Hadith", Icons.Outlined.Book)

    // Lock Apps — promoted to a primary tab in Sprint N.3 (reuses AppBlacklistScreen)
    object LockApps : Screen("lock_apps", "Lock Apps", Icons.Outlined.Lock)

    // Screens not in bottom bar
    object Profile : Screen("profile", "Profile", Icons.Outlined.Person)
    object Blacklist : Screen("blacklist", "Blacklist", Icons.AutoMirrored.Outlined.List)
}

// Sprint N.3 — Prayer and Qibla folded into Home; Lock Apps promoted to a tab.
val BottomNavItems = listOf(
    Screen.Home,
    Screen.LockApps,
    Screen.HadithAzkar,
)

/**
 * Icon-only floating navigation bar.
 *
 * Labels removed per Sprint D.3 — icon + glass pill active indicator only.
 * Takes [selectedIndex] from [HorizontalPager] page position.
 * [onTabSelected] scrolls the pager to the tapped page.
 */
@Composable
fun FloatingBottomNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val isAmoled = MaterialTheme.colorScheme.background == Color.Black

    val glassColor = when {
        isLightMode -> Color.White.copy(alpha = 0.88f)
        isAmoled -> Color(0xFF050505).copy(alpha = 0.88f)
        else -> Color(0xFF1C2426).copy(alpha = 0.72f)
    }
    val glassBorder = when {
        isLightMode -> Color.Black.copy(alpha = 0.06f)
        isAmoled -> EmeraldPrimary.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val shadowColor = if (isLightMode) Color.Black.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.55f)
    val ambientShadow = if (isLightMode) Color.Transparent else EmeraldPrimary.copy(alpha = 0.25f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(64.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = shadowColor,
                    ambientColor = ambientShadow,
                )
                .background(glassColor, RoundedCornerShape(32.dp))
                .border(1.dp, glassBorder, RoundedCornerShape(32.dp))
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItems.forEachIndexed { index, screen ->
                val isSelected = selectedIndex == index

                val weight by animateFloatAsState(
                    targetValue = if (isSelected) 1.45f else 1f,
                    animationSpec = MotionTokens.noBounceSpring(),
                    label = "navWeight_$index",
                )
                val chipColor by animateColorAsState(
                    targetValue = if (isSelected) EmeraldPrimary else Color.Transparent,
                    animationSpec = MotionTokens.normalTween(),
                    label = "navChip_$index",
                )
                val iconTint = when {
                    isSelected -> Color.White
                    isLightMode -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> Color.White.copy(alpha = 0.55f)
                }

                Box(
                    modifier = Modifier
                        .weight(weight)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(chipColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Icon only — no labels per Sprint D.3
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
