package com.salahlock.app.ads

import android.util.Log
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Adaptive anchored banner for the Home screen only.
 *
 * Safety:
 *  - Renders nothing unless ads are enabled AND UMP consent allows ad requests, so
 *    there is never a blank permanent ad container (the composable simply occupies
 *    no space until/unless an ad is available).
 *  - Fails silently: a load failure logs (debug-stripped) and leaves an empty,
 *    zero-noise slot. No crash, no spinner, no retry storm.
 *  - Lifecycle-correct: pauses/resumes with the host lifecycle and calls
 *    [AdView.destroy] on dispose to avoid leaking the AdView.
 *
 * This must NOT be placed in Quran/Hadith readers, the lock overlay, or any
 * verification flow.
 */
@Composable
fun HomeAdBanner(modifier: Modifier = Modifier) {
    if (!AdConfig.adsEnabled || !ConsentManager.canRequestAds) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val widthDp = LocalConfiguration.current.screenWidthDp

    val adView = remember {
        AdView(context).apply {
            adUnitId = AdConfig.bannerId
            setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp),
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d("HomeAdBanner", "Banner load failed: ${error.message}")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    // Mirror the host lifecycle onto the AdView, and destroy it on dispose.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(modifier = modifier, factory = { adView })
}
