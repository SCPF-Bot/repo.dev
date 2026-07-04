// :core:scoring — Pure domain logic, models, scoring engine, advisors.
// CONSTRAINT: Zero android.* (android.content, android.os, etc.) imports in source.
// androidx.annotation and androidx.paging are permitted for @VisibleForTesting and PagingData.
// KMP extraction of this module is a future step (Phase 1 → Phase 1 KMP).

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.mlbb.assistant.core.scoring"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Coroutines — StateFlow in DraftSessionManager
    implementation(libs.kotlinx.coroutines.android)

    // Paging — PagingData in HeroRepository / GetPagedHeroesUseCase
    implementation(libs.paging.runtime)

    // Hilt — @Inject in use cases
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
