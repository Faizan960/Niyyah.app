package com.salahlock.app.ui.hadith

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
import androidx.compose.ui.text.style.TextOverflow
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
 * BM-011 — "By Topic" browse (all Hadith categories with real per-topic
 * counts). Reuses [KnowledgeViewModel]; tapping a topic opens the existing
 * topic reader route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithTopicsScreen(
    viewModel: KnowledgeViewModel = viewModel(),
    onOpenTopic: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Topics", fontWeight = FontWeight.Bold) },
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
            items(state.categories, key = { it }) { topic ->
                TopicGridCard(
                    topic = topic,
                    count = state.topicCounts[topic],
                    onClick = { onOpenTopic(topic) },
                )
            }
        }
    }
}

@Composable
private fun TopicGridCard(topic: String, count: Int?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(18.dp))
            .background(libraryCardColor())
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(18.dp),
    ) {
        Text(topicEmojiPublic(topic), fontSize = 24.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            topic,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (count != null && count > 0) "$count Hadiths" else "Explore",
            style = MaterialTheme.typography.labelSmall,
            color = MutedSage,
        )
    }
}

// Small local copy so this screen is independent of HadithMainScreen internals.
private fun topicEmojiPublic(topic: String): String = when (topic.lowercase()) {
    "faith" -> "⭐"
    "patience" -> "⏳"
    "gratitude" -> "💚"
    "prayer", "salah" -> "🕌"
    "charity" -> "🤲"
    "knowledge" -> "📖"
    "dua" -> "🙏"
    "family" -> "👪"
    "character" -> "✨"
    else -> "☪️"
}
