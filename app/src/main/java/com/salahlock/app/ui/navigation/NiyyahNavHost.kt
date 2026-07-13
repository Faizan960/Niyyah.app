package com.salahlock.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahType

/** Route constants for every Figma screen (BM-005 build order). */
object NiyyahRoutes {
    const val HOME = "home"
    const val PRAYER = "prayer"
    const val SALAH_LOCK = "salah_lock"
    const val QURAN = "quran"
    const val KNOWLEDGE = "knowledge"
    const val HADITH = "hadith"
    const val AZKAR = "azkar"
    const val QIBLA = "qibla"
    const val COLLECTIONS = "collections"
    const val BOOKMARKS = "bookmarks"
    const val MONTHLY_REFLECTION = "monthly_reflection"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
}

/** Placeholder shown for screens not yet rebuilt from Figma. Replaced screen-by-screen. */
@Composable
fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name, style = NiyyahType.Quote, color = NiyyahColors.TextSecondary)
    }
}

@Composable
fun NiyyahNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = NiyyahRoutes.HOME,
        modifier = modifier,
    ) {
        composable(NiyyahRoutes.HOME) { com.salahlock.app.ui.home.HomeScreen() }
        composable(NiyyahRoutes.PRAYER) {
            com.salahlock.app.ui.prayers.PrayerScreen(
                onOpenSettings = { navController.navigate(NiyyahRoutes.SETTINGS) },
            )
        }
        composable(NiyyahRoutes.SALAH_LOCK) {
            com.salahlock.app.ui.lock.SalahLockScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            )
        }
        composable(NiyyahRoutes.QURAN) {
            com.salahlock.app.ui.quran.QuranScreen(
                onOpenBookmarks = { navController.navigate(NiyyahRoutes.BOOKMARKS) },
                onOpenCollections = { navController.navigate(NiyyahRoutes.COLLECTIONS) },
            )
        }
        composable(NiyyahRoutes.KNOWLEDGE) { com.salahlock.app.ui.knowledge.KnowledgeScreen() }
        composable(NiyyahRoutes.HADITH) { PlaceholderScreen("Hadith") }
        composable(NiyyahRoutes.AZKAR) { PlaceholderScreen("Azkar") }
        composable(NiyyahRoutes.QIBLA) { PlaceholderScreen("Qibla") }
        composable(NiyyahRoutes.COLLECTIONS) { PlaceholderScreen("Collections") }
        composable(NiyyahRoutes.BOOKMARKS) { PlaceholderScreen("Bookmarks") }
        composable(NiyyahRoutes.MONTHLY_REFLECTION) { PlaceholderScreen("Monthly Reflection") }
        composable(NiyyahRoutes.PROFILE) { PlaceholderScreen("Profile") }
        composable(NiyyahRoutes.SETTINGS) { PlaceholderScreen("Settings") }
    }
}
