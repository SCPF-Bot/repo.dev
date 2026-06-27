plugins {
    alias(libs.plugins.android.application)
    // NOTE: kotlin.android is intentionally omitted here.
    // AGP 9.x auto-applies org.jetbrains.kotlin.android when it detects Kotlin
    // sources, so an explicit second application throws
    // "Cannot add extension with name 'kotlin', as there is an extension already
    // registered with that name."
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // detekt static analysis — P3-03; config at config/detekt/detekt.yml
    // Run: ./gradlew detekt   Generate baseline: ./gradlew detektBaseline
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace   = "com.mlbb.assistant"
    compileSdk  = 37

    defaultConfig {
        applicationId = "com.mlbb.assistant"
        minSdk        = 29
        targetSdk     = 36
        versionCode   = 2
        versionName   = "2.0.0"

        // BASE_URL sourced from BuildConfig so it can be overridden per build variant
        // without changing source code. Override in local.properties or CI env.
        buildConfigField(
            "String",
            "META_API_BASE_URL",
            "\"https://api.mlbb-assistant.com/\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    // Export Room schema to allow proper migrations to be authored.
    // The generated JSON files should be checked into version control.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental",    "true")
    }

    packaging {
        resources {
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

// kotlin.android is auto-applied by AGP 9.x, so kotlinOptions {} inside android {}
// is unavailable. Use the top-level kotlin {} DSL instead — jvmToolchain sets the
// JVM target for both the Kotlin compiler and javac in one call.
kotlin {
    jvmToolchain(17)
}

configurations.all {
    exclude(group = "org.jspecify", module = "jspecify")
}

// detekt configuration — point at the shared config file
detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose BOM + modules
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Hilt WorkManager integration
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // WorkManager — periodic background hero-data sync (TD-13)
    implementation(libs.work.runtime)

    // Network
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // kotlinx.serialization runtime — P3-01; full migration from Gson complete
    implementation(libs.kotlinx.serialization.json)
    // Retrofit kotlinx.serialization converter — replaces GsonConverterFactory
    implementation(libs.retrofit.kotlinx.converter)

    // Coil 3 — image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // DataStore
    implementation(libs.datastore.preferences)

    // Paging 3 (TD-10: smooth hero grid scrolling on large datasets)
    implementation(libs.paging.runtime)
    implementation(libs.room.paging)
    // Paging 3 Compose — collectAsLazyPagingItems for HeroListScreen paged grid
    implementation(libs.paging.compose)

    // Logging
    implementation(libs.timber)

    // ── UI Enhancement Libraries ──────────────────────────────────────────────
    // compose-shimmer: loading skeleton for HeroList, MetaBoard, History screens
    implementation(libs.compose.shimmer)

    // ComposeCharts: animated pie chart for ScoreExplanationSheet score breakdown
    implementation(libs.compose.charts)

    // Lottie: phase-transition animations in overlay (scanning, pick-success, ban-warning)
    implementation(libs.lottie.compose)

    // ML Kit Text Recognition: on-device OCR for PhaseOcrDetector
    implementation(libs.mlkit.text.recognition)

    // ML Kit Object Detection (custom TFLite model) — guarded by asset existence check; see todo.md §5.9
    // Alias is mlkit-objectdetection (not mlkit-object-detection) to avoid "object" Kotlin keyword in accessor
    implementation(libs.mlkit.objectdetection)

    // JImageHash intentionally NOT declared here: it uses java.awt (unavailable on Android)
    // and JitPack does not publish a usable AAR. PortraitMatcher loads it via reflection
    // with runCatching, so the dHash fallback engages automatically on device.

    // AutoStarter: OEM auto-start settings deep-link in PermissionWizardScreen
    implementation(libs.autostarter)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    // Instrumented tests
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
