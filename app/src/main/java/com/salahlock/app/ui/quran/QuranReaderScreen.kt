package com.salahlock.app.ui.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.repository.Ayah
import com.salahlock.app.theme.ElevatedSurface
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.GoldAccent
import com.salahlock.app.theme.WarmStone
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * BM-010.3 — Quran reader. Real Uthmani Arabic + Saheeh International English
 * from the preserved dataset. Reading position auto-saves (debounced scroll),
 * ayah/surah bookmarks persist via QuranDao, prev/next surah reload in place.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun QuranReaderScreen(
    surahNumber: Int,
    initialAyah: Int? = null,
    viewModel: QuranReaderViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(surahNumber) {
        viewModel.load(surahNumber, initialAyah)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.surah?.transliteration ?: "Quran",
                            fontWeight = FontWeight.Bold,
                        )
                        state.surah?.let { s ->
                            Text(
                                text = "${s.englishName} · ${s.revelationType} · ${s.ayahCount} verses",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.surah != null) {
                        IconButton(onClick = { viewModel.toggleSurahBookmark() }) {
                            Icon(
                                imageVector = if (state.isSurahBookmarked) Icons.Default.Bookmark
                                else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark surah",
                                tint = if (state.isSurahBookmarked) GoldAccent
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            if (state.surah != null) {
                SurahSwitcherBar(
                    hasPrevious = state.hasPrevious,
                    hasNext = state.hasNext,
                    onPrevious = { viewModel.openAdjacentSurah(-1) },
                    onNext = { viewModel.openAdjacentSurah(+1) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }

            state.error != null -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(32.dp),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                val surah = state.surah!!
                val listState = rememberLazyListState()

                // Scroll to the resume/bookmark target when the surah (re)loads.
                LaunchedEffect(surah.number, state.initialAyah) {
                    listState.scrollToItem((state.initialAyah - 1).coerceAtLeast(0))
                }

                // Auto-save reading position as the user scrolls (debounced).
                LaunchedEffect(surah.number) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .debounce(800)
                        .distinctUntilChanged()
                        .collect { index -> viewModel.saveReadingPosition(index + 1) }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                ) {
                    itemsIndexed(surah.ayahs, key = { _, a -> a.numberInSurah }) { _, ayah ->
                        AyahCard(
                            surahNumber = surah.number,
                            ayah = ayah,
                            isBookmarked = ayah.numberInSurah in state.bookmarkedAyahs,
                            onBookmarkToggle = { viewModel.toggleAyahBookmark(ayah.numberInSurah) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AyahCard(
    surahNumber: Int,
    ayah: Ayah,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surface else ElevatedSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (isLight) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else null,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = ayah.arabic,
                style = MaterialTheme.typography.headlineSmall,
                // BM-TYPOGRAPHY §15 — Quran Arabic is pinned to the platform Arabic font
                // and its original weight, never the Latin Manrope migration.
                fontFamily = com.salahlock.app.theme.ArabicUi,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 44.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = GoldAccent.copy(alpha = 0.15f), thickness = 0.5.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = ayah.english,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = EmeraldPrimary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "$surahNumber:${ayah.numberInSurah} · Juz ${ayah.juz}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark
                        else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark verse",
                        tint = if (isBookmarked) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SurahSwitcherBar(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrevious, enabled = hasPrevious) {
                Text(
                    "← Previous",
                    color = if (hasPrevious) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onNext, enabled = hasNext) {
                Text(
                    "Next →",
                    color = if (hasNext) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
