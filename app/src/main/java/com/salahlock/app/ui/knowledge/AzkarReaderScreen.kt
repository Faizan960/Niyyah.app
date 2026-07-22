package com.salahlock.app.ui.knowledge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.data.db.entity.AzkarEntity
import com.salahlock.app.theme.EmeraldPrimary
import androidx.compose.ui.graphics.luminance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzkarReaderScreen(
    category: String,
    viewModel: AzkarReaderViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(category) {
        viewModel.loadCategory(category)
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
                title = { Text(text = "$category Azkar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(state.azkarList) { azkar ->
                    AzkarCard(
                        azkar = azkar,
                        cardColor = readingCardColor,
                        onIncrement = { viewModel.incrementProgress(azkar) },
                        onBookmarkToggle = { viewModel.toggleBookmark(azkar.id, !azkar.isBookmarked) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AzkarCard(
    azkar: AzkarEntity,
    cardColor: Color,
    onIncrement: () -> Unit,
    onBookmarkToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Arabic Text
            Text(
                text = azkar.arabic,
                style = MaterialTheme.typography.headlineMedium,
                // BM-TYPOGRAPHY §14 — dhikr Arabic pinned to the platform Arabic font.
                fontFamily = com.salahlock.app.theme.ArabicUi,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 40.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Transliteration
            Text(
                text = azkar.transliteration,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Translation
            Text(
                text = azkar.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Bottom Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress Button
                Surface(
                    color = if (azkar.completedCount >= azkar.targetCount) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable(onClick = onIncrement)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${azkar.completedCount} / ${azkar.targetCount}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (azkar.completedCount >= azkar.targetCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Times",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                IconButton(onClick = onBookmarkToggle) {
                    Icon(
                        imageVector = if (azkar.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (azkar.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = azkar.reference,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AzkarReaderScreenPreview() {
    com.salahlock.app.theme.SalahLockTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            AzkarCard(
                azkar = AzkarEntity(
                    id = 1,
                    category = "Morning",
                    arabic = "بِسْمِ اللَّهِ",
                    transliteration = "Bismillah",
                    translation = "In the name of Allah",
                    reference = "Muslim 200",
                    targetCount = 3,
                    completedCount = 1
                ),
                cardColor = com.salahlock.app.theme.ReadingDark,
                onIncrement = {},
                onBookmarkToggle = {}
            )
        }
    }
}
