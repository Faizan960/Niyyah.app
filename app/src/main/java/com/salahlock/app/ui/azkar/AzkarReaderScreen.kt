package com.salahlock.app.ui.azkar

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.db.entity.AzkarEntity
import com.salahlock.app.ui.knowledge.AzkarReaderViewModel
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

private val HeartGold = Color(0xFFEEC064)

/**
 * Azkar category reader — tap the counter to count each dhikr, heart to
 * favorite. New BM-006 screen in the approved design language.
 */
@Composable
fun AzkarReaderScreen(
    category: String,
    onBack: () -> Unit = {},
    viewModel: AzkarReaderViewModel = viewModel(),
) {
    LaunchedEffect(category) { viewModel.loadCategory(category) }
    val uiState by viewModel.uiState.collectAsState()

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
                text = category,
                style = NiyyahType.Quote.copy(fontSize = 24.sp, lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(start = 20.dp),
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NiyyahColors.TextPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(uiState.azkarList, key = { it.id }) { azkar ->
                    AzkarCard(
                        azkar = azkar,
                        onCount = { viewModel.incrementProgress(azkar) },
                        onToggleFavorite = { viewModel.toggleBookmark(azkar.id, !azkar.isBookmarked) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AzkarCard(
    azkar: AzkarEntity,
    onCount: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val done = azkar.completedCount >= azkar.targetCount
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
                text = azkar.reference.uppercase(),
                style = NiyyahType.LabelUppercase.copy(fontSize = 11.sp),
                color = NiyyahColors.TextBody,
            )
            Icon(
                painter = painterResource(R.drawable.ic_heart_filled),
                contentDescription = "Favorite",
                tint = if (azkar.isBookmarked) HeartGold else Color(0xFFD9D9D9),
                modifier = Modifier
                    .clickable(onClick = onToggleFavorite)
                    .width(20.dp)
                    .height(18.dp),
            )
        }
        Text(
            text = azkar.arabic,
            style = NiyyahType.Quote.copy(
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                lineHeight = 44.sp,
            ),
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth(),
        )
        if (azkar.transliteration.isNotBlank()) {
            Text(
                text = azkar.transliteration,
                style = NiyyahType.Body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = NiyyahColors.TextSecondary,
            )
        }
        Text(
            text = azkar.translation,
            style = NiyyahType.Body,
            color = NiyyahColors.TextBody,
        )
        // Counter
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (done) "COMPLETED" else "${azkar.completedCount} OF ${azkar.targetCount}",
                style = NiyyahType.Badge.copy(letterSpacing = 0.6.sp),
                color = if (done) NiyyahColors.Green else NiyyahColors.TextBody,
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        if (done) NiyyahColors.Green else NiyyahColors.TextPrimary,
                        CircleShape,
                    )
                    .clickable(enabled = !done, onClick = onCount),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_small),
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = "${azkar.targetCount - azkar.completedCount}",
                        style = NiyyahType.BodyMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
