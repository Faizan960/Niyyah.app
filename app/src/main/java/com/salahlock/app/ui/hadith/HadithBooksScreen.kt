package com.salahlock.app.ui.hadith

import android.app.Application
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.db.entity.CollectionBookEntity
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HadithBooksState(
    val loading: Boolean = true,
    val syncFailed: Boolean = false,
    val displayName: String = "",
    val books: List<CollectionBookEntity> = emptyList(),
    val query: String = "",
) {
    val filtered: List<CollectionBookEntity>
        get() = if (query.isBlank()) books
        else books.filter {
            it.title.contains(query, ignoreCase = true) || it.bookNumber == query.trim()
        }
}

/** Books within one hadith collection; syncs the collection on demand. */
class HadithBooksViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as SalahLockApplication).knowledgeRepository

    private val _state = MutableStateFlow(HadithBooksState())
    val state: StateFlow<HadithBooksState> = _state.asStateFlow()

    private var collection = ""

    fun load(collection: String, displayName: String) {
        if (this.collection == collection && _state.value.books.isNotEmpty()) return
        this.collection = collection
        _state.value = HadithBooksState(displayName = displayName)
        viewModelScope.launch {
            val ok = repository.syncSingleCollectionIfNeeded(collection)
            val books = repository.getBooksForCollection(collection)
            _state.value = _state.value.copy(
                loading = false,
                syncFailed = !ok && books.isEmpty(),
                books = books,
            )
        }
    }

    fun retry() {
        val c = collection
        collection = ""
        load(c, _state.value.displayName)
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }
}

/** All books of one collection with live filter. New BM-006 screen. */
@Composable
fun HadithBooksScreen(
    collection: String,
    displayName: String,
    onBack: () -> Unit = {},
    onOpenBook: (collection: String, bookNumber: String) -> Unit = { _, _ -> },
    viewModel: HadithBooksViewModel = viewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(collection) { viewModel.load(collection, displayName) }
    val state by viewModel.state.collectAsState()

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
                text = state.displayName,
                style = NiyyahType.Quote.copy(fontSize = 24.sp, lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(start = 20.dp),
            )
        }

        // Filter field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = NiyyahColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text("Filter books", style = NiyyahType.Body, color = NiyyahColors.TextSecondary)
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    textStyle = NiyyahType.Body.copy(color = NiyyahColors.TextPrimary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NiyyahColors.TextPrimary)
                    Text(
                        text = "Preparing this collection…",
                        style = NiyyahType.Body,
                        color = NiyyahColors.TextSecondary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            state.syncFailed -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "This collection needs a connection to download.",
                        style = NiyyahType.Body,
                        color = NiyyahColors.TextSecondary,
                    )
                    Text(
                        text = "RETRY",
                        style = NiyyahType.LabelUppercase,
                        color = NiyyahColors.TextPrimary,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clickable { viewModel.retry() },
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.filtered, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                            .border(1.dp, NiyyahColors.Hairline, NiyyahShapes.Chip)
                            .clickable { onOpenBook(collection, book.bookNumber) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(NiyyahColors.SoftFill, NiyyahShapes.Pill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = book.bookNumber,
                                style = NiyyahType.Badge,
                                color = NiyyahColors.TextPrimary,
                            )
                        }
                        Text(
                            text = book.title,
                            style = NiyyahType.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NiyyahColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_right),
                            contentDescription = null,
                            tint = Color(0xFFC5C6CE),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
