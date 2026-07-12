package com.salahlock.app.data.model

import android.content.pm.ApplicationInfo

enum class AppCategory(val displayName: String) {
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    GAMES("Games"),
    SHOPPING("Shopping"),
    PRODUCTIVITY("Productivity"),
    FINANCE("Finance"),
    EDUCATION("Education"),
    UTILITIES("Utilities"),
    OTHER("Other"),
}

private val PACKAGE_CATEGORY_MAP = mapOf(
    // Social
    "com.instagram.android"       to AppCategory.SOCIAL,
    "com.facebook.katana"         to AppCategory.SOCIAL,
    "com.facebook.lite"           to AppCategory.SOCIAL,
    "com.twitter.android"         to AppCategory.SOCIAL,
    "com.x.android"               to AppCategory.SOCIAL,
    "com.snapchat.android"        to AppCategory.SOCIAL,
    "com.whatsapp"                to AppCategory.SOCIAL,
    "com.whatsapp.w4b"            to AppCategory.SOCIAL,
    "org.telegram.messenger"      to AppCategory.SOCIAL,
    "org.telegram.plus"           to AppCategory.SOCIAL,
    "com.zhiliaoapp.musically"    to AppCategory.SOCIAL,
    "com.ss.android.ugc.trill"    to AppCategory.SOCIAL,
    "com.pinterest"               to AppCategory.SOCIAL,
    "com.linkedin.android"        to AppCategory.SOCIAL,
    "in.mohalla.sharechat"        to AppCategory.SOCIAL,
    "com.reddit.frontpage"        to AppCategory.SOCIAL,
    "com.discord"                 to AppCategory.SOCIAL,
    "com.tumblr"                  to AppCategory.SOCIAL,
    "kik.android"                 to AppCategory.SOCIAL,
    "com.viber.voip"              to AppCategory.SOCIAL,
    "com.skype.raider"            to AppCategory.SOCIAL,

    // Entertainment
    "com.google.android.youtube"    to AppCategory.ENTERTAINMENT,
    "com.netflix.mediaclient"       to AppCategory.ENTERTAINMENT,
    "com.spotify.music"             to AppCategory.ENTERTAINMENT,
    "tv.twitch.android.app"         to AppCategory.ENTERTAINMENT,
    "com.amazon.avod.thirdpartyclient" to AppCategory.ENTERTAINMENT,
    "com.hotstar.android"           to AppCategory.ENTERTAINMENT,
    "com.jio.jioplay.tv"            to AppCategory.ENTERTAINMENT,
    "com.mxtech.videoplayer.ad"     to AppCategory.ENTERTAINMENT,
    "com.mxtech.videoplayer.pro"    to AppCategory.ENTERTAINMENT,
    "com.jiofimovies"               to AppCategory.ENTERTAINMENT,
    "com.zee5.android"              to AppCategory.ENTERTAINMENT,
    "com.sony.liv"                  to AppCategory.ENTERTAINMENT,
    "air.com.vuclip.VuClip"         to AppCategory.ENTERTAINMENT,
    "com.google.android.apps.youtube.music" to AppCategory.ENTERTAINMENT,
    "com.apple.android.music"       to AppCategory.ENTERTAINMENT,
    "com.gaana"                     to AppCategory.ENTERTAINMENT,
    "com.wynk.music"                to AppCategory.ENTERTAINMENT,
    "com.jio.media.jiostream"       to AppCategory.ENTERTAINMENT,

    // Shopping
    "com.amazon.mShop.android.shopping" to AppCategory.SHOPPING,
    "com.flipkart.android"          to AppCategory.SHOPPING,
    "com.myntra.android"            to AppCategory.SHOPPING,
    "com.meesho.supply"             to AppCategory.SHOPPING,
    "com.snapdeal.main"             to AppCategory.SHOPPING,
    "com.ajio.android"              to AppCategory.SHOPPING,
    "com.ebay.mobile"               to AppCategory.SHOPPING,
    "com.bigbasket.mobileapp"       to AppCategory.SHOPPING,
    "com.blinkit.consumer"          to AppCategory.SHOPPING,
    "com.zomato.android"            to AppCategory.SHOPPING,
    "app.swiggy.android"            to AppCategory.SHOPPING,

    // Finance
    "com.google.android.apps.nbu.paisa.user" to AppCategory.FINANCE,
    "net.one97.paytm"               to AppCategory.FINANCE,
    "com.phonepe.app"               to AppCategory.FINANCE,
    "in.amazon.mShop.android.shopping" to AppCategory.FINANCE,
    "com.bhim.axisbank"             to AppCategory.FINANCE,
    "com.mobikwik_new"              to AppCategory.FINANCE,
    "com.freecharge.android"        to AppCategory.FINANCE,

    // Utilities (browsers)
    "com.android.chrome"            to AppCategory.UTILITIES,
    "com.google.android.apps.chrome" to AppCategory.UTILITIES,
    "org.mozilla.firefox"           to AppCategory.UTILITIES,
    "com.opera.browser"             to AppCategory.UTILITIES,
    "com.microsoft.emmx"            to AppCategory.UTILITIES,
    "com.brave.browser"             to AppCategory.UTILITIES,
    "com.UCMobile.intl"             to AppCategory.UTILITIES,
    "com.duckduckgo.mobile.android" to AppCategory.UTILITIES,
    "com.mi.globalbrowser"          to AppCategory.UTILITIES,
)

/**
 * Detects the app category for a given package.
 * Checks the explicit package map first, then falls back to the OS-provided category
 * (API 26+ ApplicationInfo.category), then defaults to OTHER.
 */
fun detectCategory(packageName: String, appInfoCategory: Int): AppCategory {
    PACKAGE_CATEGORY_MAP[packageName]?.let { return it }

    return when (appInfoCategory) {
        ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
        ApplicationInfo.CATEGORY_AUDIO -> AppCategory.ENTERTAINMENT
        ApplicationInfo.CATEGORY_VIDEO -> AppCategory.ENTERTAINMENT
        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> AppCategory.ENTERTAINMENT
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_MAPS -> AppCategory.UTILITIES
        ApplicationInfo.CATEGORY_IMAGE -> AppCategory.UTILITIES
        else -> AppCategory.OTHER
    }
}
