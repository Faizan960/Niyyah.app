package com.salahlock.app.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahType

/**
 * Profile — Figma frame 1:613 (light).
 *
 * NOTE: identity, stats and milestones are placeholder mirroring the frame
 * (no profile/reading data layer); quick links navigate to the real
 * Collections and Bookmarks screens.
 */
private val TextBody = Color(0xFF45474E)
private val CardRadius = RoundedCornerShape(12.dp)
private val ChipFill = Color(0xFFF2F0EC)
private val CircleFill = Color(0xFFF6F3F2)
private val RingGold = Color(0xFFEEC064)
private val MintFill = Color(0x338BF7CB)
private val GoldFill = Color(0x33EEC064)

@Composable
fun ProfileScreen(
    onOpenCollections: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ProfileHeader(onOpenSettings)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            IdentitySection()
            StatisticsCard()
            MilestonesSection()
            ReflectionPreviewCard()
            QuickLinks(onOpenCollections, onOpenBookmarks)
        }
    }
}

/** Header — node 1:648. Hamburger / NIYYAH / settings gear. */
@Composable
private fun ProfileHeader(onOpenSettings: () -> Unit) {
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
                painter = painterResource(R.drawable.ic_settings_outline),
                contentDescription = "Settings",
                tint = TextBody,
                modifier = Modifier.size(20.dp).clickable { onOpenSettings() },
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

/** Avatar + name — node 1:659. */
@Composable
private fun IdentitySection() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .border(1.dp, Color(0x4DC5C6CE), CircleShape)
                .padding(1.dp)
                .clip(CircleShape),
        ) {
            Image(
                painter = painterResource(R.drawable.img_profile_avatar),
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Aisha Rahman",
                style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(
                text = "Journeying towards inner peace",
                style = NiyyahType.Body,
                color = TextBody,
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Member since '23", "Student of Knowledge").forEach { label ->
                Box(
                    modifier = Modifier
                        .background(ChipFill, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(text = label, style = NiyyahType.Badge, color = Color(0xFF666666))
                }
            }
        }
    }
}

/** Personal statistics — node 1:673. */
@Composable
private fun StatisticsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, CardRadius)
            .border(1.dp, NiyyahColors.Border, CardRadius)
            .padding(25.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Personal Statistics",
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(bottom = 9.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NiyyahColors.Border),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatColumn(
                label = "PRAYER\nCONSISTENCY",
                bigText = "92%",
                sideText = "this week",
                progress = 0.92f,
                barColor = NiyyahColors.Navy,
                modifier = Modifier.weight(1f),
            )
            StatColumn(
                label = "READING\nPROGRESS",
                bigText = "Juz\n14",
                sideText = "Al-\nHijr",
                progress = 0.45f,
                barColor = RingGold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    bigText: String,
    sideText: String,
    progress: Float,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = NiyyahType.LabelUppercase.copy(letterSpacing = 1.4.sp),
            color = TextBody,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = bigText,
                style = NiyyahType.Quote.copy(fontSize = 36.sp, lineHeight = 36.sp, letterSpacing = (-0.36).sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(
                text = sideText,
                style = NiyyahType.Body,
                color = TextBody,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(CircleFill),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
        }
    }
}

/** Milestones bento — node 1:699. */
@Composable
private fun MilestonesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Milestones",
            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
            color = NiyyahColors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MilestoneTile(
                circleColor = MintFill,
                iconRes = R.drawable.ic_streak_calendar,
                title = "30 Day Streak",
                subtitle = "Fajr prayers",
                modifier = Modifier.weight(1f),
            )
            MilestoneTile(
                circleColor = GoldFill,
                iconRes = R.drawable.ic_khatam_book,
                title = "Khatam",
                subtitle = "Completed 1x",
                modifier = Modifier.weight(1f),
            )
        }
        // Set-new-goal dashed tile — node 1:721.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(342.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color(0xFFE7E2DA),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
                    ),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_plus_small),
                    contentDescription = null,
                    tint = TextBody,
                    modifier = Modifier.size(14.dp),
                )
                Text(text = "Set new goal", style = NiyyahType.Badge, color = TextBody)
            }
        }
    }
}

@Composable
private fun MilestoneTile(
    circleColor: Color,
    iconRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(6.dp, CardRadius, ambientColor = Color(0x0D1E2D4C), spotColor = Color(0x0D1E2D4C))
            .background(NiyyahColors.SurfaceElevated, CardRadius)
            .padding(horizontal = 16.dp, vertical = 27.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(circleColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier.width(22.dp).height(21.dp),
            )
        }
        Text(
            text = title,
            style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(text = subtitle, style = NiyyahType.Badge, color = TextBody, textAlign = TextAlign.Center)
    }
}

/** Monthly reflection preview — node 1:726. */
@Composable
private fun ReflectionPreviewCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardRadius)
            .background(NiyyahColors.Surface)
            .border(1.dp, NiyyahColors.Border, CardRadius),
    ) {
        Image(
            painter = painterResource(R.drawable.img_reflection_preview),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(128.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Patience in Stillness",
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Text(
                    text = "In the quiet moments before dawn, the heart finds its truest voice. This month's",
                    style = NiyyahType.Body,
                    color = TextBody,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column {
                    Text(
                        text = "READ FULL ENTRY",
                        style = NiyyahType.LabelUppercase.copy(letterSpacing = 1.4.sp),
                        color = NiyyahColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(136.dp)
                            .height(1.dp)
                            .background(NiyyahColors.TextPrimary),
                    )
                }
            }
            // Date badge — node 1:735.
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp)
                    .offset(y = (-32).dp)
                    .size(64.dp)
                    .shadow(8.dp, CircleShape)
                    .background(NiyyahColors.Navy, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "NOV",
                    style = NiyyahType.Badge.copy(letterSpacing = 1.2.sp, lineHeight = 12.sp),
                    color = Color.White,
                )
                Text(
                    text = "12",
                    style = NiyyahType.Quote.copy(lineHeight = 24.sp),
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Quick links — node 1:742. Navigate to the real Collections / Bookmarks. */
@Composable
private fun QuickLinks(onOpenCollections: () -> Unit, onOpenBookmarks: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        QuickLinkRow(
            iconRes = R.drawable.ic_quicklink_collections,
            title = "My Collections",
            subtitle = "Saved articles and lectures",
            onClick = onOpenCollections,
        )
        QuickLinkRow(
            iconRes = R.drawable.ic_bookmark_outline,
            title = "Bookmarks",
            subtitle = "Quick access to verses",
            onClick = onOpenBookmarks,
        )
    }
}

@Composable
private fun QuickLinkRow(iconRes: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, CardRadius)
            .border(1.dp, NiyyahColors.Border, CardRadius)
            .clickable { onClick() }
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(CircleFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(text = subtitle, style = NiyyahType.Badge, color = TextBody)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = TextBody,
            modifier = Modifier.width(8.dp).height(12.dp),
        )
    }
}
