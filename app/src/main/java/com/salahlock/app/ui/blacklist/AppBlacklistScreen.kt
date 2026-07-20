package com.salahlock.app.ui.blacklist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.model.AppCategory
import com.salahlock.app.data.model.BlockProfile
import com.salahlock.app.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlacklistScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: BlacklistViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var showSortMenu by remember { mutableStateOf(false) }
    var showPauseMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isMultiSelectMode) {
                        Text("${state.selectedPackages.size} selected", fontWeight = FontWeight.Bold)
                    } else {
                        Text("App Blocker", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    // Tab mode (onNavigateBack == null) shows no back arrow, except to
                    // exit multi-select. Deep-screen mode shows a normal back arrow.
                    if (state.isMultiSelectMode || onNavigateBack != null) {
                        IconButton(onClick = {
                            if (state.isMultiSelectMode) viewModel.exitMultiSelect() else onNavigateBack?.invoke()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (state.isMultiSelectMode) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { viewModel.blockSelected() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Block Selected", tint = EmeraldPrimary)
                        }
                        IconButton(onClick = { viewModel.unblockSelected() }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Unblock Selected")
                        }
                    } else {
                        // BM-011 — pause the lock engine (relocated from the old Home hamburger).
                        Box {
                            IconButton(onClick = {
                                if (state.isPaused) viewModel.resumePause() else showPauseMenu = true
                            }) {
                                Icon(
                                    if (state.isPaused) Icons.Outlined.PlayCircle else Icons.Outlined.PauseCircle,
                                    contentDescription = if (state.isPaused) "Resume protection" else "Pause protection",
                                    tint = if (state.isPaused) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            DropdownMenu(expanded = showPauseMenu, onDismissRequest = { showPauseMenu = false }) {
                                Text(
                                    "Pause protection",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                listOf(15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour").forEach { (min, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { viewModel.pauseForMinutes(min); showPauseMenu = false },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.enterMultiSelect() }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Multi-select")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                AppSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                order.displayName,
                                                fontWeight = if (state.sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                                color = if (state.sortOrder == order) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                                            )
                                        },
                                        onClick = { viewModel.setSortOrder(order); showSortMenu = false },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Search Bar ──────────────────────────────────────────────────
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = EmeraldPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading apps…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                // ── Block Profiles ──────────────────────────────────────────
                item {
                    BlockProfilesRow(
                        activeProfile = state.activeProfile,
                        onSelectProfile = { viewModel.applyProfile(it) },
                    )
                }

                // ── Category Filter ─────────────────────────────────────────
                if (state.availableCategories.size > 1) {
                    item {
                        CategoryFilterRow(
                            categories = state.availableCategories,
                            selected = state.selectedCategory,
                            onSelect = { viewModel.setCategory(it) },
                        )
                    }
                }

                // ── Stats header ────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${state.blockedCount} of ${state.filteredItems.size} blocked",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { viewModel.blockAll() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Block All", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = { viewModel.unblockAll() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Allow All", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // ── App List ────────────────────────────────────────────────
                items(state.filteredItems, key = { it.entity.packageName }) { item ->
                    AppListRow(
                        item = item,
                        isSelected = state.selectedPackages.contains(item.entity.packageName),
                        isMultiSelect = state.isMultiSelectMode,
                        onToggle = { viewModel.toggle(item) },
                        onSelect = { viewModel.toggleSelection(item.entity.packageName) },
                        onLongPress = {
                            viewModel.enterMultiSelect()
                            viewModel.toggleSelection(item.entity.packageName)
                        },
                    )
                }
            }
        }
    }
}

// ── Block Profiles Row ────────────────────────────────────────────────────────
@Composable
private fun BlockProfilesRow(
    activeProfile: BlockProfile,
    onSelectProfile: (BlockProfile) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Block Profile",
            style = MaterialTheme.typography.labelSmall,
            color = EmeraldPrimary,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(BlockProfile.entries) { profile ->
                val isActive = profile == activeProfile
                Surface(
                    onClick = { onSelectProfile(profile) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isActive) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            profile.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            profile.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Category Filter Row ───────────────────────────────────────────────────────
@Composable
private fun CategoryFilterRow(
    categories: List<AppCategory>,
    selected: AppCategory?,
    onSelect: (AppCategory?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EmeraldPrimary,
                    selectedLabelColor = Color.White,
                ),
            )
        }
        items(categories) { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(if (selected == cat) null else cat) },
                label = { Text(cat.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EmeraldPrimary,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

// ── App List Row ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListRow(
    item: UiAppItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
) {
    val isBlocked = item.entity.isBlocked
    val bgColor = when {
        isSelected -> EmeraldPrimary.copy(alpha = 0.12f)
        isBlocked -> EmeraldPrimary.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = { if (isMultiSelect) onSelect() else onToggle() },
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Checkbox (multi-select) or App Icon
        if (isMultiSelect) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect() },
                colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary),
            )
        } else {
            AppIconComposable(
                drawable = item.icon,
                label = item.entity.appLabel,
                isBlocked = isBlocked,
            )
        }

        // App name + package name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.entity.appLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.entity.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Category badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Text(
                text = item.category.displayName,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Toggle
        Switch(
            checked = isBlocked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EmeraldPrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        thickness = 0.5.dp,
    )
}

// ── App Icon ──────────────────────────────────────────────────────────────────
@Composable
private fun AppIconComposable(drawable: Drawable?, label: String, isBlocked: Boolean) {
    var bitmap by remember(drawable) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(drawable) {
        if (drawable != null) {
            withContext(Dispatchers.IO) {
                try {
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap = bmp.asImageBitmap()
                } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Fallback: initial letter
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isBlocked) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isBlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
