package com.salahlock.app.ui.azkar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.MutedSage
import com.salahlock.app.ui.components.libraryCardColor
import com.salahlock.app.ui.components.pressScale
import com.salahlock.app.ui.knowledge.KnowledgeViewModel

/**
 * BM-011 — Azkar categories, relocated out of the retired Knowledge tab into a
 * dedicated deep screen (reached from the Home menu). Reuses [KnowledgeViewModel]
 * for the real category list; tapping a tile opens the existing azkar reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzkarCategoriesScreen(
    viewModel: KnowledgeViewModel = viewModel(),
    onOpenCategory: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Azkar", fontWeight = FontWeight.Bold)
                        Text(
                            "Remembrance of Allah",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.azkarCategories, key = { it }) { category ->
                AzkarCategoryTile(category = category, onClick = { onOpenCategory(category) })
            }
        }
    }
}

@Composable
private fun AzkarCategoryTile(category: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(18.dp))
            .background(libraryCardColor())
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(EmeraldPrimary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(azkarEmoji(category), fontSize = 26.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            category,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text("Azkar", style = MaterialTheme.typography.labelSmall, color = MutedSage)
    }
}

private fun azkarEmoji(category: String): String {
    val c = category.lowercase()
    return when {
        "morning" in c -> "🌅"
        "evening" in c -> "🌇"
        "sleep" in c || "night" in c -> "🌙"
        "wake" in c -> "⏰"
        "prayer" in c || "salah" in c -> "🕌"
        "food" in c || "eat" in c -> "🍽️"
        "travel" in c -> "🧳"
        "wudu" in c -> "💧"
        "mosque" in c -> "🕋"
        "anxiety" in c -> "🤲"
        else -> "📿"
    }
}
