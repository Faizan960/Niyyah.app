package com.salahlock.app.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.data.preferences.UserPreferences
import com.salahlock.app.ui.theme.EbGaramond
import com.salahlock.app.ui.theme.HankenGrotesk
import com.salahlock.app.ui.theme.NiyyahColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val SPLASH_DURATION_MS = 1600L

/**
 * Splash — design/Reference/Splash screen.png.
 *
 * Shows the emblem + wordmark, then hands off to Home (returning user)
 * or Onboarding (first launch) based on [UserPreferences.onboardingDone].
 */
@Composable
fun SplashScreen(onFinished: (onboardingDone: Boolean) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = UserPreferences(context.applicationContext)
        delay(SPLASH_DURATION_MS)
        onFinished(prefs.onboardingDone.first())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NiyyahColors.Background),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Emblem()
            Spacer(modifier = Modifier.height(20.dp))
            // Soft reflection under the emblem
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(26.dp)
                    .blur(8.dp)
                    .clip(CircleShape)
                    .background(Color(0x1A071836)),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "NIYYAH",
                fontFamily = EbGaramond,
                fontWeight = FontWeight.Medium,
                fontSize = 44.sp,
                lineHeight = 52.sp,
                letterSpacing = 1.5.sp,
                color = NiyyahColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "LIVE WITH INTENTION.",
                fontFamily = HankenGrotesk,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 1.3.sp,
                color = NiyyahColors.TextSecondary,
            )
        }
        // Thin vertical line near the bottom edge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
                .width(1.dp)
                .height(48.dp)
                .background(Color(0x66374B6B)),
        )
    }
}

/** Concentric-ring emblem with the star/crescent mark at its center. */
@Composable
private fun Emblem() {
    Box(
        modifier = Modifier
            .size(104.dp)
            .border(1.dp, Color(0xFFDDD9D1), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .border(1.dp, Color(0xFF9BA4B5), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .border(1.dp, Color(0xFFC6CBD6), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_splash_emblem),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
