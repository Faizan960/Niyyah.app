package com.salahlock.app.ui.bookmarks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.model.BookmarkItem
import com.salahlock.app.data.model.BookmarkType
import com.salahlock.app.data.repository.CollectionSummary
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Bookmarks — Figma frame 1:1181 (light).
 *
 * BM-006.5: fully wired to [BookmarksViewModel]. Live bookmarks across
 * Quran/Hadith/Knowledge/Azkar, search, type tabs, sort toggle, remove,
 * open, and long-press add-to-collection.
 */
private val TextBody = Color(0xFF45474E)
private val TextFaded = Color(0x9945474E)
private val ChipText = Color(0xFF666666)
private val SearchBorder = Color(0xFF6B7280)
private val InputRadius = RoundedCornerShape(8.dp)

@Composable
fun BookmarksScreen(
    initialFilter: BookmarkType? = null,
    onOpenQuran: (surah: Int, ayah: Int) -> Unit = { _, _ -> },
    onOpenHadith: (hadithId: String) -> Unit = {},
    onOpenAzkar: (category: String) -> Unit = {},
    viewModel: BookmarksViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var appliedInitialFilter by remember { mutableStateOf(false) }
    if (!appliedInitialFilter && initialFilter != null) {
        appliedInitialFilter = true
        viewModel.setFilter(initialFilter)
    }
    var collectionSheetItem by remember { mutableStateOf<BookmarkItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BookmarksHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                TitleSection()
                SearchAndFilterBar(
                    query = uiState.query,
                    onQueryChange = viewModel::setQuery,
                    sortNewestFirst = uiState.sortNewestFirst,
                    onToggleSort = viewModel::toggleSort,
                    onFilterSelected = viewModel::setFilter,
                )
            }
            SegmentedTabs(selected = uiState.filter, onSelect = viewModel::setFilter)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    uiState.isLoading -> StateMessage("Gathering your bookmarks…")
                    uiState.isEmpty -> StateMessage(
                        "Nothing saved yet.\nBookmark a verse, hadith or dhikr while reading, and it will rest here.",
                    )
                    uiState.items.isEmpty() -> StateMessage("No bookmarks match your search.")
                    else -> uiState.items.forEach { item ->
                        BookmarkCard(
                            item = item,
                            onOpen = {
                                when (item.type) {
                                    BookmarkType.QURAN ->
                                        item.surah?.let { onOpenQuran(it, item.ayah ?: 1) }
                                    BookmarkType.HADITH, BookmarkType.KNOWLEDGE ->
                                        item.hadithId?.let(onOpenHadith)
                                    BookmarkType.AZKAR ->
                                        item.azkarCategory?.let(onOpenAzkar)
                                }
                            },
                            onRemove = { viewModel.remove(item) },
                            onLongPress = { collectionSheetItem = item },
                        )
                    }
                }
            }
        }
    }

    collectionSheetItem?.let { item ->
        AddToCollectionDialog(
            item = item,
            collections = uiState.collections,
            onAdd = { collectionId ->
                viewModel.addToCollection(collectionId, item)
                collectionSheetItem = null
            },
            onCreateAndAdd = { name ->
                viewModel.createCollectionAndAdd(name, item)
                collectionSheetItem = null
            },
            onDismiss = { collectionSheetItem = null },
        )
    }
}

/** Loading / empty / no-results message in the list area. */
@Composable
private fun StateMessage(text: String) {
    Text(
        text = text,
        style = NiyyahType.Body,
        color = TextBody,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    )
}

/** Header — node 1:1182. */
@Composable
private fun BookmarksHeader() {
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

/** Page title — node 1:1192. */
@Composable
private fun TitleSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Bookmarks",
            style = NiyyahType.DisplayLarge,
            color = NiyyahColors.TextPrimary,
        )
        Text(
            text = "Your saved reflections, knowledge, and daily prayers.",
            style = NiyyahType.Body.copy(fontSize = 18.sp, lineHeight = 28.sp),
            color = TextBody,
        )
    }
}

