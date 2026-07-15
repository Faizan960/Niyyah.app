package com.salahlock.app.ui.collections

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.BookmarkItem
import com.salahlock.app.data.model.BookmarkType
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionDetailUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val items: List<BookmarkItem> = emptyList(),
)

/** Backs one opened collection: resolves membership rows to live bookmarks. */
class CollectionDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val collectionsRepository = app.collectionsRepository
    private val collectionId: Long = savedStateHandle.get<Long>("collectionId") ?: 0L

    val uiState: StateFlow<CollectionDetailUiState> = combine(
        collectionsRepository.observeCollection(collectionId),
        collectionsRepository.observeItems(collectionId),
        app.bookmarksRepository.observeAll(),
    ) { collection, memberships, bookmarks ->
        val byKey = bookmarks.associateBy { it.type.name to it.key }
        CollectionDetailUiState(
            isLoading = false,
            name = collection?.name ?: "",
            items = memberships.mapNotNull { byKey[it.contentType to it.contentKey] },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CollectionDetailUiState())

    fun removeItem(item: BookmarkItem) {
        viewModelScope.launch {
            collectionsRepository.removeItem(collectionId, item.type, item.key)
        }
    }
}

private val TextBody = Color(0xFF45474E)
private val TextFaded = Color(0x9945474E)
private val ChipFill = Color(0xFFF2F0EC)
private val ChipText = Color(0xFF666666)

/**
 * One opened collection — list styled like the Bookmarks cards (frame 1:1228).
 * Tap opens the item; the bookmark icon removes it from this collection only.
 */
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onBack: () -> Unit = {},
    onOpenQuran: (surah: Int, ayah: Int) -> Unit = { _, _ -> },
    onOpenHadith: (hadithId: String) -> Unit = {},
    onOpenAzkar: (category: String) -> Unit = {},
    viewModel: CollectionDetailViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DetailHeader(title = uiState.name, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .padding(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                uiState.isLoading -> StateText("Opening your collection…")
                uiState.items.isEmpty() -> StateText(
                    "This collection is empty.\nLong-press any bookmark to add it here.",
                )
                else -> uiState.items.forEach { item ->
                    CollectionItemCard(
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
                        onRemove = { viewModel.removeItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StateText(text: String) {
    Text(
        text = text,
        style = NiyyahType.Body,
        color = TextBody,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    )
}

/** Back-arrow header, matching the reader screens' chrome. */
@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xCCFCF9F8))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = "Back",
                tint = TextBody,
                modifier = Modifier
                    .clickable { onBack() }
                    .size(16.dp)
                    .rotate(180f),
            )
            Text(
                text = title.ifBlank { "Collection" },
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x4DC5C6CE)),
        )
    }
}

private fun metaIconFor(type: BookmarkType): Int = when (type) {
    BookmarkType.QURAN -> R.drawable.ic_meta_quran
    BookmarkType.HADITH -> R.drawable.ic_meta_hadith
    BookmarkType.KNOWLEDGE -> R.drawable.ic_meta_knowledge
    BookmarkType.AZKAR -> R.drawable.ic_meta_azkar
}

@Composable
private fun CollectionItemCard(item: BookmarkItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
            .clickable { onOpen() }
            .padding(25.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(ChipFill, NiyyahShapes.Pill)
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
                style = NiyyahType.Quote.copy(fontFamily = FontFamily.Serif, lineHeight = 48.sp),
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
            contentDescription = "Remove from collection",
            tint = NiyyahColors.TextPrimary,
            modifier = Modifier
                .clickable { onRemove() }
                .padding(start = 8.dp, top = 8.dp, bottom = 7.dp)
                .width(14.dp)
                .height(18.dp),
        )
    }
}
