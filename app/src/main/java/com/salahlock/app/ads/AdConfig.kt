package com.salahlock.app.ads

import com.salahlock.app.BuildConfig

/**
 * Single source of truth for AdMob ad-unit IDs and the master ad switch.
 *
 * The actual values come from [BuildConfig], which is configured per build type in
 * `app/build.gradle.kts`:
 *   - DEBUG   → Google official TEST ad units (never bill/serve production).
 *   - RELEASE → NIYYAH production ad units.
 *
 * UI/managers reference [bannerId] / [interstitialId] here rather than reading
 * BuildConfig directly, so ad IDs never get scattered across Compose screens.
 *
 * [adsEnabled] is the compile-time master switch. Every ad request is additionally
 * gated at runtime by [ConsentManager.canRequestAds].
 */
object AdConfig {
    val adsEnabled: Boolean get() = BuildConfig.ADS_ENABLED
    val bannerId: String get() = BuildConfig.ADMOB_BANNER_ID
    val interstitialId: String get() = BuildConfig.ADMOB_INTERSTITIAL_ID
}
