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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Quran hub — Figma frame 1:269 (light), wired to [QuranViewModel] (BM-006):
 * continue-reading, recents, and library counts are live; search opens the
 * surah list.
 */
private val TextMuted = Color(0xFFC5C6CE)

@Composable
fun QuranScreen(
    onOpenBookmarks: () -> Unit = {},
    onOpenCollections: () -> Unit = {},
    onOpenSurahList: () -> Unit = {},
    onOpenReader: (surahNumber: Int, ayah: Int) -> Unit = { _, _ -> },
    viewModel: QuranViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val hub by viewModel.hub.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header — node 1:288
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NiyyahColors.Background.copy(alpha = 0.9f))
                .statusBarsPadding()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Quran", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NiyyahColors.Surface, CircleShape)
                    .border(1.dp, NiyyahColors.Border, CircleShape)
                    .clickable { onOpenSurahList() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = "Search",
                    tint = NiyyahColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            ContinueReadingSection(
                continueReading = hub.continueReading,
                onClick = {
                    val c = hub.continueReading
                    if (c != null) onOpenReader(c.surahNumber, c.lastAyah) else onOpenReader(1, 1)
                },
            )
            RecentlyReadSection(recents = hub.recents, onOpenReader = onOpenReader, onBrowseAll = onOpenSurahList)
            LibrarySection(
                bookmarkCount = hub.bookmarkCount,
                collectionCount = hub.collectionCount,
                onOpenBookmarks = onOpenBookmarks,
                onOpenCollections = onOpenCollections,
            )
        }
    }
}

/** Continue reading — node 1:295, live from quran_progress. */
@Composable
private fun ContinueReadingSection(continueReading: ContinueReading?, onClick: () -> Unit) {
    val surahName = continueReading?.surahName ?: "Al-Fatihah"
    val juz = continueReading?.juz ?: 1
    val ayahLine = continueReading?.let { "Ayah ${it.lastAyah} of ${it.ayahCount}" } ?: "Begin from Ayah 1"
    val progress = continueReading?.overallProgress ?: 0f
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "CONTINUE READING",
            style = NiyyahType.LabelUppercaseWide,
            color = NiyyahColors.TextSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .background(NiyyahColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, NiyyahColors.Border, RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .padding(25.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .background(NiyyahColors.SoftFill, NiyyahShapes.Chip)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Juz $juz",
                            style = NiyyahType.Badge,
                            color = Color(0xFF666666),
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_bookmark_outline),
                        contentDescription = null,
                        tint = NiyyahColors.TextPrimary,
                        modifier = Modifier.width(14.dp).height(18.dp),
                    )
                }
                Text(
                    text = surahName,
                    style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
                    color = NiyyahColors.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(text = ayahLine, style = NiyyahType.Body, color = TextMuted)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Progress", style = NiyyahType.Badge, color = TextMuted)
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = NiyyahType.Badge,
                        color = TextMuted,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(NiyyahColors.SoftFill, NiyyahShapes.Pill),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(NiyyahColors.TextPrimary, NiyyahShapes.Pill),
                    )
                }
            }
        }
    }
}

/** Recently read — node 1:318, live from quran_progress. */
@Composable
private fun RecentlyReadSection(
    recents: List<RecentSurah>,
    onOpenReader: (surahNumber: Int, ayah: Int) -> Unit,
    onBrowseAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "RECENTLY READ",
            style = NiyyahType.LabelUppercaseWide,
            color = NiyyahColors.TextSecondary,
        )
        if (recents.isEmpty()) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                    .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                    .clickable { onBrowseAll() }
                    .padding(21.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Browse Surahs",
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Text(text = "114 chapters", style = NiyyahType.Badge, color = TextMuted)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                recents.forEach { recent ->
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                            .clickable { onOpenReader(recent.surahNumber, recent.lastAyah) }
                            .padding(21.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = recent.surahName,
                            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                            color = NiyyahColors.TextPrimary,
                        )
                        Text(text = "Ayah ${recent.lastAyah}", style = NiyyahType.Badge, color = TextMuted)
                    }
                }
            }
        }
    }
}

/** Your library bento — node 1:337, live counts. */
@Composable
private fun LibrarySection(
    bookmarkCount: Int,
    collectionCount: Int,
    onOpenBookmarks: () -> Unit,
    onOpenCollections: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "YOUR LIBRARY",
            style = NiyyahType.LabelUppercaseWide,
            color = NiyyahColors.TextSecondary,
        )
        LibraryCard(
            title = "Bookmarks",
            subtitle = "Verses you've saved",
            chipText = "$bookmarkCount Saved",
            chipOnWhite = true,
            iconRes = R.drawable.ic_bookmark_check,
            iconTint = Color(0xFF8C7D76),
            iconCircle = Color(0x26CEC0BB),
            background = NiyyahColors.SurfaceElevated,
            elevated = true,
            onClick = onOpenBookmarks,
        )
        LibraryCard(
            title = "Collections",
            subtitle = "Organized by topic",
            chipText = "$collectionCount Folders",
            chipOnWhite = false,
            iconRes = R.drawable.ic_folder,
            iconTint = Color(0xFF7D927C),
            iconCircle = Color(0x26ACBDAA),
            background = NiyyahColors.Surface,
            elevated = false,
            onClick = onOpenCollections,
        )
    }
}

@Composable
private fun LibraryCard(
    title: String,
    subtitle: String,
    chipText: String,
    chipOnWhite: Boolean,
    iconRes: Int,
    iconTint: Color,
    iconCircle: Color,
    background: Color,
    elevated: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .then(
                if (elevated) Modifier.shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = NiyyahColors.NavShadow,
                    spotColor = NiyyahColors.NavShadow,
                ) else Modifier,
            )
            .background(background, RoundedCornerShape(16.dp))
            .border(1.dp, NiyyahColors.Border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(25.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(iconCircle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        if (chipOnWhite) NiyyahColors.Surface else NiyyahColors.SoftFill,
                        NiyyahShapes.Pill,
                    )
                    .border(1.dp, NiyyahColors.Border, NiyyahShapes.Pill)
                    .padding(horizontal = 13.dp, vertical = 5.dp),
            ) {
                Text(
                    text = chipText,
                    style = NiyyahType.Badge,
                    color = if (chipOnWhite) NiyyahColors.TextPrimary else NiyyahColors.TextSecondary,
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = title, style = NiyyahType.Quote.copy(lineHeight = 32.sp), color = NiyyahColors.TextPrimary)
            Text(text = subtitle, style = NiyyahType.Body, color = TextMuted)
        }
    }
}
