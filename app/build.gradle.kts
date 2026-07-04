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
        // Points to the GitHub raw content root of SCPF-Bot/repo.dev (main branch).
        // MetaApi.@GET appends the file path relative to this base.
        buildConfigField(
            "String",
            "META_API_BASE_URL",
            "\"https://raw.githubusercontent.com/SCPF-Bot/repo.dev/main/\""
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

    // Prevent AAPT from compressing the TFLite model inside the APK.
    // The TFLite Interpreter memory-maps the model file directly from the APK
    // via MappedByteBuffer — this requires the asset to be stored uncompressed.
    // Without this flag, aapt compresses .tflite assets and the Interpreter
    // throws an IOException when trying to map a compressed byte range.
    androidResources {
        noCompress += listOf("tflite")
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
    // ── Multi-module dependencies ─────────────────────────────────────────────
    // :app is the composition root. It depends on all modules for DI wiring and
    // hosts the navigation graph that ties feature modules together.
    implementation(project(":core:scoring"))
    implementation(project(":core:data"))
    implementation(project(":core:cv"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:overlay"))
    implementation(project(":feature:draft"))
    implementation(project(":feature:settings"))

    // ── Core ──────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // ── Compose BOM + modules ─────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // ── Room ──────────────────────────────────────────────────────────────────
    // Kept here so :app can reference AppDatabase for DatabaseModule migrations.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Hilt ──────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Hilt WorkManager integration
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ── WorkManager ───────────────────────────────────────────────────────────
    implementation(libs.work.runtime)

    // ── Network ───────────────────────────────────────────────────────────────
    // Kept for NetworkModule.kt (BuildConfig.META_API_BASE_URL reference).
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.converter)

    // ── Image loading ─────────────────────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // ── DataStore ─────────────────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── Paging ────────────────────────────────────────────────────────────────
    implementation(libs.paging.runtime)
    implementation(libs.room.paging)
    implementation(libs.paging.compose)

    // ── Logging ───────────────────────────────────────────────────────────────
    implementation(libs.timber)

    // ── Pluto — embedded debug companion ─────────────────────────────────────
    debugImplementation(libs.pluto)
    releaseImplementation(libs.pluto.no.op)
    debugImplementation(libs.pluto.plugin.exceptions)
    releaseImplementation(libs.pluto.plugin.exceptions.no.op)
    debugImplementation(libs.pluto.plugin.logger)
    releaseImplementation(libs.pluto.plugin.logger.no.op)
    debugImplementation(libs.pluto.plugin.network)
    releaseImplementation(libs.pluto.plugin.network.no.op)
    debugImplementation(libs.pluto.plugin.rooms)
    releaseImplementation(libs.pluto.plugin.rooms.no.op)
    debugImplementation(libs.pluto.plugin.datastore)
    releaseImplementation(libs.pluto.plugin.datastore.no.op)
    debugImplementation(libs.pluto.plugin.prefs)
    releaseImplementation(libs.pluto.plugin.prefs.no.op)

    // ── UI Enhancement ────────────────────────────────────────────────────────
    implementation(libs.compose.shimmer)
    implementation(libs.compose.charts)
    implementation(libs.lottie.compose)

    // ── ML / CV ───────────────────────────────────────────────────────────────
    implementation(libs.mlkit.text.recognition)
    implementation(libs.tensorflow.lite)

    // ── OEM utilities ─────────────────────────────────────────────────────────
    implementation(libs.autostarter)

    // ── Unit tests ────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    // ── Instrumented tests ────────────────────────────────────────────────────
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
