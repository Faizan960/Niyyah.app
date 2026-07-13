package com.salahlock.app.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Bookmarks — Figma frame 1:1181 (light).
 *
 * NOTE: no bookmarks aggregation layer exists yet (hadith bookmarks live in
 * Room; Quran/Knowledge/Azkar have no data layers). List content is
 * placeholder mirroring the frame.
 */
private val TextBody = Color(0xFF45474E)
private val TextFaded = Color(0x9945474E)
private val ChipFill = Color(0xFFF2F0EC)
private val ChipText = Color(0xFF666666)
private val SearchBorder = Color(0xFF6B7280)
private val InputRadius = RoundedCornerShape(8.dp)

@Composable
fun BookmarksScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BookmarksHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                TitleSection()
                SearchAndFilterBar()
            }
            SegmentedTabs()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BookmarkCard(
                    tag = "Quran",
                    metaIcon = R.drawable.ic_meta_quran,
                    metaText = "Al-Baqarah 2:286",
                    title = "لَا يُكَلِّفُ ٱللَّهُ نَفْسًا إِلَّا وُسْعَهَا",
                    titleIsArabic = true,
                    body = "\"Allah does not burden a soul beyond that it can bear...\"",
                )
                BookmarkCard(
                    tag = "Knowledge",
                    metaIcon = R.drawable.ic_meta_knowledge,
                    metaText = "Purification of the Heart",
                    title = "The Disease of Envy (Hasad)",
                    body = "Understanding the spiritual sickness of envy, its roots in dissatisfaction with…",
                )
                BookmarkCard(
                    tag = "Hadith",
                    metaIcon = R.drawable.ic_meta_hadith,
                    metaText = "Sahih al-Bukhari 1",
                    title = "The Reward of Deeds…",
                    body = "\"Actions are according to intentions, and everyone will get what was intended...\"",
                )
                BookmarkCard(
                    tag = "Azkar",
                    metaIcon = R.drawable.ic_meta_azkar,
                    metaText = "Morning Remembrance",
                    title = "Protection Supplication",
                    body = "\"In the name of Allah, with whose name nothing on earth or in the heavens can…",
                )
            }
        }
    }
}

/** Header — node 1:1182. */
@Composable
private fun BookmarksHeader() {
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

/** Page title — node 1:1192. */
@Composable
private fun TitleSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Bookmarks",
            style = NiyyahType.DisplayLarge,
            color = NiyyahColors.TextPrimary,
        )
        Text(
            text = "Your saved reflections, knowledge, and daily prayers.",
            style = NiyyahType.Body.copy(fontSize = 18.sp, lineHeight = 28.sp),
            color = TextBody,
        )
    }
}

/** Search + sort/filter — node 1:1197. */
@Composable
private fun SearchAndFilterBar() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NiyyahColors.Surface, InputRadius)
                .border(1.dp, SearchBorder, InputRadius)
                .padding(start = 12.dp, end = 17.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = SearchBorder,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Search bookmarks...",
                style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                color = SearchBorder,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterButton(iconRes = R.drawable.ic_sort_arrows, label = "Sort")
            FilterButton(iconRes = R.drawable.ic_filter_lines, label = "Filter")
        }
    }
}

@Composable
private fun FilterButton(iconRes: Int, label: String) {
    Row(
        modifier = Modifier
            .background(NiyyahColors.Surface, InputRadius)
            .border(1.dp, NiyyahColors.Border, InputRadius)
            .padding(horizontal = 17.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color(0xFF1C1B1B),
            modifier = Modifier.width(11.dp).height(7.dp),
        )
        Text(
            text = label,
            style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
            color = Color(0xFF1C1B1B),
        )
    }
}

/** Segmented tabs — node 1:1215. Full-bleed horizontal scroll. */
@Composable
private fun SegmentedTabs() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentPill("All", selected = true)
        SegmentPill("Quran", selected = false)
        SegmentPill("Hadith", selected = false)
        SegmentPill("Knowledge", selected = false)
        SegmentPill("Azkar", selected = false)
    }
}

@Composable
private fun SegmentPill(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .background(if (selected) NiyyahColors.Navy else ChipFill, NiyyahShapes.Pill)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
            color = if (selected) Color.White else ChipText,
        )
    }
}

/** Bookmark card — node 1:1228. */
@Composable
private fun BookmarkCard(
    tag: String,
    metaIcon: Int,
    metaText: String,
    title: String,
    body: String,
    titleIsArabic: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
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
                Text(text = tag, style = NiyyahType.Badge, color = ChipText)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(metaIcon),
                    contentDescription = null,
                    tint = TextFaded,
                    modifier = Modifier.size(12.dp),
                )
                Text(text = metaText, style = NiyyahType.Badge, color = TextFaded)
            }
        }
        if (titleIsArabic) {
            Text(
                text = title,
                style = NiyyahType.Quote.copy(
                    fontFamily = FontFamily.Serif,
                    lineHeight = 48.sp,
                ),
                color = NiyyahColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = title,
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
            )
        }
        Text(text = body, style = NiyyahType.Body, color = TextBody)
        Icon(
            painter = painterResource(R.drawable.ic_bookmark_filled),
            contentDescription = "Bookmarked",
            tint = NiyyahColors.TextPrimary,
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp, bottom = 7.dp)
                .width(14.dp)
                .height(18.dp),
        )
    }
}
