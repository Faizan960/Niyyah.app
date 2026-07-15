package com.salahlock.app.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.repository.Surah
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

private val ChipFill = Color(0xFFF2F0EC)

/**
 * All 114 surahs with live search (English name, Arabic name, transliteration,
 * number) and juz filtering. New BM-006 screen in the approved design language.
 */
@Composable
fun QuranSurahListScreen(
    onBack: () -> Unit = {},
    onOpenSurah: (surahNumber: Int) -> Unit = {},
    viewModel: QuranViewModel = viewModel(),
) {
    val surahs by viewModel.surahs.collectAsState()
    val query by viewModel.query.collectAsState()
    val selectedJuz by viewModel.selectedJuz.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NiyyahColors.Background),
    ) {
        // Header
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
                text = "Surahs",
                style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(start = 20.dp),
            )
        }

        // Search field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .background(NiyyahColors.Surface, NiyyahShapes.Pill)
                .border(1.dp, NiyyahColors.Border, NiyyahShapes.Pill)
                .padding(horizontal = 20.dp, vertical = 14.dp),
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
                if (query.isEmpty()) {
                    Text(
                        text = "Search surah name or number",
                        style = NiyyahType.Body,
                        color = NiyyahColors.TextSecondary,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    textStyle = NiyyahType.Body.copy(color = NiyyahColors.TextPrimary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Clear",
                    tint = NiyyahColors.TextSecondary,
                    modifier = Modifier
                        .clickable { viewModel.setQuery("") }
                        .size(12.dp),
                )
            }
        }

        // Juz chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(label = "All", selected = selectedJuz == null) { viewModel.selectJuz(null) }
            (1..30).forEach { juz ->
                FilterChip(label = "Juz $juz", selected = selectedJuz == juz) { viewModel.selectJuz(juz) }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp, end = 24.dp, top = 8.dp, bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(surahs, key = { it.number }) { surah ->
                SurahRow(surah = surah, onClick = { onOpenSurah(surah.number) })
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) NiyyahColors.TextPrimary else ChipFill,
                NiyyahShapes.Pill,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = NiyyahType.Badge,
            color = if (selected) Color.White else NiyyahColors.TextSecondary,
        )
    }
}

@Composable
private fun SurahRow(surah: Surah, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
            .border(1.dp, NiyyahColors.Hairline, NiyyahShapes.Chip)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ChipFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = surah.number.toString(),
                style = NiyyahType.Badge,
                color = NiyyahColors.TextPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = surah.transliteration,
                style = NiyyahType.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NiyyahColors.TextPrimary,
            )
            Text(
                text = "${surah.englishName} · ${surah.ayahCount} Ayahs · ${surah.revelationType}",
                style = NiyyahType.Badge,
                color = NiyyahColors.TextSecondary,
            )
        }
        Text(
            text = surah.arabicName,
            style = NiyyahType.Quote.copy(fontSize = 20.sp, lineHeight = 28.sp),
            color = NiyyahColors.TextPrimary,
        )
    }
}
