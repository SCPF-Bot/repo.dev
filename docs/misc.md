# Miscellaneous Architecture Notes — MLBB Draft Assistant

> Small but non-obvious decisions that future maintainers would otherwise have to rediscover.
> If a change deviates from `mission.md`, log it here with an explicit rationale.
>
> For product-level architecture see [`overview.md`](./overview.md).
> For audit-driven findings see [`temp/findings.md`](./temp/findings.md).

---

## Contents

1. [Retry layer: interceptor → coroutine repository](#1-retry-layer-network-interceptor--repository)
2. [`@VisibleForTesting` on dual scoring entry points](#2-visiblefortesting-as-interim-solution-for-dual-scoring-entry-points)
3. [`PhaseDetectionConfig` as single CV threshold source](#3-phasedetectionconfig-as-the-single-source-for-cv-thresholds-td-03)
4. [Notification channel ID ownership](#4-notification-channel-id-ownership)
5. [Adaptive luminance threshold (TD-04)](#5-luminance_normalised_ratio-and-adaptive-brightness-td-04)
6. [Thread-safety pass — what changed and what was deferred](#6-thread-safety--compose-stability-pass)
7. [`FrameProcessor` thread-safety fix (P0-05)](#7-frameprocessor-thread-safety-fix-p0-05)
8. [`libs.versions.toml` duplicate key cleanup (P0-06)](#8-libsversiontoml-duplicate-key-cleanup-p0-06)
9. [JImageHash integration pattern for `PortraitMatcher`](#9-jimagehas-integration-pattern-for-portraitmatcher-ra-04)
9a. [CV pipeline maintenance cycle](#9a-cv-pipeline-maintenance-cycle)
10. [kotlinx.serialization migration guidance](#10-kotlinxserialization-migration-guidance-ra-07)
11. [JetOverlay adoption and `OverlayService` decomposition](#11-jetoverlay-adoption-and-overlayservice-decomposition-ra-01)
12. [`PickPhaseContent` pick-success animation](#12-pickphasecontent-pick-success-animation)
13. [TFLite hero classifier integration (TD-15)](#13-tflite-hero-classifier-integration-td-15)
14. [Full-offline audit (TD-20)](#14-full-offline-audit-td-20)
15. [Manual screenshot-mapping feature removal (TD-21)](#15-manual-screenshot-mapping-feature-removal-td-21)

---

## 1. Retry layer: network interceptor → repository

### What changed
`RetryInterceptor` — an OkHttp `Interceptor` that called `Thread.sleep()` between
attempts — was removed from `NetworkModule`. Retry logic now lives in
`HeroRepositoryImpl.syncHeroes()` using coroutine `delay()`.

### Why
OkHttp interceptors run on OkHttp's dispatcher thread pool (default 64 threads).
Calling `Thread.sleep()` inside an interceptor occupies a dispatcher thread for the
full back-off window (up to 4 s for three retries). Under concurrent requests this can
starve the pool and delay unrelated API calls.

Coroutine `delay()` suspends without occupying any thread during back-off. The retry
is also cancellation-aware, and the logic is testable without a `MockWebServer`.

### Trade-offs accepted
- Retry is specific to `syncHeroes()`; any future endpoint that needs retry must opt in explicitly.
- HTTP 4xx/5xx responses are **not** retried — only `IOException`/timeout failures. Same policy as the old interceptor.

### When to revisit
If a second retry need emerges, prefer a shared `suspend fun <T> withRetry(...)` utility
in the repository package over reintroducing a blocking OkHttp interceptor.

---

## 2. `@VisibleForTesting` as interim solution for dual scoring entry points

`DraftScorer` has two scoring functions:

- `score()` / `rankAll()` — the full production path (adaptive weights, dynamic bounds, patch velocity, positional modifiers, proficiency multipliers).
- `computeScore()` — a simplified three-term linear formula **used in unit tests only**.

The correct long-term fix is to merge these into a single `score()` with a
`simplified = true` parameter (tracked in `todo.md` §3). Until then, `computeScore`
carries `@VisibleForTesting` and an expanded KDoc warning so that no production caller
accidentally picks it.

---

## 3. `PhaseDetectionConfig` as the single source for CV thresholds (TD-03)

All computer-vision thresholds (luminance raw threshold, normalised ratio, frame
throttle intervals, hash distance limits, histogram similarity floor) live in
`capture/PhaseDetectionConfig.kt`.

**Rule:** do not define a new threshold in a feature class. Add it to `PhaseDetectionConfig`
and reference it from the feature class. This makes device-calibration patches a
single-file change.

---

## 4. Notification channel ID ownership

`OverlayService` owns its private `NOTIF_CHANNEL = "overlay_channel"` constant.

**Rule:** each Android component that creates a notification channel owns its own
channel ID as a private constant in that class. Do not re-export channel IDs into
`AppConstants` or any cross-module utility — the channel string is an implementation
detail of the component that registers it.

---

## 5. `LUMINANCE_NORMALISED_RATIO` and adaptive brightness (TD-04)

`PhaseDetectionConfig.LUMINANCE_NORMALISED_RATIO` is multiplied by a per-session
luminance baseline sampled from a stable background region (top-left 10% × 10% of
the first captured frame). The slot-fill threshold is:

```
threshold = max(LUMINANCE_DARK_THRESHOLD_RAW, baseline × LUMINANCE_NORMALISED_RATIO)
```

This ensures that if the user has auto-brightness enabled and is in a bright
environment, the "empty slot" luminance rises together with the background — the
normalised threshold rises with them, preventing false positives.
`LUMINANCE_DARK_THRESHOLD_RAW` is the floor to prevent collapse in very dark environments.

**Recalibration:** run the `FrameProcessorBenchmark` instrumented test against captures
from target devices before adjusting these constants.

---

## 6. Thread-safety & Compose-stability pass

### What changed
- **P0-04:** The four `OverlayService` slot-tracking sets (`filledEnemyBanSlots`,
  `filledOurBanSlots`, `filledEnemyPickSlots`, `filledOurPickSlots`) were migrated from
  `mutableSetOf<Int>()` to `ConcurrentHashMap.newKeySet<Int>()`. The previous belief
  ("safe because both paths run on Main") was wrong: `launchCaptureLoop()` launches on
  `Dispatchers.IO` and scans inside `withContext(Dispatchers.Default)`, so the sets were
  genuinely raced.
- **P1-04:** `@Immutable` added to `HomeUiState`, `InsightsState`, `HeroPoolState`,
  `HeroPoolEntry`, and `LogScreenState`. Fewer recompositions on the live-draft path.

### What was deliberately deferred and why
Certain changes were deferred even though the instruction set asked for autonomous execution:

1. **`OverlayService` decomposition (P1-03, effort L):** A safe split requires moving
   lifecycle, window, and capture state across new class boundaries and re-wiring ~13
   call sites. Without a Gradle build to verify, shipping an unverified split of the
   most critical runtime component carries more risk than the debt it removes.
   *Resolved subsequently* in the UI/UX overhaul pass via JetOverlay (see §11).

2. **Gson → kotlinx.serialization migration (P3-01):** Swapping the JSON engine
   touches every DTO, `JsonParser`, and the Retrofit converter factory, and interacts
   with R8 keep-rules. Without a build, a ProGuard regression on release builds was
   unverifiable. *Partially resolved* in the sixth pass — all entry points migrated,
   Gson removal pending smoke test (see §10).

### Standing principle for future autonomous passes
Prefer *small, verifiable, source-compatible* fixes over large rewrites that cannot be
validated in the current environment. Document every deferral here with a concrete
reconciliation path.

---

## 7. `FrameProcessor` thread-safety fix (P0-05)

The four `FrameProcessor` internal slot-tracking sets (`filledEnemyBans`,
`filledOurBans`, `filledEnemyPicks`, `filledOurPicks`) were migrated from
`mutableSetOf<Int>()` to `ConcurrentHashMap.newKeySet<Int>()`.

`FrameProcessor.processFrame` runs inside `withContext(Dispatchers.Default)` where the
sets are both read (`i !in filledEnemyBans`) and mutated (`.add(i)`).
`resetSlotTracking()` is called from `OverlayService`, which operates across multiple
dispatchers. This is the same race class as P0-04 but at the frame level rather than
the session level.

**Never replace these with plain `mutableSetOf`.**

---

## 8. `libs.versions.toml` duplicate key cleanup (P0-06)

The version catalog had 17 duplicate keys. The TOML 1.0 spec prohibits duplicates;
Gradle's parser silently tolerates them with last-wins semantics, making the effective
version set unpredictable.

**Cleaning approach:** the file was rewritten with exactly one entry per key, preserving
the last-defined value to maintain exact current build behavior.

**Suspicious downgrades discovered** (last-wins value was an older version — not corrected;
flagged for deliberate review in the next dependency PR):

| Key | First entry | Effective (last) | Notes |
|---|---|---|---|
| `coreKtx` | 1.19.0 | 1.16.0 | Unclear why 1.16.0 was added after 1.19.0 |
| `navigationCompose` | 2.9.8 | 2.9.0 | Downgrade |
| `savedstate` | 1.5.0 | 1.2.1 | Significant downgrade |
| `retrofit` | 3.0.0 | 2.11.0 | 3.0.0 has breaking changes; deliberate downgrade |
| `okhttp` | 4.12.0 → 5.4.0 | 4.12.0 | Three entries; effectively unchanged |

**Action:** review these in the next dependency-update PR. Do not blindly bump to
the first-defined (higher) version — the last-defined values are what CI has been
building against.

---

## 9. JImageHash integration pattern for `PortraitMatcher` (RA-04)

> **Status update (2026-07-03):** `KilianB/JImageHash` was removed from the version
> catalog — it was never actually declared as a Gradle dependency (JitPack does not
> publish a usable AAR, and the library depends on `java.awt`, which is unavailable on
> ART). `SlotAwareHasher`'s pure-Kotlin triple-hash fusion (see `todo.md` TD-16) now
> fills the role this section originally envisioned for JImageHash. The pattern below
> is kept for historical context only — do **not** re-add the dependency without first
> confirming a JVM-compatible, Android-safe fork exists.

`KilianB/JImageHash` (`com.github.KilianB:JImageHash:3.0.0`) was never resolvable in Gradle.
Historical plan for completing the integration into `PortraitMatcher.kt` (superseded):

**Algorithm pairing:**
- **Primary:** `WaveletHash` — perceptually robust to colour variation and compression
  artifacts; best for MLBB portrait crops where saturation differs by skin tier.
- **Secondary:** `ColorDifferenceHash` — adds colour discrimination that dHash misses,
  helping separate heroes with similar silhouettes (e.g. all Fighter-archetype heroes
  with golden frames).
- **Fallback:** keep `PerceptualHash.dHash()` as a lightweight fallback when JImageHash
  is unavailable or causes startup delay.

**Integration guard:** JImageHash JARs are JVM-only. Wrap all calls in `runCatching {}`
with a fallback to the existing dHash path to prevent a missing class from crashing the
CV pipeline.

**Required before merging:** run `PerceptualHashTest` through both paths. The JImageHash
path must show a false-positive rate ≤ 2% before the dHash path is removed as primary.

---

## 9a. CV pipeline maintenance cycle

The slot-aware hash pipeline (`SlotAwareHasher`, `HeroThresholds`, `SlotConsensusManager` — TD-16/17/18)
is not "set and forget." Treat these as standing triggers, not one-time setup:

| Trigger | Action | SLA |
|---|---|---|
| New MLBB patch released | Re-run `scripts/calibrate_thresholds.py` against refreshed CDN portraits + new device crops | <24h |
| New hero added | Add CDN portrait → regenerate `hero_thresholds.json` → add to the test corpus (see `todo.md` §9) | <48h |
| False-positive spike reported | Investigate `SlotAwareHasher` distances for the affected hero/slot before touching global `PhaseDetectionConfig` constants | best-effort |
| Quarterly | Spot-check mean match confidence per slot type; recalibrate thresholds if it has drifted materially | every ~90 days |

This list absorbs the "perpetual maintenance cycle" content that previously lived in
`docs/temp/recommendations.md` before that file was merged into permanent docs and removed.

---

## 10. kotlinx.serialization migration guidance (RA-07)

The `org.jetbrains.kotlin.plugin.serialization` plugin and `kotlinx-serialization-json:1.7.3`
runtime are in Gradle. All three JSON entry points have been migrated:

- DTOs (`MetaSnapshotDto`, `HeroDto`) are `@Serializable`.
- `NetworkModule` uses `json.asConverterFactory(...)` (not `GsonConverterFactory`).
- `JsonParser` uses `Json.decodeFromString<>()`.

**One remaining step before Gson can be removed:**

Run `./gradlew assembleRelease` to confirm no ProGuard errors. `kotlinx.serialization`
generates `Serializer` companion classes via the plugin — these are not reflection-based
but the generated class names may need explicit keep rules if serializers are referenced
through `KClass`. Tracked in `todo.md` §5.

**Important:** do **not** annotate `@Serializable` on Room `@Entity` classes. Room
entities are handled by the Room KSP processor; mixing the two annotation processors
on the same class causes KSP conflicts.

**`Json` instance configuration:**
```kotlin
Json { ignoreUnknownKeys = true; coerceInputValues = true }
```
This matches Gson's default leniency toward unknown/null JSON fields.

---

## 11. JetOverlay adoption and `OverlayService` decomposition (RA-01)

### What changed
`OverlayService.kt` was decomposed from a ~1,100-line god class into:

| File | Responsibility |
|---|---|
| `OverlayStateHolder.kt` | Owns all mutable overlay state (`StateFlow`s for phase, picks, bans, recommendations) and the coroutine scope. No Android service lifecycle. |
| `OverlayCaptureCoordinator.kt` | Orchestrates `ScreenCaptureManager` + `FrameProcessor`; calls back to `OverlayStateHolder` as hero detections arrive. |
| `DraftOverlayContent.kt` | Top-level `@Composable`; routes to `BanPhaseContent`, `PickPhaseContent`, `TradingPhaseContent`, or `FinalReportContent` based on `phase`. |
| `OverlayService.kt` (shell) | Sets up `JetOverlay.show/hide`, wires the foreground notification, starts/stops `OverlayCaptureCoordinator`. ~250 LOC. |

`MLBBApplication.onCreate()` calls:
```kotlin
JetOverlay.initialize(this) { overlayContent { DraftOverlayContent() } }
```

### LOC delta

| Before | After |
|---|---|
| `OverlayService.kt`: ~1,100 LOC (monolithic) | Shell: ~250 LOC + 3 focused classes |

### Why this was executed despite the earlier deferral in §6
The earlier §6 entry deferred the split because the environment lacked a Gradle build
for compile-time verification. This pass executed the split via JetOverlay because:
1. JetOverlay encapsulates all `WindowManager`, `LifecycleOwner`, and drag-to-dismiss
   boilerplate, reducing the blast radius of the split.
2. The decomposition follows the three-class boundary already planned in §6's
   reconciliation path.
3. The JetOverlay SDK handles lifecycle safety, absorbing the main historical risk.

### Trade-offs accepted
- All overlay window-creation quirks must be debugged through JetOverlay's API rather
  than a custom `WindowManager.LayoutParams`.
- Callers must use `JetOverlay.show/hide` rather than direct window manager access.

---

## 12. `PickPhaseContent` pick-success animation

### What changed
`PickPhaseContent.kt` tracks a `lastPickedHero: Hero?` state variable. When the player
taps a hero chip in `RecommendationCard`, the wrapped `onTap` lambda sets
`lastPickedHero = hero` before calling `onHeroTap`. A `PickSuccessOverlay` composable
appears immediately above the recommendation row, plays `R.raw.lottie_pick_success` once
at 1.2× speed (~1.4 s), and dismisses via `LaunchedEffect(hero) { delay(1_400L); onDone() }`.

### Design decisions
- **State lives in the parent `PickPhaseContent`**, not inside `RecommendationCard`, so
  the animation persists even if the recommendation list re-renders during the animation window.
- **`LaunchedEffect` keyed to `hero`** so that if the player taps a second hero during
  the 1.4 s window, the effect restarts and the animation replays for the new selection.
- **`speed = 1.2f`** keeps the animation under 1.5 s — readable on MLBB's 30-second
  pick clock, short enough not to delay the player.
- The animation is **visual-only** and does not block the `onHeroTap` business logic,
  which fires synchronously before the animation starts.

---

## 13. TFLite hero classifier integration (TD-15)

### What was integrated
`mlbb_hero_classifier.tflite` (MobileNetV3Small, ~2 MB, trained with TensorFlow 2.20.0)
is the **primary** hero-portrait matching path in `PortraitMatcher`. The pHash + colour-
histogram path becomes the fallback.

| File | Role |
|---|---|
| `capture/HeroClassifier.kt` | `Interpreter` wrapper — loads model, preprocesses bitmaps, runs inference |
| `assets/mlbb_hero_classifier.tflite` | Trained MobileNetV3Small model |
| `assets/hero_classifier_labels.txt` | Output index → heroId mapping — **must be regenerated alongside the model on every retrain** |
| `capture/PortraitMatcher.kt` | Tries `HeroClassifier.classify()` first; falls through to pHash when confidence is below `TFLITE_TENTATIVE_THRESHOLD` |
| `capture/PhaseDetectionConfig.kt` | `TFLITE_ACCEPT_THRESHOLD = 0.70`, `TFLITE_TENTATIVE_THRESHOLD = 0.45`, `TFLITE_TOP_K = 3` |
| `gradle/libs.versions.toml` | `tensorflowLite = "2.16.1"` + `tensorflow-lite` library alias |
| `app/build.gradle.kts` | `implementation(libs.tensorflow.lite)` + `androidResources { noCompress += listOf("tflite") }` |
| `ml/mlbb_hero_classifier_train.ipynb` | Google Colab training notebook — source of truth for model + label file generation |

### Retraining the model
Open `ml/mlbb_hero_classifier_train.ipynb` in Google Colab (Runtime → T4 GPU). The notebook:
1. Uploads `app/src/main/res/raw/default_heroes.json`
2. Downloads CDN portraits, applies augmentation, trains two-phase MobileNetV3Small
3. Converts to float16 TFLite
4. **Generates both output files** — `mlbb_hero_classifier.tflite` and `hero_classifier_labels.txt`

Copy both downloaded files to `app/src/main/assets/`. The app requires both — without
`hero_classifier_labels.txt` matching the model's class count, `HeroClassifier.isAvailable`
returns `false` and the TFLite path is silently bypassed.

### Model specification
- **Architecture:** MobileNetV3Small (Keras) → `global_average_pooling2d` → Dense → softmax
- **Input:** `[1, 224, 224, 3]` float32, normalised to [−1, 1] (`pixel / 127.5 − 1.0`)
- **Output:** `[1, 120]` float32 softmax probabilities
- **Inference threads:** 2 (`Interpreter.Options.setNumThreads(2)`)

### APK packaging requirement
TFLite's `Interpreter` memory-maps the model via `MappedByteBuffer` + `FileChannel.map()`.
This requires the asset to be **uncompressed** in the APK. Without `androidResources { noCompress += listOf("tflite") }`, aapt compresses the file and the `FileChannel.map()` call throws `IOException: offset + length > FileChannel size`.

### Label file contract
`hero_classifier_labels.txt` is loaded by `HeroClassifier` line by line (skipping `#` comments
and blank lines). Line index *n* maps to output neuron *n*. If the label count doesn't equal
the model output size, `isAvailable` returns `false` and every call falls back to pHash.
When the model is retrained with a different class ordering, update this file to match.

### Confidence thresholds
| Constant | Value | Meaning |
|---|---|---|
| `TFLITE_ACCEPT_THRESHOLD` | 0.70 | Use result directly (no confirmation required if count met) |
| `TFLITE_TENTATIVE_THRESHOLD` | 0.45 | Start multi-frame counter; mark `requiresConfirmation = true` |
| Below `TFLITE_TENTATIVE_THRESHOLD` | — | Fall through to pHash + histogram |

Tuning note: MobileNetV3Small reaches ~0.90 confidence on clean, static portrait crops but
drops to ~0.60 on hero-reveal animation frames. The 0.70 accept threshold is deliberately
conservative — lower it only after benchmarking false-positive rates on a representative
frame set from MLBB's current patch.

### Class disambiguation
`HeroPortraitObjectDetector` (stub) handles portrait *region detection* (bounding boxes in
the raw frame) and is a separate, not-yet-trained model. Do not confuse it with
`HeroClassifier`, which handles *identification* of heroes in pre-cropped slots.

### Thread safety
`org.tensorflow.lite.Interpreter` is not thread-safe. `HeroClassifier.classify()` must
be called from a single thread. `PortraitMatcher.match()` runs inside
`withContext(Dispatchers.Default)` — callers must not invoke `match()` concurrently on
the same `PortraitMatcher` instance without external synchronisation.

### Trade-offs accepted
- The labels file (`hero_classifier_labels.txt`) is generated by the training notebook
  (Cell 13b) sorted ascending by hero ID. If the model is retrained with a different
  hero set or ordering, re-run the notebook and replace both files in `assets/` — the
  notebook's Cell 13b asserts the ID order is consistent before writing the file.
- Heroes added after the last training run (currently IDs 121–132 in the roster) are
  not classified by TFLite and fall back to pHash. Retrain with the updated
  `default_heroes.json` to include them.
- `Bitmap.createScaledBitmap` uses bilinear interpolation — sufficient for the
  MobileNetV3Small receptive field but not as accurate as bicubic. This is an acceptable
  trade-off for inference speed on mid-range devices.

---

## 14. Full-offline audit (TD-20)

### What changed
An end-to-end audit of every network touch-point, done from a read-only checkout with
no Gradle/JVM/Android toolchain and no network egress available. Findings:

| Touch-point | Verdict | Notes |
|---|---|---|
| Hero portraits (`HeroPortrait.kt`, `HeroGrid.kt`) | ✅ already offline | Both load `file:///android_asset/portraits/{id}.webp` via Coil's `AssetUriFetcher` — no network. `features.md` §9.9 previously claimed "Coil 3 + OkHttp network backend"; that line was stale documentation, not actual behaviour, and has been corrected. |
| Hero portrait *classification* (`HeroClassifier`, `mlbb_hero_classifier.tflite`) | ✅ already offline | Bundled asset, runs entirely on-device. |
| Hero meta sync (`MetaApi`, `HeroRepositoryImpl.syncHeroes`) | ✅ already offline-safe | Retries 3× then falls back to the existing Room DB, and seeds from bundled `res/raw/default_heroes.json` if the DB is empty. Never blocks the UI — `HeroListViewModel.init` fires it with `runCatching` and does not await it before rendering cached/seed data. |
| OCR phase disambiguation (`PhaseOcrDetector`, ML Kit Text Recognition) | ✅ kept, lazy download, user-toggleable | Disambiguates edge-case frames (dark loading screens vs. ban phase, double-pick animation, ally/enemy pick turn, end-of-draft) that the colour-based `PhaseDetector` alone misreads — see the file header in `PhaseOcrDetector.kt` for the full list. It is a confidence-boosting cross-check, not a required dependency: on any failure (or when disabled) it stays at `UNKNOWN`/confidence 0 and callers fall back to `PhaseDetector`, per the "manual is the dependable fallback" principle in `mission.md` §2. An install-time model dependency (`com.google.mlkit.vision.DEPENDENCIES`) was evaluated and **rejected** — it would force every install to download the model even for users who never trigger OCR fallback, which is *more* total network use than the default. Kept as ML Kit's default lazy/on-demand download (fetched at most once per device, then cached and reused fully offline), and additionally gated behind a new `CvFeatureFlags.enableOcr` flag wired to an "OCR phase detection" toggle in Settings → OVERLAY, so a user who wants zero chance of that one-time download can opt out entirely without losing the app's core (colour-based) functionality. |
| Background hero sync worker | ℹ️ doc/code mismatch | `roadmap.md`, `features.md` §7.10 and `todo.md` TD-13 describe a `HeroSyncWorker` (WorkManager + HiltWorker, 24 h periodic). No such class, and no WorkManager usage at all, exists anywhere in `app/src/main/java` in this checkout. Treat those doc references as aspirational/stale until the worker is actually implemented — do not assume periodic background sync is running. |
| Crash handling, DataStore, Room, TFLite, portraits, counter/archetype JSON | ✅ already offline | All local-first; none require network at any point. |

### Why
`mission.md` §3 already states the product is "not permanently coupled to any specific
backend" and the hero data model is independent of where data originates — the
architecture was already offline-first by design. This audit exists to verify that
design intent actually holds in the current source (docs elsewhere had drifted from
code — see the worker mismatch above), and to close the one real gap found (OCR model
delivery timing).

### What's intentionally unchanged
- `INTERNET` / `ACCESS_NETWORK_STATE` permissions are kept — meta sync and future OCR
  model updates are opportunistic enhancements, not requirements. Removing them would
  regress the "sync when available" feature for no offline benefit.
- `ConnectivityBanner` / `NetworkMonitor` are kept as-is — informing the user "no
  internet — showing cached data" is correct behaviour for an app that is offline
  *capable*, not offline-*only*.

---

## 15. Manual screenshot-mapping feature removal (TD-21)

### What changed
Removed the manual "BAN PHASE REFERENCE" screenshot feature from Settings in its
entirety: users could pick a screenshot of the ban phase and manually tap-map
portrait slot positions on it via a dialog, stored as a JSON blob and consumed
only by that same dialog to show a "mapped positions" count. Deleted:
- `presentation/settings/components/BanPhaseScreenshotSection.kt`
- `presentation/settings/components/ScreenMappingDialog.kt`
- `presentation/settings/components/MappingSlotModel.kt`

And stripped all wiring: the `BanPhaseScreenshotSection` usage, `showMappingDialog`
state, and `ScreenMappingDialog` invocation in `SettingsScreen.kt`; the
`banPhaseScreenshotUri` / `screenMappingJson` fields in `SettingsState.kt`; and the
`KEY_BAN_SCREENSHOT_URI` / `KEY_SCREEN_MAPPING` DataStore keys plus
`setBanPhaseScreenshotUri` / `setScreenMapping` setters in `SettingsViewModel.kt`.
Also removed the corresponding row (§6.13) from `features.md`.

### Why
Confirmed via grep across `app/src/main/java` that `screenMappingJson` /
`parseMappedPoints` / `ScreenMappingDialog` / `MappingSlotModel` had **no**
consumers outside this Settings UI — the automated capture pipeline
(`FrameProcessor`, `SlotRegions`, `PortraitMatcher`, etc.) does not read this
JSON at all, so the feature was purely a manual reference/debugging aid, not a
dependency of ban-slot detection. User explicitly requested full removal.

### Trade-offs accepted
- Users lose the ability to manually eyeball-map ban-slot coordinates against a
  reference screenshot. Automated calibration (`WeightCalibrator`,
  `hero_thresholds.json` via `scripts/calibrate_thresholds.py`, see TD-17) is
  the supported path for slot-position accuracy going forward.

### Verification caveat
No Gradle/Kotlin toolchain is available in this checkout — this removal was
verified by exhaustive grep for every symbol name (`BanPhaseScreenshot*`,
`banPhaseScreenshotUri`, `screenMappingJson`, `ScreenMappingDialog`,
`MappingSlotModel`, `KEY_BAN_SCREENSHOT_URI`, `KEY_SCREEN_MAPPING`,
`setScreenMapping`, `setBanPhaseScreenshotUri`) across
`app/src/main/java`, confirming zero remaining references, not by a build.
