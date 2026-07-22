package com.salahlock.app.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.repository.Surah
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.GoldAccent
import com.salahlock.app.theme.MutedSage
import com.salahlock.app.ui.components.LibrarySectionHeader
import com.salahlock.app.ui.components.PremiumSearchBar
import com.salahlock.app.ui.components.pressScale

/**
 * BM-011.2 — Quran main tab (reference LEFT). Reuses [QuranViewModel] /
 * QuranRepository (quran.json + Room progress) — no new backend. Header,
 * premium search with a real Meccan/Medinan filter, Bookmarks/Collections
 * secondary access, real Continue Reading, and the All Surahs list.
 */
@Composable
fun QuranMainScreen(
    viewModel: QuranViewModel = viewModel(),
    onOpenSurah: (surah: Int, ayah: Int?) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenCollections: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var filterMenuOpen by remember { mutableStateOf(false) }

    val isBrowsing = state.searchQuery.isBlank() && state.revelationFilter == RevelationFilter.ALL

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = EmeraldPrimary) }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        state.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.loadSurahs() }) {
                        Text("Try again", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = 24.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item(key = "header") {
                    QuranHeader()
                    Spacer(Modifier.height(20.dp))
                }

                item(key = "search") {
                    Box {
                        PremiumSearchBar(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = "Search Surah or Ayah",
                            modifier = Modifier.padding(horizontal = 24.dp),
                            onClear = { viewModel.clearSearch(); focusManager.clearFocus() },
                            onTrailingClick = { filterMenuOpen = true },
                            trailingActive = state.revelationFilter != RevelationFilter.ALL,
                        )
                        // Revelation filter (real dataset field) — anchored under the filter icon.
                        Box(Modifier.align(Alignment.TopEnd).padding(end = 24.dp)) {
                            DropdownMenu(
                                expanded = filterMenuOpen,
                                onDismissRequest = { filterMenuOpen = false },
                            ) {
                                RevelationFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.label) },
                                        onClick = {
                                            viewModel.onRevelationFilterChanged(filter)
                                            filterMenuOpen = false
                                        },
                                        leadingIcon = {
                                            if (state.revelationFilter == filter) {
                                                Icon(Icons.Outlined.MenuBook, null, tint = EmeraldPrimary)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                item(key = "saved_access") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SecondaryAccessChip(
                            label = "Bookmarks",
                            icon = Icons.Outlined.Bookmark,
                            onClick = onOpenBookmarks,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryAccessChip(
                            label = "Collections",
                            icon = Icons.Outlined.CollectionsBookmark,
                            onClick = onOpenCollections,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }

                if (isBrowsing) {
                    item(key = "continue_header") {
                        LibrarySectionHeader(
                            title = "Continue Reading",
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    item(key = "continue_card") {
                        ContinueReadingCard(
                            target = state.continueReading,
                            onClick = {
                                val cr = state.continueReading
                                if (cr != null) onOpenSurah(cr.surah.number, cr.lastAyah)
                                else onOpenSurah(1, null)
                            },
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Spacer(Modifier.height(28.dp))
                    }
                    item(key = "all_surahs_header") {
                        LibrarySectionHeader(
                            title = "All Surahs",
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                } else {
                    item(key = "results_header") {
                        Text(
                            text = "${state.filteredSurahs.size} surah${if (state.filteredSurahs.size == 1) "" else "s"}" +
                                if (state.revelationFilter != RevelationFilter.ALL) " · ${state.revelationFilter.label}" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (state.filteredSurahs.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No surahs match “${state.searchQuery}”",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Try an English or Arabic name, or a surah number like 36.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(state.filteredSurahs, key = { it.number }) { surah ->
                        SurahListItem(surah = surah, onClick = { onOpenSurah(surah.number, null) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranHeader() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            text = "Quran",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Read, reflect & connect with\nthe words of Allah",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun SecondaryAccessChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ContinueReadingCard(
    target: ContinueReading?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    // Emerald-tinted gradient card matching the reference (theme-aware).
    val gradient = if (isLight) {
        Brush.linearGradient(listOf(EmeraldPrimary.copy(alpha = 0.12f), EmeraldPrimary.copy(alpha = 0.05f)))
    } else {
        Brush.linearGradient(listOf(EmeraldPrimary.copy(alpha = 0.35f), EmeraldPrimary.copy(alpha = 0.12f)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .border(1.dp, EmeraldPrimary.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        if (target == null) {
            Column {
                Text(
                    "Begin your journey",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Open Al-Fatihah and start reading. Your place is saved automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "START READING →",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            val pct = (target.lastAyah * 100 / target.surah.ayahCount).coerceIn(0, 100)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        target.surah.transliteration,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Verse ${target.lastAyah} · Juz ${target.surah.ayahs.getOrNull(target.lastAyah - 1)?.juz ?: target.surah.startJuz}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = EmeraldPrimary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "$pct%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GoldAccent.copy(alpha = 0.14f))
                        .border(1.dp, GoldAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        target.surah.arabicName.take(6),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = com.salahlock.app.theme.ArabicUi,
                        color = GoldAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
private fun SurahListItem(surah: Surah, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmeraldPrimary.copy(alpha = 0.10f))
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    surah.number.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    surah.transliteration,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    surah.englishName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    surah.arabicName,
                    style = MaterialTheme.typography.titleMedium,
                    // BM-TYPOGRAPHY §14 — surah Arabic name uses the platform Arabic font.
                    fontFamily = com.salahlock.app.theme.ArabicUi,
                    color = GoldAccent.copy(alpha = 0.9f),
                    maxLines = 1,
                )
                Text(
                    "${surah.ayahCount} Ayahs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSage,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 84.dp, end = 24.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            thickness = 0.5.dp,
        )
    }
}
