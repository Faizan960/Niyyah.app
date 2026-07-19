package com.salahlock.app.ui.lock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.service.UsageStatsPollingService
import com.salahlock.app.theme.SalahLockTheme
import com.salahlock.app.theme.ThemeMode
import com.salahlock.app.ui.camera.CameraVerificationActivity
import com.salahlock.app.ui.verification.VerificationFlowScreen
import com.salahlock.app.verification.VerificationMethod
import com.salahlock.app.verification.VerificationMethod.Companion.effective
import kotlinx.coroutines.launch

/**
 * The full-screen lock overlay shown when a blacklisted app is detected.
 *
 * Production verification is handled by [VerificationFlowScreen] (text / voice).
 *
 * The [cameraLauncher] is preserved as dormant infrastructure for Sprint ML.3+.
 * It is only activated when [VerificationMethod.CAMERA_AI_FUTURE] is the active method —
 * which is not exposed in any v1 UI.
 */
class LockOverlayActivity : ComponentActivity() {

    private val viewModel: LockViewModel by viewModels()

    // ── DORMANT CAMERA INFRASTRUCTURE ─────────────────────────────────────────
    // Preserved for Sprint ML.3+ (TFLite prayer environment verification).
    // Not reachable from any v1 UI — VerificationMethod.CAMERA_AI_FUTURE is never
    // selectable by the user in this release.
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            lifecycleScope.launch {
                viewModel.recordPrayerCompleted()
                Toast.makeText(this@LockOverlayActivity, "May Allah accept your prayer.", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            Toast.makeText(this, "Verification didn't complete. You can try again anytime.", Toast.LENGTH_SHORT).show()
        }
    }

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
                        onVerifyPrayer = {
                            when (state.verificationMethod.effective()) {
                                VerificationMethod.CAMERA_AI_FUTURE -> {
                                    // Future ML path — dormant in v1
                                    val intent = Intent(this@LockOverlayActivity, CameraVerificationActivity::class.java).apply {
                                        putExtra(UsageStatsPollingService.EXTRA_PRAYER_NAME, state.prayer.name)
                                    }
                                    cameraLauncher.launch(intent)
                                }
                                else -> inVerification = true
                            }
                        },
                        onOverrideGranted = { finish() },
                        onLockExpired = { finish() },
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}
