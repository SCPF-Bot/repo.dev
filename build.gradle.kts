plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false

    // detekt static analysis — P3-03 (recommendations.md §5.1).
    // Configured with a baseline so CI catches NEW violations without forcing
    // instant cleanup of OverlayService (LargeClass rule) or other pre-existing debt.
    // Generate baseline: ./gradlew detektBaseline
    // Run analysis:      ./gradlew detekt
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}
