package com.salahlock.app.ui.collections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Collections — Figma frame 1:876 (light).
 *
 * NOTE: no collections data layer exists yet; counts and libraries are
 * placeholder mirroring the frame.
 */
private val TextBody = Color(0xFF45474E)
private val CardRadius = RoundedCornerShape(8.dp)
private val NotesFill = Color(0xFFF6F3F2)

@Composable
fun CollectionsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CollectionsHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            PageHeader()
            SavedBentoGrid()
            LibrariesSection()
        }
    }
}

/** Header — node 1:910. Hamburger / NIYYAH / bell. */
@Composable
private fun CollectionsHeader() {
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

/** Page header — node 1:920. */
@Composable
private fun PageHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Collections",
            style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
            color = NiyyahColors.TextPrimary,
        )
        Text(
            text = "Your sanctuary of curated wisdom.",
            style = NiyyahType.Body,
            color = TextBody,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 17.dp)
                .height(1.dp)
                .background(Color(0x4DC5C6CE)),
        )
    }
}

/** Saved categories bento — node 1:925. */
@Composable
private fun SavedBentoGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SavedCard(R.drawable.ic_saved_quran, 16.dp, "Saved\nQuran", "42 Ayahs", NiyyahColors.Surface, Modifier.weight(1f))
            SavedCard(R.drawable.ic_saved_hadith, 16.dp, "Saved\nHadith", "18 Narrations", NiyyahColors.Surface, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SavedCard(R.drawable.ic_saved_articles, 18.dp, "Saved\nArticles", "7 Reads", NiyyahColors.Surface, Modifier.weight(1f))
            SavedCard(R.drawable.ic_saved_notes, 16.dp, "Saved\nNotes", "12 Reflections", NotesFill, Modifier.weight(1f))
        }
    }
}

/** Saved card — node 1:926. */
@Composable
private fun SavedCard(
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    title: String,
    count: String,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(background, CardRadius)
            .border(1.dp, NiyyahColors.Border, CardRadius)
            .padding(25.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = NiyyahColors.TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp).size(iconSize),
        )
        Text(
            text = title,
            style = NiyyahType.Quote.copy(lineHeight = 30.sp),
            color = NiyyahColors.TextPrimary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(text = count, style = NiyyahType.Badge, color = TextBody)
    }
}

/** Your Libraries — node 1:962. */
@Composable
private fun LibrariesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "Your Libraries",
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_plus),
                        contentDescription = null,
                        tint = NiyyahColors.TextPrimary,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = "New",
                        style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                        color = NiyyahColors.TextPrimary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x4DC5C6CE)),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LibraryRow(
                imageRes = R.drawable.img_collection_theology,
                category = "THEOLOGY",
                title = "Names of Allah",
                count = "99 Items",
            )
            LibraryRow(
                imageRes = R.drawable.img_collection_practice,
                category = "PRACTICE",
                title = "Morning Adhkar",
                count = "14 Items",
            )
            LibraryRow(
                imageRes = null,
                category = "REFLECTION",
                title = "Patience (Sabr)",
                count = "3 Items",
            )
        }
    }
}

/** Collection card — node 1:971. 160dp tall; image band 113dp with navy overlay. */
@Composable
private fun LibraryRow(imageRes: Int?, category: String, title: String, count: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(CardRadius)
            .background(NiyyahColors.Surface)
            .border(1.dp, NiyyahColors.Border, CardRadius),
    ) {
        Box(
            modifier = Modifier
                .width(113.dp)
                .fillMaxHeight()
                .background(if (imageRes == null) Color(0xFFEBE7E7) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (imageRes != null) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(modifier = Modifier.fillMaxSize().background(Color(0x1A071836)))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_collection_notes_large),
                    contentDescription = null,
                    tint = NiyyahColors.TextSecondary,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = category,
                style = NiyyahType.Badge.copy(letterSpacing = 1.2.sp),
                color = TextBody,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = title,
                style = NiyyahType.Quote.copy(lineHeight = 30.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(text = count, style = NiyyahType.Badge, color = TextBody)
        }
    }
}
