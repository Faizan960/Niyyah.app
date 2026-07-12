package com.salahlock.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.salahlock.app.BuildConfig
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.service.UsageStatsPollingService
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.first

/**
 * One interstitial per user per 24h, shown only when the user voluntarily opens
 * SalahLock and lands on Home. Fails open: if no ad is cached the app continues
 * instantly (0 ms wait). Reuses [UserPreferences] for the timestamp — no new
 * repository / DataStore / ViewModel.
 *
 * Lifecycle: a single cached [InterstitialAd]; cleared after show/dismiss and
 * reloaded for next time. Never holds an Activity reference beyond [show].
 */
object DailyInterstitialManager {
    private const val TAG = "DailyInterstitial"
    private const val TWENTY_FOUR_HOURS_MS = 24L * 60 * 60 * 1000

    @Volatile private var ad: InterstitialAd? = null
    @Volatile private var loading = false

    /** Preloads (and caches) the next interstitial. Safe to call repeatedly. */
    fun preload(context: Context) {
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded; loading = false
                    Log.d(TAG, "Interstitial loaded.")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null; loading = false
                    Log.d(TAG, "Interstitial load failed: ${error.message}")
                }
            },
        )
    }

    /**
     * Shows the daily interstitial iff eligible. Eligibility:
     *  - 24h elapsed since last shown
     *  - an ad is cached (else fail open + preload for next time)
     *  - no lock window is active (never interrupt the prayer/lock flow)
     *
     * Records the timestamp before showing so a missed/aborted show still counts
     * for the day (we never want to risk showing twice).
     */
    suspend fun maybeShow(activity: Activity, app: SalahLockApplication) {
        // Never during an active prayer-lock window.
        if (UsageStatsPollingService.isRunning) return

        val prefs = app.userPreferences
        val last = prefs.lastInterstitialShown.first()
        val now = System.currentTimeMillis()
        if (now - last < TWENTY_FOUR_HOURS_MS) return

        val current = ad ?: run {
            // Fail open — continue immediately and warm the cache for next launch.
            preload(app)
            return
        }

        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { ad = null; preload(app) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { ad = null; preload(app) }
        }
        prefs.setLastInterstitialShown(now)
        ad = null
        current.show(activity)
    }
}
