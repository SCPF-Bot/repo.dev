// :core:designsystem — Shared Compose UI, Material 3 theme, common components.
// All feature modules depend on this for consistent visual language.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace  = "com.mlbb.assistant.core.designsystem"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Domain models needed for component signatures (Hero, HeroRole, etc.)
    api(project(":core:scoring"))
    implementation(project(":core:data"))

    // Compose BOM + modules
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // Shimmer loading skeleton
    implementation(libs.compose.shimmer)

    // Paging Compose
    implementation(libs.paging.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
