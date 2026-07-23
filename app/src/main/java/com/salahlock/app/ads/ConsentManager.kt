package com.salahlock.app.ads

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google User Messaging Platform (UMP) consent gate for AdMob.
 *
 * Flow (per Google guidance):
 *   1. Request the latest consent info for the device/region.
 *   2. If a consent form is required, load & show it.
 *   3. Once consent is resolved, [canRequestAds] reflects whether ads may be
 *      requested. Only then do we initialize the Mobile Ads SDK and load ads.
 *
 * Fail-open: any UMP error (offline, form unavailable, etc.) leaves the app fully
 * functional. We simply fall back to whatever [ConsentInformation.canRequestAds]
 * reports (ads may or may not load) and never block core NIYYAH functionality.
 *
 * All ad entry points ([DailyInterstitialManager], the Home banner) must check
 * [canRequestAds] before requesting an ad.
 */
object ConsentManager {
    private const val TAG = "ConsentManager"

    /** True once ads are permitted to be requested (consent obtained or not required). */
    var canRequestAds: Boolean = false
        private set

    /**
     * Whether a user-accessible "Privacy choices" entry should be shown. Backed by
     * Compose state so Settings recomposes when the requirement is resolved.
     */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    // Ensures MobileAds.initialize runs at most once across the process.
    private val mobileAdsInitialized = AtomicBoolean(false)

    private var consentInformation: ConsentInformation? = null

    /**
     * Gathers consent (showing the UMP form if required) and, once resolved,
     * initializes the Mobile Ads SDK. [onReady] is invoked on the main thread with
     * the final [canRequestAds] value (whether or not a form was shown).
     */
    fun gatherConsent(activity: Activity, onReady: (Boolean) -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity.applicationContext)
        consentInformation = info
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    finish(activity, info, onReady)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                // Fall back to whatever the SDK already knows; never block the app.
                finish(activity, info, onReady)
            },
        )
    }

    private fun finish(activity: Activity, info: ConsentInformation, onReady: (Boolean) -> Unit) {
        canRequestAds = info.canRequestAds()
        privacyOptionsRequired =
            info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (canRequestAds) initializeMobileAdsOnce(activity)
        onReady(canRequestAds)
    }

    private fun initializeMobileAdsOnce(activity: Activity) {
        if (mobileAdsInitialized.getAndSet(true)) return
        runCatching { MobileAds.initialize(activity.applicationContext) {} }
            .onFailure { Log.w(TAG, "MobileAds init failed: ${it.message}") }
    }

    /**
     * Presents the UMP-managed privacy options form (used by the Settings
     * "Privacy choices" entry). No-op if consent info hasn't been gathered yet.
     */
    fun showPrivacyOptionsForm(activity: Activity) {
        val info = consentInformation ?: return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options form error: ${error.message}")
            // Requirement can change after the user updates choices.
            privacyOptionsRequired =
                info.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        }
    }
}
