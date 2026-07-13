package com.salahlock.app.ui.prayers

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.data.model.PrayerTime
import com.salahlock.app.ui.home.HomeUiState
import com.salahlock.app.ui.home.HomeViewModel
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Prayer — Figma frame 1:116 (light). */
@Composable
fun PrayerScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PrayerHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 136.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LocationDateHeader(uiState)
            NextPrayerCard(uiState, onOpenSettings)
            ScheduleList(uiState)
        }
    }
}

/** Header — node 1:117. Same bar as Home but with a 32dp avatar. */
@Composable
private fun PrayerHeader() {
    Box(modifier = Modifier.fillMaxWidth().background(NiyyahColors.HeaderBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_menu),
                contentDescription = "Menu",
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(8.dp).width(18.dp).height(12.dp),
            )
            Text(text = "NIYYAH", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
            Box(modifier = Modifier.size(32.dp).clip(CircleShape)) {
                Image(
                    painter = painterResource(R.drawable.img_user_profile),
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

/** Location + date block — node 1:127. */
@Composable
private fun LocationDateHeader(uiState: HomeUiState) {
    val today = LocalDate.now()
    val hijri = HijrahDate.from(today)
    val hijriLine = hijri.format(DateTimeFormatter.ofPattern("d MMMM y", Locale.ENGLISH)) + " AH"
    val place = uiState.cityName.ifBlank { "—" }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_location_pin_alt),
                contentDescription = null,
                tint = NiyyahColors.TextSecondary,
                modifier = Modifier.width(10.67.dp).height(13.33.dp),
            )
            Text(
                text = place.uppercase(Locale.ENGLISH),
                style = NiyyahType.LabelUppercaseWide,
                color = NiyyahColors.TextSecondary,
            )
        }
        Text(
            text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)),
            style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = hijriLine,
            style = NiyyahType.Body,
            color = NiyyahColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Next-prayer hero — node 1:136. White 16dp card with subtle radial glow. */
@Composable
private fun NextPrayerCard(uiState: HomeUiState, onOpenSettings: () -> Unit) {
    val next = uiState.nextPrayer
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    val remaining = next?.let {
        val d = Duration.between(LocalDateTime.now(), it.time)
        if (d.isNegative) null
        else String.format(
            Locale.ENGLISH, "- %02d : %02d : %02d",
            d.toHours(), d.toMinutes() % 60, d.seconds % 60,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, NiyyahColors.Border, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        NiyyahColors.Navy.copy(alpha = 0.05f),
                        NiyyahColors.Navy.copy(alpha = 0f),
                    ),
                ),
            ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings_gear),
            contentDescription = "Prayer settings",
            tint = NiyyahColors.TextSecondary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(20.dp)
                .clickable { onOpenSettings() },
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(25.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "NEXT PRAYER",
                style = NiyyahType.LabelUppercaseWide,
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = next?.name?.displayName ?: "—",
                style = NiyyahType.DisplayLarge,
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = next?.time?.format(timeFormatter) ?: "",
                style = NiyyahType.Quote,
                color = NiyyahColors.TextSecondary,
                modifier = Modifier.padding(bottom = 24.dp),
            )
            if (remaining != null) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .background(NiyyahColors.SoftFill, NiyyahShapes.Pill)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_countdown_timer),
                        contentDescription = null,
                        tint = NiyyahColors.TextPrimary,
                        modifier = Modifier.width(13.5.dp).height(15.75.dp),
                    )
                    Text(
                        text = remaining,
                        style = NiyyahType.ButtonLabel,
                        color = NiyyahColors.TextPrimary,
                    )
                }
            }
        }
    }
}

/** Today's schedule — node 1:156. */
@Composable
private fun ScheduleList(uiState: HomeUiState) {
    val prayers = uiState.todayPrayers?.toFullList() ?: emptyList()
    val nextName = uiState.nextPrayer?.name

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "TODAY'S SCHEDULE",
            style = NiyyahType.LabelUppercaseWide,
            color = NiyyahColors.TextSecondary,
        )
        prayers.forEach { prayer ->
            if (prayer.name == PrayerName.SUNRISE) {
                SunriseRow(prayer)
            } else {
                PrayerRow(
                    prayer = prayer,
                    isNext = prayer.name == nextName,
                    isCompleted = uiState.todayRecords[prayer.name]?.verified == true,
                )
            }
        }
    }
}

/** Info-only sunrise row — node 1:175. */
@Composable
private fun SunriseRow(prayer: PrayerTime) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    Row(
        modifier = Modifier.fillMaxWidth().alpha(0.6f).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_prayer_sunrise),
                    contentDescription = null,
                    tint = NiyyahColors.TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(text = "Sunrise", style = NiyyahType.Body, color = NiyyahColors.TextSecondary)
        }
        Text(
            text = prayer.time.format(timeFormatter),
            style = NiyyahType.Body,
            color = NiyyahColors.TextSecondary,
        )
    }
}

/** Prayer schedule row — nodes 1:159 (completed), 1:185 (next), 1:199+ (upcoming). */
@Composable
private fun PrayerRow(prayer: PrayerTime, isNext: Boolean, isCompleted: Boolean) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    val iconRes = when (prayer.name) {
        PrayerName.FAJR -> R.drawable.ic_prayer_fajr
        PrayerName.SUNRISE -> R.drawable.ic_prayer_sunrise
        PrayerName.DHUHR -> R.drawable.ic_prayer_dhuhr_alt
        PrayerName.ASR -> R.drawable.ic_prayer_asr_alt
        PrayerName.MAGHRIB -> R.drawable.ic_prayer_maghrib_alt
        PrayerName.ISHA -> R.drawable.ic_prayer_isha_alt
    }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isNext) {
                    Modifier
                        .shadow(
                            elevation = 10.dp,
                            shape = shape,
                            ambientColor = NiyyahColors.Navy.copy(alpha = 0.05f),
                            spotColor = NiyyahColors.Navy.copy(alpha = 0.05f),
                        )
                        .background(NiyyahColors.Surface, shape)
                        .border(1.dp, NiyyahColors.TextPrimary, shape)
                } else {
                    Modifier
                        .background(NiyyahColors.Surface, shape)
                        .border(1.dp, NiyyahColors.Border, shape)
                },
            )
            .padding(17.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .alpha(if (isCompleted) 0.5f else 1f)
                    .size(40.dp)
                    .background(
                        if (isNext) NiyyahColors.TextPrimary else NiyyahColors.SoftFill,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (isNext) NiyyahColors.Surface else NiyyahColors.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    text = prayer.name.displayName,
                    style = NiyyahType.Quote.copy(
                        fontSize = 20.sp,
                        lineHeight = 25.sp,
                        fontWeight = if (isNext) androidx.compose.ui.text.font.FontWeight.SemiBold
                        else androidx.compose.ui.text.font.FontWeight.Normal,
                    ),
                    color = NiyyahColors.TextPrimary,
                )
                Text(
                    text = prayer.time.format(timeFormatter),
                    style = NiyyahType.Body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = if (isNext) NiyyahColors.TextPrimary else NiyyahColors.TextSecondary,
                )
            }
        }
        if (isCompleted) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(NiyyahColors.TextPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_small),
                    contentDescription = "Completed",
                    tint = NiyyahColors.Surface,
                    modifier = Modifier.width(9.5.dp).height(7.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(1.dp, NiyyahColors.TextSecondary, CircleShape),
            )
        }
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
