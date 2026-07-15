package com.salahlock.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.salahlock.app.ui.components.NiyyahBottomNav
import com.salahlock.app.ui.home.HomeScreen
import com.salahlock.app.ui.knowledge.KnowledgeScreen
import com.salahlock.app.ui.prayers.PrayerScreen
import com.salahlock.app.ui.quran.QuranScreen
import kotlinx.coroutines.launch

/**
 * The tab shell — a [HorizontalPager] over the four top-level screens with the
 * floating [NiyyahBottomNav] overlaid. Restores the premium swipe navigation
 * (BM-007): horizontal swipe moves between tabs, tapping a tab animates to it,
 * and the bottom-nav dot/tint track the pager fraction so both stay in sync.
 *
 * Deep destinations (readers, collections, profile, settings, …) live in
 * [NiyyahNavHost] and are reached through [navController]; navigating to one of
 * those replaces this shell, so the bottom nav is only ever shown on the tabs.
 */
private val TAB_PAGES = 4 // Home, Prayer, Quran, Knowledge

@Composable
fun MainShell(navController: NavHostController) {
    val pagerState = rememberPagerState(pageCount = { TAB_PAGES })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onOpenProfile = { navController.navigate(NiyyahRoutes.PROFILE) },
                )
                1 -> PrayerScreen(
                    onOpenSettings = { navController.navigate(NiyyahRoutes.SETTINGS) },
                )
                2 -> QuranScreen(
                    onOpenBookmarks = { navController.navigate(NiyyahRoutes.bookmarks("QURAN")) },
                    onOpenCollections = { navController.navigate(NiyyahRoutes.COLLECTIONS) },
                    onOpenSurahList = { navController.navigate(NiyyahRoutes.QURAN_SURAHS) },
                    onOpenReader = { surah, ayah ->
                        navController.navigate(NiyyahRoutes.quranReader(surah, ayah))
                    },
                )
                3 -> KnowledgeScreen(
                    onOpenTopic = { topic -> navController.navigate(NiyyahRoutes.hadithTopicReader(topic)) },
                    onOpenHadith = { id -> navController.navigate(NiyyahRoutes.hadithSingleReader(id)) },
                    onOpenBook = { collection, book ->
                        navController.navigate(NiyyahRoutes.hadithBookReader(collection, book))
                    },
                    onViewAll = { navController.navigate(NiyyahRoutes.HADITH) },
                )
            }
        }

        NiyyahBottomNav(
            pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 25.dp),
        )
    }
}
