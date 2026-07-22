package com.salahlock.app.ui.quran

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.repository.PageBlock
import com.salahlock.app.data.repository.QuranPageContent
import com.salahlock.app.theme.ArabicUi
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.GoldAccent
import com.salahlock.app.theme.MutedSage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * BM-QURAN-PAGES — Mushaf (604-page) reader. Book-like, quieter than the ayah reader.
 * Swipe is RTL (page turns like a physical mushaf). Chrome hides on tap. Content comes
 * from the shared repository (offline); bookmarks + reading position use the existing
 * canonical systems.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranPageReaderScreen(
    initialPage: Int,
    onBack: () -> Unit,
    onOpenSurahAyah: (surah: Int, ayah: Int) -> Unit,
    viewModel: QuranPageReaderViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.init(initialPage) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookmarked by viewModel.bookmarkedKeys.collectAsStateWithLifecycle()

    if (!state.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EmeraldPrimary)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = state.initialPage - 1, pageCount = { state.pageCount })
    val scope = rememberCoroutineScope()
    var chromeVisible by remember { mutableStateOf(true) }
    var showJump by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val currentPage = pagerState.currentPage + 1
    val currentJuz = remember(currentPage, state.juzStartPages) {
        state.juzStartPages.indexOfLast { it <= currentPage }.let { if (it < 0) 1 else it + 1 }
    }

    // Persist reading position only when the page settles (not on every frame).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.savePagePosition(it + 1) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                TopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("Page $currentPage", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Juz $currentJuz",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showJump = true }) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "Jump to")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.showTranslation) "Hide translation" else "Show translation") },
                                leadingIcon = { Icon(Icons.Outlined.Translate, null) },
                                onClick = { viewModel.toggleTranslation(); menuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Open in Surah View") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val (s, a) = viewModel.surahAyahForPage(currentPage)
                                        onOpenSurahAyah(s, a)
                                    }
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        },
    ) { padding ->
        // RTL pager → swiping like turning a physical mushaf page (page number rises).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding),
                beyondViewportPageCount = 1,
                key = { it },
            ) { pageIndex ->
                MushafPageContent(
                    pageNumber = pageIndex + 1,
                    viewModel = viewModel,
                    showTranslation = state.showTranslation,
                    bookmarked = bookmarked,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onToggleBookmark = { s, a -> viewModel.toggleAyahBookmark(s, a) },
                )
            }
        }
    }

    if (showJump) {
        JumpToSheet(
            pageCount = state.pageCount,
            juzStartPages = state.juzStartPages,
            surahStartPages = state.surahStartPages,
            onJump = { page ->
                showJump = false
                scope.launch { pagerState.scrollToPage(page - 1) }
            },
            onDismiss = { showJump = false },
        )
    }
}

