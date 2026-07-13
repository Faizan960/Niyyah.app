package com.salahlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.salahlock.app.ui.components.NiyyahBottomNav
import com.salahlock.app.ui.components.NiyyahTabs
import com.salahlock.app.ui.navigation.NiyyahNavHost
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NiyyahTheme {
                NiyyahApp()
            }
        }
    }
}

@Composable
private fun NiyyahApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTabRoute = NiyyahTabs.any { it.route == currentRoute }

    Surface(modifier = Modifier.fillMaxSize(), color = NiyyahColors.Background) {
        Box(modifier = Modifier.fillMaxSize()) {
            NiyyahNavHost(navController = navController, modifier = Modifier.fillMaxSize())
            if (isTabRoute) {
                NiyyahBottomNav(
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 25.dp),
                )
            }
        }
    }
}
