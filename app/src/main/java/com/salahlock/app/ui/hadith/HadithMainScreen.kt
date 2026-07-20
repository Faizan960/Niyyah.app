package com.salahlock.app.ui.hadith

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.db.entity.collectionDisplayName
import com.salahlock.app.data.db.entity.formattedReference
import com.salahlock.app.data.db.entity.globalNumber
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.GoldAccent
import com.salahlock.app.theme.MutedSage
import com.salahlock.app.theme.WarmStone
import com.salahlock.app.ui.components.LibrarySectionHeader
import com.salahlock.app.ui.components.PremiumSearchBar
import com.salahlock.app.ui.components.libraryCardColor
import com.salahlock.app.ui.components.pressScale
import com.salahlock.app.ui.knowledge.HadithSearchResult
import com.salahlock.app.ui.knowledge.KnowledgeViewModel

/**
 * BM-011.3 — Hadith main tab (reference RIGHT). Reuses [KnowledgeViewModel] /
 * KnowledgeRepository (Bukhari + Muslim in Room, existing search) — no new
 * backend. Header, search, discovery cards mapped to real destinations, real
 * Daily Hadith, and Popular Topics with real per-topic counts.
 *
 * Note on the reference "Sahih / Authentic" card: the bundled corpus is
 * Sahih al-Bukhari + Sahih Muslim with no per-hadith grading field, so an
 * authenticity filter would be a fake control. The discovery row instead
 * exposes the two real Sahih collections plus Topics and Bookmarks.
 */
@Composable
fun HadithMainScreen(
    viewModel: KnowledgeViewModel = viewModel(),
    onOpenCollectionBooks: (String) -> Unit,
    onOpenTopics: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onOpenSingleHadith: (String) -> Unit,
    onOpenBookmarks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 24.dp, bottom = 112.dp),
        ) {
            item(key = "header") {
                HadithHeader()
                Spacer(Modifier.height(20.dp))
            }

            item(key = "search") {
                PremiumSearchBar(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = "Search hadiths, topics, books…",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    onClear = { viewModel.clearSearch(); focusManager.clearFocus() },
                )
                Spacer(Modifier.height(16.dp))
            }

            if (state.isSearchActive) {
                searchResults(
                    results = state.searchResults,
                    isSearching = state.isSearching,
                    query = state.searchQuery,
                    onClick = { onOpenSingleHadith(it.hadith.id) },
                )
            } else {
                item(key = "discovery") {
                    DiscoveryRow(
                        onBukhari = { onOpenCollectionBooks("bukhari") },
                        onMuslim = { onOpenCollectionBooks("muslim") },
                        onTopics = onOpenTopics,
                        onBookmarks = onOpenBookmarks,
                    )
                    Spacer(Modifier.height(24.dp))
                }

                if (state.syncError != null) {
                    item(key = "sync_error") {
                        SyncErrorBanner(message = state.syncError!!, onRetry = { viewModel.retrySyncData() })
                        Spacer(Modifier.height(16.dp))
                    }
                }

                item(key = "daily") {
                    DailyHadithCard(
                        state = state,
                        onRead = { id -> onOpenSingleHadith(id) },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                }

                item(key = "topics_header") {
                    LibrarySectionHeader(
                        title = "Popular Topics",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        trailingText = "See All",
                        onTrailingClick = onOpenTopics,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                item(key = "topics_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.categories, key = { it }) { topic ->
                            TopicCard(
                                topic = topic,
                                count = state.topicCounts[topic],
                                onClick = { onOpenTopic(topic) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HadithHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Hadith",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Learn from the sayings &\nteachings of the Prophet ﷺ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            GoldAccent.copy(alpha = if (isLight) 0.22f else 0.34f),
                            GoldAccent.copy(alpha = if (isLight) 0.06f else 0.10f),
                        ),
                    ),
                )
                .border(1.dp, GoldAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "ٱلْحَدِيث",
                style = MaterialTheme.typography.titleLarge,
                color = GoldAccent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DiscoveryRow(
    onBukhari: () -> Unit,
    onMuslim: () -> Unit,
    onTopics: () -> Unit,
    onBookmarks: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DiscoveryCard("Bukhari", "Authentic", Icons.Outlined.MenuBook, onBukhari, Modifier.weight(1f))
        DiscoveryCard("Muslim", "Authentic", Icons.Outlined.AutoStories, onMuslim, Modifier.weight(1f))
        DiscoveryCard("Topics", "Explore", Icons.Outlined.Category, onTopics, Modifier.weight(1f))
        DiscoveryCard("Saved", "Bookmarks", Icons.Outlined.Bookmark, onBookmarks, Modifier.weight(1f))
    }
}

@Composable
private fun DiscoveryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(18.dp))
            .background(libraryCardColor())
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MutedSage,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DailyHadithCard(
    state: com.salahlock.app.ui.knowledge.KnowledgeUiState,
    onRead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hadith = state.dailyHadith
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(libraryCardColor())
            .border(1.dp, GoldAccent.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .padding(24.dp),
    ) {
        when {
            hadith != null -> Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LightMode, null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Daily Hadith",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.dp, GoldAccent.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            hadith.globalNumber,
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldAccent,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    hadith.formattedReference,
                    style = MaterialTheme.typography.labelLarge,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "“${hadith.translationText.trim()}”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 28.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    hadith.collectionDisplayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = WarmStone,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = { onRead(hadith.id) },
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                    ) {
                        Text("Read Hadith →", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            state.isSyncing -> Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = EmeraldPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Loading hadith library…", style = MaterialTheme.typography.bodyMedium, color = WarmStone)
            }

            else -> Text(
                "No hadiths available yet. Connect to the internet to load the library.",
                style = MaterialTheme.typography.bodyMedium,
                color = WarmStone,
            )
        }
    }
}

@Composable
private fun TopicCard(topic: String, count: Int?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(140.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(18.dp))
            .background(libraryCardColor())
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(topicEmoji(topic), fontSize = 22.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            topic,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null && count > 0) {
            Text(
                "$count Hadiths",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSage,
            )
        }
    }
}

private fun topicEmoji(topic: String): String = when (topic.lowercase()) {
    "faith" -> "⭐"
    "patience" -> "⏳"
    "gratitude" -> "💚"
    "prayer", "salah" -> "🕌"
    "charity" -> "🤲"
    "knowledge" -> "📖"
    "dua" -> "🙏"
    "family" -> "👪"
    "character" -> "✨"
    else -> "☪️"
}

@Composable
private fun SyncErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("Retry", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Inline hadith search results (reuses KnowledgeViewModel's real search). */
private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(
    results: List<HadithSearchResult>,
    isSearching: Boolean,
    query: String,
    onClick: (HadithSearchResult) -> Unit,
) {
    when {
        isSearching -> item(key = "searching") {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary, modifier = Modifier.size(28.dp))
            }
        }

        results.isEmpty() && query.isNotBlank() -> item(key = "no_results") {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "No results for “$query”",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Try a keyword, or Collection + Number like: Bukhari 647, Muslim 178.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> {
            item(key = "results_count") {
                Text(
                    "${results.size} result${if (results.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            itemsIndexed(results, key = { _, r -> r.hadith.id }) { _, result ->
                Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                    HadithResultCard(result, onClick = { onClick(result) })
                }
            }
        }
    }
}

@Composable
private fun HadithResultCard(result: HadithSearchResult, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(libraryCardColor())
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Text(
            result.hadith.formattedReference,
            style = MaterialTheme.typography.labelLarge,
            color = GoldAccent,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            result.matchHighlight,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            lineHeight = 22.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
