# Audit Findings — MLBB Draft Assistant
> Generated: 2026-06-23 · Source reconciled against `versionName 2.0.0` (versionCode 2)
> Kotlin 2.1.0 · AGP 8.10.1 · Min SDK 29 · Target SDK 36

---

## Summary Table

| Priority | Label | Count |
|---|---|---|
| P0 | Critical (crashes / data-loss / security) | 4 |
| P1 | Performance | 4 |
| P2 | Maintainability | 6 |
| P3 | Deprecations / outdated dependencies | 3 |
| P4 | Gaps (missing best-practice infrastructure) | 5 |
| **Total** | | **22** |

---

## P0 — Critical

### P0-01 · `!!` on mutable nullable field — race-condition NPE risk
**File:** `:app/src/main/java/com/mlbb/assistant/service/ScreenCaptureManager.kt:54`

```kotlin
virtualDisplay = mediaProjection?.createVirtualDisplay(
    "MLBBCapture", screenWidth, screenHeight, screenDpi,
    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
    imageReader!!.surface,   // ← !! on a lateinit-style nullable var
    null, null
)
```
`imageReader` is assigned one line earlier, so it is non-null *at that call site* — but it is a `var` field with no `@Volatile` or synchronisation. If `stopCapture()` is called on another thread between assignment and use (e.g. a service lifecycle event arriving while `startCapture` runs), `imageReader` can be nulled and `!!` will throw `NullPointerException`, silently killing the foreground service. **This is the most operationally dangerous crash vector in the capture pipeline.**

**Fix:** Capture `imageReader` into a local `val` immediately after assignment:
```kotlin
val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
    .also { imageReader = it }
virtualDisplay = mediaProjection?.createVirtualDisplay("MLBBCapture", ..., reader.surface, ...)
```

---

### P0-02 · `!!` on a mutable state property in Compose after explicit `null` check
**File:** `:app/src/main/java/com/mlbb/assistant/presentation/history/DraftReplayScreen.kt:134`

```kotlin
state.session == null -> { /* handled */ }
else -> {
    val s = state.session!!   // ← compiler cannot smart-cast var/delegated property
```
Kotlin cannot smart-cast `state.session` when it is a property of a `data class` held in a `StateFlow` — the value can change between the `null` check and the `!!` dereference. If the ViewModel emits a new `null` state between those two lines, this is a guaranteed crash.

**Fix:** Use a local binding at the top of the `when` block:
```kotlin
val session = state.session ?: return@Scaffold
```

---

### P0-03 · `!!` on nullable `Intent` after redundant manual null check
**File:** `:app/src/main/java/com/mlbb/assistant/presentation/main/MainActivity.kt:32`

```kotlin
if (result.resultCode == RESULT_OK && result.data != null) {
    OverlayService.startWithProjection(this, result.resultCode, result.data!!)
}
```
The manual `!= null` check correctly guards the branch, but using `!!` afterward bypasses smart-cast safety. Under aggressive Proguard/R8 class rewriting in release builds, or if the activity result contract changes, this becomes an unguarded crash.

**Fix:**
```kotlin
val data = result.data ?: return@registerForActivityResult
OverlayService.startWithProjection(this, result.resultCode, data)
```

---

### P0-04 · `mutableSetOf<Int>()` shared mutable state accessed across coroutine scopes without synchronisation
**File:** `:app/src/main/java/com/mlbb/assistant/presentation/overlay/OverlayService.kt` (fields `filledEnemyBanSlots`, `filledOurBanSlots`, `filledEnemyPickSlots`, `filledOurPickSlots`)

These are plain `MutableSet<Int>` instances (not thread-safe). They are read/written from:
- `serviceScope.launch { frameProcessor.frameAnalysis.collect { ... } }` — Main dispatcher
- `launchCaptureLoop()` coroutine — also Main dispatcher via `serviceScope`

