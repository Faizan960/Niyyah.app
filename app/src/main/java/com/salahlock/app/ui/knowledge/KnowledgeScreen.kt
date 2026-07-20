package com.salahlock.app.ui.knowledge

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.db.entity.HadithEntity
import com.salahlock.app.data.db.entity.collectionDisplayName
import com.salahlock.app.data.db.entity.formattedReference
import com.salahlock.app.theme.ElevatedSurface
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.GoldAccent
import com.salahlock.app.theme.MotionTokens
import com.salahlock.app.theme.MutedSage
import com.salahlock.app.theme.WarmStone
import kotlinx.coroutines.delay

@Composable
fun KnowledgeScreen(
    viewModel: KnowledgeViewModel = viewModel(),
    onNavigateToHadithTopic: (String) -> Unit,
    onNavigateToCollection: (String) -> Unit,
    onNavigateToAzkarReader: (String) -> Unit,
    onNavigateToSingleHadith: (String) -> Unit = {},
    // BM-010 — Knowledge is the gateway to the restored library modules.
    onNavigateToQuran: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToCollections: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 48.dp),
        ) {
            val titleAlpha by animateFloatAsState(
                targetValue = if (state.isSearchActive) 0f else 1f,
                animationSpec = MotionTokens.normalTween(),
                label = "knowledgeTitleAlpha",
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .alpha(titleAlpha),
            ) {
                Text(
                    text = "Knowledge",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Explore the depths of Islamic heritage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Search Bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search hadiths, topics, or references…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.clearSearch()
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Search hint
            if (state.searchQuery.isEmpty()) {
                Text(
                    text = "Try: patience, salah, Bukhari 647, Muslim 178",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Sync Error Banner ─────────────────────────────────────────────
            if (state.syncError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.syncError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.retrySyncData() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Library (BM-010): gateway to Quran / Bookmarks / Collections ──
            if (!state.isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LibraryTile(
                        title = "Quran",
                        subtitle = "114 Surahs",
                        icon = Icons.Outlined.AutoStories,
                        onClick = onNavigateToQuran,
                        modifier = Modifier.weight(1f),
                    )
                    LibraryTile(
                        title = "Bookmarks",
                        subtitle = "${state.bookmarkCount} saved",
                        icon = Icons.Outlined.BookmarkBorder,
                        onClick = onNavigateToBookmarks,
                        modifier = Modifier.weight(1f),
                    )
                    LibraryTile(
                        title = "Collections",
                        subtitle = "${state.collectionCount} made",
                        icon = Icons.Outlined.CollectionsBookmark,
                        onClick = onNavigateToCollections,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Tab Selector (only shown when not searching) ──────────────────
            if (!state.isSearchActive) {
                SegmentedControl(
                    selectedTab = state.selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Content Area ──────────────────────────────────────────────────
            Crossfade(
                targetState = state.isSearchActive,
                animationSpec = MotionTokens.normalTween(),
                label = "search_crossfade",
            ) { inSearchMode ->
                if (inSearchMode) {
                    SearchResultsSection(
                        results = state.searchResults,
                        isSearching = state.isSearching,
                        query = state.searchQuery,
                        onResultClick = { result -> onNavigateToSingleHadith(result.hadith.id) },
                    )
                } else {
                    Crossfade(
                        targetState = state.selectedTab,
                        animationSpec = MotionTokens.normalTween(),
                        label = "tab_crossfade",
                    ) { tab ->
                        when (tab) {
                            KnowledgeTab.HADITH -> HadithContent(
                                state = state,
                                onNavigateToTopic = onNavigateToHadithTopic,
                                onNavigateToCollection = onNavigateToCollection,
                            )
                            KnowledgeTab.AZKAR -> AzkarContent(
                                state = state,
                                onNavigateToReader = onNavigateToAzkarReader,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Search Results ────────────────────────────────────────────────────────────

@Composable
private fun SearchResultsSection(
    results: List<HadithSearchResult>,
    isSearching: Boolean,
    query: String,
    onResultClick: (HadithSearchResult) -> Unit,
) {
    when {
        isSearching -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = EmeraldPrimary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Searching…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        results.isEmpty() && query.isNotBlank() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        "No results for: $query",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Try a different keyword, or use Collection + Number format like: Bukhari 647, Muslim 178.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        else -> {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        text = "${results.size} result${if (results.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                itemsIndexed(results, key = { _, r -> r.hadith.id }) { index, result ->
                    StaggeredAppear(index = index) {
                        SearchResultCard(result = result, onClick = { onResultClick(result) })
                    }
                }
            }
        }
    }
}

/**
 * Plays a one-time fade + 16dp rise on first appearance, staggered by [index].
 * Uses graphicsLayer (no layout pass) so it never causes layout shift.
 * LaunchedEffect(Unit) ensures it plays once per item instance, not on every scroll.
 */
@Composable
private fun StaggeredAppear(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MotionTokens.staggerDelayFor(index).toLong())
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MotionTokens.normalTween(),
        label = "staggerAlpha",
    )
    val rise by animateFloatAsState(
        targetValue = if (shown) 0f else 32f,
        animationSpec = MotionTokens.normalTween(),
        label = "staggerRise",
    )
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha; translationY = rise }) {
        content()
    }
}

/** Press-scale feedback: 1.0 → 0.97 while pressed, springs back on release. No bounce. */
@Composable
private fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = MotionTokens.fastTween(),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
private fun SearchResultCard(result: HadithSearchResult, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surface else ElevatedSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (isLight) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else null,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Reference header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.hadith.formattedReference,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = GoldAccent,
                )
                Text(
                    text = result.bookTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmStone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hadith preview with match context
            Text(
                text = result.matchHighlight,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                lineHeight = 22.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Tap to read full hadith →",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Existing Content Sections ─────────────────────────────────────────────────

@Composable
private fun SegmentedControl(selectedTab: KnowledgeTab, onTabSelected: (KnowledgeTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KnowledgeTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) EmeraldPrimary else Color.Transparent)
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun HadithContent(
    state: KnowledgeUiState,
    onNavigateToTopic: (String) -> Unit,
    onNavigateToCollection: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionTitle("Daily Hadith")
            DailyHadithCard(state)
        }
        item {
            SectionTitle("Topics")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.categories) { category ->
                    TopicCard(title = category, onClick = { onNavigateToTopic(category) })
                }
            }
        }
        item {
            SectionTitle("Collections")
            data class CollectionMeta(val display: String, val internal: String, val hadiths: String, val books: String)
            val collections = listOf(
                CollectionMeta("Sahih al-Bukhari", "bukhari", "7,563 Hadiths", "114 Books"),
                CollectionMeta("Sahih Muslim", "muslim", "7,470 Hadiths", "56 Books"),
            )
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                collections.forEach { meta ->
                    CollectionCard(
                        title = meta.display,
                        hadiths = meta.hadiths,
                        books = meta.books,
                        onClick = { onNavigateToCollection(meta.internal) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AzkarContent(state: KnowledgeUiState, onNavigateToReader: (String) -> Unit) {
    // SL-007 — categories as a 2-column tile grid (icon + title) instead of a list.
    // Navigation unchanged: tap → same onNavigateToReader(category).
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.azkarCategories.size) { i ->
            val category = state.azkarCategories[i]
            AzkarCategoryCard(title = category, onClick = { onNavigateToReader(category) })
        }
    }
}

/** Icon for an azkar category tile (keyword match — categories are dynamic strings). */
private fun azkarEmoji(category: String): String {
    val c = category.lowercase()
    return when {
        "morning" in c -> "🌅"
        "evening" in c -> "🌇"
        "sleep" in c || "night" in c -> "🌙"
        "wake" in c -> "⏰"
        "prayer" in c || "salah" in c -> "🕌"
        "food" in c || "eat" in c -> "🍽️"
        "travel" in c -> "🧳"
        "home" in c || "house" in c -> "🏠"
        "protection" in c -> "🛡️"
        "forgive" in c || "istighfar" in c -> "🤲"
        "praise" in c || "tasbih" in c -> "📿"
        else -> "☪️"
    }
}

/** BM-010 — compact gateway tile to a restored library module. */
@Composable
private fun LibraryTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surface else ElevatedSurface

    Card(
        modifier = modifier
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (isLight) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) else null,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmeraldPrimary.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MutedSage,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MutedSage,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
    )
}

@Composable
private fun DailyHadithCard(state: KnowledgeUiState) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surface else ElevatedSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (isLight) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                 else androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            if (state.dailyHadith != null) {
                val hadith = state.dailyHadith

                // Gold citation chip at top
                Surface(
                    color = GoldAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = hadith.formattedReference,
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (hadith.arabicText.isNotEmpty()) {
                    Text(
                        text = hadith.arabicText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 40.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = GoldAccent.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Text(
                    text = hadith.translationText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 28.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = hadith.collectionDisplayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmStone,
                    )
                    Text(
                        text = "READ HADITH →",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }
            } else if (state.isSyncing) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = EmeraldPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("Syncing hadith library…", style = MaterialTheme.typography.bodySmall, color = WarmStone)
                    }
                }
            } else {
                Text(
                    "No hadiths available. Connect to the internet to load the hadith library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarmStone,
                )
            }
        }
    }
}

/** Maps topic category names to Material icons for visual hierarchy. */
private fun topicIcon(title: String) = when (title.lowercase()) {
    "salah", "prayer" -> Icons.Outlined.Mosque
    "faith", "iman" -> Icons.Outlined.AutoAwesome
    "knowledge", "ilm" -> Icons.Outlined.MenuBook
    "charity", "zakat", "sadaqah" -> Icons.Outlined.VolunteerActivism
    "family", "marriage" -> Icons.Outlined.FamilyRestroom
    "character", "manners", "akhlaq" -> Icons.Outlined.SelfImprovement
    "patience", "sabr" -> Icons.Outlined.HourglassEmpty
    "justice", "adl" -> Icons.Outlined.Balance
    "dua", "supplication" -> Icons.Outlined.EmojiPeople
    "brotherhood", "unity" -> Icons.Outlined.Diversity3
    "quran" -> Icons.Outlined.AutoStories
    "fasting", "sawm", "ramadan" -> Icons.Outlined.NightShelter
    "hajj", "pilgrimage" -> Icons.Outlined.NearMe
    "tawbah", "repentance" -> Icons.Outlined.Refresh
    else -> Icons.Outlined.Star
}

@Composable
private fun TopicCard(title: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surface else ElevatedSurface

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(104.dp)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (isLight) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) else null,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = topicIcon(title),
                contentDescription = null,
                tint = EmeraldPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun CollectionCard(title: String, hadiths: String = "", books: String = "", onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surface else ElevatedSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (isLight) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else null,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                if (hadiths.isNotEmpty() || books.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOf(hadiths, books).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmStone,
                    )
                }
            }
            Icon(
                Icons.Outlined.LibraryBooks,
                contentDescription = null,
                tint = if (isLight) MaterialTheme.colorScheme.onSurfaceVariant else WarmStone.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AzkarCategoryCard(title: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surface else ElevatedSurface

    // SL-007 — square-ish tile: icon on top, title below.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (isLight) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) else null,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(EmeraldPrimary.copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(azkarEmoji(title), fontSize = 26.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = "Azkar",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSage,
            )
        }
    }
}
