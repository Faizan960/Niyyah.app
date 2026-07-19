package com.salahlock.app.ui.qibla

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.R
import com.salahlock.app.theme.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun QiblaScreen(
    viewModel: QiblaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        viewModel.startCompass()
        onDispose {
            viewModel.stopCompass()
        }
    }

    // Determine alignment state
    val normalizedAzimuth = (state.azimuth % 360f + 360f) % 360f
    val angleDiff = abs(normalizedAzimuth - state.qiblaAngle).let { diff ->
        if (diff > 180f) 360f - diff else diff
    }
    
    val isAligned = angleDiff <= 3f
    val isNear = angleDiff <= 15f && !isAligned

    // Haptic feedback logic (trigger only once when entering alignment zone)
    var hasVibratedForCurrentLock by remember { mutableStateOf(false) }
    LaunchedEffect(isAligned) {
        if (isAligned && !hasVibratedForCurrentLock) {
            triggerHaptic(context)
            hasVibratedForCurrentLock = true
        } else if (!isAligned) {
            hasVibratedForCurrentLock = false
        }
    }

        // Stitch V2 qibla: clean canvas, location chip on top, compass center,
        // serif degree readout + "Distance to Kaaba" below. No split background.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.locationMissing) {
                MissingLocationState()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 32.dp, bottom = 90.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Qibla",
                        fontFamily = NiyyahSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Location chip — uppercase city, hairline border
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = state.cityName.ifEmpty { "Location Active" }.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    CompassDial(state = state, isAligned = isAligned)
                    Spacer(Modifier.weight(1f))

                    // Degree readout — serif, aligned turns emerald
                    val heading = ((state.azimuth % 360f + 360f) % 360f).roundToInt()
                    Text(
                        text = "$heading°",
                        fontFamily = NiyyahSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 56.sp,
                        lineHeight = 60.sp,
                        color = if (isAligned) EmeraldSecondary else MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "DISTANCE TO KAABA",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "%,d".format(state.distanceKm),
                            fontFamily = NiyyahSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = " km",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
            }
        }
}

@Composable
private fun CompassDial(state: QiblaUiState, isAligned: Boolean) {
    val compassRotation by animateFloatAsState(
        targetValue = -state.azimuth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    // Gold ring per Stitch (emerald when aligned); dial surface follows the theme
    val restRing = GoldAccent
    val ringGlow by animateColorAsState(
        targetValue = if (isAligned) EmeraldPrimary else restRing,
        animationSpec = tween(MotionTokens.Slow)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        // Glowing ring border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(ringGlow.copy(alpha = 0.08f))
                .border(
                    width = 1.5.dp,
                    color = ringGlow.copy(alpha = 0.4f),
                    shape = CircleShape,
                ),
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
        ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_islamic_pattern),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
            modifier = Modifier.fillMaxSize(0.8f)
        )

        // The Rotating Dial (North, East, South, West marks + Kaaba Pointer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(compassRotation)
        ) {
            // Cardinal letters — N in gold, others in MutedSage
            Text("N", modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp), color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("E", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), color = MutedSage, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text("S", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), color = MutedSage, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text("W", modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp), color = MutedSage, fontWeight = FontWeight.Medium, fontSize = 13.sp)

            // Precision tick marks — gold for major, MutedSage for minor
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                for (i in 0..359 step 5) {
                    val angle = Math.toRadians(i.toDouble() - 90.0)
                    val isMajor = i % 90 == 0
                    val isMid = i % 45 == 0 && !isMajor
                    val lineLength = when { isMajor -> 14.dp.toPx(); isMid -> 9.dp.toPx(); else -> 5.dp.toPx() }
                    val strokeWidth = when { isMajor -> 2.dp.toPx(); isMid -> 1.2.dp.toPx(); else -> 0.8.dp.toPx() }
                    val tickColor = when { isMajor -> GoldAccent.copy(alpha = 0.7f); isMid -> MutedSage.copy(alpha = 0.4f); else -> MutedSage.copy(alpha = 0.2f) }

                    val startX = (radius + (radius - lineLength) * Math.cos(angle)).toFloat()
                    val startY = (radius + (radius - lineLength) * Math.sin(angle)).toFloat()
                    val endX = (radius + radius * Math.cos(angle)).toFloat()
                    val endY = (radius + radius * Math.sin(angle)).toFloat()

                    drawLine(
                        color = tickColor,
                        start = androidx.compose.ui.geometry.Offset(startX, startY),
                        end = androidx.compose.ui.geometry.Offset(endX, endY),
                        strokeWidth = strokeWidth,
                    )
                }
            }
        }

        // Qibla pointer — gold, rotates to Kaaba direction
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(compassRotation + state.qiblaAngle)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropUp,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(32.dp)
                )
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_kaaba),
                    contentDescription = "Kaaba",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // Fixed direction indicator — gold when not aligned, emerald when aligned
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = if (isAligned) EmeraldPrimary else GoldAccent.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp)
                .rotate(180f)
                .size(48.dp)
        )
    }
    }
    }
}

@Composable
private fun MissingLocationState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Location Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enable location access to determine the Qibla direction.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun triggerHaptic(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}
