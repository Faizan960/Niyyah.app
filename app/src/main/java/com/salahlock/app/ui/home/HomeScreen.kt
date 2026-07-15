package com.salahlock.app.ui.home

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.ui.components.ProfileAvatar
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Home — Figma frame 1:2 (light). Every measurement is taken from that frame. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onOpenProfile: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HomeHeader(photoUrl = uiState.userPhotoUrl, onOpenProfile = onOpenProfile)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            GreetingSection()
            IntentionCard()
            PrayerHeroCard(uiState)
            PrayerTimeline(uiState)
        }
    }
}

/**
 * Header — node 1:22. #FCF9F8 bar, 24dp sides, 16dp vertical.
 * BM-007: the left hamburger was removed (it opened nothing); the wordmark is now
 * centred in the bar and the profile avatar — the single Profile entry point — sits
 * at the trailing edge and opens the Profile screen.
 */
@Composable
private fun HomeHeader(photoUrl: String?, onOpenProfile: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.HeaderBackground)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "NIYYAH", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onOpenProfile() }
                .border(1.dp, NiyyahColors.Border, CircleShape)
                .padding(1.dp),
        ) {
            ProfileAvatar(
                photoUrl = photoUrl,
                contentDescription = "Profile",
                modifier = Modifier.matchParentSize().clip(CircleShape),
            )
        }
    }
}

/** Greeting — node 1:31. Hijri + Gregorian date over the salam, both centered. */
@Composable
private fun GreetingSection() {
    val today = LocalDate.now()
    val hijri = HijrahDate.from(today)
    val hijriMonth = hijri.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
    val dateLine = "${hijri.get(ChronoField.DAY_OF_MONTH)} $hijriMonth ${hijri.get(ChronoField.YEAR)} • " +
        today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = dateLine.uppercase(Locale.ENGLISH),
            style = NiyyahType.LabelUppercaseWide,
            color = NiyyahColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = "Assalamu Alaikum",
            style = NiyyahType.DisplayLarge,
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** Today's Intention — node 1:38. White card, 24dp radius, 25dp padding. */
@Composable
private fun IntentionCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, NiyyahShapes.Card)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Card)
            .padding(25.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_intention_heart),
            contentDescription = null,
            tint = NiyyahColors.Green,
            modifier = Modifier.width(20.dp).height(22.35.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "TODAY'S INTENTION",
                style = NiyyahType.LabelUppercase,
                color = NiyyahColors.TextSecondary,
            )
            Text(
                text = "\"To approach every task today with patience and seeking only His pleasure.\"",
                style = NiyyahType.Quote,
                color = NiyyahColors.TextPrimary,
            )
        }
    }
}

/** Prayer hero — node 1:46. 400dp navy card, mosque image, bottom gradient. */
@Composable
private fun PrayerHeroCard(uiState: HomeUiState) {
    val next = uiState.nextPrayer
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    val countdown = next?.let {
        val mins = ChronoUnit.MINUTES.between(LocalDateTime.now(), it.time)
        when {
            mins < 0 -> ""
            mins < 60 -> "in $mins mins"
            else -> "in ${mins / 60}h ${mins % 60}m"
        }
    } ?: ""
    val place = uiState.activeMasjidName.ifBlank { uiState.cityName }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .shadow(
                elevation = 40.dp,
                shape = NiyyahShapes.Hero,
                ambientColor = Color(0x261E2D4C),
                spotColor = Color(0x261E2D4C),
            )
            .clip(NiyyahShapes.Hero)
            .background(NiyyahColors.Navy),
    ) {
        // Figma: mosque image at 40% opacity with mix-blend-overlay onto the navy fill
        val mosque = ImageBitmap.imageResource(R.drawable.img_mosque_hero)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val scale = maxOf(size.width / mosque.width, size.height / mosque.height)
                    val srcW = (size.width / scale).toInt().coerceAtMost(mosque.width)
                    val srcH = (size.height / scale).toInt().coerceAtMost(mosque.height)
                    drawImage(
                        image = mosque,
                        srcOffset = IntOffset((mosque.width - srcW) / 2, (mosque.height - srcH) / 2),
                        srcSize = IntSize(srcW, srcH),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = 0.4f,
                        blendMode = BlendMode.Overlay,
                    )
                },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x001E2D4C),
                        0.5f to Color(0xCC1E2D4C),
                        1f to NiyyahColors.Navy,
                    ),
                ),
        )
        Column(
            modifier = Modifier.matchParentSize().padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .background(NiyyahColors.HeroBadgeBackground, NiyyahShapes.Pill)
                            .border(1.dp, NiyyahColors.HeroBadgeBorder, NiyyahShapes.Pill)
                            .padding(horizontal = 13.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "NEXT PRAYER",
                            style = NiyyahType.Badge,
                            color = NiyyahColors.OnNavy,
                        )
                    }
                    Text(
                        text = next?.name?.displayName ?: "—",
                        style = NiyyahType.DisplayLarge,
                        color = NiyyahColors.OnNavy,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = next?.time?.format(timeFormatter) ?: "",
                        style = NiyyahType.HeadingMedium,
                        color = NiyyahColors.OnNavy,
                    )
                    Text(
                        text = countdown,
                        style = NiyyahType.Body,
                        color = NiyyahColors.OnNavySecondary,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location_pin),
                        contentDescription = null,
                        tint = NiyyahColors.OnNavy,
                        modifier = Modifier.width(9.33.dp).height(11.67.dp),
                    )
                    Text(
                        text = place,
                        style = NiyyahType.Body,
                        color = NiyyahColors.OnNavyTertiary,
                    )
                }
                Row(
                    modifier = Modifier
                        .background(NiyyahColors.Surface, NiyyahShapes.Pill)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_walk),
                        contentDescription = null,
                        tint = NiyyahColors.Navy,
                        modifier = Modifier.width(9.75.dp).height(16.125.dp),
                    )
                    Text(
                        text = "Directions",
                        style = NiyyahType.ButtonLabel,
                        color = NiyyahColors.Navy,
                    )
                }
            }
        }
    }
}

