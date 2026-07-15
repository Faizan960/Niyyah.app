package com.salahlock.app.ui.hadith

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
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
import com.salahlock.app.data.db.entity.formattedReference
import com.salahlock.app.data.db.entity.fullCitation
import com.salahlock.app.ui.knowledge.HadithReaderViewModel
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType
import kotlinx.coroutines.flow.distinctUntilChanged

private val TextBody = Color(0xFF45474E)

/**
 * Hadith reader — book, topic, or single-hadith mode over the paged
 * [HadithReaderViewModel]. Reading position persists per book/topic.
 * New BM-006 screen in the approved design language.
 */
@Composable
fun HadithReaderScreen(
    collection: String? = null,
    bookNumber: String? = null,
    topic: String? = null,
    hadithId: String? = null,
    onBack: () -> Unit = {},
    viewModel: HadithReaderViewModel = viewModel(),
) {
    LaunchedEffect(collection, bookNumber, topic, hadithId) {
        when {
            hadithId != null -> viewModel.loadSingle(hadithId)
            topic != null -> viewModel.loadTopic(topic, "eng")
            collection != null && bookNumber != null -> viewModel.loadBook(collection, bookNumber, "eng")
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Jump to the saved/searched position once content is known.
    LaunchedEffect(uiState.isLoading, uiState.totalCount) {
        if (!uiState.isLoading && uiState.initialIndex > 0 && uiState.totalCount > 0) {
            listState.scrollToItem(uiState.initialIndex.coerceIn(0, uiState.totalCount - 1))
        }
    }

    // Page in chunks + persist position as the user scrolls.
    LaunchedEffect(uiState.totalCount) {
        if (uiState.totalCount == 0) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                viewModel.requestPage(index)
                viewModel.savePosition(index)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NiyyahColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = "Back",
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .width(18.dp)
                    .height(14.dp),
            )
            Text(
                text = uiState.title,
                style = NiyyahType.Quote.copy(fontSize = 20.sp, lineHeight = 26.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp),
                maxLines = 1,
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NiyyahColors.TextPrimary)
            }
        } else if (uiState.totalCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No hadiths found.",
                    style = NiyyahType.Body,
                    color = NiyyahColors.TextSecondary,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items((0 until uiState.totalCount).toList(), key = { it }) { index ->
                    val hadith = uiState.hadiths[index]
                    if (hadith == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                                .border(1.dp, NiyyahColors.Hairline, NiyyahShapes.Chip),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = NiyyahColors.TextSecondary,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        HadithCard(
                            hadith = hadith,
                            onToggleBookmark = {
                                viewModel.toggleBookmark(hadith.id, !hadith.isBookmarked)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HadithCard(hadith: HadithEntity, onToggleBookmark: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Card)
            .border(1.dp, NiyyahColors.Hairline, NiyyahShapes.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = hadith.formattedReference.uppercase(),
                style = NiyyahType.LabelUppercase,
                color = TextBody,
            )
            Icon(
                painter = painterResource(
                    if (hadith.isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline,
                ),
                contentDescription = "Bookmark",
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier
                    .clickable(onClick = onToggleBookmark)
                    .width(14.dp)
                    .height(18.dp),
            )
        }
        if (hadith.arabicText.isNotBlank()) {
            Text(
                text = hadith.arabicText,
                style = NiyyahType.Quote.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    lineHeight = 40.sp,
                ),
                color = NiyyahColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = hadith.translationText,
            style = NiyyahType.Body.copy(fontSize = 16.sp, lineHeight = 26.sp),
            color = TextBody,
        )
        Row(
            modifier = Modifier
                .clickable {
                    val text = "${hadith.translationText}\n\n— ${hadith.fullCitation}"
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
