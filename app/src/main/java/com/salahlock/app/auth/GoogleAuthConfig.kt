package com.salahlock.app.auth

/**
 * Google OAuth configuration.
 *
 * To enable Google Sign-In:
 * 1. Go to https://console.cloud.google.com/apis/credentials
 * 2. Create an OAuth 2.0 Web Application client ID for your project
 * 3. Also create an Android client ID (SHA-1 fingerprint required)
 * 4. Replace WEB_CLIENT_ID below with your Web client ID
 *
 * The Web client ID (not the Android one) is used with Credential Manager.
 */
object GoogleAuthConfig {
    const val WEB_CLIENT_ID = "295290632900-tk0dp1jea520br61ivfrtqiu5m13mrbq.apps.googleusercontent.com"
    val isConfigured: Boolean get() = !WEB_CLIENT_ID.startsWith("YOUR_")
}