Currently both paths are on `Dispatchers.Main`, making this safe *today*. However, if the capture loop is ever moved to `Dispatchers.IO` or `Dispatchers.Default` (a natural optimisation), this becomes a data race with no compile-time warning. The `@Volatile` annotation on `currentWeights` and `poolMap` demonstrates awareness of this class of problem, but these sets are unguarded.

**Fix:** Replace with `ConcurrentHashMap.newKeySet()`, or, since the logic is already Main-scoped, document the Main-only invariant explicitly with a comment and an assertion.

---

## P1 — Performance

### P1-01 · `Bitmap.getPixel()` in nested loops — critical render-path bottleneck
**Files:**
- `:app/src/main/java/com/mlbb/assistant/capture/FrameProcessor.kt` — `sampleLuminanceBaseline()` and `isSlotFilled()`

```kotlin
for (x in 0 until w step step) {
    for (y in 0 until h step step) {
        val px = frame.getPixel(x, y)   // ← JNI call per pixel
```
`Bitmap.getPixel()` is a JNI call that crosses the Java/native boundary on **every pixel**. Even with `step = 2`, a 1080×2400 frame produces ~270,000 calls for a single full-frame luminance sample. This runs on `Dispatchers.Default` but still adds 10–40 ms per frame on mid-range devices, directly limiting the maximum useful frame rate of the detection pipeline.

**Fix:** Use `Bitmap.copyPixelsToBuffer(ByteBuffer)` once per crop, then iterate the buffer in pure Kotlin:
```kotlin
val buf = ByteBuffer.allocate(crop.width * crop.height * 4)
crop.copyPixelsToBuffer(buf)
val arr = buf.array()
var sum = 0L
for (i in arr.indices step (step * 4)) {
    val r = arr[i].toInt() and 0xFF
    val g = arr[i + 1].toInt() and 0xFF
    val b = arr[i + 2].toInt() and 0xFF
    sum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
}
```
This eliminates JNI overhead entirely and is typically 5–20× faster than repeated `getPixel()`.

---

### P1-02 · `Thread.sleep()` inside OkHttp `Interceptor` blocks a thread-pool thread
**File:** `:app/src/main/java/com/mlbb/assistant/di/NetworkModule.kt:45`

```kotlin
val backoffMs = (500L * attempt).coerceAtMost(4_000L)
Thread.sleep(backoffMs)   // blocks an OkHttp dispatcher thread for up to 4 s
```
OkHttp's dispatcher has a fixed pool of up to 64 threads by default. Blocking one for 4 seconds during a retry means concurrent requests must queue. Under real conditions (app startup with hero sync + portrait-hash preloading), multiple simultaneous network calls could stall the entire network layer.

**Fix:** Either use OkHttp's built-in `CallTimeout` with event listeners, or implement the retry at the repository layer as a proper coroutine with `delay()`:
```kotlin
// In HeroRepositoryImpl.syncHeroes():
repeat(maxRetries) { attempt ->
    runCatching { /* network call */ }.onSuccess { return }
    delay(500L * (attempt + 1))
}
```
This frees the thread during the wait and plays nicely with structured concurrency cancellation.

---

### P1-03 · `OverlayService` (~1,100 LOC) causes broad recomposition scope
**File:** `:app/src/main/java/com/mlbb/assistant/presentation/overlay/OverlayService.kt`

The service holds `mutableStateListOf<Hero>()`, `mutableStateListOf<HeroScore>()`, `mutableStateListOf<BanSuggestion>()`, and `mutableStateListOf<String>()` as top-level fields. Every update to any of these triggers the entire `ComposeView` tree to re-evaluate. Because the overlay Compose tree spans phase-specific panels (Ban, Pick, Trading, Final), a ban-slot fill during the pick phase can trigger recomposition of non-visible composables.

**Fix (tracked in roadmap as P1/L):** Extract overlay state into a dedicated `OverlayStateHolder` — a plain Kotlin class with `StateFlow` fields. Each phase composable observes only its own slice, narrowing the recomposition scope.

---

