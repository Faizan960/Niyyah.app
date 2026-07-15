package com.salahlock.app.ui.hadith

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.db.entity.HadithEntity
import com.salahlock.app.data.db.entity.collectionDisplayName
import com.salahlock.app.data.db.entity.formattedReference
import com.salahlock.app.data.db.entity.fullCitation
import com.salahlock.app.data.db.entity.globalNumber
import com.salahlock.app.ui.knowledge.KnowledgeViewModel
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Hadith library — Figma frame 1:493 (light).
 *
 * Daily Hadith is wired to [KnowledgeViewModel] (falls back to the frame's
 * sample while syncing). "The Six Books" list mirrors the frame; only
 * Bukhari + Muslim exist in the local DB today.
 */
private val TextBody = Color(0xFF45474E)
private val TextMutedLocal = Color(0xFFC5C6CE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HadithScreen(
    onOpenCollection: (collection: String, displayName: String) -> Unit = { _, _ -> },
    onOpenTopic: (topic: String) -> Unit = {},
    onOpenHadith: (hadithId: String) -> Unit = {},
    onOpenBook: (collection: String, bookNumber: String) -> Unit = { _, _ -> },
    viewModel: KnowledgeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HadithHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onClear = viewModel::clearSearch,
                )
                if (uiState.isSearchActive) {
                    SearchResults(
                        results = uiState.searchResults,
                        isSearching = uiState.isSearching,
                        onOpen = { onOpenHadith(it.hadith.id) },
                    )
                }
            }
            if (!uiState.isSearchActive) {
                if (uiState.recentHadiths.isNotEmpty()) {
                    ContinueReadingSection(
                        recent = uiState.recentHadiths.first(),
                        onOpen = { h -> onOpenBook(h.collection, h.bookNumber) },
                    )
                }
                DailyHadithSection(
                    dailyHadith = uiState.dailyHadith,
                    onToggleBookmark = { uiState.dailyHadith?.let(viewModel::toggleHadithBookmark) },
                )
                SixBooksSection(
                    counts = uiState.collectionCounts,
                    onOpenCollection = onOpenCollection,
                )
                ThemesSection(onOpenTopic = onOpenTopic)
            }
        }
    }
}

/** Header — node 1:494. Same bar as Knowledge: hamburger / NIYYAH / bell. */
@Composable
private fun HadithHeader() {
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

/** Search bar — node 1:504, live via KnowledgeViewModel (debounced). */
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
            .padding(start = 16.dp, end = 17.dp, top = 18.dp, bottom = 19.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = TextMutedLocal,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search Hadith...",
                    style = NiyyahType.Body,
                    color = TextMutedLocal,
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
                tint = TextMutedLocal,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .size(12.dp),
            )
        }
    }
}