@Composable
private fun MushafPageContent(
    pageNumber: Int,
    viewModel: QuranPageReaderViewModel,
    showTranslation: Boolean,
    bookmarked: Set<Long>,
    onToggleChrome: () -> Unit,
    onToggleBookmark: (surah: Int, ayah: Int) -> Unit,
) {
    val content by produceState<QuranPageContent?>(initialValue = null, pageNumber) {
        value = runCatching { viewModel.pageContent(pageNumber) }.getOrNull()
    }
    // Content stays LTR-neutral for layout; Arabic Text uses its own RTL text direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val interaction = remember { MutableInteractionSource() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interaction, indication = null, onClick = onToggleChrome)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            val c = content
            if (c == null) {
                Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EmeraldPrimary, strokeWidth = 2.dp)
                }
            } else {
                // Group consecutive ayah blocks of the same surah into flowing paragraphs.
                val items = groupBlocks(c.blocks)
                items.forEach { item ->
                    when (item) {
                        is PageRenderItem.SurahDivider -> SurahDivider(item)
                        is PageRenderItem.Paragraph -> {
                            MushafParagraph(item, bookmarked, onToggleChrome, onToggleBookmark)
                            if (showTranslation) {
                                Spacer(Modifier.height(8.dp))
                                TranslationBlock(item)
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Page ${c.page}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedSage,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Rendering model — group blocks into surah dividers + flowing paragraphs ──────

private sealed interface PageRenderItem {
    data class SurahDivider(
        val transliteration: String,
        val arabicName: String,
        val showBismillah: Boolean,
    ) : PageRenderItem

    data class Paragraph(
        val ayahs: List<com.salahlock.app.data.repository.PageAyah>,
    ) : PageRenderItem
}

private fun groupBlocks(blocks: List<PageBlock>): List<PageRenderItem> {
    val out = ArrayList<PageRenderItem>()
    var run = ArrayList<com.salahlock.app.data.repository.PageAyah>()
    fun flush() {
        if (run.isNotEmpty()) { out += PageRenderItem.Paragraph(run); run = ArrayList() }
    }
    for (b in blocks) when (b) {
        is PageBlock.Header -> {
            flush()
            out += PageRenderItem.SurahDivider(b.header.transliteration, b.header.arabicName, b.header.showBismillah)
        }
        is PageBlock.AyahLine -> run += b.ayah
    }
    flush()
    return out
}

@Composable
private fun SurahDivider(item: PageRenderItem.SurahDivider) {
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(Modifier.fillMaxWidth(0.5f), thickness = 0.5.dp, color = GoldAccent.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))
        Text(
            item.arabicName,
            fontFamily = ArabicUi,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            color = GoldAccent,
        )
        Text(
            item.transliteration,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // NOTE: the corpus embeds bismillah as the prefix of each surah's first ayah
        // (except At-Tawba), so it is rendered inline with ayah 1 — no separate line here
        // (matches the existing Surah reader; avoids a double bismillah).
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Continuous flowing Arabic paragraph. Single tap toggles the reader chrome (handled by
 * the parent); LONG-PRESS on an ayah bookmarks/un-bookmarks it (robust hit target — the
 * whole ayah span is annotated, not just the tiny marker). Bookmarked ayahs show a gold
 * end-marker highlight.
 */
@Composable
private fun MushafParagraph(
    item: PageRenderItem.Paragraph,
    bookmarked: Set<Long>,
    onToggleChrome: () -> Unit,
    onToggleBookmark: (surah: Int, ayah: Int) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val markerColor = GoldAccent
    val markerBookmarked = GoldAccent.copy(alpha = 0.30f)
    val onSurface = MaterialTheme.colorScheme.onSurface

    val annotated = buildAnnotatedString {
        item.ayahs.forEach { ay ->
            val isBm = QuranPageReaderViewModel.key(ay.surahNumber, ay.ayahNumber) in bookmarked
            // Annotate the WHOLE ayah (text + marker) so a long-press anywhere on it hits.
            pushStringAnnotation("bm", "${ay.surahNumber}:${ay.ayahNumber}")
            withStyle(SpanStyle(color = onSurface)) { append(ay.arabic) }
            append(" ")
            withStyle(
                SpanStyle(
                    color = markerColor,
                    background = if (isBm) markerBookmarked else androidx.compose.ui.graphics.Color.Transparent,
                ),
            ) {
                append(" ﴿${toArabicNumerals(ay.ayahNumber)}﴾ ")
            }
            pop()
            append(" ")
        }
    }

    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(
        text = annotated,
        onTextLayout = { layout = it },
        style = MaterialTheme.typography.headlineSmall.merge(
            androidx.compose.ui.text.TextStyle(
                fontFamily = ArabicUi,
                fontWeight = FontWeight.Medium,
                fontSize = 26.sp,
                lineHeight = 52.sp,
                textAlign = TextAlign.Right,
                textDirection = TextDirection.Rtl,
                color = onSurface,
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(annotated) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onLongPress = { pos ->
                        val l = layout ?: return@detectTapGestures
                        val offset = l.getOffsetForPosition(pos)
                        annotated.getStringAnnotations("bm", offset, offset).firstOrNull()?.let { ann ->
                            val parts = ann.item.split(":")
                            val s = parts[0].toInt()
                            val a = parts[1].toInt()
                            val wasBookmarked = QuranPageReaderViewModel.key(s, a) in bookmarked
                            onToggleBookmark(s, a)
                            android.widget.Toast.makeText(
                                context,
                                if (wasBookmarked) "Bookmark removed" else "Ayah $s:$a bookmarked",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            },
    )
}

@Composable
private fun TranslationBlock(item: PageRenderItem.Paragraph) {
    Column(Modifier.fillMaxWidth()) {
        item.ayahs.forEach { ay ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    "${ay.ayahNumber}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldPrimary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    ay.english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Left,
                )
            }
        }
    }
}

// ── Jump-to sheet: Page / Juz / Surah ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpToSheet(
    pageCount: Int,
    juzStartPages: List<Int>,
    surahStartPages: List<Int>,
    onJump: (page: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableStateOf(0) } // 0 Page, 1 Juz, 2 Surah
    var pageInput by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Jump to", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                listOf("Page", "Juz", "Surah").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> {
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { s -> pageInput = s.filter { it.isDigit() }.take(3) },
                        label = { Text("Page number (1–$pageCount)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val p = pageInput.toIntOrNull()
                            if (p != null && p in 1..pageCount) onJump(p)
                        },
                        enabled = pageInput.toIntOrNull()?.let { it in 1..pageCount } == true,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = androidx.compose.ui.graphics.Color.White),
                    ) { Text("Go to page", fontWeight = FontWeight.SemiBold) }
                }
                1 -> LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items((1..30).toList()) { juz ->
                        JumpChip("$juz") { juzStartPages.getOrNull(juz - 1)?.let(onJump) }
                    }
                }
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items((1..surahStartPages.size).toList()) { surah ->
                        val page = surahStartPages.getOrNull(surah - 1) ?: 1
                        Row(
                            Modifier.fillMaxWidth().clickable { onJump(page) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$surah", color = EmeraldPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                            Text("Surah $surah", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("p.$page", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(EmeraldPrimary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = EmeraldPrimary, fontWeight = FontWeight.SemiBold) }
}

/** Eastern-Arabic-Indic numerals for the ayah-end markers. */
private fun toArabicNumerals(n: Int): String =
    n.toString().map { d -> if (d in '0'..'9') ('٠' + (d - '0')) else d }.joinToString("")