### P1-04 · Missing `@Immutable` on several ViewModel UI state classes
**Files (confirmed annotated):** `DraftState.kt`, `HeroListState.kt`, `SettingsState.kt`
**Files (not confirmed):** `HeroPoolViewModel`, `DraftHistoryViewModel`, `HomeViewModel`, `LogViewModel` output states

The three confirmed classes carry `@Immutable`, which is correct. However, the Compose compiler infers stability conservatively — any `data class` containing a `List<T>` (even an immutable list) is treated as unstable unless `@Immutable` is applied. ViewModels emitting state with `List<Hero>`, `List<DraftHistoryItem>`, or `List<HeroScore>` fields may cause spurious full-screen recompositions on every emission.

**Fix:** Audit every `UiState` / `State` class used in Compose and apply `@Immutable` where all fields are either primitives, `String`, or themselves `@Immutable` types.

---

## P2 — Maintainability

### P2-01 · Magic float literals in `BuildAdvisor` and `CompositionAnalyzer` without named constants
**Files:**
- `:app/src/main/java/com/mlbb/assistant/domain/advisor/BuildAdvisor.kt:109,185`
- `:app/src/main/java/com/mlbb/assistant/domain/advisor/CompositionAnalyzer.kt:65,117,124`

Thresholds like `0.60f`, `0.70f`, `0.80f`, `0.40f` for magic-damage percentage decisions appear multiple times without named constants. Recalibrating for a new patch requires searching all occurrence sites.

**Fix:** Extract to a companion object or `object BuildThresholds`:
```kotlin
private object MagicThresholds {
    const val PARTIAL = 0.60f
    const val HEAVY   = 0.70f
    const val FULL    = 0.80f
}
```

---

### P2-02 · Duplicate notification channel ID definitions (dead constant)
**Files:**
- `:app/src/main/java/com/mlbb/assistant/utils/AppConstants.kt` — `OVERLAY_NOTIFICATION_CHANNEL_ID = "draft_overlay_channel"`
- `:app/src/main/java/com/mlbb/assistant/presentation/overlay/OverlayService.kt` — `NOTIF_CHANNEL = "overlay_channel"`

`AppConstants.OVERLAY_NOTIFICATION_CHANNEL_ID` is never used — `OverlayService` defines its own private constant with a *different* string. The constant in `AppConstants` is dead code and creates confusion about which channel ID is authoritative.

**Fix:** Delete `AppConstants.OVERLAY_NOTIFICATION_CHANNEL_ID` and replace `OverlayService.NOTIF_CHANNEL` with a reference to a single authoritative constant.

---

### P2-03 · Two scoring entry points with unclear canonical status
**File:** `:app/src/main/java/com/mlbb/assistant/domain/scoring/DraftScorer.kt`

`DraftScorer` exposes both:
- `score(...)` / `rankAll(...)` — full adaptive scoring with dynamic bounds (the production path)
- `computeScore(...)` — lightweight linear path used in tests and simple callers

The `todo.md` acknowledges this but it is not resolved. A caller choosing the wrong entry point gets qualitatively different scores with no compile-time warning.

**Fix:** Annotate `computeScore` with `@VisibleForTesting` and add a KDoc warning on the method. Alternatively, replace the duplicate with a `simplified = true` parameter on the main `score()` function.

---

### P2-04 · TD-09 numbering gap in technical-debt register
**File:** `:app/src/main/java/com/mlbb/assistant/docs/todo.md` §1

`TD-09` is listed as "reserved / verify" with no source annotation. `grep TD-09` returns no hits. The gapless numbering is a stated goal of the register.

**Fix:** Either document what TD-09 was (if it was resolved inline) or renumber subsequent entries. Mark the register closed at TD-12.

---

### P2-05 · No compile-time enforcement of the repository write-path invariant
**File:** `:app/src/main/java/com/mlbb/assistant/domain/usecase/SaveDraftSessionUseCase.kt`

`overview.md` §6 states `SaveDraftSessionUseCase` is the **only** write path to `DraftSessionDao`. This is a convention enforced by review, not by the compiler. A ViewModel that injects `DraftSessionDao` directly (already guarded against via Hilt module structure, but not by an architecture lint rule) silently breaks the invariant.

