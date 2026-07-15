package com.salahlock.app.ui.verification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.ui.theme.NiyyahTheme

/**
 * Prayer-verification flow, launched from [com.salahlock.app.ui.lock.LockOverlayActivity]
 * over the lock overlay. Hosts an internal NavHost:
 *
 *   method → voice | typing | camera → success
 *
 * On success the prayer is recorded (VerificationViewModel), which the
 * UsageStatsPollingService observes to end the lock window; both this activity
 * and the lock overlay then finish. This replaces the old placeholder
 * CameraVerificationActivity — there is exactly one verification flow.
 *
 * Intent extras: "prayer_name" (String, PrayerName.name).
 */
class VerificationActivity : ComponentActivity() {

    private val viewModel: VerificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prayer = intent.getStringExtra(EXTRA_PRAYER_NAME)
            ?.let { runCatching { PrayerName.valueOf(it) }.getOrNull() }
            ?: PrayerName.FAJR

        val app = application as SalahLockApplication

        setContent {
            // The lock/verification context is dark by design (it launches over the
            // dark lock overlay), so this focused flow always renders dark for a
            // seamless transition. VerifyPalette.light is the token-swap variant
            // (identical layouts) used for the light rendering of the typing screen.
            val isDark = true
            val confirmCount by app.userPreferences.verificationConfirmCount
                .collectAsState(initial = 3)

            LaunchedEffect(confirmCount) { viewModel.start(prayer, confirmCount) }

            NiyyahTheme(darkTheme = isDark) {
                val palette = VerifyPalette.of(isDark)
                val state by viewModel.uiState.collectAsState()
                val navController = rememberNavController()

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> if (granted) viewModel.startListening() }

                fun requestMicThenListen() {
                    val granted = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.startListening()
                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                NavHost(navController = navController, startDestination = ROUTE_METHOD) {
                    composable(ROUTE_METHOD) {
                        VerificationMethodScreen(
                            palette = palette,
                            selected = state.selectedMethod,
                            onSelect = viewModel::selectMethod,
                            onContinue = {
                                when (state.selectedMethod) {
                                    VerifyMethod.VOICE -> navController.navigate(ROUTE_VOICE)
                                    VerifyMethod.TYPING -> navController.navigate(ROUTE_TYPING)
                                    VerifyMethod.CAMERA -> navController.navigate(ROUTE_CAMERA)
                                }
                            },
                            onCancel = { finish() },
                        )
                    }
                    composable(ROUTE_VOICE) {
                        LaunchedEffect(state.status) {
                            if (state.status == AttemptStatus.SUCCESS) navController.navigate(ROUTE_SUCCESS)
                        }
                        VoiceVerificationScreen(
                            palette = palette,
                            state = state,
                            onBack = { viewModel.stopListening(); navController.popBackStack() },
                            onTapToSpeak = { requestMicThenListen() },
                            onRetry = { viewModel.retry() },
                            onSwitchToTyping = {
                                viewModel.stopListening()
                                viewModel.retry()
                                viewModel.selectMethod(VerifyMethod.TYPING)
                                navController.navigate(ROUTE_TYPING) { popUpTo(ROUTE_METHOD) }
                            },
                        )
                    }
                    composable(ROUTE_TYPING) {
                        LaunchedEffect(state.status) {
                            if (state.status == AttemptStatus.SUCCESS) navController.navigate(ROUTE_SUCCESS)
                        }
                        TypingVerificationScreen(
                            palette = palette,
                            state = state,
                            onBack = { navController.popBackStack() },
                            onTextChange = viewModel::onTypedTextChange,
                            onVerify = { viewModel.verifyTyped() },
                            onSwitchToVoice = {
                                viewModel.retry()
                                viewModel.selectMethod(VerifyMethod.VOICE)
                                navController.navigate(ROUTE_VOICE) { popUpTo(ROUTE_METHOD) }
                            },
                        )
                    }
                    composable(ROUTE_CAMERA) {
                        CameraComingSoonScreen(
                            palette = palette,
                            onBack = { navController.popBackStack() },
                            onNotifyMe = {
                                viewModel.selectMethod(VerifyMethod.VOICE)
                                navController.navigate(ROUTE_METHOD) { popUpTo(ROUTE_METHOD) { inclusive = true } }
                            },
                            onChooseAnother = {
                                navController.navigate(ROUTE_METHOD) { popUpTo(ROUTE_METHOD) { inclusive = true } }
                            },
                        )
                    }
                    composable(ROUTE_SUCCESS) {
                        UnlockSuccessScreen(
                            palette = palette,
                            onContinue = { finish() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PRAYER_NAME = "prayer_name"

        private const val ROUTE_METHOD = "method"
        private const val ROUTE_VOICE = "voice"
        private const val ROUTE_TYPING = "typing"
        private const val ROUTE_CAMERA = "camera"
        private const val ROUTE_SUCCESS = "success"
    }
}
