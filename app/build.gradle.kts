plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mlbbdraftassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mlbbdraftassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.3.0"
    }
}

dependencies {
    // Core AndroidX
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Jetpack Compose (Bill of Materials)
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Modern overlay engine
    implementation("io.github.petterpx:floatingx:2.3.7")
    implementation("io.github.petterpx:floatingx-compose:2.3.7")

    // Lottie animations
    implementation("com.airbnb.android:lottie-compose:6.6.2")

    // Material 3 settings (ComposePreference)
    implementation("me.zhanghai.compose.preference:preference:2.2.0")

    // Image loading (Coil)
    implementation("io.coil-kt:coil-compose:3.3.0")

    // ML Kit OCR (free, unbundled)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.13.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // TensorFlow Lite Support (for ObjectDetectorHelper)
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}