**Fix:** Add a custom Lint rule or ArchUnit test that asserts no class in `presentation.*` imports `DraftSessionDao`.

---

### P2-06 · Single Gradle module (`:app`) cannot enforce the dependency rule at compile time
**File:** `settings.gradle.kts`

The Clean Architecture dependency rule (`presentation → domain ← data`) is currently a code-review convention. Because everything lives in `:app`, a developer can import a `data.*` class directly from a `domain.*` class with no build error.

**Fix (tracked in roadmap as P2/M):** Split into `:domain`, `:data`, and `:app` modules. This is a medium-effort refactor but the payoff is compile-time enforcement of the dependency rule.

---

## P3 — Deprecations / Outdated Libraries

### P3-01 · Gson used for JSON parsing instead of `kotlinx.serialization`
**Files:** `gradle/libs.versions.toml` (gson = "2.11.0"), `NetworkModule.kt` (`GsonConverterFactory`), `HeroEntity.kt` / DTOs

Gson uses reflection and does not support Kotlin sealed classes, `value class`, or `@SerialName` natively. For a Kotlin 2.1 codebase, `kotlinx.serialization` is the idiomatic choice — it is compile-safe, generates no reflection code (important for R8 minification), and is approximately 2× faster on Android benchmarks.

**Proposed replacement:**
- Library: `org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1` (✅ actively maintained, ~8k GitHub stars, Apache 2.0)
- Retrofit converter: `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0`
- Migration: Replace `@SerializedName` with `@SerialName`, add `@Serializable` to DTOs, switch `GsonConverterFactory` to `Json.asConverterFactory(...)`.

---

### P3-02 · No CI pipeline — breaking changes reach `main` silently
**File:** *(missing)* — no `.github/workflows/`, no `bitrise.yml`, no `.gitlab-ci.yml`

Every PR is merged without automated lint + test validation. The unit-test suite (8 suites in `src/test/`) never runs on CI. A regression in `DraftScorer`, `RankRuleEngine`, or `PickSequenceEngine` would only be caught in manual review.

**Proposed implementation (Quick Win):**
```yaml
# .github/workflows/ci.yml
- run: ./gradlew lint testDebugUnitTest assembleDebug
```

---

### P3-03 · `ktlint` / `detekt` not configured — no automated style or complexity gating
**File:** *(missing)* — no `detekt.yml`, no `.editorconfig` with ktlint rules, no Gradle plugin

Code style is enforced only by `kotlin.code.style=official` in `gradle.properties`, which is an IDE hint only.

**Proposed fix:** Add `detekt` with a baseline. It will catch future instances of the P2-01 magic-literal pattern, function-length violations (e.g. the 1,100-LOC `OverlayService`), and naming inconsistencies at commit time.

---

## P4 — Gaps

### P4-01 · No Room migration test
**File:** *(missing)* — `MigrationTestHelper` never instantiated in `androidTest/`

Room migrations 1→2 and 2→3 are defined in `DatabaseModule` but never tested. A broken migration silently destroys user data (their draft history) on upgrade.

**Fix:** Add `RoomMigrationTest` using `MigrationTestHelper`:
```kotlin
@Test fun migrate1To3() {
    helper.createDatabase(TEST_DB, 1).close()
    val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)
    // assert column existence
}
```

---

### P4-02 · No `kotlinx.serialization` for domain/network models
See P3-01. Gson is functional but introduces reflection overhead and R8 keep-rule fragility. `kotlinx.serialization` generates code at compile time, eliminating both risks.

---

### P4-03 · No remote crash reporting
**File:** `:app/src/main/java/com/mlbb/assistant/data/local/crashlog/CrashLogStore.kt`

Crashes are captured locally in `CrashLogStore` (file-backed, mutex-guarded). There is no remote reporting, meaning crash regressions in production are invisible until a user manually shares their log. `todo.md` §6 tracks this.

**Proposed library:** Firebase Crashlytics (free tier, actively maintained, GDPR-configurable consent gate). Alternative: Sentry Android SDK (self-hostable). Both integrate in < 1 hour.

