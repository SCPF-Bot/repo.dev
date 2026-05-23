## Project Overview

```
1.1 Create GitHub repository with Android project structure
  1.1.1 Choose repository name and set public/private visibility
  1.1.2 Add Android .gitignore template from GitHub
  1.1.3 Initialize with a README.md containing project overview and disclaimer
  1.1.4 Create top‑level directories (app/, gradle/, .github/, .devcontainer/, docs/)
  1.1.5 Download and commit Gradle wrapper files (gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties) using Gradle 9.2.1
  1.1.6 Write settings.gradle.kts with plugin management, repositories, and module inclusion
  1.1.7 Write project‑level build.gradle.kts with Android Gradle Plugin 8.12.2 and Kotlin 2.3.0
  1.1.8 Write app/build.gradle.kts with:
    1.1.8.1 Compile SDK 36, Min SDK 26, Target SDK 35
    1.1.8.2 Build types (debug, release) with ProGuard configuration
    1.1.8.3 Compile options (Java 17, Kotlin JVM target 17)
    1.1.8.4 Dependencies:
      // Core
      implementation("androidx.core:core-ktx:1.18.0")
      implementation("androidx.appcompat:appcompat:1.7.1")
      // Jetpack Compose BOM (aligns all Compose versions)
      implementation(platform("androidx.compose:compose-bom:2026.01.01"))
      implementation("androidx.compose.ui:ui")
      implementation("androidx.compose.ui:ui-graphics")
      implementation("androidx.compose.ui:ui-tooling-preview")
      implementation("androidx.compose.material3:material3:1.4.0")
      implementation("androidx.activity:activity-compose:1.10.1")
      // Modern overlay engine
      implementation("io.github.petterpx:floatingx:2.3.7")
      implementation("io.github.petterpx:floatingx-compose:2.3.7")
      // High‑quality Lottie animations
      implementation("com.airbnb.android:lottie-compose:6.6.2")
      // Material 3 settings UI
      implementation("me.zhanghai.compose.preference:preference:2.2.0")
      // Image loading
      implementation("io.coil-kt:coil-compose:3.3.0")
      // Google ML Kit OCR – unbundled, completely free
      implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
      // JSON parsing
      implementation("com.google.code.gson:gson:2.13.2")
      // Coroutines
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
      // Testing
      testImplementation("junit:junit:4.13.2")
      androidTestImplementation("androidx.compose.ui:ui-test-junit4")
      debugImplementation("androidx.compose.ui:ui-tooling")
      debugImplementation("androidx.compose.ui:ui-test-manifest")
  1.1.9 Write gradle.properties with AndroidX, Kotlin style, JVM args
  1.1.10 Create AndroidManifest.xml with necessary permissions and service declarations
  1.1.11 Create initial Kotlin source files (MainActivity.kt, OverlayService.kt)
  1.1.12 Create resource files (strings.xml, themes, Lottie raw resources)
  1.1.13 Verify the project builds successfully with `./gradlew assembleDebug` in a GitHub Codespace or via a GitHub Actions workflow (see 8.1)
  1.1.14 Set up Git branching model (main, develop, feature branches)
  1.1.15 Add CODEOWNERS and CONTRIBUTING.md files (optional)
  1.1.16 Configure GitHub Codespace for zero‑local‑setup development:
    1.1.16.1 Create .devcontainer/devcontainer.json with the Android SDK and JDK 17 pre‑installed (e.g., using the `akhildevelops/devcontainer-features/android-sdk` feature)
    1.1.16.2 Ensure the devcontainer automatically runs `chmod +x gradlew` and sets up the `local.properties` with the correct sdk.dir
    1.1.16.3 Test that `./gradlew assembleDebug` succeeds inside the Codespace

1.2 Request SYSTEM_ALERT_WINDOW permission and handle runtime grant
  1.2.1 In MainActivity, check Settings.canDrawOverlays(this)
  1.2.2 If not granted, launch Intent with ACTION_MANAGE_OVERLAY_PERMISSION
  1.2.3 Register ActivityResultLauncher to handle permission result
  1.2.4 On permission granted, start OverlayService as foreground
  1.2.5 If denied, show a dialog explaining why it’s required and offer to go to settings again
  1.2.6 Add string resources for permission rationales and dialog messages
  1.2.7 Handle edge cases: overlay permission revoked while service is running (service should stop itself)
  1.2.8 Test on Android 10+ (where overlay behavior changed)

1.3 Implement foreground service that draws a floating view over other apps
  1.3.1 Create OverlayService class extending Service
  1.3.2 Create notification channel (channel ID, name, importance LOW) in onCreate
  1.3.3 Build a Notification with content title, small icon, and PendingIntent to launch MainActivity
  1.3.4 Call startForeground(NOTIFICATION_ID, notification) before adding the overlay view
  1.3.5 Initialize FloatingX for a robust overlay:
    1.3.5.1 Implement a GameOverlayService that extends Service and LifecycleOwner (reference: Xtra‑Kernel‑Manager architecture)
    1.3.5.2 Configure FloatingX for system‑level floating window with edge‑snap, position persistence, and transparent background
    1.3.5.3 Use a ComposeView as the overlay content container
    1.3.5.4 Apply a Liquid Glassmorphism theme (blur + semi‑transparent colors) using Material 3 color scheme
  1.3.6 Set up WindowManager.LayoutParams with TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
  1.3.7 Override onDestroy to remove the floating view and clean up

1.4 Overlay UI foundation with modern design
  1.4.1 Build the overlay UI entirely with Jetpack Compose (hosted in ComposeView)
  1.4.2 Implement collapsible/expandable states:
    1.4.2.1 Collapsed: a sleek, minimal bubble showing the app icon or top recommendation
    1.4.2.2 Expanded: full recommendation panel
  1.4.3 Use Lottie animations for any loading/processing states instead of basic ProgressBars
  1.4.4 Add a semi‑transparent handle bar that indicates draggability (drag is handled by FloatingX)
  1.4.5 Test overlay behavior on different screen sizes and in landscape

2.1 Choose a free public API for MLBB heroes
  2.1.1 Evaluate open‑source repos (https://github.com/ridwaanhall/api-mobilelegends primary, others as fallback)
  2.1.2 Verify data includes hero names, roles, counters, synergies, win/pick/ban rates
  2.1.3 Prefer dataset with hero icon URLs
  2.1.4 Document API endpoint and JSON structure
  2.1.5 Create a local backup copy in case the source goes offline
  2.1.6 Study existing MLBB draft assistants for algorithm validation

2.2 Parse hero data: ID, name, role, attributes, counters, synergies, win/pick/ban rates
  2.2.1 Define Kotlin data classes (Hero, Counter, Synergy, MetaStats)
  2.2.2 Use Gson 2.13.2 for JSON parsing
  2.2.3 Parse heroes array and nested objects; validate mandatory fields
  2.2.4 Normalize hero names to lowercase and strip special characters for robust matching
  2.2.5 Write unit tests to verify parsing correctness

2.3 Implement local caching (Room or JSON file) to reduce network calls
  2.3.1 Decide between JSON file cache (simpler) and Room database (more powerful)
  2.3.2 JSON file cache: store parsed list with timestamp; refresh if older than X hours
  2.3.3 Room cache: define entities, DAOs, and database; use coroutines for queries
  2.3.4 Offline fallback: load built‑in asset JSON if no network and no cache

2.4 Create a repository class to fetch, cache, and update hero data
  2.4.1 Define HeroRepository interface with methods (getAllHeroes, getHeroById, refreshHeroData, observeHeroes)
  2.4.2 Implement repository combining network and cache; emit cached data first, then updated data
  2.4.3 Store last update timestamp in DataStore/SharedPreferences
  2.4.4 Provide a singleton via a simple service locator
  2.4.5 Write unit tests for repository with mock network responses

3.1 Define scoring criteria: team synergy, counter to enemy picks, role balance, meta strength
  3.1.1 Create ScoringConfig data class with configurable weights
  3.1.2 Synergy score: sum synergy strengths with each allied hero
  3.1.3 Counter score: sum counter effectiveness against each enemy hero
  3.1.4 Role balance score: analyze role composition, penalize overstacking
  3.1.5 Meta score: normalized (win rate + 0.3*pick rate + 0.5*ban rate)
  3.1.6 Normalize all sub‑scores to 0–1 range
  3.1.7 Allow user to adjust weights via settings later
  3.1.8 Validate scoring logic against known counter/synergy matrices

3.2 Build scoring function that takes ally/enemy lists and returns sorted suggestions
  3.2.1 Create RecommendationEngine with recommend(allies, enemies, availableHeroes): List<Recommendation>
  3.2.2 For each available hero, compute weighted total score
  3.2.3 Create Recommendation object containing hero, totalScore, score breakdown, and reasoning string
  3.2.4 Return top 5 (configurable) sorted by totalScore; fallback to meta score when no ally/enemy
  3.2.5 Handle edge cases: full team, missing heroes, duplicates

3.3 Expose algorithm as a standalone module for testing
  3.3.1 Keep engine free of Android dependencies
  3.3.2 Write unit tests for various draft scenarios
  3.3.3 Create a ViewModel (DraftViewModel) that holds draft state and exposes recommendations as StateFlow

4.1 Manual Input (for quick prototyping)
  4.1.1 Add dropdown pickers for each ally and enemy slot
    4.1.1.1 Use Material 3 ExposedDropdownMenuBox with search filtering
    4.1.1.2 Layout with collapsible sections and scroll for small screens
    4.1.1.3 Load hero names from repository; set adapters
    4.1.1.4 Disable free text input; only allow selection from list
    4.1.1.5 Provide a reset button to clear all selections
  4.1.2 Connect input to recommendation engine and refresh results
    4.1.2.1 Update draft state on selection change
    4.1.2.2 Trigger recommendation automatically or via a "Suggest" button
    4.1.2.3 Update the recommendation list in real time
    4.1.2.4 Add a "Lock" toggle to prevent accidental changes after picks are final

4.2 Automatic Detection using OCR (Simpler) – completely free, no API costs
  4.2.1 Implement MediaProjection screen capture
    4.2.1.1 Add FOREGROUND_SERVICE_MEDIA_PROJECTION permission in manifest
    4.2.1.2 Request screen capture permission with createScreenCaptureIntent() + ActivityResultLauncher
    4.2.1.3 Create MediaProjection, VirtualDisplay, and ImageReader (RGBA_8888, maxImages=1)
    4.2.1.4 Capture a single frame on demand, convert to Bitmap
    4.2.1.5 Release MediaProjection immediately after capture to save battery
    4.2.1.6 A "Capture" button in the overlay triggers the flow
  4.2.2 Crop frames to hero name areas using calibrated coordinates
    4.2.2.1 Research typical hero name positions in MLBB draft UI
    4.2.2.2 Provide a calibration activity where the user drags rectangles over a sample screenshot
    4.2.2.3 Save crop regions as percentage of screen dimensions (portrait)
    4.2.2.4 Apply preprocessing (grayscale, thresholding) to improve OCR accuracy
  4.2.3 Use Google ML Kit Text Recognition (free, unbundled Play Services model)
    4.2.3.1 Add dependency: com.google.android.gms:play-services-mlkit-text-recognition:19.0.1
    4.2.3.2 Create TextRecognizer; process each cropped bitmap
    4.2.3.3 Extract text blocks, handle empty results
  4.2.4 Match recognized text to hero names with fuzzy matching
    4.2.4.1 Normalize recognized string
    4.2.4.2 Compute similarity (Levenshtein / Jaro‑Winkler) against known hero names
    4.2.4.3 Accept match if similarity > 0.8; resolve ties by highest confidence
    4.2.4.4 Mark unrecognized names for manual correction
    4.2.4.5 Map matched heroes to correct slot (ally/enemy, 1‑5) based on crop order
    4.2.4.6 Populate draft state and trigger recommendation

4.3 Automatic Detection using Icon Recognition (Advanced) – model training may require effort; TFLite inference is free
  4.3.1 Train/use a TFLite model for hero icon detection
    4.3.1.1 Collect and annotate a dataset of hero icons from draft screenshots
    4.3.1.2 Train a lightweight YOLOv8n model and export to TFLite (quantized)
    4.3.1.3 Validate accuracy on a test set
  4.3.2 Run inference on captured frames
    4.3.2.1 Add TFLite library and model to assets/
    4.3.2.2 Load model, preprocess bitmap, run inference, parse output tensors
    4.3.2.3 Filter detections by confidence (>0.6)
  4.3.3 Map detections to hero IDs and slots
    4.3.3.1 Maintain label‑to‑heroId mapping
    4.3.3.2 Assign slots based on bounding box center relative to a grid
    4.3.3.3 Apply non‑max suppression; fallback to OCR/manual on failure

5.1 Overlay UI Design – Modern Glassmorphism Recommendation Panel
  5.1.1 Adopt Liquid Glassmorphism design language for the entire overlay:
    5.1.1.1 Use Modifier.blur() and semi‑transparent Surface (alpha ~0.7)
    5.1.1.2 Implement dynamic color adaptation based on the game background (light/dark)
    5.1.1.3 Use subtle gradients and soft shadows for depth
  5.1.2 Collapsed state: a small, draggable bubble with the top recommended hero’s icon or an app icon
  5.1.3 Expanded state: full panel with LazyColumn for recommendations
    5.1.3.1 Each item is a Glassmorphic Card showing hero icon (Coil), name, total score bar, and short reason
  5.1.4 Empty state: a Lottie animation prompting "Tap Detect Draft"
  5.1.5 Use animated visibility transitions for expand/collapse

5.2 Score Breakdown & Detailed Reasoning (Modern, Interactive)
  5.2.1 On tapping a recommendation, expand the card inline or show a BottomSheet
  5.2.2 Display detailed breakdown with smooth animations:
    5.2.2.1 Counter icons + names + strength bars
    5.2.2.2 Synergy icons + names + strength bars
    5.2.2.3 Meta stats (win/pick/ban) as small charts or bars
  5.2.3 Use Material 3 color tokens for positive/negative values
  5.2.4 Add a "Copy to Clipboard" button with haptic feedback

5.3 Detection Trigger & Refresh Button
  5.3.1 Design a floating action button with a pulsing Lottie animation (ready state)
  5.3.2 On press, animate to a loading state (Lottie spinner) while OCR processes
  5.3.3 After completion, show results and reset button to idle state
  5.3.4 Implement debounce to prevent accidental double‑taps

5.4 Settings Integration for UI Preferences
  5.4.1 Overlay opacity and animation toggles can be changed in the settings screen (see 7.2)
  5.4.2 Changes reflect immediately in the overlay without requiring a restart

6.1 Detect when MLBB is in draft phase (optional, via accessibility service)
  6.1.1 Implement AccessibilityService, declare in manifest with BIND_ACCESSIBILITY_SERVICE
  6.1.2 Listen for TYPE_WINDOW_STATE_CHANGED
  6.1.3 Monitor package name (com.mobile.legends) and activity class names
  6.1.4 Trigger startService for overlay when entering draft
  6.1.5 Stop/hide overlay when exiting draft
  6.1.6 Handle edge cases (re‑entry, service killed)
  6.1.7 Toggle auto‑detection in settings

6.2 Trigger capture automatically or with a floating button
  6.2.1 Prominent "Detect Draft" button on overlay changes color when ready
  6.2.2 Auto‑mode: after draft detected, wait 2s then capture automatically
  6.2.3 Show a notification that detection is in progress
  6.2.4 On failure, display an error and allow manual retry
  6.2.5 Disable auto‑trigger from settings

6.3 Handle different screen resolutions and aspect ratios (calibration setup)
  6.3.1 Store screen dimensions globally
  6.3.2 Express all crop regions as floats (0.0–1.0) relative to screen size
  6.3.3 Calibration wizard: user adjusts draggable rectangles over a sample draft screenshot
  6.3.4 Test detection with a static screenshot post‑calibration
  6.3.5 Support portrait only (MLBB orientation); reset calibration to defaults if needed

7.1 Minimise battery drain: capture only on demand, use low‑resolution frames
  7.1.1 Single capture per request (not continuous)
  7.1.2 Downscale captured image to max 720px long side before OCR
  7.1.3 Release MediaProjection immediately after obtaining Bitmap
  7.1.4 Use coroutines with cancellation for long‑running OCR tasks
  7.1.5 Profile battery usage with Android Studio (if available) or via device logs

7.2 Add a settings screen with modern ComposePreference (Material 3)
  7.2.1 Create SettingsActivity with ComposePreference for full Material 3 integration
  7.2.2 Preferences using ComposePreference DSL:
    7.2.2.1 SliderPreference for overlay opacity (0.3–1.0)
    7.2.2.2 ListPreference for detection method (manual/OCR/icon)
    7.2.2.3 TextFieldPreference for custom API endpoint URL
    7.2.2.4 SwitchPreference for auto‑start and Lottie animation toggle
    7.2.2.5 Custom preference for calibration reset
    7.2.2.6 SliderPreference for scoring weight adjustments (advanced)
  7.2.3 Apply preference changes in real time using MutableStateFlow<Preferences>
  7.2.4 Overlay opacity and other UI changes take effect immediately

7.3 Persist user preferences (SharedPreferences/DataStore)
  7.3.1 ComposePreference automatically integrates with DataStore (Preferences)
  7.3.2 FloatingX handles overlay position persistence automatically
  7.3.3 Save calibration data and detection method; restore on service start
  7.3.4 Handle preference migrations if format changes

7.4 Add legal disclaimer and “Not affiliated with Moonton” notice
  7.4.1 Show disclaimer dialog on first launch (remember via preference)
  7.4.2 Include exact text: “This app is not affiliated with or endorsed by Moonton. Mobile Legends: Bang Bang is a trademark of Moonton.”
  7.4.3 Add disclaimer section in Settings and README.md
  7.4.4 Use only publicly available hero data, no copyrighted assets

8.1 Set up GitHub Actions to build, test, and release the APK (full CI/CD)
  8.1.1 Create .github/workflows/build.yml
  8.1.2 Define trigger: on push to main and on pull_request
  8.1.3 Use the official `gradle/actions/setup-gradle` to set up JDK 17 and Gradle, with dependency caching
  8.1.4 Optionally set up the Android SDK using `android-actions/setup-android` (if not already present in the runner)
  8.1.5 Add a step to make `gradlew` executable: `chmod +x gradlew`
  8.1.6 Build the debug APK: `./gradlew assembleDebug`
  8.1.7 Run lint and unit tests: `./gradlew lintDebug testDebugUnitTest`
  8.1.8 (Optional) Sign the release APK using a keystore stored in GitHub Secrets and environment variables
  8.1.9 Upload the debug (or release) APK as an artifact using `actions/upload-artifact`
  8.1.10 Add a status badge to the README.md pointing to the workflow
  8.1.11 Verify that the artifact can be downloaded and installed on a real device

8.2 Test on multiple devices/resolutions (emulators or real phones)
  8.2.1 Test checklist: 720p, 1080p, 1440p, Android 10–15, real device with MLBB
  8.2.2 Verify overlay behavior: dragging, touch passthrough, appearance on game screen
  8.2.3 Test manual input and automatic OCR detection with sample screenshots
  8.2.4 Validate all permission flows and error messages
  8.2.5 Use Firebase Test Lab / GitHub emulator for automated UI tests
  8.2.6 Log with Timber or standard Logcat for debugging

8.3 Collect feedback and iterate on algorithm accuracy
  8.3.1 GitHub Issue templates for bug reports and hero data corrections
  8.3.2 In‑app feedback button (Email intent) or Google Form
  8.3.3 Request user‑submitted draft screenshots where detection failed
  8.3.4 Regularly pull updated hero data; adjust scoring weights based on meta shifts
  8.3.5 Optional anonymous analytics to track detection success rate
  8.3.6 A/B test algorithm changes via Firebase App Distribution beta channel
```

