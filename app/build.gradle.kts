plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace   = "com.mlbb.assistant"
    compileSdk  = 36

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

    kotlinOptions {
        jvmTarget = "17"
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

configurations.all {
    exclude(group = "org.jspecify", module = "jspecify")
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

    // Network
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coil 3
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // DataStore
    implementation(libs.datastore.preferences)
    implementation(libs.savedstate)

    // Paging 3 (TD-10: smooth hero grid scrolling on large datasets)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.room.paging)

    // Logging
    implementation(libs.timber)

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
