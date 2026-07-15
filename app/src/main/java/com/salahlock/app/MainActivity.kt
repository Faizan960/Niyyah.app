package com.salahlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.salahlock.app.data.preferences.UserPreferences
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.salahlock.app.ui.navigation.NiyyahNavHost
import com.salahlock.app.ui.theme.NiyyahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // BM-006.9: Dark Mode preference drives the Material scheme.
            // SYSTEM/LIGHT stay light (BM-005 frames are light-only).
            val theme by (application as SalahLockApplication)
                .userPreferences.themePreference
                .collectAsState(initial = UserPreferences.ThemePreference.SYSTEM)
            val dark = theme == UserPreferences.ThemePreference.DARK ||
                theme == UserPreferences.ThemePreference.AMOLED
            NiyyahTheme(darkTheme = dark) {
                NiyyahApp()
            }
        }
    }
}

@Composable
private fun NiyyahApp() {
    val navController = rememberNavController()
    // Light and dark both map to colorScheme.background (the token that flips with
    // the theme); the tab shell overlays its own bottom nav inside MainShell.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NiyyahNavHost(navController = navController, modifier = Modifier.fillMaxSize())
    }
}
