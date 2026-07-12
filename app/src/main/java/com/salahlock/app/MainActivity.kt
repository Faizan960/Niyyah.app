package com.salahlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salahlock.app.theme.MotionTokens
import com.salahlock.app.theme.SalahLockTheme
import com.salahlock.app.ui.blacklist.AppBlacklistScreen
import com.salahlock.app.ui.home.HomeScreen
import com.salahlock.app.ui.knowledge.AzkarReaderScreen
import com.salahlock.app.ui.knowledge.CollectionBooksScreen
import com.salahlock.app.ui.knowledge.HadithReaderScreen
import com.salahlock.app.ui.knowledge.KnowledgeScreen
import com.salahlock.app.ui.navigation.BottomNavItems
import com.salahlock.app.ui.navigation.FloatingBottomNavigationBar
import com.salahlock.app.ui.navigation.Screen
import com.salahlock.app.ui.onboarding.OnboardingScreen
import com.salahlock.app.ui.masjid.LocalMasjidScreen
import com.salahlock.app.ui.profile.ProfileScreen
import com.salahlock.app.ui.qibla.QiblaScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = LocalContext.current.applicationContext as SalahLockApplication
            val themePreference by app.userPreferences.themePreference.collectAsStateWithLifecycle(
                initialValue = com.salahlock.app.data.preferences.UserPreferences.ThemePreference.SYSTEM
            )

            val themeMode = when (themePreference) {
                com.salahlock.app.data.preferences.UserPreferences.ThemePreference.SYSTEM -> {
                    if (androidx.compose.foundation.isSystemInDarkTheme()) com.salahlock.app.theme.ThemeMode.DARK
                    else com.salahlock.app.theme.ThemeMode.LIGHT
                }
                com.salahlock.app.data.preferences.UserPreferences.ThemePreference.LIGHT -> com.salahlock.app.theme.ThemeMode.LIGHT
                com.salahlock.app.data.preferences.UserPreferences.ThemePreference.DARK -> com.salahlock.app.theme.ThemeMode.DARK
                com.salahlock.app.data.preferences.UserPreferences.ThemePreference.AMOLED -> com.salahlock.app.theme.ThemeMode.AMOLED
            }

            SalahLockTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SalahLockApp()
                }
            }
        }
    }
}

@Composable
fun SalahLockApp() {
    val context = LocalContext.current
    val app = context.applicationContext as SalahLockApplication
    val scope = rememberCoroutineScope()

    val onboardingDone by app.userPreferences.onboardingDone
        .collectAsStateWithLifecycle(initialValue = null)

    AnimatedContent(
        targetState = onboardingDone,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "root_nav",
    ) { done ->
        when (done) {
            null -> Box(modifier = Modifier.fillMaxSize())
            false -> OnboardingScreen(
                onComplete = {
                    scope.launch {
                        app.userPreferences.setOnboardingDone(true)
                        app.blacklistRepository.populateInstalledApps()
                    }
                }
            )
            true -> {
                // Sprint A.1 — show the daily interstitial when the user voluntarily
                // reaches Home. Fail-open + 24h-gated inside the manager. Never runs
                // for onboarding (different branch) or lock/verification (separate Activity).
                val activity = LocalContext.current as? android.app.Activity
                LaunchedEffect(Unit) {
                    // Sprint A.2 — infra stays live (init + preload), but display is
                    // gated by the master flag. ADS_ENABLED=false ⇒ no ad ever shown.
                    if (BuildConfig.ADS_ENABLED && activity != null) {
                        com.salahlock.app.ads.DailyInterstitialManager.maybeShow(activity, app)
                    }
                }
                MainAppContent()
            }
        }
    }
}

/**
 * Root navigation host.
 *
 * Architecture:
 *  "main" destination → [MainTabsContent] — HorizontalPager with 5 tabs, swipeable.
 *  Deep screens (Profile, Blacklist, HadithReader, …) push on top via NavController.
 *
 * Swipe between tabs is handled by the pager.
 * Bottom nav taps animate the pager to the selected page (spring, no bounce).
 * Deep-screen transitions use fade-through at 250ms (MotionTokens.Normal).
 */
