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
import androidx.compose.material3.Text
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
 * Stitch V2 bottom bar: full-width "dark lens" glass bar — background at
 * ~70–90% opacity, 1dp hairline top border, icon over label, active item
 * tinted (emerald in dark, navy in light) with a small dot indicator.
 * Keeps the 3-tab pager contract (navigation structure unchanged).
 */
@Composable
fun FloatingBottomNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val activeTint = if (isLightMode) Color(0xFF1E2D4C) else Color(0xFF61DCAC)
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.background.copy(alpha = if (isLightMode) 0.92f else 0.85f)

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
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItems.forEachIndexed { index, screen ->
                val isSelected = selectedIndex == index
                val tint by animateColorAsState(
                    targetValue = if (isSelected) activeTint else inactiveTint,
                    animationSpec = MotionTokens.normalTween(),
                    label = "navTint_$index",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(index) },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(
                                if (isSelected) tint else Color.Transparent,
                                RoundedCornerShape(50),
                            )
                    )
                }
            }
        }
    }
}
