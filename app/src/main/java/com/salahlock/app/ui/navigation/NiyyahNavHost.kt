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
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PRAYER = "prayer"
    const val SALAH_LOCK = "salah_lock"
    const val QURAN = "quran"
    const val QURAN_SURAHS = "quran_surahs"
    const val QURAN_READER = "quran_reader/{surahNumber}?ayah={ayah}"
    fun quranReader(surahNumber: Int, ayah: Int = 1) = "quran_reader/$surahNumber?ayah=$ayah"
    const val KNOWLEDGE = "knowledge"
    const val HADITH = "hadith"
    const val HADITH_BOOKS = "hadith_books/{collection}?name={name}"
    const val HADITH_READER = "hadith_reader?collection={collection}&book={book}&topic={topic}&hadithId={hadithId}"
    fun hadithBooks(collection: String, name: String) = "hadith_books/$collection?name=$name"
    fun hadithBookReader(collection: String, book: String) = "hadith_reader?collection=$collection&book=$book"
    fun hadithTopicReader(topic: String) = "hadith_reader?topic=$topic"
    fun hadithSingleReader(hadithId: String) = "hadith_reader?hadithId=$hadithId"
    const val AZKAR = "azkar"
    const val AZKAR_READER = "azkar_reader/{category}"
    fun azkarReader(category: String) = "azkar_reader/${android.net.Uri.encode(category)}"
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
        startDestination = NiyyahRoutes.SPLASH,
        modifier = modifier,
    ) {
        composable(NiyyahRoutes.SPLASH) {
            com.salahlock.app.ui.splash.SplashScreen(
                onFinished = { onboardingDone ->
                    val target = if (onboardingDone) NiyyahRoutes.HOME else NiyyahRoutes.ONBOARDING
                    navController.navigate(target) {
                        popUpTo(NiyyahRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(NiyyahRoutes.ONBOARDING) {
            com.salahlock.app.ui.onboarding.OnboardingScreen(
                onDone = {
                    navController.navigate(NiyyahRoutes.HOME) {
                        popUpTo(NiyyahRoutes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
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
                onOpenSurahList = { navController.navigate(NiyyahRoutes.QURAN_SURAHS) },
                onOpenReader = { surah, ayah -> navController.navigate(NiyyahRoutes.quranReader(surah, ayah)) },
            )
        }
        composable(NiyyahRoutes.QURAN_SURAHS) {
            com.salahlock.app.ui.quran.QuranSurahListScreen(
                onBack = { navController.popBackStack() },
                onOpenSurah = { surah -> navController.navigate(NiyyahRoutes.quranReader(surah)) },
            )
        }
        composable(
            route = NiyyahRoutes.QURAN_READER,
            arguments = listOf(
                androidx.navigation.navArgument("surahNumber") { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("ayah") {
                    type = androidx.navigation.NavType.IntType
                    defaultValue = 1
                },
            ),
        ) { entry ->
            com.salahlock.app.ui.quran.QuranReaderScreen(
                surahNumber = entry.arguments?.getInt("surahNumber") ?: 1,
                startAyah = entry.arguments?.getInt("ayah") ?: 1,
                onBack = { navController.popBackStack() },
            )
        }
        composable(NiyyahRoutes.KNOWLEDGE) {
            com.salahlock.app.ui.knowledge.KnowledgeScreen(
                onOpenTopic = { topic -> navController.navigate(NiyyahRoutes.hadithTopicReader(topic)) },
                onOpenHadith = { id -> navController.navigate(NiyyahRoutes.hadithSingleReader(id)) },
                onOpenBook = { collection, book ->
                    navController.navigate(NiyyahRoutes.hadithBookReader(collection, book))
                },
                onViewAll = { navController.navigate(NiyyahRoutes.HADITH) },
            )
        }
        composable(NiyyahRoutes.HADITH) {
            com.salahlock.app.ui.hadith.HadithScreen(
                onOpenCollection = { collection, name ->
                    navController.navigate(NiyyahRoutes.hadithBooks(collection, name))
                },
                onOpenTopic = { topic -> navController.navigate(NiyyahRoutes.hadithTopicReader(topic)) },
                onOpenHadith = { id -> navController.navigate(NiyyahRoutes.hadithSingleReader(id)) },
                onOpenBook = { collection, book ->
                    navController.navigate(NiyyahRoutes.hadithBookReader(collection, book))
                },
            )
        }
        composable(
            route = NiyyahRoutes.HADITH_BOOKS,
            arguments = listOf(
                androidx.navigation.navArgument("collection") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("name") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val collection = entry.arguments?.getString("collection") ?: "bukhari"
            com.salahlock.app.ui.hadith.HadithBooksScreen(
                collection = collection,
                displayName = entry.arguments?.getString("name")?.ifBlank { null }
                    ?: collection.replaceFirstChar { it.uppercase() },
                onBack = { navController.popBackStack() },
                onOpenBook = { c, book -> navController.navigate(NiyyahRoutes.hadithBookReader(c, book)) },
            )
        }
        composable(
            route = NiyyahRoutes.HADITH_READER,
            arguments = listOf("collection", "book", "topic", "hadithId").map { name ->
                androidx.navigation.navArgument(name) {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            },
        ) { entry ->
            com.salahlock.app.ui.hadith.HadithReaderScreen(
                collection = entry.arguments?.getString("collection"),
                bookNumber = entry.arguments?.getString("book"),
                topic = entry.arguments?.getString("topic"),
                hadithId = entry.arguments?.getString("hadithId"),
                onBack = { navController.popBackStack() },
            )
        }
        composable(NiyyahRoutes.AZKAR) {
            com.salahlock.app.ui.azkar.AzkarScreen(
                onOpenCategory = { category -> navController.navigate(NiyyahRoutes.azkarReader(category)) },
                onOpenFavorites = { navController.navigate(NiyyahRoutes.BOOKMARKS) },
            )
        }
        composable(
            route = NiyyahRoutes.AZKAR_READER,
            arguments = listOf(
                androidx.navigation.navArgument("category") { type = androidx.navigation.NavType.StringType },
            ),
        ) { entry ->
            com.salahlock.app.ui.azkar.AzkarReaderScreen(
                category = entry.arguments?.getString("category") ?: "Morning",
                onBack = { navController.popBackStack() },
            )
        }
        composable(NiyyahRoutes.QIBLA) { com.salahlock.app.ui.qibla.QiblaScreen() }
        composable(NiyyahRoutes.COLLECTIONS) { com.salahlock.app.ui.collections.CollectionsScreen() }
        composable(NiyyahRoutes.BOOKMARKS) { com.salahlock.app.ui.bookmarks.BookmarksScreen() }
        composable(NiyyahRoutes.MONTHLY_REFLECTION) { com.salahlock.app.ui.reflection.ReflectionScreen() }
        composable(NiyyahRoutes.PROFILE) {
            com.salahlock.app.ui.profile.ProfileScreen(
                onOpenCollections = { navController.navigate(NiyyahRoutes.COLLECTIONS) },
                onOpenBookmarks = { navController.navigate(NiyyahRoutes.BOOKMARKS) },
                onOpenSettings = { navController.navigate(NiyyahRoutes.SETTINGS) },
            )
        }
        composable(NiyyahRoutes.SETTINGS) { com.salahlock.app.ui.settings.SettingsScreen() }
    }
}
