package com.salahlock.app.ui.lock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.salahlock.app.ui.navigation.PlaceholderScreen
import com.salahlock.app.ui.theme.NiyyahTheme

/**
 * Fullscreen lock overlay launched by UsageStatsPollingService when a blacklisted
 * app is opened during a prayer window.
 *
 * BM-005 stub: real UI arrives with the Salah Lock screen (Figma frame 1:1008).
 * Intent extras contract (unchanged): "prayer_name" (String), "lock_end_ms" (Long).
 */
class LockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NiyyahTheme {
                PlaceholderScreen("Salah Lock")
            }
        }
    }
}
