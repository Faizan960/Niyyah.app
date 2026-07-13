package com.salahlock.app.ui.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.ui.camera.CameraVerificationActivity
import com.salahlock.app.ui.theme.NiyyahTheme

/**
 * Fullscreen lock overlay launched by UsageStatsPollingService when a blacklisted
 * app is opened during a prayer window — Figma frame 1:1008.
 *
 * Intent extras contract (unchanged): "prayer_name" (String), "lock_end_ms" (Long).
 */
class LockOverlayActivity : ComponentActivity() {
    private val viewModel: LockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prayer = intent.getStringExtra("prayer_name")
            ?.let { runCatching { PrayerName.valueOf(it) }.getOrNull() }
            ?: PrayerName.FAJR
        val lockEndMs = intent.getLongExtra("lock_end_ms", 0L)
        viewModel.init(prayer, lockEndMs)

        setContent {
            NiyyahTheme {
                SalahLockScreen(
                    onEmergency = { viewModel.showOverrideSheet() },
                    onVerify = {
                        startActivity(Intent(this, CameraVerificationActivity::class.java))
                    },
                    viewModel = viewModel,
                )
            }
        }
    }
}