/** Inline search results below the search bar. */
@Composable
private fun SearchResults(
    results: List<com.salahlock.app.ui.knowledge.HadithSearchResult>,
    isSearching: Boolean,
    onOpen: (com.salahlock.app.ui.knowledge.HadithSearchResult) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            isSearching -> Text(
                text = "Searching…",
                style = NiyyahType.Body,
                color = TextMutedLocal,
            )
            results.isEmpty() -> Text(
                text = "No hadiths matched your search.",
                style = NiyyahType.Body,
                color = TextMutedLocal,
            )
            else -> results.take(30).forEach { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                        .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                        .clickable { onOpen(result) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${result.hadith.formattedReference.uppercase()} · ${result.bookTitle}",
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

/** Continue reading strip — shows the most recently read hadith's book. */
@Composable
private fun ContinueReadingSection(recent: HadithEntity, onOpen: (HadithEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "CONTINUE READING",
            style = NiyyahType.LabelUppercaseWide,
            color = NiyyahColors.TextSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                .clickable { onOpen(recent) }
                .padding(17.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = recent.collectionDisplayName,
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Text(
                    text = "Book ${recent.bookNumber} · Hadith ${recent.globalNumber}",
                    style = NiyyahType.Body,
                    color = TextBody,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = TextMutedLocal,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Daily Hadith — node 1:510. Wired to KnowledgeViewModel.dailyHadith. */
@Composable
private fun DailyHadithSection(dailyHadith: HadithEntity?, onToggleBookmark: () -> Unit) {
    val context = LocalContext.current
    val reference = dailyHadith
        ?.let { "${it.collectionDisplayName} ${it.globalNumber}".uppercase() }
        ?: "SAHIH AL-BUKHARI 1"
    // Only use the sample Arabic when there is no hadith at all — never pair
    // one hadith's Arabic with another's translation.
    val arabic = if (dailyHadith == null) {
        "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ،\nوَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى"
    } else {
        dailyHadith.arabicText.takeIf { it.isNotBlank() }
    }
    val translation = dailyHadith?.translationText
        ?: "Narrated 'Umar bin Al-Khattab: I heard Allah's Messenger (ﷺ) saying, \"The reward of deeds depends upon the intentions and every person will get the reward according to what he has intended.\""

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = "Daily Hadith",
            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
            color = NiyyahColors.TextPrimary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                .padding(25.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = reference,
                    style = NiyyahType.LabelUppercase,
                    color = TextBody,
                )
                Icon(
                    painter = painterResource(
                        if (dailyHadith?.isBookmarked == true) R.drawable.ic_bookmark_filled
                        else R.drawable.ic_bookmark_outline,
                    ),
                    contentDescription = "Bookmark",
                    tint = NiyyahColors.TextPrimary,
                    modifier = Modifier
                        .clickable(onClick = onToggleBookmark)
                        .width(14.dp)
                        .height(18.dp),
                )
            }
            if (arabic != null) Text(
                text = arabic,
                style = NiyyahType.Quote.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 36.sp,
                    lineHeight = 45.sp,
                    letterSpacing = (-0.36).sp,
                ),
                color = NiyyahColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
            Text(
                text = translation,
                style = NiyyahType.Body.copy(fontSize = 18.sp, lineHeight = 28.sp),
                color = TextBody,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NiyyahColors.Hairline),
                )
                Row(
                    modifier = Modifier
                        .padding(top = 17.dp)
                        .clickable {
                            val text = "$translation\n\n— ${dailyHadith?.fullCitation ?: "Sahih al-Bukhari, Book 1, Hadith 1"}"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        tint = NiyyahColors.TextPrimary,
                        modifier = Modifier.width(14.dp).height(15.dp),
                    )
                    Text(
                        text = "SHARE",
                        style = NiyyahType.LabelUppercase,
                        color = NiyyahColors.TextPrimary,
                    )
                }
            }
        }
    }
}

/** Canonical six-books metadata: id, display name, author, published count. */
private data class SixBook(
    val collection: String,
    val title: String,
    val author: String,
    val canonicalCount: String,
)

private val SIX_BOOKS = listOf(
    SixBook("bukhari", "Sahih al-Bukhari", "Imam Bukhari", "7,563"),
    SixBook("muslim", "Sahih Muslim", "Imam Muslim", "3,033"),
    SixBook("nasai", "Sunan an-Nasa'i", "Imam Nasa'i", "5,758"),
    SixBook("abudawud", "Sunan Abi Dawud", "Abu Dawud", "5,274"),
    SixBook("tirmidhi", "Jami at-Tirmidhi", "Imam Tirmidhi", "3,956"),
    SixBook("ibnmajah", "Sunan Ibn Majah", "Ibn Majah", "4,341"),
)

/** The Six Books — node 1:527. All six open; VIEW ALL expands the list. */
@Composable
private fun SixBooksSection(
    counts: Map<String, Int>,
    onOpenCollection: (collection: String, displayName: String) -> Unit,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val visible = if (expanded) SIX_BOOKS else SIX_BOOKS.take(4)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "The Six Books",
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
            )
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { expanded = !expanded },
            ) {
                Text(
                    text = if (expanded) "SHOW LESS" else "VIEW ALL",
                    style = NiyyahType.LabelUppercase,
                    color = NiyyahColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NiyyahColors.TextPrimary),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            visible.forEach { book ->
                val local = counts[book.collection] ?: 0
                val countText =
                    if (local > 0) "%,d Hadiths".format(local) else "${book.canonicalCount} Hadiths"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                        .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                        .clickable { onOpenCollection(book.collection, book.title) }
                        .padding(17.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = book.title,
                            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                            color = NiyyahColors.TextPrimary,
                        )
                        Text(
                            text = "$countText • ${book.author}",
                            style = NiyyahType.Body,
                            color = TextBody,
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        tint = TextMutedLocal,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Explore by Theme — node 1:566. Chips open the topic reader. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemesSection(onOpenTopic: (topic: String) -> Unit) {
    // Display label → seeded topic name in hadith_category_mapping.
    val themes = listOf(
        "Prayer" to "Salah",
        "Fasting" to "Fasting",
        "Charity" to "Charity",
        "Manners" to "Character",
        "Knowledge" to "Knowledge",
    )
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = "Explore by Theme",
            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
            color = NiyyahColors.TextPrimary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            themes.forEach { (label, topic) ->
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF2F0EC), NiyyahShapes.Chip)
                        .clickable { onOpenTopic(topic) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = label,
                        style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                        color = Color(0xFF666666),
                    )
                }
            }
        }
    }
}
