package com.salahlock.app.ui.bookmarks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.salahlock.app.data.model.BookmarkItem
import com.salahlock.app.data.model.BookmarkType
import com.salahlock.app.data.repository.CollectionSummary
import com.salahlock.app.theme.ElevatedSurface
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.GoldAccent
import com.salahlock.app.theme.WarmStone

/**
 * BM-010.4 — unified Bookmarks screen over the preserved single bookmark
 * system. Real persisted data only; filter chips per module; search; remove;
 * add-to-collection. Calm empty state when nothing is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: BookmarksViewModel = viewModel(),
    onOpenBookmark: (BookmarkItem) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var collectionTarget by remember { mutableStateOf<BookmarkItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bookmarks", fontWeight = FontWeight.Bold)
                        if (!state.isLoading) {
                            Text(
                                text = "${state.allItems.size} saved",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
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

            state.allItems.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                title = "Nothing saved yet",
                body = "Bookmarks you make in the Quran, Hadith, Knowledge and Azkar readers will gather here.",
            )

            else -> Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search your bookmarks…") },
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

                Spacer(Modifier.height(12.dp))

                // Type filter chips — All + one per module that has bookmarks.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        TypeChip(
                            label = "All",
                            selected = state.typeFilter == null,
                            onClick = { viewModel.onTypeFilterChanged(null) },
                        )
                    }
                    items(BookmarkType.entries.filter { t -> state.allItems.any { it.type == t } }) { type ->
                        TypeChip(
                            label = type.label,
                            selected = state.typeFilter == type,
                            onClick = { viewModel.onTypeFilterChanged(type) },
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (state.visibleItems.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        title = "No bookmarks match",
                        body = "Try a different keyword or filter.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.visibleItems, key = { "${it.type}:${it.key}" }) { item ->
                            BookmarkCard(
                                item = item,
                                onClick = { onOpenBookmark(item) },
                                onAddToCollection = { collectionTarget = item },
                                onRemove = { viewModel.remove(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    collectionTarget?.let { item ->
        AddToCollectionDialog(
            collections = state.collections,
            onAdd = { collectionId ->
                viewModel.addToCollection(collectionId, item)
                collectionTarget = null
            },
            onCreateAndAdd = { name ->
                viewModel.createCollectionWith(name, item)
                collectionTarget = null
            },
            onDismiss = { collectionTarget = null },
        )
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EmeraldPrimary,
            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
        ),
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, title: String, body: String) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Shared card for one unified bookmark. Also used by the collection detail
 * screen, where [onAddToCollection] is null and [removeContentDescription]
 * reads "Remove from collection".
 */
@Composable
fun BookmarkCard(
    item: BookmarkItem,
    onClick: () -> Unit,
    onAddToCollection: (() -> Unit)?,
    onRemove: () -> Unit,
    removeContentDescription: String = "Remove bookmark",
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = if (isLight) MaterialTheme.colorScheme.surface else ElevatedSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (isLight) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else null,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.meta,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = GoldAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmStone,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = item.title,
                style = if (item.titleIsArabic) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = if (item.titleIsArabic) 40.sp else 24.sp,
                textAlign = if (item.titleIsArabic) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onAddToCollection != null) {
                    IconButton(onClick = onAddToCollection, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = "Add to collection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = removeContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Pick an existing collection or create a new one for the given bookmark. */
@Composable
fun AddToCollectionDialog(
    collections: List<CollectionSummary>,
    onAdd: (Long) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add to collection", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (collections.isEmpty()) {
                    Text(
                        "You have no collections yet. Name one to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    collections.forEach { collection ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(collection.id) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                collection.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${collection.itemCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("New collection name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreateAndAdd(newName.trim()) },
                enabled = newName.isNotBlank(),
            ) {
                Text("Create & add", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
