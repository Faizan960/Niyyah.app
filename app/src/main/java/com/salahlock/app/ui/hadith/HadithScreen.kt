package com.salahlock.app.ui.hadith

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.db.entity.HadithEntity
import com.salahlock.app.data.db.entity.collectionDisplayName
import com.salahlock.app.data.db.entity.fullCitation
import com.salahlock.app.data.db.entity.globalNumber
import com.salahlock.app.ui.knowledge.KnowledgeViewModel
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Hadith library — Figma frame 1:493 (light).
 *
 * Daily Hadith is wired to [KnowledgeViewModel] (falls back to the frame's
 * sample while syncing). "The Six Books" list mirrors the frame; only
 * Bukhari + Muslim exist in the local DB today.
 */
private val TextBody = Color(0xFF45474E)
private val TextMutedLocal = Color(0xFFC5C6CE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HadithScreen(viewModel: KnowledgeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HadithHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            SearchBar()
            DailyHadithSection(uiState.dailyHadith)
            SixBooksSection()
            ThemesSection()
        }
    }
}

/** Header — node 1:494. Same bar as Knowledge: hamburger / NIYYAH / bell. */
@Composable
private fun HadithHeader() {
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

/** Search bar — node 1:504. */
@Composable
private fun SearchBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
            .padding(start = 16.dp, end = 17.dp, top = 18.dp, bottom = 19.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = TextMutedLocal,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Search Hadith...",
            style = NiyyahType.Body,
            color = TextMutedLocal,
        )
    }
}

/** Daily Hadith — node 1:510. Wired to KnowledgeViewModel.dailyHadith. */
@Composable
private fun DailyHadithSection(dailyHadith: HadithEntity?) {
    val context = LocalContext.current
    val reference = dailyHadith
        ?.let { "${it.collectionDisplayName} ${it.globalNumber}".uppercase() }
        ?: "SAHIH AL-BUKHARI 1"
    // Only use the sample Arabic when there is no hadith at all — never pair
    // one hadith's Arabic with another's translation.
    val arabic = if (dailyHadith == null) {
        "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ،\nوَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى"
    } else {
        dailyHadith.arabicText.takeIf { it.isNotBlank() }
    }
    val translation = dailyHadith?.translationText
        ?: "Narrated 'Umar bin Al-Khattab: I heard Allah's Messenger (ﷺ) saying, \"The reward of deeds depends upon the intentions and every person will get the reward according to what he has intended.\""

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = "Daily Hadith",
            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
            color = NiyyahColors.TextPrimary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                .padding(25.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = reference,
                    style = NiyyahType.LabelUppercase,
                    color = TextBody,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_bookmark_outline),
                    contentDescription = "Bookmark",
                    tint = NiyyahColors.TextPrimary,
                    modifier = Modifier.width(14.dp).height(18.dp),
                )
            }
            if (arabic != null) Text(
                text = arabic,
                style = NiyyahType.Quote.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 36.sp,
                    lineHeight = 45.sp,
                    letterSpacing = (-0.36).sp,
                ),
                color = NiyyahColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
            Text(
                text = translation,
                style = NiyyahType.Body.copy(fontSize = 18.sp, lineHeight = 28.sp),
                color = TextBody,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x4DC5C6CE)),
                )
                Row(
                    modifier = Modifier
                        .padding(top = 17.dp)
                        .clickable {
                            val text = "$translation\n\n— ${dailyHadith?.fullCitation ?: "Sahih al-Bukhari, Book 1, Hadith 1"}"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        tint = NiyyahColors.TextPrimary,
                        modifier = Modifier.width(14.dp).height(15.dp),
                    )
                    Text(
                        text = "SHARE",
                        style = NiyyahType.LabelUppercase,
                        color = NiyyahColors.TextPrimary,
                    )
                }
            }
        }
    }
}

/** The Six Books — node 1:527. Static list mirroring the frame. */
@Composable
private fun SixBooksSection() {
    val books = listOf(
        "Sahih al-Bukhari" to "7,563 Hadiths • Imam Bukhari",
        "Sahih Muslim" to "3,033 Hadiths • Imam Muslim",
        "Sunan an-Nasa'i" to "5,758 Hadiths • Imam Nasa'i",
        "Sunan Abi Dawud" to "5,274 Hadiths • Abu Dawud",
    )
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "The Six Books",
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
            )
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                Text(
                    text = "VIEW ALL",
                    style = NiyyahType.LabelUppercase,
                    color = NiyyahColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NiyyahColors.TextPrimary),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            books.forEach { (title, subtitle) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NiyyahColors.Surface, NiyyahShapes.Chip)
                        .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
                        .padding(17.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = title,
                            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                            color = NiyyahColors.TextPrimary,
                        )
                        Text(text = subtitle, style = NiyyahType.Body, color = TextBody)
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        tint = TextMutedLocal,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Explore by Theme — node 1:566. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = "Explore by Theme",
            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
            color = NiyyahColors.TextPrimary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Prayer", "Fasting", "Charity", "Manners", "Knowledge").forEach { theme ->
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF2F0EC), NiyyahShapes.Chip)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = theme,
                        style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                        color = Color(0xFF666666),
                    )
                }
            }
        }
    }
}
