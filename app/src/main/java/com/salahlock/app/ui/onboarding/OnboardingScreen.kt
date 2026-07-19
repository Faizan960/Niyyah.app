package com.salahlock.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.AppCategory
import com.salahlock.app.theme.*
import com.salahlock.app.util.PermissionHelper
import com.salahlock.app.verification.VerificationMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "OnboardingPermissions"

// Total onboarding steps:
// 0 = Welcome
// 1 = Location
// 2 = App Usage Access
// 3 = Display Over Apps
// 4 = Battery Optimization
// 5 = Choose Apps To Lock   (only after all permissions)
// 6 = Verification Method
private const val TOTAL_STEPS = 7

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Always start at step 0 (Welcome) on first install.
    // firstIncompleteOnboardingStep handles returning users (e.g., re-opened mid-onboarding).
    var step by rememberSaveable {
        mutableIntStateOf(
            if (PermissionHelper.hasCriticalPermissions(appContext)) {
                // All critical permissions already granted — skip to battery step
                4
            } else {
                0
            }
        )
    }
    var permissionState by remember { mutableStateOf(readPermissionState(appContext)) }
    var usageSettingsOpened by rememberSaveable { mutableStateOf(false) }
    var overlaySettingsOpened by rememberSaveable { mutableStateOf(false) }
    var gpsDetecting by remember { mutableStateOf(false) }
    var gpsError by remember { mutableStateOf<String?>(null) }

    fun refreshPermissions(reason: String) {
        val after = readPermissionState(appContext)
        permissionState = after
        Log.d(TAG, "Permission refresh ($reason): $after currentStep=$step")
    }

    // Auto-advance from step 2 when usage access granted
    LaunchedEffect(permissionState.usageStatsGranted, usageSettingsOpened) {
        if (step == 2 && permissionState.usageStatsGranted) {
            Log.d(TAG, "Usage access granted; advancing to step 3")
            usageSettingsOpened = false
            step = 3
        }
    }
    // Auto-advance from step 3 when overlay granted
    LaunchedEffect(permissionState.overlayGranted, overlaySettingsOpened) {
        if (step == 3 && permissionState.overlayGranted) {
            Log.d(TAG, "Overlay granted; advancing to step 4")
            overlaySettingsOpened = false
            step = 4
        }
    }

    // Resume listener to refresh permissions when returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions("onResume")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission launchers
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissions("locationResult")
        if (results.values.any { it }) step++
        else step++ // Advance even if denied — user can set manually in settings
    }

    // Android 13+ requires POST_NOTIFICATIONS at runtime. We request it between Welcome
    // and Location — the OS dialog fires, then we advance to step 1 regardless of outcome.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        refreshPermissions("notificationResult")
        step = 1 // Advance to Location step whether granted or denied
    }

    val usageSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Returned from Usage Access settings.")
        refreshPermissions("usageSettingsResult")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // Background gradient
        Box(
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-150).dp)
                .background(
                    Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), Color.Transparent))
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            // Step indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(TOTAL_STEPS) { i ->
                    val isActive = i == step
                    val isDone = i < step
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 28.dp else 8.dp, 8.dp)
                            .background(
                                when {
                                    isActive -> MaterialTheme.colorScheme.tertiary
                                    isDone   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    else     -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                RoundedCornerShape(4.dp),
                            )
                            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { if (forward) -it else it } + fadeOut())
                },
                label = "onboarding_step",
            ) { currentStep ->
                when (currentStep) {
                    0 -> WelcomeStep(onNext = {
                        // On Android 13+, request notification permission before Location step.
                        // Without this, adhan and missed-prayer notifications are silently blocked.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !permissionState.notificationGranted) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            step++
                        }
                    })
                    1 -> LocationStep(
                        isGranted = permissionState.locationGranted,
                        gpsDetecting = gpsDetecting,
                        gpsError = gpsError,
                        onRequestLocation = {
                            gpsError = null
                            locationLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ))
                        },
                        onDetectGps = {
                            if (!permissionState.locationGranted) {
                                locationLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ))
                            } else {
                                gpsDetecting = true
                                gpsError = null
                                scope.launch {
                                    try {
                                        val app = appContext as SalahLockApplication
                                        val locationClient = com.salahlock.app.util.LocationClient(appContext)
                                        val location = locationClient.getCurrentLocation()
                                        if (location != null) {
                                            val cityName = withContext(Dispatchers.IO) {
                                                try {
                                                    @Suppress("DEPRECATION")
                                                    Geocoder(appContext, Locale.getDefault())
                                                        .getFromLocation(location.latitude, location.longitude, 1)
                                                        ?.firstOrNull()?.locality ?: "Unknown"
                                                } catch (_: Exception) { "Unknown" }
                                            }
                                            app.userPreferences.setLocation(
                                                lat = location.latitude,
                                                lng = location.longitude,
                                                city = cityName,
                                                mode = "GPS",
                                            )
                                        } else {
                                            gpsError = "Could not detect location. Please try again."
                                        }
                                    } catch (e: Exception) {
                                        gpsError = "Location error: ${e.localizedMessage}"
                                    } finally {
                                        gpsDetecting = false
                                    }
                                }
                            }
                        },
                        onSkip = { step++ },
                        onNext = { step++ },
                    )
                    2 -> PermissionStep(
                        title = "App Usage Access",
                        arabicText = "المراقبة",
                        description = "Niyyah needs to see which app is open so it can show the prayer reminder when you open a distracting app during prayer time.\n\nThis is used ONLY during active prayer windows — not 24/7.",
                        buttonText = "Open Settings",
                        onGrant = {
                            Log.d(TAG, "Opening Usage Access settings.")
                            usageSettingsOpened = true
                            usageSettingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        isGranted = permissionState.usageStatsGranted,
                        onSkip = {},
                        canSkip = false,
                        onNext = { step++ },
                    )
                    3 -> PermissionStep(
                        title = "Display Over Apps",
                        arabicText = "العرض",
                        description = "Niyyah needs to display the prayer reminder over other apps.\n\nThis is the screen you see when you open Instagram during prayer time.",
                        buttonText = "Open Settings",
                        onGrant = {
                            Log.d(TAG, "Opening overlay permission settings.")
                            overlaySettingsOpened = true
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        isGranted = permissionState.overlayGranted,
                        onSkip = {},
                        canSkip = false,
                        onNext = { step++ },
                    )
                    4 -> BatteryStep(
                        onGrant = {
                            Log.d(TAG, "Opening battery optimization settings.")
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            ).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        },
                        isGranted = permissionState.batteryOptimizationIgnored,
                        onContinue = { step++ },
                    )
                    5 -> AppSelectionStep(
                        onContinue = { step++ },
                        onSkip = { step++ },
                    )
                    6 -> VerificationMethodStep(onComplete = onComplete)
                }
            }
        }
    }
}

