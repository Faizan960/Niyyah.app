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

// ── AdMob configuration (single source of truth) ────────────────────────────
// Ad-unit IDs and the App ID are client-side, non-secret values (they ship in the
// APK/manifest), so production unit IDs live here. Per build type:
//   DEBUG   → Google's official TEST units + TEST app id (never bills/serves prod).
//   RELEASE → NIYYAH production units. The production App ID is NOT derivable from
//             the unit IDs, so it must be supplied via local.properties:
//                 ADMOB_APP_ID=ca-app-pub-9308348424075904~XXXXXXXXXX
//             If absent, release falls back to the TEST app id and logs a warning,
//             so the build stays green but production ads will NOT serve until set.
object Ad {
    // Google official sample/test IDs — safe for development & automated QA.
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    // NIYYAH production ad units (provided by the app owner).
    const val PROD_BANNER = "ca-app-pub-9308348424075904/2489589627"
    const val PROD_INTERSTITIAL = "ca-app-pub-9308348424075904/1385311385"
}
val prodAdmobAppId: String = signingProp("ADMOB_APP_ID") ?: ""
if (prodAdmobAppId.isBlank()) {
    logger.warn("ADMOB_APP_ID not set in local.properties — RELEASE will use the TEST AdMob app id and production ads will NOT serve. Add: ADMOB_APP_ID=ca-app-pub-9308348424075904~XXXXXXXXXX")
}

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

        // AdMob IDs (App ID placeholder + BuildConfig ad-unit fields + ADS_ENABLED)
        // are configured per build type below so DEBUG can never request production
        // units. See the `Ad` object above.

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

            // Production AdMob config. Ads are enabled; UMP consent still gates every
            // ad request at runtime. App id falls back to TEST if not provided (warns above).
            manifestPlaceholders["admobAppId"] = prodAdmobAppId.ifBlank { Ad.TEST_APP_ID }
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${Ad.PROD_BANNER}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${Ad.PROD_INTERSTITIAL}\"")
            buildConfigField("Boolean", "ADS_ENABLED", "true")
        }
        debug {
            isMinifyEnabled = false

            // DEBUG/QA uses Google TEST units only — production units are never
            // requested during development or automated tests.
            manifestPlaceholders["admobAppId"] = Ad.TEST_APP_ID
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${Ad.TEST_BANNER}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${Ad.TEST_INTERSTITIAL}\"")
            buildConfigField("Boolean", "ADS_ENABLED", "true")
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
    // SL-021: fragment ≥1.3 was previously transitive via play-services-auth (since
    // removed). Kept as an explicit pin required by Credential Manager / ActivityResult
    // integration on some devices.
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

    // AdMob — daily interstitial + Home banner. No mediation, no analytics.
    implementation("com.google.android.gms:play-services-ads:24.5.0")
    // Google User Messaging Platform (UMP) — GDPR/UMP consent gathering before ads.
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")

    // BM-AUTH-001 — Clerk native Android SDK (custom/API-driven flow; no prebuilt
    // -ui artifact, since we keep the existing restored Profile UI). Resolves from
    // Maven Central. Clerk becomes the single authentication authority.
    implementation("com.clerk:clerk-android-api:1.0.36")
}