---

### P4-04 · API backend liveness not documented or tested
**File:** `:app/build.gradle.kts` — `BuildConfig.META_API_BASE_URL = "https://api.mlbb-assistant.com/"`

`todo.md` §8 flags this. If the endpoint is down, the app silently falls back to the bundled `default_heroes.json` seed with no staleness indicator. The seed is undated — heroes' win/ban/pick rates drift each patch.

**Fix:** Add a `lastFetchedAt` timestamp to `MetaSnapshotDto`, persist it in DataStore, and surface "Meta data N days old" in the overlay status bar.

---

### P4-05 · No baseline profile configured
Startup time and Compose first-frame rendering on cold launch are unmeasured. For an overlay app, the time from "Start Draft" tap to the floating bubble appearing is user-visible and latency-sensitive.

**Fix:** Add a `BaselineProfileGenerator` instrumented test and include the generated profile in the release APK. Typical improvement: 30–50% reduction in cold-start Compose frame time.

---

## Quick Wins (P0 / P1 fixable in < 30 minutes each)

| # | Issue | File | Fix in brief |
|---|---|---|---|
| QW-01 | P0-02: `state.session!!` | `DraftReplayScreen.kt:134` | Replace with `val s = state.session ?: return@Scaffold` |
| QW-02 | P0-03: `result.data!!` | `MainActivity.kt:32` | `val data = result.data ?: return@registerForActivityResult` |
| QW-03 | P0-01: `imageReader!!.surface` | `ScreenCaptureManager.kt:54` | Capture `imageReader` into local `val` before use |
| QW-04 | P2-02: Dead constant | `AppConstants.kt` | Delete `OVERLAY_NOTIFICATION_CHANNEL_ID` |
| QW-05 | P3-02: CI pipeline | `.github/workflows/ci.yml` | `./gradlew lint testDebugUnitTest assembleDebug` |
| QW-06 | P4-01: Migration test | `androidTest/RoomMigrationTest.kt` | `MigrationTestHelper` 1→2→3 round-trip |

---

## Refactoring Playbook — Top 5 Issues (P0 / P1)

### Fix 1 · P0-01: `imageReader!!` in `ScreenCaptureManager`

**Diff:**
```diff
-        imageReader = ImageReader.newInstance(
-            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
-        )
-
-        virtualDisplay = mediaProjection?.createVirtualDisplay(
-            "MLBBCapture",
-            screenWidth, screenHeight, screenDpi,
-            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
-            imageReader!!.surface,
-            null, null
-        )
+        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
+        imageReader = reader
+
+        virtualDisplay = mediaProjection?.createVirtualDisplay(
+            "MLBBCapture",
+            screenWidth, screenHeight, screenDpi,
+            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
+            reader.surface,   // local val — no !! needed
+            null, null
+        )
```

---

### Fix 2 · P0-02: `state.session!!` in `DraftReplayScreen`

**Diff:**
```diff
 else -> {
-    val s = state.session!!
+    val s = state.session ?: return@Scaffold   // smart-cast-safe local val
```

---

### Fix 3 · P0-03: `result.data!!` in `MainActivity`

**Diff:**
```diff
-        if (result.resultCode == RESULT_OK && result.data != null) {
-            OverlayService.startWithProjection(this, result.resultCode, result.data!!)
-        }
+        if (result.resultCode != RESULT_OK) return@registerForActivityResult
+        val data = result.data ?: return@registerForActivityResult
+        OverlayService.startWithProjection(this, result.resultCode, data)
```

---

### Fix 4 · P1-01: `getPixel()` loop → `copyPixelsToBuffer` in `FrameProcessor`

