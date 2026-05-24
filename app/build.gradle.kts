plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Core AndroidX
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // FIX: added androidx.preference so PreferenceManager is on the classpath.
    // The me.zhanghai.compose.preference library only provides Compose UI components;
    // it does NOT bundle the AndroidX preference runtime (PreferenceManager, etc.).
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Jetpack Compose (Bill of Materials)
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material:material-icons-extended")

    // Modern overlay engine
    implementation("io.github.petterpx:floatingx:2.3.7")
    implementation("io.github.petterpx:floatingx-compose:2.3.7")

    // Lottie animations
    implementation("com.airbnb.android:lottie-compose:6.7.1")

    // Material 3 settings (Compose preference UI only)
    implementation("me.zhanghai.compose.preference:preference:2.2.0")

    // Image loading (Coil 3)
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    // ML Kit OCR (free, unbundled)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.14.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // TensorFlow Lite (core interpreter + support library)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
