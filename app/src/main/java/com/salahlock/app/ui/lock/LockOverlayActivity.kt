package com.salahlock.app.ui.lock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.service.UsageStatsPollingService
import com.salahlock.app.theme.SalahLockTheme
import com.salahlock.app.theme.ThemeMode
import com.salahlock.app.ui.verification.VerificationFlowScreen
import kotlinx.coroutines.launch

/**
 * The full-screen lock overlay shown when a blacklisted app is detected.
 *
 * Production verification is handled by [VerificationFlowScreen] (text / voice).
 */
class LockOverlayActivity : ComponentActivity() {

    private val viewModel: LockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindowFlags()
        blockBackGesture()
        applyIntentState(intent)
        renderContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentState(intent)
    }

    private fun configureWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun blockBackGesture() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                android.util.Log.d("LockOverlay", "Back press intercepted — lock active.")
            }
        })
    }

    private fun applyIntentState(intent: Intent) {
        val name = intent.getStringExtra(UsageStatsPollingService.EXTRA_PRAYER_NAME)
        val prayer = try { PrayerName.valueOf(name ?: "FAJR") } catch (_: Exception) { PrayerName.FAJR }
        val lockEndMs = intent.getLongExtra(
            "lock_end_ms",
            System.currentTimeMillis() + 30 * 60_000L,
        )
        viewModel.init(prayer, lockEndMs)
        android.util.Log.d("LockOverlay", "State applied: prayer=$prayer endMs=$lockEndMs")
    }

    private fun renderContent() {
        setContent {
            SalahLockTheme(themeMode = ThemeMode.DARK) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                var inVerification by remember { mutableStateOf(false) }

                if (inVerification) {
                    VerificationFlowScreen(
                        prayer = state.prayer,
                        verificationMethod = state.verificationMethod,
                        confirmCount = state.confirmCount,
                        reminderQuran = state.reminderQuran,
                        reminderHadith = state.reminderHadith,
                        reminderReflection = state.reminderReflection,
                        currentStreak = state.currentStreak,
                        onSuccess = {
                            lifecycleScope.launch {
                                viewModel.recordPrayerCompleted()
                                finish()
                            }
                        },
                        onBack = { inVerification = false },
                    )
                } else {
                    LockOverlayScreen(
                        onVerifyPrayer = { inVerification = true },
                        onOverrideGranted = { finish() },
                        onLockExpired = { finish() },
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}