/** Search + sort/filter — node 1:1197. */
@Composable
private fun SearchAndFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sortNewestFirst: Boolean,
    onToggleSort: () -> Unit,
    onFilterSelected: (BookmarkType?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NiyyahColors.Surface, InputRadius)
                .border(1.dp, SearchBorder, InputRadius)
                .padding(start = 12.dp, end = 17.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = SearchBorder,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = NiyyahType.Body.copy(color = NiyyahColors.TextPrimary),
                cursorBrush = SolidColor(NiyyahColors.TextPrimary),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search bookmarks...",
                                style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                                color = SearchBorder,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterButton(
                iconRes = R.drawable.ic_sort_arrows,
                label = if (sortNewestFirst) "Sort" else "Sort ↑",
                onClick = onToggleSort,
            )
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                FilterButton(
                    iconRes = R.drawable.ic_filter_lines,
                    label = "Filter",
                    onClick = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("All", style = NiyyahType.Body) },
                        onClick = { onFilterSelected(null); menuOpen = false },
                    )
                    BookmarkType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label, style = NiyyahType.Body) },
                            onClick = { onFilterSelected(type); menuOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButton(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(NiyyahColors.Surface, InputRadius)
            .border(1.dp, NiyyahColors.Border, InputRadius)
            .clickable { onClick() }
            .padding(horizontal = 17.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color(0xFF1C1B1B),
            modifier = Modifier.width(11.dp).height(7.dp),
        )
        Text(
            text = label,
            style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
            color = Color(0xFF1C1B1B),
        )
    }
}

/** Segmented tabs — node 1:1215. Full-bleed horizontal scroll. */
@Composable
private fun SegmentedTabs(selected: BookmarkType?, onSelect: (BookmarkType?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentPill("All", selected = selected == null) { onSelect(null) }
        BookmarkType.entries.forEach { type ->
            SegmentPill(type.label, selected = selected == type) { onSelect(type) }
        }
    }
}

@Composable
private fun SegmentPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) NiyyahColors.Navy else NiyyahColors.SoftFill, NiyyahShapes.Pill)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
            color = if (selected) Color.White else ChipText,
        )
    }
}

private fun metaIconFor(type: BookmarkType): Int = when (type) {
    BookmarkType.QURAN -> R.drawable.ic_meta_quran
    BookmarkType.HADITH -> R.drawable.ic_meta_hadith
    BookmarkType.KNOWLEDGE -> R.drawable.ic_meta_knowledge
    BookmarkType.AZKAR -> R.drawable.ic_meta_azkar
}

/** Bookmark card — node 1:1228. Tap opens; bookmark icon removes; long-press collects. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkCard(
    item: BookmarkItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(25.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(NiyyahColors.SoftFill, NiyyahShapes.Pill)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(text = item.type.label, style = NiyyahType.Badge, color = ChipText)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(metaIconFor(item.type)),
                    contentDescription = null,
                    tint = TextFaded,
                    modifier = Modifier.size(12.dp),
                )
                Text(text = item.meta, style = NiyyahType.Badge, color = TextFaded)
            }
        }
        if (item.titleIsArabic) {
            Text(
                text = item.title,
                style = NiyyahType.Quote.copy(
                    fontFamily = FontFamily.Serif,
                    lineHeight = 48.sp,
                ),
                color = NiyyahColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = item.title,
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
            )
        }
        Text(text = item.body, style = NiyyahType.Body, color = TextBody)
        Icon(
            painter = painterResource(R.drawable.ic_bookmark_filled),
            contentDescription = "Remove bookmark",
            tint = NiyyahColors.TextPrimary,
            modifier = Modifier
                .clickable { onRemove() }
                .padding(start = 8.dp, top = 8.dp, bottom = 7.dp)
                .width(14.dp)
                .height(18.dp),
        )
    }
}

/** Long-press action: add this bookmark to a collection (or create one). */
@Composable
private fun AddToCollectionDialog(
    item: BookmarkItem,
    collections: List<CollectionSummary>,
    onAdd: (Long) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = NiyyahColors.Surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Add to Collection",
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Text(text = item.meta, style = NiyyahType.Badge, color = TextBody)
                if (collections.isEmpty()) {
                    Text(
                        text = "No collections yet — create your first below.",
                        style = NiyyahType.Body,
                        color = TextBody,
                    )
                } else {
                    collections.forEach { collection ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(collection.id) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = collection.name,
                                style = NiyyahType.Body,
                                color = NiyyahColors.TextPrimary,
                            )
                            Text(
                                text = "${collection.itemCount} Items",
                                style = NiyyahType.Badge,
                                color = TextBody,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("New collection name", style = NiyyahType.Body) },
                    textStyle = NiyyahType.Body,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = NiyyahType.LabelUppercase, color = TextBody)
                    }
                    TextButton(
                        onClick = { if (newName.isNotBlank()) onCreateAndAdd(newName) },
                    ) {
                        Text(
                            "Create & Add",
                            style = NiyyahType.LabelUppercase,
                            color = NiyyahColors.Navy,
                        )
                    }
                }
            }
        }
    }
}
