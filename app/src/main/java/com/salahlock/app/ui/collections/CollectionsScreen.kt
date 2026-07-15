package com.salahlock.app.ui.collections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.model.BookmarkType
import com.salahlock.app.data.repository.CollectionSummary
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Collections — Figma frame 1:876 (light).
 *
 * BM-006.6: fully wired to [CollectionsViewModel]. Saved bento counts are
 * live per-module bookmark counts; Your Libraries lists real user
 * collections with create ("New"), rename/delete (long-press) and search.
 */
private val TextBody = Color(0xFF45474E)
private val CardRadius = RoundedCornerShape(8.dp)
private val SearchBorder = Color(0xFF6B7280)

@Composable
fun CollectionsScreen(
    onOpenBookmarks: (BookmarkType?) -> Unit = {},
    onOpenCollection: (Long) -> Unit = {},
    viewModel: CollectionsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<CollectionSummary?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CollectionsHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            PageHeader()
            SavedBentoGrid(counts = uiState.savedCounts, onOpen = onOpenBookmarks)
            LibrariesSection(
                collections = uiState.collections,
                hasNoCollections = uiState.hasNoCollections,
                query = uiState.query,
                onQueryChange = viewModel::setQuery,
                onNew = { showCreateDialog = true },
                onOpen = onOpenCollection,
                onLongPress = { editTarget = it },
            )
        }
    }

    if (showCreateDialog) {
        NameDialog(
            title = "New Collection",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.create(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
    editTarget?.let { target ->
        EditCollectionDialog(
            collection = target,
            onRename = { name ->
                viewModel.rename(target.id, name)
                editTarget = null
            },
            onDelete = {
                viewModel.delete(target.id)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }
}

/** Header — node 1:910. Hamburger / NIYYAH / bell. */
@Composable
private fun CollectionsHeader() {
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

/** Page header — node 1:920. */
@Composable
private fun PageHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Collections",
            style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
            color = NiyyahColors.TextPrimary,
        )
        Text(
            text = "Your sanctuary of curated wisdom.",
            style = NiyyahType.Body,
            color = TextBody,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 17.dp)
                .height(1.dp)
                .background(NiyyahColors.Hairline),
        )
    }
}

/** Saved categories bento — node 1:925. Counts are live; tap opens Bookmarks filtered. */
@Composable
private fun SavedBentoGrid(counts: Map<BookmarkType, Int>, onOpen: (BookmarkType?) -> Unit) {
    fun count(type: BookmarkType) = counts[type] ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SavedCard(
                R.drawable.ic_saved_quran, 16.dp, "Saved\nQuran",
                "${count(BookmarkType.QURAN)} Ayahs", NiyyahColors.Surface,
                Modifier.weight(1f).clickable { onOpen(BookmarkType.QURAN) },
            )
            SavedCard(
                R.drawable.ic_saved_hadith, 16.dp, "Saved\nHadith",
                "${count(BookmarkType.HADITH)} Narrations", NiyyahColors.Surface,
                Modifier.weight(1f).clickable { onOpen(BookmarkType.HADITH) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SavedCard(
                R.drawable.ic_saved_articles, 18.dp, "Saved\nArticles",
                "${count(BookmarkType.KNOWLEDGE)} Reads", NiyyahColors.Surface,
                Modifier.weight(1f).clickable { onOpen(BookmarkType.KNOWLEDGE) },
            )
            SavedCard(
                R.drawable.ic_saved_notes, 16.dp, "Saved\nNotes",
                "${count(BookmarkType.AZKAR)} Reflections", NiyyahColors.SoftFill,
                Modifier.weight(1f).clickable { onOpen(BookmarkType.AZKAR) },
            )
        }
    }
}

/** Saved card — node 1:926. */
@Composable
private fun SavedCard(
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    title: String,
    count: String,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(background, CardRadius)
            .border(1.dp, NiyyahColors.Border, CardRadius)
            .padding(25.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = NiyyahColors.TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp).size(iconSize),
        )
        Text(
            text = title,
            style = NiyyahType.Quote.copy(lineHeight = 30.sp),
            color = NiyyahColors.TextPrimary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(text = count, style = NiyyahType.Badge, color = TextBody)
    }
}

/** Your Libraries — node 1:962. Live user collections with search + New. */
@Composable
private fun LibrariesSection(
    collections: List<CollectionSummary>,
    hasNoCollections: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onLongPress: (CollectionSummary) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "Your Libraries",
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Row(
                    modifier = Modifier.clickable { onNew() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_plus),
                        contentDescription = null,
                        tint = NiyyahColors.TextPrimary,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = "New",
                        style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                        color = NiyyahColors.TextPrimary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NiyyahColors.Hairline),
            )
        }
        // Live collection search — same input styling as the Bookmarks search bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NiyyahColors.Surface, CardRadius)
                .border(1.dp, NiyyahColors.Border, CardRadius)
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
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search collections...",
                                style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                                color = SearchBorder,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                hasNoCollections -> Text(
                    text = "No libraries yet.\nTap New to gather your first collection of wisdom.",
                    style = NiyyahType.Body,
                    color = TextBody,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
                collections.isEmpty() -> Text(
                    text = "No collections match your search.",
                    style = NiyyahType.Body,
                    color = TextBody,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
                else -> collections.forEach { collection ->
                    LibraryRow(
                        category = "COLLECTION",
                        title = collection.name,
                        count = "${collection.itemCount} Items",
                        onClick = { onOpen(collection.id) },
                        onLongClick = { onLongPress(collection) },
                    )
                }
            }
        }
    }
}

/** Collection card — node 1:971. 160dp tall; notes icon band (user collections). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    category: String,
    title: String,
    count: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(CardRadius)
            .background(NiyyahColors.Surface)
            .border(1.dp, NiyyahColors.Border, CardRadius)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .width(113.dp)
                .fillMaxHeight()
                .background(Color(0xFFEBE7E7)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_collection_notes_large),
                contentDescription = null,
                tint = NiyyahColors.TextSecondary,
                modifier = Modifier.size(30.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = category,
                style = NiyyahType.Badge.copy(letterSpacing = 1.2.sp),
                color = TextBody,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = title,
                style = NiyyahType.Quote.copy(lineHeight = 30.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(text = count, style = NiyyahType.Badge, color = TextBody)
        }
    }
}

/** Create / rename input dialog. */
@Composable
internal fun NameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = NiyyahColors.Surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Collection name", style = NiyyahType.Body) },
                    textStyle = NiyyahType.Body,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = NiyyahType.LabelUppercase, color = TextBody)
                    }
                    TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                        Text(confirmLabel, style = NiyyahType.LabelUppercase, color = NiyyahColors.Navy)
                    }
                }
            }
        }
    }
}

/** Long-press actions on a library: rename or delete. */
@Composable
private fun EditCollectionDialog(
    collection: CollectionSummary,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(collection.name) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = NiyyahColors.Surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Edit Collection",
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = NiyyahType.Body,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", style = NiyyahType.LabelUppercase, color = Color(0xFFBA1A1A))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", style = NiyyahType.LabelUppercase, color = TextBody)
                        }
                        TextButton(onClick = { if (name.isNotBlank()) onRename(name) }) {
                            Text("Save", style = NiyyahType.LabelUppercase, color = NiyyahColors.Navy)
                        }
                    }
                }
            }
        }
    }
}
