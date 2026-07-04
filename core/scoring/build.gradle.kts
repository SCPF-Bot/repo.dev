// :core:scoring — Pure domain logic, models, scoring engine, advisors.
// CONSTRAINT: Zero android.os / android.content.* (non-Context) imports in source.
// androidx.annotation and androidx.paging are permitted for @VisibleForTesting and PagingData.
// android.content.Context is permitted for HeroArchetypeService (reads assets).
// KMP extraction of this module is a future step — deferred until paging dependency is removed.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
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

    // Serialization — @Serializable on Hero model, Json parsing in HeroArchetypeService
    implementation(libs.kotlinx.serialization.json)

    // Paging — PagingData in HeroRepository / GetPagedHeroesUseCase
    implementation(libs.paging.runtime)

    // Hilt — @Inject in use cases and HeroArchetypeService
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Timber — logging in HeroArchetypeService
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
