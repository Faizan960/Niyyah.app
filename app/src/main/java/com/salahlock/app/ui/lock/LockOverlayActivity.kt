package com.salahlock.app.ui.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.ui.theme.NiyyahTheme
import com.salahlock.app.ui.verification.VerificationActivity
import kotlinx.coroutines.launch

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

        // Dismiss the overlay once the prayer is verified in the verification flow.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.verified.collect { verified -> if (verified) finish() }
            }
        }

        setContent {
            NiyyahTheme {
                SalahLockScreen(
                    onEmergency = { viewModel.showOverrideSheet() },
                    onVerify = {
                        startActivity(
                            Intent(this, VerificationActivity::class.java).apply {
                                putExtra(VerificationActivity.EXTRA_PRAYER_NAME, prayer.name)
                            },
                        )
                    },
                    viewModel = viewModel,
                )
            }
        }
    }
}
