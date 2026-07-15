package com.salahlock.app.ui.knowledge

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Knowledge library — Figma frame 1:767 (light), wired to real data (BM-006):
 * search runs over the hadith library, category chips open curated topic
 * collections, and the Continue Reading rail resumes real books in progress.
 */
private val TextBody = Color(0xFF45474E)
private val ChipBorder = Color(0xFFC5C6CE)
private val ChipFill = Color(0xFFF6F3F2)
private val TrackFill = Color(0xFFE5E2E1)
private val ProgressGold = Color(0xFFEEC064)

@Composable
fun KnowledgeScreen(
    onOpenTopic: (topic: String) -> Unit = {},
    onOpenHadith: (hadithId: String) -> Unit = {},
    onOpenBook: (collection: String, bookNumber: String) -> Unit = { _, _ -> },
    onViewAll: () -> Unit = {},
    viewModel: KnowledgeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        KnowledgeHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            SearchAndFilterSection(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                onClear = viewModel::clearSearch,
                isSearchActive = uiState.isSearchActive,
                isSearching = uiState.isSearching,
                results = uiState.searchResults,
                onOpenHadith = onOpenHadith,
                onOpenTopic = onOpenTopic,
            )
            if (!uiState.isSearchActive) {
                FeaturedCollectionCard(onExplore = { onOpenTopic("Knowledge") })
                ContinueReadingSection(
                    recentBooks = uiState.recentBooks,
                    onOpenBook = onOpenBook,
                    onViewAll = onViewAll,
                )
            }
        }
    }
}

/** Header — node 1:801. 64dp bar, hamburger / NIYYAH / bell, hairline bottom border. */
@Composable
private fun KnowledgeHeader() {
    Column(modifier = Modifier.fillMaxWidth().background(NiyyahColors.HeaderBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_menu),
                contentDescription = "Menu",
                tint = TextBody,
                modifier = Modifier.width(18.dp).height(12.dp),
            )
            Text(text = "NIYYAH", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = "Notifications",
                tint = TextBody,
                modifier = Modifier.width(16.dp).height(20.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NiyyahColors.Hairline),
        )
    }
}

/** Search input + topic filter chips — nodes 1:812 / 1:818. Live (BM-006). */
@Composable
private fun SearchAndFilterSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    isSearchActive: Boolean,
    isSearching: Boolean,
    results: List<HadithSearchResult>,
    onOpenHadith: (hadithId: String) -> Unit,
    onOpenTopic: (topic: String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(1.dp, NiyyahShapes.Chip, ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000))
                .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                .border(1.dp, ChipBorder, NiyyahShapes.Chip)
                .padding(start = 16.dp, end = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = NiyyahColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search the library...",
                        style = NiyyahType.Body,
                        color = NiyyahColors.TextSecondary,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = NiyyahType.Body.copy(color = NiyyahColors.TextPrimary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Clear search",
                    tint = NiyyahColors.TextSecondary,
                    modifier = Modifier
                        .clickable(onClick = onClear)
                        .size(12.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopicChip("All Topics", selected = true, onClick = {})
            TopicChip("Theology", selected = false, onClick = { onOpenTopic("Theology") })
            TopicChip("History", selected = false, onClick = { onOpenTopic("History") })
            TopicChip("Spirituality", selected = false, onClick = { onOpenTopic("Spirituality") })
            TopicChip("Jurisprudence", selected = false, onClick = { onOpenTopic("Jurisprudence") })
        }
        if (isSearchActive) {
            when {
                isSearching -> Text(
                    text = "Searching…",
                    style = NiyyahType.Body,
                    color = NiyyahColors.TextSecondary,
                )
                results.isEmpty() -> Text(
                    text = "Nothing in the library matched your search.",
                    style = NiyyahType.Body,
                    color = NiyyahColors.TextSecondary,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    results.take(30).forEach { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                                .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                                .clickable { onOpenHadith(result.hadith.id) }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = result.bookTitle,
                                style = NiyyahType.LabelUppercase.copy(fontSize = 11.sp),
                                color = TextBody,
                            )
                            Text(
                                text = result.matchHighlight,
                                style = NiyyahType.Body,
                                color = NiyyahColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) NiyyahColors.Navy else ChipFill, NiyyahShapes.Chip)
            .then(if (selected) Modifier else Modifier.border(1.dp, ChipBorder, NiyyahShapes.Chip))
            .clickable(onClick = onClick)
            .padding(horizontal = 21.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = NiyyahType.Body,
            color = if (selected) Color.White else TextBody,
            textAlign = TextAlign.Center,
        )
    }
}

/** Featured collection hero card — node 1:829. Opens the Knowledge topic. */
@Composable
private fun FeaturedCollectionCard(onExplore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NiyyahColors.Surface)
            .border(1.dp, NiyyahColors.Border, RoundedCornerShape(16.dp)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(256.dp)) {
            Image(
                painter = painterResource(R.drawable.img_knowledge_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x66000000)),
                        ),
                    ),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
            Text(
                text = "FEATURED COLLECTION",
                style = NiyyahType.Body.copy(letterSpacing = 1.6.sp),
                color = NiyyahColors.Green,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = "The Golden Age of\nIslamic Scholarship",
                style = NiyyahType.Quote.copy(fontSize = 16.sp, lineHeight = 20.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = "Explore foundational texts that shaped intellectual discourse during the classical period, featuring works from Andalusia to Baghdad.",
                style = NiyyahType.Body,
                color = TextBody,
                modifier = Modifier.padding(bottom = 32.dp),
            )
            Box(
                modifier = Modifier
                    .background(NiyyahColors.Navy, NiyyahShapes.Button)
                    .clickable(onClick = onExplore)
                    .padding(horizontal = 32.dp, vertical = 12.dp),
            ) {
                Text(text = "Explore Collection", style = NiyyahType.Body, color = Color.White)
            }
        }
    }
}

/** Continue reading rail — node 1:845. Real in-progress books (BM-006). */
@Composable
private fun ContinueReadingSection(
    recentBooks: List<RecentBook>,
    onOpenBook: (collection: String, bookNumber: String) -> Unit,
    onViewAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Continue Reading",
                style = NiyyahType.Quote.copy(fontSize = 16.sp, lineHeight = 24.sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(
                text = "View All",
                style = NiyyahType.Body,
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.clickable(onClick = onViewAll),
            )
        }
        if (recentBooks.isEmpty()) {
            Text(
                text = "Open a book from the Hadith library to begin your reading journey.",
                style = NiyyahType.Body,
                color = TextBody,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(modifier = Modifier.width(8.dp))
                recentBooks.forEachIndexed { index, book ->
                    BookCard(
                        coverRes = if (index % 2 == 0) R.drawable.img_book_purification else R.drawable.img_book_ghazali,
                        title = book.title,
                        author = book.author,
                        progress = book.progress,
                        onClick = { onOpenBook(book.collection, book.bookNumber) },
                    )
                }
                Box(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/** Book item — node 1:852. 256dp card, 80x112 cover, gold progress. */
@Composable
private fun BookCard(coverRes: Int, title: String, author: String, progress: Float, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(256.dp)
            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
            .clickable(onClick = onClick)
            .padding(17.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(coverRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(80.dp)
                .height(112.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Column(
            modifier = Modifier.height(112.dp).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = NiyyahType.Body.copy(lineHeight = 22.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Text(text = author, style = NiyyahType.Body, color = TextBody)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(NiyyahShapes.Pill)
                    .background(TrackFill),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .background(ProgressGold),
                )
            }
        }
    }
}