## Repository Structure

```
repo.dev/
├── .github/
│   └── workflows/
│       └── build.yml
├── .devcontainer/
│   └── devcontainer.json
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/mlbbdraftassistant/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── OverlayService.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── Hero.kt
│   │   │   │   │   │   ├── Counter.kt
│   │   │   │   │   │   ├── Synergy.kt
│   │   │   │   │   │   └── MetaStats.kt
│   │   │   │   │   ├── remote/
│   │   │   │   │   │   └── HeroApi.kt
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── HeroDao.kt
│   │   │   │   │   │   └── AppDatabase.kt
│   │   │   │   │   └── repository/
│   │   │   │   │       └── HeroRepository.kt
│   │   │   │   ├── domain/
│   │   │   │   │   ├── RecommendationEngine.kt
│   │   │   │   │   └── ScoringConfig.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── overlay/
│   │   │   │   │   │   ├── OverlayContent.kt
│   │   │   │   │   │   └── OverlayViewModel.kt
│   │   │   │   │   ├── settings/
│   │   │   │   │   │   └── SettingsActivity.kt
│   │   │   │   │   └── theme/
│   │   │   │   │       └── Theme.kt
│   │   │   │   └── util/
│   │   │   │       ├── CsvParser.kt
│   │   │   │       ├── FuzzyMatcher.kt
│   │   │   │       └── ScreenCaptureHelper.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   └── (empty, Compose uses setContent)
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   ├── drawable/
│   │   │   │   │   └── ic_launcher_foreground.xml
│   │   │   │   ├── mipmap-anydpi-v26/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   ├── mipmap-xxxhdpi/
│   │   │   │   └── raw/
│   │   │   │       ├── loading_animation.json
│   │   │   │       └── pulse_animation.json
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   │   └── java/com/example/mlbbdraftassistant/
│   │   │       ├── RecommendationEngineTest.kt
│   │   │       └── HeroRepositoryTest.kt
│   │   └── androidTest/
│   │       └── java/com/example/mlbbdraftassistant/
│   │           └── ExampleInstrumentedTest.kt
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── docs/
├── .gitignore
├── README.md
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```