private data class OnboardingPermissionState(
    val locationGranted: Boolean,
    val usageStatsGranted: Boolean,
    val overlayGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val notificationGranted: Boolean,
)

private fun readPermissionState(context: android.content.Context): OnboardingPermissionState =
    OnboardingPermissionState(
        locationGranted = PermissionHelper.hasLocationPermission(context),
        usageStatsGranted = PermissionHelper.hasUsageStatsPermission(context),
        overlayGranted = PermissionHelper.canDrawOverlays(context),
        batteryOptimizationIgnored = PermissionHelper.isBatteryOptimizationIgnored(context),
        notificationGranted = PermissionHelper.hasNotificationPermission(context),
    )

// ─────────────────────────────────────────────────────────────────────────────
// Step Composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "بسم الله الرحمن الرحيم",
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Text(
            text = "Niyyah",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Live with intention.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = "Niyyah creates a gentle space between the adhan and distraction.\n\nWhen prayer time arrives, the apps you choose rest quietly until you confirm your prayer.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                modifier = Modifier.padding(20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun LocationStep(
    isGranted: Boolean,
    gpsDetecting: Boolean,
    gpsError: String?,
    onRequestLocation: () -> Unit,
    onDetectGps: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("القبلة", fontFamily = FontFamily.Serif, fontSize = 64.sp, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
        Spacer(Modifier.height(4.dp))
        Text(
            "Location",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Niyyah needs your location to calculate accurate prayer times for your city.\n\nYour location is stored locally on your device only — never uploaded.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.weight(1f))

        if (isGranted) {
            // Permission granted — show GPS detect option
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Text(
                    "✓ Location permission granted",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SuccessLight,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onDetectGps,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                if (gpsDetecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.background,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Detect My Location", fontWeight = FontWeight.Bold)
                }
            }
            gpsError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RustLight,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Continue →", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRequestLocation,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Text("Grant Location", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSkip) {
                Text(
                    "Skip for now (set manually in Settings)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun PermissionStep(
    title: String,
    arabicText: String,
    description: String,
    buttonText: String,
    onGrant: () -> Unit,
    isGranted: Boolean,
    onSkip: () -> Unit = {},
    canSkip: Boolean = true,
    onNext: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(arabicText, fontFamily = FontFamily.Serif, fontSize = 64.sp, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.weight(1f))

        if (isGranted) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Text(
                    "✓ Permission granted",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SuccessLight,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onNext ?: onSkip,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Continue →", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            if (canSkip) {
                TextButton(onClick = onSkip) {
                    Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    "This permission is required for Niyyah to work.",
                    style = MaterialTheme.typography.labelSmall,
                    color = RustLight,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun BatteryStep(onGrant: () -> Unit, isGranted: Boolean, onContinue: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("🔋", fontSize = 64.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "Battery Optimization",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Disable battery optimization for Niyyah so prayer alarms fire reliably — especially on Xiaomi, Samsung, and Realme devices which aggressively kill background apps.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.weight(1f))
        if (isGranted) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Text(
                    "✓ Battery optimization disabled",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SuccessLight,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Text("Disable Battery Optimization", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                if (isGranted) "Continue →" else "Continue without (less reliable)",
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 5 — Choose Apps To Lock  (reuses AppBlacklistRepository via OnboardingAppsViewModel)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppSelectionStep(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val vm: OnboardingAppsViewModel = viewModel()
    val state by vm.state.collectAsState()
    var persisting by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Protect Your Focus",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose the apps that should be locked during prayer times.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Search — by app name or package name
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(12.dp))

        // Quick presets (additive)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetChip("Social Media") { vm.applyPreset(AppCategory.SOCIAL) }
            PresetChip("Entertainment") { vm.applyPreset(AppCategory.ENTERTAINMENT) }
            PresetChip("Gaming") { vm.applyPreset(AppCategory.GAMES) }
            PresetChip("Productivity") { vm.applyPreset(AppCategory.PRODUCTIVITY) }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "${state.selectedCount} apps selected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.filtered, key = { it.packageName }) { app ->
                    AppSelectRow(
                        app = app,
                        selected = app.packageName in state.selected,
                        onToggle = { vm.toggle(app.packageName) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { persisting = true; vm.persist(onContinue) },
            enabled = !persisting,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.background),
        ) {
            Text("Continue →", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip For Now", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun AppSelectRow(
    app: OnboardingApp,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingAppIcon(packageName = app.packageName, label = app.label)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.category.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun OnboardingAppIcon(packageName: String, label: String) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap = bmp.asImageBitmap()
            } catch (_: Exception) {}
        }
    }
    Box(
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = label, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 6 — Verification Method
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VerificationMethodStep(onComplete: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SalahLockApplication
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(VerificationMethod.ASK_EVERY_TIME) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("التحقق", fontFamily = FontFamily.Serif, fontSize = 64.sp, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
        Spacer(Modifier.height(4.dp))
        Text(
            "Verification Method",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Choose how Niyyah confirms you have prayed. You can change this anytime in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        VerificationOption("Ask Every Time", "Pick text or voice each time", selected == VerificationMethod.ASK_EVERY_TIME) {
            selected = VerificationMethod.ASK_EVERY_TIME
        }
        Spacer(Modifier.height(12.dp))
        VerificationOption("Type", "Type a short affirmation", selected == VerificationMethod.TEXT) {
            selected = VerificationMethod.TEXT
        }
        Spacer(Modifier.height(12.dp))
        VerificationOption("Voice", "Say your confirmation aloud", selected == VerificationMethod.VOICE) {
            selected = VerificationMethod.VOICE
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                saving = true
                scope.launch {
                    app.userPreferences.setVerificationMethod(selected.name)
                    onComplete()
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            Text("Begin — الله أكبر", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VerificationOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) EmeraldPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = EmeraldPrimary)
            }
        }
    }
}
