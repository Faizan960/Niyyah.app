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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Azkar hub — Figma frame 1:365 (light).
 *
 * Category tiles and dua counts mirror the frame; the azkar data layer
 * (azkar.json via KnowledgeRepository) uses different category names, so
 * wiring counts/navigation is deferred until the reader screen exists.
 */
private val TextBody = Color(0xFF45474E)
private val HeartGold = Color(0xFFEEC064)
private val CircleFill = Color(0xFFF6F3F2)
private val DividerColor = Color(0xFFE5E2E1)

@Composable
fun AzkarScreen() {
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
            FavoritesSection()
            CategoriesSection()
        }
    }
}

/** Header — node 1:366. Hamburger / NIYYAH / bell over a hairline border. */
@Composable
private fun AzkarHeader() {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xCCFCF9F8))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_menu),
                contentDescription = "Menu",
                tint = TextBody,
                modifier = Modifier.width(18.dp).height(12.dp),
            )
            Text(text = "NIYYAH", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = "Notifications",
                tint = TextBody,
                modifier = Modifier.width(16.dp).height(20.dp),
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
            color = TextBody,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 34.dp),
        )
    }
}

/** Favorites bento — node 1:380. */
@Composable
private fun FavoritesSection() {
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
                color = TextBody,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FavoriteCard(
                iconRes = R.drawable.ic_azkar_sun,
                iconSize = 30.dp,
                title = "Morning Supplications",
                subtitle = "Start your day with remembrance",
                count = "34 DUAS",
            )
            FavoriteCard(
                iconRes = R.drawable.ic_azkar_moon,
                iconSize = 25.dp,
                title = "Sleep Supplications",
                subtitle = "Peaceful rest through Dhikr",
                count = "21 DUAS",
            )
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Card)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Card)
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
            Text(text = subtitle, style = NiyyahType.Body, color = TextBody)
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
                    color = TextBody,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = TextBody,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** All categories grid — node 1:423. Two columns, 16dp gaps. */
@Composable
private fun CategoriesSection() {
    val categories = listOf(
        Triple(R.drawable.ic_cat_morning, "Morning", "34 Duas"),
        Triple(R.drawable.ic_cat_evening, "Evening", "28 Duas"),
        Triple(R.drawable.ic_cat_prayer, "Prayer", "15 Duas"),
        Triple(R.drawable.ic_cat_travel, "Travel", "12 Duas"),
        Triple(R.drawable.ic_cat_food, "Food", "8 Duas"),
        Triple(R.drawable.ic_cat_health, "Health", "19 Duas"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = "All Categories",
            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
            color = NiyyahColors.TextPrimary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            categories.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowItems.forEach { (iconRes, label, count) ->
                        CategoryTile(
                            iconRes = iconRes,
                            label = label,
                            count = count,
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
private fun CategoryTile(iconRes: Int, label: String, count: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(NiyyahColors.Surface, NiyyahShapes.Card)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Card)
            .padding(25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(48.dp)
                .background(CircleFill, CircleShape),
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
            color = TextBody,
            textAlign = TextAlign.Center,
        )
    }
}
