package com.salahlock.app.ui.azkar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Azkar hub — Figma frame 1:365 (light), wired to azkar_table (BM-006).
 * Tile labels map to real azkar.json categories: Prayer → "After Prayer",
 * Health → "Anxiety".
 */
private val HeartGold = Color(0xFFEEC064)
private val DividerColor = Color(0xFFE5E2E1)

@Composable
fun AzkarScreen(
    onOpenCategory: (category: String) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    viewModel: AzkarViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        AzkarHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            TitleSection()
            FavoritesSection(
                state = state,
                onOpenCategory = onOpenCategory,
                onSeeAll = onOpenFavorites,
            )
            CategoriesSection(
                counts = state.categoryCounts,
                onOpenCategory = onOpenCategory,
            )
        }
    }
}

/** Header — node 1:366. Hamburger / NIYYAH / bell over a hairline border. */
@Composable
private fun AzkarHeader() {
    Column(modifier = Modifier.fillMaxWidth().background(NiyyahColors.HeaderBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Balances the trailing icon so the wordmark stays centred (BM-009.2:
            // the non-functional hamburger was removed).
            Box(modifier = Modifier.width(16.dp))
            Text(text = "NIYYAH", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = "Notifications",
                tint = NiyyahColors.TextBody,
                modifier = Modifier.width(16.dp).height(20.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NiyyahColors.Hairline),
        )
    }
}

/** Title — node 1:376. */
@Composable
private fun TitleSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Azkar",
            style = NiyyahType.DisplayLarge,
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Find peace in remembrance. Daily supplications for every moment.",
            style = NiyyahType.Body.copy(fontSize = 18.sp, lineHeight = 28.sp),
            color = NiyyahColors.TextBody,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 34.dp),
        )
    }
}

/** Favorites bento — node 1:380. Live from bookmarked azkar (BM-006). */
@Composable
private fun FavoritesSection(
    state: AzkarHubState,
    onOpenCategory: (category: String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_heart_filled),
                    contentDescription = null,
                    tint = HeartGold,
                    modifier = Modifier.width(20.dp).height(18.dp),
                )
                Text(
                    text = "Favorites",
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
            }
            Text(
                text = "See all",
                style = NiyyahType.LabelUppercase,
                color = NiyyahColors.TextBody,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.favoriteCategories.isEmpty()) {
                // No favorites yet — the two frame defaults act as entry points.
                FavoriteCard(
                    iconRes = R.drawable.ic_azkar_sun,
                    iconSize = 30.dp,
                    title = "Morning Supplications",
                    subtitle = "Start your day with remembrance",
                    count = "${state.categoryCounts["Morning"] ?: 0} DUAS",
                    onClick = { onOpenCategory("Morning") },
                )
                FavoriteCard(
                    iconRes = R.drawable.ic_azkar_moon,
                    iconSize = 25.dp,
                    title = "Sleep Supplications",
                    subtitle = "Peaceful rest through Dhikr",
                    count = "${state.categoryCounts["Sleep"] ?: 0} DUAS",
                    onClick = { onOpenCategory("Sleep") },
                )
            } else {
                state.favoriteCategories.take(2).forEach { fav ->
                    FavoriteCard(
                        iconRes = if (fav.category == "Sleep" || fav.category == "Evening")
                            R.drawable.ic_azkar_moon else R.drawable.ic_azkar_sun,
                        iconSize = 28.dp,
                        title = "${fav.category} Supplications",
                        subtitle = "Your favorited remembrances",
                        count = "${fav.favoriteCount} FAVORITED",
                        onClick = { onOpenCategory(fav.category) },
                    )
                }
            }
        }
    }
}

/** Favorite card — node 1:389. */
@Composable
private fun FavoriteCard(
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    title: String,
    subtitle: String,
    count: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Card)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Card)
            .clickable(onClick = onClick)
            .padding(25.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier.size(iconSize),
            )
            Icon(
                painter = painterResource(R.drawable.ic_heart_filled),
                contentDescription = "Favorited",
                tint = HeartGold,
                modifier = Modifier.width(20.dp).height(18.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(text = subtitle, style = NiyyahType.Body, color = NiyyahColors.TextBody)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 17.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = count,
                    style = NiyyahType.Badge.copy(letterSpacing = 0.6.sp),
                    color = NiyyahColors.TextBody,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = NiyyahColors.TextBody,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Tile label → real azkar.json category. */
private data class AzkarTile(val iconRes: Int, val label: String, val category: String)

/** All categories grid — node 1:423. Two columns, live counts (BM-006). */
@Composable
private fun CategoriesSection(
    counts: Map<String, Int>,
    onOpenCategory: (category: String) -> Unit,
) {
    val tiles = listOf(
        AzkarTile(R.drawable.ic_cat_morning, "Morning", "Morning"),
        AzkarTile(R.drawable.ic_cat_evening, "Evening", "Evening"),
        AzkarTile(R.drawable.ic_cat_prayer, "Prayer", "After Prayer"),
        AzkarTile(R.drawable.ic_cat_travel, "Travel", "Travel"),
        AzkarTile(R.drawable.ic_cat_food, "Food", "Food"),
        AzkarTile(R.drawable.ic_cat_health, "Health", "Anxiety"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = "All Categories",
            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
            color = NiyyahColors.TextPrimary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            tiles.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowItems.forEach { tile ->
                        CategoryTile(
                            iconRes = tile.iconRes,
                            label = tile.label,
                            count = "${counts[tile.category] ?: 0} Duas",
                            onClick = { onOpenCategory(tile.category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Category tile — node 1:427. */
@Composable
private fun CategoryTile(
    iconRes: Int,
    label: String,
    count: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(NiyyahColors.Surface, NiyyahShapes.Card)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Card)
            .clickable(onClick = onClick)
            .padding(25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(48.dp)
                .background(NiyyahColors.SoftFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = NiyyahType.LabelUppercase,
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = count,
            style = NiyyahType.Badge,
            color = NiyyahColors.TextBody,
            textAlign = TextAlign.Center,
        )
    }
}
