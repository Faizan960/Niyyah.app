package com.salahlock.app.ui.quran

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType
import kotlinx.coroutines.flow.distinctUntilChanged

private val ChipFill = Color(0xFFF2F0EC)

/**
 * Surah reader — Arabic (Uthmani) + Saheeh International translation.
 * Reading position is persisted as the user scrolls and restored on reopen.
 * New BM-006 screen in the approved design language.
 */
@Composable
fun QuranReaderScreen(
    surahNumber: Int,
    startAyah: Int = 1,
    onBack: () -> Unit = {},
    viewModel: QuranReaderViewModel = viewModel(),
) {
    LaunchedEffect(surahNumber) { viewModel.load(surahNumber) }

    val surah by viewModel.surah.collectAsState()
    val surahBookmarked by viewModel.surahBookmarked.collectAsState()
    val bookmarkedAyahs by viewModel.bookmarkedAyahs.collectAsState()

    val listState = rememberLazyListState()

    // Scroll to the requested ayah once verses are loaded.
    LaunchedEffect(surah) {
        if (surah != null && startAyah > 1) {
            listState.scrollToItem((startAyah - 1).coerceIn(0, surah!!.ayahCount - 1))
        }
    }

    // Persist reading position as the user scrolls (top visible ayah).
    LaunchedEffect(surah) {
        if (surah == null) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> viewModel.savePosition(index + 1) }
    }
    DisposableEffect(Unit) {
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NiyyahColors.Background),
    ) {
        // Header: back · surah name · surah bookmark
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
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = surah?.transliteration ?: "",
                    style = NiyyahType.Quote.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    color = NiyyahColors.TextPrimary,
                )
                surah?.let {
                    Text(
                        text = "${it.englishName} · ${it.ayahCount} Ayahs",
                        style = NiyyahType.Badge,
                        color = NiyyahColors.TextSecondary,
                    )
                }
            }
            Icon(
                painter = painterResource(
                    if (surahBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline,
                ),
                contentDescription = "Bookmark surah",
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier
                    .clickable { viewModel.toggleSurahBookmark() }
                    .width(14.dp)
                    .height(18.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val s = surah ?: return@LazyColumn
            itemsIndexed(s.ayahs, key = { _, a -> a.numberInSurah }) { _, ayah ->
                AyahCard(
                    surahNumber = s.number,
                    ayahNumber = ayah.numberInSurah,
                    arabic = ayah.arabic,
                    english = ayah.english,
                    bookmarked = bookmarkedAyahs.contains(ayah.numberInSurah),
                    onToggleBookmark = { viewModel.toggleAyahBookmark(ayah.numberInSurah) },
                )
            }
        }
    }
}

@Composable
private fun AyahCard(
    surahNumber: Int,
    ayahNumber: Int,
    arabic: String,
    english: String,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
) {
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
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(ChipFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$surahNumber:$ayahNumber",
                    style = NiyyahType.Badge.copy(fontSize = 10.sp),
                    color = NiyyahColors.TextPrimary,
                )
            }
            Icon(
                painter = painterResource(
                    if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline,
                ),
                contentDescription = "Bookmark ayah",
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier
                    .clickable(onClick = onToggleBookmark)
                    .width(12.dp)
                    .height(16.dp),
            )
        }
        Text(
            text = arabic,
            fontSize = 24.sp,
            lineHeight = 44.sp,
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = english,
            style = NiyyahType.Body,
            color = NiyyahColors.TextSecondary,
        )
    }
}