@Composable
fun MainAppContent() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            fadeIn(MotionTokens.normalTween())
        },
        exitTransition = {
            fadeOut(MotionTokens.fastTween())
        },
        popEnterTransition = {
            fadeIn(MotionTokens.normalTween())
        },
        popExitTransition = {
            fadeOut(MotionTokens.fastTween())
        },
    ) {
        // ── MAIN PAGER HOST ─────────────────────────────────────────────────────
        composable("main") {
            MainTabsContent(navController = navController)
        }

        // ── DEEP SCREENS ────────────────────────────────────────────────────────
        // Profile opens with a Google-style slide-from-left + fade + slight scale
        // (MotionTokens.Normal, no overshoot). Matches the left-edge swipe direction.
        composable(
            Screen.Profile.route,
            enterTransition = {
                slideInHorizontally(MotionTokens.normalTween()) { -it / 4 } +
                    fadeIn(MotionTokens.normalTween()) +
                    scaleIn(MotionTokens.normalTween(), initialScale = 0.96f)
            },
            popExitTransition = {
                slideOutHorizontally(MotionTokens.normalTween()) { -it / 4 } +
                    fadeOut(MotionTokens.fastTween()) +
                    scaleOut(MotionTokens.normalTween(), targetScale = 0.96f)
            },
        ) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBlacklist = { navController.navigate(Screen.Blacklist.route) },
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        // ── SL-009: dedicated settings pages (reuse ProfileViewModel + sections) ──
        composable("settings/verification") {
            com.salahlock.app.ui.profile.VerificationSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/appearance") {
            com.salahlock.app.ui.profile.AppearanceSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/backup") {
            com.salahlock.app.ui.profile.BackupSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/app") {
            com.salahlock.app.ui.profile.AppSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/lock_per_prayer") {
            com.salahlock.app.ui.profile.LockPerPrayerScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/permissions") {
            com.salahlock.app.ui.profile.PermissionStatusScreen(onBack = { navController.popBackStack() })
        }
        composable("help_center") {
            com.salahlock.app.ui.profile.HelpCenterScreen(onBack = { navController.popBackStack() })
        }
        // Monthly Spiritual Reflection — private frozen monthly reports (Style E, AMOLED)
        composable("monthly_reflections") {
            com.salahlock.app.ui.reflection.MonthlyReflectionScreen(onBack = { navController.popBackStack() })
        }
        composable("local_masjid_setup") {
            LocalMasjidScreen(onBack = { navController.popBackStack() })
        }
        // Qibla — folded out of the tab bar (Sprint N.3); opened from the Home card.
        composable(Screen.Qibla.route) {
            QiblaScreen()
        }
        composable(Screen.Blacklist.route) {
            AppBlacklistScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("collection_books/{collection}") { backStackEntry ->
            val collection = backStackEntry.arguments?.getString("collection") ?: ""
            CollectionBooksScreen(
                collectionName = collection,
                onNavigateToReader = { coll, book ->
                    navController.navigate("hadith_reader/book/$coll/$book")
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable("hadith_reader/topic/{topic}") { backStackEntry ->
            val topic = backStackEntry.arguments?.getString("topic") ?: ""
            HadithReaderScreen(topic = topic, language = "eng", onBack = { navController.popBackStack() })
        }
        composable("hadith_reader/book/{collection}/{bookNumber}") { backStackEntry ->
            val collection = backStackEntry.arguments?.getString("collection") ?: ""
            val bookNumber = backStackEntry.arguments?.getString("bookNumber") ?: ""
            HadithReaderScreen(
                collection = collection, bookNumber = bookNumber, language = "eng",
                onBack = { navController.popBackStack() },
            )
        }
        composable("hadith_reader/single/{hadithId}") { backStackEntry ->
            val encodedId = backStackEntry.arguments?.getString("hadithId") ?: ""
            val hadithId = java.net.URLDecoder.decode(encodedId, "UTF-8")
            HadithReaderScreen(hadithId = hadithId, language = "eng", onBack = { navController.popBackStack() })
        }
        composable("azkar_reader/{category}") { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category") ?: ""
            AzkarReaderScreen(category = category, onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Five-tab pager content. Placed at the "main" NavHost destination.
 *
 * Pages:  0=Home (prayer dashboard)  1=Lock Apps  2=Knowledge
 *
 * [beyondViewportPageCount]=1 keeps adjacent pages alive to preserve scroll state
 * and ViewModel state across swipes.
 */
@Composable
private fun MainTabsContent(navController: androidx.navigation.NavController) {
    val pagerState = rememberPagerState(pageCount = { BottomNavItems.size })
    val scope = rememberCoroutineScope()

    // Left-edge swipe → open Profile (Gmail / Google Drive style).
    // Only arms when the gesture STARTS within 32dp of the left edge, and consumes
    // on the Initial pass so the HorizontalPager never sees edge drags. Normal swipes
    // (starting beyond 32dp) fall through to the pager untouched.
    val density = LocalDensity.current
    val edgePx = with(density) { 32.dp.toPx() }
    val triggerPx = with(density) { 56.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.position.x > edgePx) return@awaitEachGesture
                    var dx = 0f
                    var dy = 0f
                    var fired = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dx += change.positionChange().x
                        dy += change.positionChange().y
                        if (!fired && dx > triggerPx && dx > kotlin.math.abs(dy)) {
                            fired = true
                            change.consume()
                            navController.navigate(Screen.Profile.route)
                        }
                        if (change.changedToUp() || !change.pressed) break
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = true,
            key = { it },
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                    onNavigateToBlacklist = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onNavigateToQibla = { navController.navigate(Screen.Qibla.route) },
                    onNavigateToAzkar = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onNavigateToLocalMasjid = { navController.navigate("local_masjid_setup") },
                )
                // Lock Apps tab — reuses AppBlacklistScreen (no back arrow in tab mode)
                1 -> AppBlacklistScreen(onNavigateBack = null)
                2 -> KnowledgeScreen(
                    onNavigateToHadithTopic = { topic ->
                        navController.navigate("hadith_reader/topic/$topic")
                    },
                    onNavigateToCollection = { collection ->
                        navController.navigate("collection_books/$collection")
                    },
                    onNavigateToAzkarReader = { category ->
                        navController.navigate("azkar_reader/$category")
                    },
                    onNavigateToSingleHadith = { hadithId ->
                        navController.navigate("hadith_reader/single/${java.net.URLEncoder.encode(hadithId, "UTF-8")}")
                    },
                )
            }
        }

        // Icon-only floating nav — synced with pager position
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            FloatingBottomNavigationBar(
                selectedIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        }
    }
}
