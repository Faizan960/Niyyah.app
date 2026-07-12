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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Split Background & Mosque Silhouettes
            MosqueSplitBackground()

            if (state.locationMissing) {
                MissingLocationState()
            } else {
                // Top Section (Anchored to top)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp)
                ) {
                    TopPrayerInfo(state)
                }

                // Compass Section (Anchored to bottom)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp) // Accounts for bottom navigation space
                ) {
                    CompassDial(state = state, isAligned = isAligned)
                }
            }
        }
}

@Composable
private fun TopPrayerInfo(state: QiblaUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.nextPrayer != null) {
            Text(
                text = state.nextPrayer.name.name,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Until",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp, bottom = 0.dp)
            )
            
            val formattedTime = state.nextPrayer.time.format(DateTimeFormatter.ofPattern("hh:mm"))
            val amPm = state.nextPrayer.time.format(DateTimeFormatter.ofPattern("a"))
            Row {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    text = amPm,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline().padding(start = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = state.cityName.ifEmpty { "Location Active" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Cardinal direction calculation
        val cardinals = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = (((state.azimuth % 360f + 360f) % 360f) / 45).roundToInt() % 8
        val cardinalStr = cardinals[index]
        
        Text(
            text = "${((state.azimuth % 360f + 360f) % 360f).roundToInt()} $cardinalStr",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Connect both arrows pill
        Surface(
            color = Color.White.copy(alpha = 0.15f),
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info, // Need to import this
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Connect both arrows",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
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

    val ringGlow by animateColorAsState(
        targetValue = if (isAligned) EmeraldPrimary else Color.White,
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
            color = DarkSurface,
            shadowElevation = 0.dp,
        ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_islamic_pattern),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.03f),
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
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enable location access to determine the Qibla direction.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
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

@Composable
private fun MosqueSplitBackground() {
    // SL-005 - generated dome/minaret Canvas shapes replaced with a proper SVG
    // silhouette (ic_mosque_silhouette). The curved navy backdrop is kept - it is
    // the section divider, not an illustration.
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val curveBase = h * 0.55f
            val bgPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w, curveBase)
                quadraticTo(w / 2f, curveBase + 180f, 0f, curveBase)
                close()
            }
            drawPath(bgPath, color = DeepNavy)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.53f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_mosque_silhouette),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth(0.96f).height(110.dp),
            )
        }
    }
}
