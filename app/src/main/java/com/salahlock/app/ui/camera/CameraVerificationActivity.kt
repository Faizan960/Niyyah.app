package com.salahlock.app.ui.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.salahlock.app.ui.navigation.PlaceholderScreen
import com.salahlock.app.ui.theme.NiyyahTheme

/**
 * Prayer-verification capture flow (camera / voice providers via
 * VerificationProviderFactory).
 *
 * BM-005 stub: real UI arrives with the Salah Lock screen rebuild.
 */
class CameraVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NiyyahTheme {
                PlaceholderScreen("Verification")
            }
        }
    }
}
