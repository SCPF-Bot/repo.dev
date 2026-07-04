pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack — required for:
        //   • skydoves/Balloon           (com.github.skydoves:balloon:1.6.12)        — rec. §8.4 / RA-06
        //   • KilianB/JImageHash         (com.github.KilianB:JImageHash:3.0.0)       — rec. §3.1 / RA-04
        //   • judemanutd/AutoStarter     (com.github.judemanutd:autostarter:1.1.0)   — rec. §2.4
        //   • YazanAesmael/JetOverlay    (com.github.YazanAesmael:JetOverlay:1.1.0)  — rec. §1.1 / Critical
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "MLBB Assistant 2.0"

// ── Application ───────────────────────────────────────────────────────────────
include(":app")

// ── Core modules ─────────────────────────────────────────────────────────────
include(":core:scoring")       // Pure-domain logic, models, scoring engine, advisors (zero android.* imports)
include(":core:data")          // Room, DataStore, Retrofit, repositories
include(":core:cv")            // MediaProjection, FrameProcessor, TFLite, ML Kit
include(":core:designsystem")  // Shared Compose UI, Material 3 theme

// ── Feature modules ───────────────────────────────────────────────────────────
include(":feature:overlay")    // Floating UI, JetOverlay integration
include(":feature:draft")      // Manual draft fallback, state machine UI
include(":feature:settings")   // Calibration, safe-zones, telemetry toggles