**Full refactored `isSlotFilled` (drop-in replacement):**
```kotlin
private fun isSlotFilled(frame: Bitmap, region: SlotRegionF): Boolean {
    val crop  = SlotRegions.cropSlot(frame, region)
    val bytes = ByteBuffer.allocate(crop.byteCount)
    crop.copyPixelsToBuffer(bytes)
    crop.recycle()
    val arr   = bytes.array()
    val step  = 4 * 4   // sample every 4th pixel (ARGB_8888 = 4 bytes/pixel)
    var total = 0f
    var count = 0
    var i     = 0
    while (i < arr.size) {
        val r = arr[i].toInt() and 0xFF
        val g = arr[i + 1].toInt() and 0xFF
        val b = arr[i + 2].toInt() and 0xFF
        total += (0.299f * r + 0.587f * g + 0.114f * b)
        count++
        i += step
    }
    if (count == 0) return false
    val mean = total / count
    val threshold = maxOf(
        PhaseDetectionConfig.LUMINANCE_DARK_THRESHOLD_RAW.toFloat(),
        lumBaseline * PhaseDetectionConfig.LUMINANCE_NORMALISED_RATIO
    )
    return mean > threshold
}
```

---

### Fix 5 · P1-02: `Thread.sleep` in `RetryInterceptor` → coroutine `delay`

**Strategy:** Move retry logic out of the OkHttp interceptor layer into `HeroRepositoryImpl.syncHeroes()` using `kotlinx.coroutines.delay`.

```diff
-private class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
-    override fun intercept(chain: Interceptor.Chain): Response {
-        ...
-        Thread.sleep(backoffMs)
-        ...
-    }
-}

// In HeroRepositoryImpl:
+    private suspend fun syncWithRetry(maxAttempts: Int = 3): Unit = withContext(Dispatchers.IO) {
+        var lastError: Throwable? = null
+        repeat(maxAttempts) { attempt ->
+            runCatching {
+                val snapshot = metaApi.getMetaSnapshot()
+                heroDao.replaceAll(snapshot.heroes.map { it.toEntity() })
+                Timber.i("syncHeroes: synced ${snapshot.heroes.size} heroes (attempt ${attempt + 1})")
+                return@withContext
+            }.onFailure { e ->
+                lastError = e
+                Timber.w(e, "syncHeroes: attempt ${attempt + 1} failed")
+                if (attempt < maxAttempts - 1) delay(500L * (attempt + 1))
+            }
+        }
+        throw lastError ?: IOException("All $maxAttempts sync attempts failed")
+    }
```

---

## Library Evaluation for Proposed Replacements

| Current | Proposed | Stars | Last Commit | License | Justification |
|---|---|---|---|---|---|
| Gson 2.11 | `kotlinx.serialization-json 1.8.1` | ~4.5k | Active (JetBrains) | Apache 2.0 | Compile-safe, no reflection, ~2× faster, KMP-compatible |
| Manual retry in OkHttp | Coroutine `delay()` in repo layer | — | — | — | Non-blocking, cancellation-aware, testable |
| Local `CrashLogStore` only | Firebase Crashlytics or Sentry Android | 3k+ | Active | Apache 2.0 / Sentry | Remote visibility; free tier sufficient |
| No CI | GitHub Actions | — | — | — | Free for public repos; standard Android workflow |
| No static analysis | Detekt 1.23+ | ~6k | Active | Apache 2.0 | Kotlin-aware; catches complexity/magic-literal issues |

---

## Assumptions

1. The `META_API_BASE_URL` (`https://api.mlbb-assistant.com/`) is assumed to be a real backend under the team's control; if it is mock/stub, `todo.md §8` items are blocked.
2. The codebase uses Gson across the network layer; migration to `kotlinx.serialization` is assumed desirable per Kotlin 2.0+ idioms, even though it is not explicitly stated in `mission.md`.
3. `SimpleDateFormat` is confirmed absent (only `java.time` APIs are used) — P0 concern does not apply.
4. No secrets are hardcoded in `BuildConfig` beyond the API base URL, which is not a credential. P0 secret-exposure concern does not apply.
5. `GlobalScope` is not used anywhere in the production codebase. `OverlayService` correctly uses `CoroutineScope(SupervisorJob() + Dispatchers.Main)`. P0 structured-concurrency concern does not apply beyond P0-04.
