package com.salahlock.app.ui.knowledge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.salahlock.app.theme.MotionTokens
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import android.content.Intent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.db.entity.HadithEntity
import com.salahlock.app.data.db.entity.collectionDisplayName
import com.salahlock.app.data.db.entity.formattedReference
import com.salahlock.app.data.db.entity.fullCitation
import com.salahlock.app.data.db.entity.globalNumber
import com.salahlock.app.theme.ElevatedSurface
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.GoldAccent
import com.salahlock.app.theme.MutedSage
import com.salahlock.app.theme.WarmStone
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HadithReaderScreen(
    topic: String? = null,
    collection: String? = null,
    bookNumber: String? = null,
    hadithId: String? = null,     // For single-hadith navigation from search
    language: String = "eng",
    viewModel: HadithReaderViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(topic, collection, bookNumber, hadithId, language) {
        when {
            hadithId != null -> viewModel.loadSingle(hadithId)
            topic != null -> viewModel.loadTopic(topic, language)
            collection != null && bookNumber != null -> viewModel.loadBook(collection, bookNumber, language)
        }
    }

    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val isAmoled = MaterialTheme.colorScheme.background == Color.Black
    val readingCardColor = when {
        isLightMode -> com.salahlock.app.theme.ReadingLight
        isAmoled -> com.salahlock.app.theme.ReadingAmoled
        else -> com.salahlock.app.theme.ReadingDark
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = state.title, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (state.totalCount > 0 && !state.isSingleMode) {
                            Text(
                                text = "${state.totalCount} Hadiths",
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
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = EmeraldPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Loading hadiths…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.totalCount == 0 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(
                            "No hadiths found.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The hadiths may not have synced yet. Visit the Knowledge tab to start the sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            else -> {
                val pagerState = rememberPagerState(
                    initialPage = state.initialIndex,
                    pageCount = { state.totalCount },
                )

                LaunchedEffect(pagerState.currentPage) {
                    viewModel.requestPage(pagerState.currentPage)
                }

                // Position persistence
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }
                        .distinctUntilChanged()
                        .debounce(2000L)
                        .collectLatest { page -> viewModel.savePosition(page) }
                }
                DisposableEffect(lifecycleOwner) {
                    onDispose { viewModel.savePosition(pagerState.currentPage) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    // ── Reference Counter ─────────────────────────────────────
                    // Shows the authentic hadith reference, not the pager index.
                    val currentHadith = state.hadiths[pagerState.currentPage]
                    val counterText = if (currentHadith != null) {
                        if (state.isSingleMode)
                            currentHadith.formattedReference
                        else
                            "${currentHadith.formattedReference}  ·  ${pagerState.currentPage + 1} of ${state.totalCount}"
                    } else {
                        "${pagerState.currentPage + 1} of ${state.totalCount}"
                    }

                    // Gold citation chip — the authentic reference is the visual anchor
                    Surface(
                        color = GoldAccent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 10.dp),
                    ) {
                        Text(
                            text = counterText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }

                    if (!state.isSingleMode) {
                        val progress by animateFloatAsState(
                            targetValue = if (state.totalCount > 0)
                                (pagerState.currentPage + 1).toFloat() / state.totalCount
                            else 0f,
                            animationSpec = tween(MotionTokens.Normal),
                            label = "progressAnimation",
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 16.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 16.dp,
                        userScrollEnabled = !state.isSingleMode,
                    ) { page ->
                        val hadith = state.hadiths[page]
                        val pageOffsetVal = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp)
                                .zIndex(1f - pageOffsetVal.absoluteValue)
                                .graphicsLayer {
                                    if (pageOffsetVal > 0) {
                                        alpha = 1f - pageOffsetVal.absoluteValue
                                    } else {
                                        val scale = 1f - (0.05f * pageOffsetVal.absoluteValue)
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = pageOffsetVal * size.width * 0.15f
                                        alpha = 1f - (0.3f * pageOffsetVal.absoluteValue)
                                    }
                                },
                        ) {
                            if (hadith == null) {
                                Card(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(32.dp),
                                    colors = CardDefaults.cardColors(containerColor = readingCardColor),
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = EmeraldPrimary)
                                    }
                                }
                            } else {
                                FlashcardItem(
                                    hadith = hadith,
                                    cardColor = readingCardColor,
                                    onBookmarkToggle = { viewModel.toggleBookmark(hadith.id, !hadith.isBookmarked) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardItem(
    hadith: HadithEntity,
    cardColor: Color,
    onBookmarkToggle: () -> Unit,
) {
    var flipped by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = MotionTokens.Slow),
        label = "flipRotation",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { flipped = !flipped }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            },
    ) {
        if (rotation <= 90f) {
            FlashcardFront(hadith, cardColor, onBookmarkToggle)
        } else {
            FlashcardBack(hadith, cardColor, Modifier.graphicsLayer { rotationY = 180f })
        }
    }
}

@Composable
private fun FlashcardFront(
    hadith: HadithEntity,
    cardColor: Color,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showReflectDialog by remember { mutableStateOf(false) }
    val bookmarkScale = remember { Animatable(1f) }
    LaunchedEffect(hadith.isBookmarked) {
        if (hadith.isBookmarked) {
            bookmarkScale.snapTo(0.85f)
            bookmarkScale.animateTo(1.15f, MotionTokens.fastTween())
            bookmarkScale.animateTo(1f, MotionTokens.fastTween())
        }
    }

    if (showReflectDialog) {
        AlertDialog(
            onDismissRequest = { showReflectDialog = false },
            title = {
                Text(
                    "Reflect",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = hadith.formattedReference,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = hadith.translationText.take(300) +
                                if (hadith.translationText.length > 300) "..." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text(
                        text = "Take a moment to reflect. How does this hadith apply to your life today?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showReflectDialog = false }) {
                    Text("Done", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            // Scrollable reading content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (hadith.arabicText.isNotEmpty()) {
                    Text(
                        text = hadith.arabicText,
                        style = MaterialTheme.typography.headlineLarge,
                        // BM-TYPOGRAPHY §14 — Arabic hadith pinned to the platform Arabic
                        // font + original weight, isolated from the Latin Manrope migration.
                        fontFamily = com.salahlock.app.theme.ArabicUi,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 52.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = GoldAccent.copy(alpha = 0.2f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(24.dp))
                }
                Text(
                    text = hadith.translationText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 32.sp,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = GoldAccent.copy(alpha = 0.15f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Fixed bottom — reference chip + actions
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Gold citation chip
                        Surface(
                            color = GoldAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = hadith.formattedReference,
                                style = MaterialTheme.typography.labelMedium,
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            text = "Tap card to see full reference",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmStone,
                        )
                    }
                    IconButton(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.graphicsLayer {
                            scaleX = bookmarkScale.value
                            scaleY = bookmarkScale.value
                        },
                    ) {
                        Icon(
                            imageVector = if (hadith.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (hadith.isBookmarked) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(buildAnnotatedString {
                            append(hadith.translationText)
                            append("\n\n— ${hadith.fullCitation}")
                        })
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        val shareText = "${hadith.translationText}\n\n— ${hadith.fullCitation}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            putExtra(Intent.EXTRA_SUBJECT, hadith.formattedReference)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Hadith"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showReflectDialog = true },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Reflect",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardBack(
    hadith: HadithEntity,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        ) {
            Text(
                text = "REFERENCE",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSage,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 28.dp),
            )

            // Collection
            InfoRow(
                label = "Collection",
                value = hadith.collectionDisplayName,
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Book
            InfoRow(
                label = "Book",
                value = "Book ${hadith.bookNumber}",
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Hadith number within book
            InfoRow(
                label = "Hadith in Book",
                value = "Hadith ${hadith.hadithNumber}",
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Global sequential reference (the citable number)
            InfoRow(
                label = "Global Reference",
                value = "${hadith.collectionDisplayName} ${hadith.globalNumber}",
                isHighlighted = true,
            )
            Spacer(modifier = Modifier.height(28.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(28.dp))

            InfoRow(
                label = "Full Citation",
                value = hadith.fullCitation,
            )
            Spacer(modifier = Modifier.height(20.dp))

            InfoRow(
                label = "Authenticity",
                value = "Verified — both collections are Sahih (authentic).",
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isHighlighted: Boolean = false,
    isWarning: Boolean = false,
) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = WarmStone,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                isWarning -> WarmStone
                isHighlighted -> GoldAccent
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isWarning) FontWeight.Medium else FontWeight.SemiBold,
        )
    }
}
