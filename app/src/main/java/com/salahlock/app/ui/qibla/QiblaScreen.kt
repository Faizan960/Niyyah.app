package com.salahlock.app.ui.qibla

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahShapes
import com.salahlock.app.ui.theme.NiyyahType
import java.util.Locale

/**
 * Qibla compass — Figma frame 1:1078 (light). Luxury watch aesthetic.
 *
 * Wired to the existing [QiblaViewModel]: the dial (ticks, letters, emerald
 * qibla indicator) rotates with -azimuth, the needle stays fixed, matching
 * the frame's static pose at azimuth 0.
 */
private val TickStone = Color(0xFFCEC0BB)

@Composable
fun QiblaScreen(viewModel: QiblaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startCompass()
        onDispose { viewModel.stopCompass() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        QiblaHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LocationContext(uiState)
            CompassDial(
                azimuth = uiState.azimuth,
                qiblaAngle = uiState.qiblaAngle,
                modifier = Modifier.padding(vertical = 40.dp),
            )
            MetadataCards(uiState)
        }
    }
}

/** Header — node 1:1110. Hamburger / QIBLA / bell. */
@Composable
private fun QiblaHeader() {
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
            Text(text = "QIBLA", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
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

/** Location context — node 1:1120. */
@Composable
private fun LocationContext(uiState: QiblaUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "CURRENT LOCATION",
            style = NiyyahType.Badge.copy(letterSpacing = 1.2.sp),
            color = NiyyahColors.TextBody,
        )
        Text(
            text = uiState.cityName.ifBlank { "Locating..." },
            style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_accuracy_target),
                contentDescription = null,
                tint = NiyyahColors.Green,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "${uiState.accuracy} Accuracy",
                style = NiyyahType.Body,
                color = NiyyahColors.Green,
            )
        }
    }
}

/** The compass — node 1:1152. 320dp bezel, rotating dial, fixed needle. */
@Composable
private fun CompassDial(azimuth: Float, qiblaAngle: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(320.dp)
            .shadow(10.dp, CircleShape, ambientColor = Color(0x0D1E2D4C), spotColor = Color(0x0D1E2D4C))
            .background(
                Brush.radialGradient(
                    colors = listOf(NiyyahColors.SurfaceElevated, NiyyahColors.Background),
                ),
                CircleShape,
            )
            .border(1.dp, NiyyahColors.Border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Rotating dial: inner circle, cardinal ticks, letters, qibla indicator.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(-azimuth),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width * 306.87f / 2f / 320f
                drawCircle(
                    color = Color(0xFFE7E2DA),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.59.dp.toPx()),
                )
                // Cardinal ticks — 12.72 long at N/S/W/E, stone color.
                val tickLen = 12.72.dp.toPx()
                val outer = size.width * 305.28f / 2f / 320f
                listOf(0f, 90f, 180f, 270f).forEach { deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val dx = kotlin.math.sin(rad).toFloat()
                    val dy = -kotlin.math.cos(rad).toFloat()
                    drawLine(
                        color = TickStone,
                        start = center + Offset(dx * (outer - tickLen), dy * (outer - tickLen)),
                        end = center + Offset(dx * outer, dy * outer),
                        strokeWidth = 1.59.dp.toPx(),
                    )
                }
            }
            CardinalLetter("N", Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
            CardinalLetter("S", Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
            CardinalLetter("W", Modifier.align(Alignment.CenterStart).padding(start = 25.dp))
            CardinalLetter("E", Modifier.align(Alignment.CenterEnd).padding(end = 25.dp))
            // Emerald qibla direction indicator — node 1:1170.
            Icon(
                painter = painterResource(R.drawable.ic_qibla_indicator),
                contentDescription = "Qibla direction",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(318.dp)
                    .rotate(qiblaAngle),
            )
        }
        // Fixed needle — node 1:1173.
        Column(
            modifier = Modifier.height(318.dp).padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_needle_north),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.width(16.dp).height(96.dp),
            )
            Icon(
                painter = painterResource(R.drawable.ic_needle_south),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.width(16.dp).height(96.dp).rotate(180f),
            )
        }
        // Center pivot — node 1:1180.
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(NiyyahColors.Gold, CircleShape)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}

@Composable
private fun CardinalLetter(letter: String, modifier: Modifier = Modifier) {
    Text(
        text = letter,
        style = NiyyahType.DisplayLarge.copy(fontSize = 51.sp, lineHeight = 66.sp, letterSpacing = 0.sp),
        color = NiyyahColors.Navy,
        modifier = modifier,
    )
}

/** Distance + status cards — node 1:1132. */
@Composable
private fun MetadataCards(uiState: QiblaUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MetadataCard(
            iconRes = R.drawable.ic_distance_route,
            label = "DISTANCE",
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = String.format(Locale.US, "%,d km", uiState.distanceKm),
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
            )
        }
        MetadataCard(
            iconRes = R.drawable.ic_status_sensor,
            label = "STATUS",
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Calibrated",
                style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                color = NiyyahColors.Green,
            )
        }
    }
}

@Composable
private fun MetadataCard(
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(NiyyahColors.Surface, NiyyahShapes.Chip)
            .border(1.dp, NiyyahColors.Border, NiyyahShapes.Chip)
            .padding(17.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = NiyyahColors.TextBody,
            modifier = Modifier.padding(bottom = 8.dp).size(18.dp),
        )
        Text(
            text = label,
            style = NiyyahType.Badge,
            color = NiyyahColors.TextBody,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        value()
    }
}
