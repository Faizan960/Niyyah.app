plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.room)
}

// RC.4 — release signing read from local.properties (never hardcode secrets).
// Add to local.properties (not committed):
//   KEYSTORE_PATH=/abs/path/upload-keystore.jks
//   KEYSTORE_PASSWORD=...
//   KEY_ALIAS=...
//   KEY_PASSWORD=...
fun signingProp(key: String): String? = rootProject.file("local.properties")
    .takeIf { it.exists() }?.readLines()
    ?.firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()
    ?.takeIf { it.isNotBlank() }

val hasReleaseSigning = signingProp("KEYSTORE_PATH") != null

// Sprint A.1 — AdMob IDs. Default to Google's official TEST IDs; override in
// local.properties (ADMOB_APP_ID / ADMOB_INTERSTITIAL_ID) for production. Never
// hardcode production unit IDs in the repo.
val admobAppId = signingProp("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"
val admobInterstitialId = signingProp("ADMOB_INTERSTITIAL_ID") ?: "ca-app-pub-3940256099942544/1033173712"

// BM-AUTH-001 — Clerk publishable key (public/client-safe key). Read from
// local.properties so it stays out of VCS; empty fallback lets the build succeed
// on machines without it (Clerk simply won't initialize). Never read the secret key.
val clerkPublishableKey = signingProp("CLERK_PUBLISHABLE_KEY") ?: ""

// BM-013 Checkpoint A — Supabase (cloud DB only; Clerk remains the sole auth
// authority). Both are PUBLIC/client-safe: project URL + sb_publishable key.
// Read from local.properties so they stay out of VCS; empty fallback keeps the
// build green on machines without them (the probe just reports "not configured").
// Never read the service_role key, DB password, or any Supabase secret.
val supabaseUrl = signingProp("SUPABASE_URL") ?: ""
val supabasePublishableKey = signingProp("SUPABASE_PUBLISHABLE_KEY") ?: ""

android {
    namespace = "com.salahlock.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.salahlock.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // BM-013 — instrumented tests (Room MigrationTestHelper runs on a device).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        // Sprint A.2 — master ad switch. Infra stays live (init + preload + cache),
        // but ads are never DISPLAYED while false. Flip to true to monetize (one line).
        buildConfigField("Boolean", "ADS_ENABLED", "false")

        // BM-AUTH-001 — Clerk publishable key exposed to app code via BuildConfig.
        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$clerkPublishableKey\"")

        // BM-013 Checkpoint A — public Supabase client config via BuildConfig.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingProp("KEYSTORE_PATH")!!)
                storePassword = signingProp("KEYSTORE_PASSWORD")
                keyAlias = signingProp("KEY_ALIAS")
                keyPassword = signingProp("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // SL-021: strip unreferenced resources (library locales, unused drawables)
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use the real upload key when configured; otherwise stays unsigned so the
            // build still succeeds in CI / on machines without the keystore.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // VerificationPhraseProvider emits debug-gated android.util.Log output;
        // let unmocked android.* calls return defaults instead of throwing in
        // JVM unit tests.
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // SL-021: the app's own strings are English (Arabic content is data, not
    // resources) — drop the ~80 translation locales bundled by play-services /
    // AndroidX libraries. Shrinks the APK with zero user-visible change.
    androidResources {
        localeFilters += listOf("en")
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
    // BM-013 — expose exported Room schemas to instrumented migration tests.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Task.await() for FusedLocationProviderClient — was transitive via Firebase
    // before RC.2 Firebase removal; now declared explicitly.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation(libs.kotlinx.serialization.json)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    // Navigation (SL-021: navigation3 removed — the app uses navigation-compose only)
    implementation(libs.androidx.navigation.compose)

    // Location (SL-021: legacy play-services-auth removed — sign-in uses
    // Credential Manager + googleid below; no GoogleSignIn API references exist)
    implementation(libs.play.services.location)
    // SL-021: fragment ≥1.3 was previously transitive via play-services-auth;
    // ActivityResult APIs (LockOverlayActivity camera launcher) require it.
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore & Security
    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Adhan (prayer times)
    implementation(libs.adhan)

    // Retrofit & OkHttp (Aladhan API)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // BM-013 — Room MigrationTestHelper for v8→v9 migration tests.
    androidTestImplementation("androidx.room:room-testing:2.7.1")


    // BM-AUTH-001: Google Sign-In is now handled by Clerk (clerk-android-api). The
    // former Credential Manager + googleid dependencies were removed after Clerk
    // authentication passed full runtime verification.

    // Coil — loads the signed-in user's Google profile photo (BM-008.1). Compose-native,
    // small footprint; the only remote-image need in the app.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // AdMob — single daily interstitial (Sprint A.1). No mediation, no analytics.
    implementation("com.google.android.gms:play-services-ads:24.5.0")

    // BM-AUTH-001 — Clerk native Android SDK (custom/API-driven flow; no prebuilt
    // -ui artifact, since we keep the existing restored Profile UI). Resolves from
    // Maven Central. Clerk becomes the single authentication authority.
    implementation("com.clerk:clerk-android-api:1.0.36")
}
