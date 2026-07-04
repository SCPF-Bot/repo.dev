// :core:cv — Computer vision pipeline: MediaProjection, FrameProcessor, TFLite, ML Kit.
// Phase 2 will introduce YOLO + TemporalConsensusBuffer; this module is the target for that work.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.mlbb.assistant.core.cv"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Prevent AAPT from compressing TFLite models — the Interpreter memory-maps
    // the asset file directly from the APK and requires it to be uncompressed.
    androidResources {
        noCompress += listOf("tflite")
    }

    packaging {
        resources {
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:scoring"))
    implementation(project(":core:data"))

    // TFLite runtime — HeroClassifier (MobileNetV3Small portrait classifier)
    implementation(libs.tensorflow.lite)

    // ML Kit Text Recognition — PhaseOcrDetector
    implementation(libs.mlkit.text.recognition)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Logging
    implementation(libs.timber)

    // AutoStarter — OEM auto-start settings deep-link
    implementation(libs.autostarter)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
