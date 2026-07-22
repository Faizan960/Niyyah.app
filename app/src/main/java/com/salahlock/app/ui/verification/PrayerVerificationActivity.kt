package com.salahlock.app.ui.verification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.PrayerName
import com.salahlock.app.data.preferences.UserPreferences.ThemePreference
import com.salahlock.app.theme.SalahLockTheme
import com.salahlock.app.theme.ThemeMode
import com.salahlock.app.ui.lock.LockViewModel
import kotlinx.coroutines.launch

/**
 * BM-HOME-PRAYER-UX — voluntary verification launched from Home / Today's Prayers.
 *
 * Hosts the SAME canonical [VerificationFlowScreen] the lock overlay uses (typing /
 * voice, reminder, success), for a specific [PrayerName] passed at launch. On success
 * it records completion through the single completion authority
 * ([LockViewModel.recordPrayerCompleted] → StreakRepository → PrayerRecordDao) and
 * returns [RESULT_OK]. There is no lock, no second verification flow, and no second
 * completion path.
 *
 * The prayer is fixed at [onCreate] via [LockViewModel.init] and is never re-inferred
 * from the wall clock during the flow — so verifying "Asr" always records ASR.
 */
class PrayerVerificationActivity : ComponentActivity() {

    private val viewModel: LockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prayer = runCatching {
            PrayerName.valueOf(intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "")
        }.getOrNull()

        if (prayer == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        viewModel.init(prayer, lockEndMs = 0L)

        setContent {
            val app = applicationContext as SalahLockApplication
            val themePreference by app.userPreferences.themePreference
                .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)
            val themeMode = when (themePreference) {
                ThemePreference.SYSTEM ->
                    if (androidx.compose.foundation.isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT
                ThemePreference.LIGHT -> ThemeMode.LIGHT
                ThemePreference.DARK -> ThemeMode.DARK
                ThemePreference.AMOLED -> ThemeMode.AMOLED
            }

            SalahLockTheme(themeMode = themeMode) {
                val state by viewModel.state.collectAsStateWithLifecycle()
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
                            setResult(RESULT_OK)
                            finish()
                        }
                    },
                    onBack = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"

        fun intent(context: Context, prayer: PrayerName): Intent =
            Intent(context, PrayerVerificationActivity::class.java)
                .putExtra(EXTRA_PRAYER_NAME, prayer.name)
    }
}