/** Daily prayers timeline — node 1:71. Horizontal 112dp cards, active gets a gold dot. */
@Composable
private fun PrayerTimeline(uiState: HomeUiState) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    val prayers = uiState.todayPrayers?.toList() ?: emptyList()
    val nextName = uiState.nextPrayer?.name

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = "DAILY PRAYERS",
            style = NiyyahType.LabelUppercase,
            color = NiyyahColors.TextSecondary,
            modifier = Modifier.padding(start = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            prayers.forEach { prayer ->
                val completed = uiState.todayRecords[prayer.name]?.verified == true
                val active = prayer.name == nextName
                PrayerTimelineCard(
                    name = prayer.name,
                    time = prayer.time.format(timeFormatter),
                    completed = completed,
                    active = active,
                )
            }
        }
    }
}

@Composable
private fun PrayerTimelineCard(
    name: PrayerName,
    time: String,
    completed: Boolean,
    active: Boolean,
) {
    val iconRes = when {
        completed -> R.drawable.ic_prayer_check
        else -> when (name) {
            PrayerName.FAJR, PrayerName.SUNRISE, PrayerName.ASR -> R.drawable.ic_prayer_asr
            PrayerName.DHUHR -> R.drawable.ic_prayer_dhuhr
            PrayerName.MAGHRIB -> R.drawable.ic_prayer_maghrib
            PrayerName.ISHA -> R.drawable.ic_prayer_isha
        }
    }
    val iconTint = when {
        completed -> NiyyahColors.Green
        active -> NiyyahColors.TextPrimary
        else -> NiyyahColors.TextSecondary
    }
    val nameColor = if (completed) NiyyahColors.TextSecondary else NiyyahColors.TextPrimary
    val timeColor = if (active) NiyyahColors.TextPrimary else NiyyahColors.TextSecondary

    Box {
        Column(
            modifier = Modifier
                .alpha(if (completed) 0.6f else 1f)
                .width(112.dp)
                .height(116.dp)
                .then(
                    if (active) {
                        Modifier
                            .shadow(
                                elevation = 10.dp,
                                shape = NiyyahShapes.Card,
                                ambientColor = NiyyahColors.NavShadow,
                                spotColor = NiyyahColors.NavShadow,
                            )
                            .background(NiyyahColors.Surface, NiyyahShapes.Card)
                            .border(2.dp, NiyyahColors.TextPrimary, NiyyahShapes.Card)
                    } else {
                        Modifier
                            .background(
                                if (completed) NiyyahColors.SurfaceElevated else NiyyahColors.Surface,
                                NiyyahShapes.Card,
                            )
                            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Card)
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(if (active) 22.dp else 20.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(text = name.displayName, style = NiyyahType.LabelUppercase, color = nameColor)
                Text(
                    text = time,
                    style = if (active) NiyyahType.BodyMedium else NiyyahType.Body,
                    color = timeColor,
                )
            }
        }
        if (active) {
            // Gold indicator riding the top border — Figma node 1:91
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-8).dp)
                    .size(16.dp)
                    .background(NiyyahColors.Gold, CircleShape)
                    .border(2.dp, NiyyahColors.Surface, CircleShape),
            )
        }
    }
}
