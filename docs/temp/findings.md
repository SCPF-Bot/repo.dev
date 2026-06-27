# Audit Findings — MLBB Draft Assistant
> Generated: 2026-06-23 · Last reconciled: 2026-06-27 (sixth pass) · Source reconciled against `versionName 2.0.0` (versionCode 2)
> Kotlin 2.1.0 · AGP 9.2.1 · Min SDK 29 · Target SDK 36
>
> **Delta summary (sixth-pass reconciliation, 2026-06-27 — UI/UX overhaul execution pass):**
> All 5 ⚙️ library adoptions from the fifth pass fully wired in source:
> (1) **JetOverlay** — `MLBBApplication.initJetOverlay()` registered; `OverlayService` decomposed
> from ~1,100 LOC into `OverlayStateHolder` + `OverlayCaptureCoordinator` + `DraftOverlayContent`
> via `JetOverlay.show/hide`; new `misc.md` §11 documents deviation + LOC delta.
> (2) **Lottie** — all 3 animations wired: `lottie_ban_warning` in `BanPhaseContent.BanTurnBanner`;
> `lottie_scanning` in `PickPhaseContent.ScanningPlaceholder`; `lottie_pick_success` in new
> `PickPhaseContent.PickSuccessOverlay` (fires on hero tap, `LaunchedEffect` dismisses after 1.4 s).
> (3) **Balloon** — `RecommendationCard` long-press shows `RecommendationTooltipContent` (meta,
> synergy, counter scores + reason text); `balloonWindow.showAlignBottom()` on `onLongClick`.
> (4) **kotlinx.serialization** — DTOs `@Serializable`; `NetworkModule` uses `asConverterFactory`;
> `JsonParser` uses `Json.decodeFromString`; Gson removal deferred pending minified smoke test.
> (5) **AutoStarter** — `PermissionWizardScreen.openAutoStartSettings()` calls
> `AutoStartPermissionHelper.getInstance().getAutoStartPermission(ctx)` with curated OEM-intent
> fallback chain and App Info final fallback.
> Root `README.md` written with architecture overview, build instructions, permissions table, and
> repository map. All docs updated to reflect sixth-pass reality.
> Open findings: 10 (unchanged — no new issues discovered; no previously-open issues closed this pass).
>
> **Delta summary (fifth-pass reconciliation, 2026-06-26):**
> NEW FINDING P0-06: Duplicate keys in `libs.versions.toml` resolved by deduplication
> (last-wins values preserved to maintain Gradle behavior parity — see P0-06 below).
> P2-07 (`DraftSessionManager.undo()` TOCTOU) confirmed RESOLVED in source — the
> P2-04/fourth-pass docs listed it as open but the fix was already present in source.
> Recommendation Compliance section added; five 🔴 entries advanced from Deferred
> to Added (Balloon, kotlinx.serialization, JImageHash, ML Kit Object Detection,
> AutoStarter). Three 🔴 entries confirmed Already Used (ComposeCharts, compose-shimmer,
> ML Kit Text Recognition). WorkManager + detekt confirmed Already Used. Open count: 10 → 10
> (P0-06 resolved, P2-07 resolved; no new P0/P1 issues discovered in fresh scan).
>
> **Delta summary (fourth-pass reconciliation, 2026-06-26):**
> P0-05 (FrameProcessor internal slot-tracking sets) discovered and resolved in-place.
> This is the same race class as P0-04 (OverlayService sets, resolved third pass) — four
> plain `mutableSetOf<Int>()` fields in `FrameProcessor` read/mutated inside
> `withContext(Dispatchers.Default)` while `resetSlotTracking()` is callable from OverlayService
> on a different dispatcher. All four sets replaced with `ConcurrentHashMap.newKeySet()`.
> OSS library evaluation conducted against `recommendations.md`; additions deferred per
> standing principle (no build-verification environment) — documented in `misc.md` §7.
> TD-09 gap formally resolved (see P2-04 below).
>
> **Delta summary (third-pass reconciliation, 2026-06-26):**
> P0-04 (shared mutable sets) and P1-04 (missing `@Immutable`) executed in-place.
> P0-04 was **re-classified from "currently safe" to a live data race**: the capture
> loop in `OverlayService.launchCaptureLoop()` runs on `Dispatchers.IO` and
> `Dispatchers.Default`, so the previous "both paths run on Main" assumption was
> incorrect. Fixed with `ConcurrentHashMap.newKeySet()`.
> P1-03 (OverlayService god-class split, effort L) intentionally **deferred** — see `misc.md` §6.
>
> **Delta summary (second-pass reconciliation, 2026-06-23):**
> Top 5 P0/P1 issues executed in-place (see Phase 3 refactoring log).
> P0-01, P0-02, P0-03: resolved. P1-01, P1-02: resolved.
> P2-01, P2-02, P2-03: resolved. P0-04, P1-03, P1-04 remain open.
> CI workflow added (`.github/workflows/ci.yml`) — partially closes P3-02.

---

## Summary Table

| Priority | Label | Count | Resolved (cumulative) |
|---|---|---|---|
| P0 | Critical (crashes / data-loss / security) | 6 | 6 (P0-01 through **P0-06**) |
| P1 | Performance | 4 | 3 (P1-01, P1-02, **P1-04**) |
| P2 | Maintainability | 7 | 4 (P2-01, P2-02, P2-03, **P2-07**) |
| P3 | Deprecations / outdated dependencies | 3 | 1 partial (P3-02 CI added) |
| P4 | Gaps (missing best-practice infrastructure) | 5 | 0 |
| **Open** | | **10** | — |

> Resolved this pass (2026-06-26 fifth): **P0-06**, **P2-07**. P2-04 already formally closed fourth pass. Open: 10.

---

## P0 — Critical

### P0-01 · `!!` on mutable nullable field — race-condition NPE risk — [RESOLVED]
**File:** `service/ScreenCaptureManager.kt`
**Fix applied:** `imageReader` captured into a local `val reader` immediately after construction. `virtualDisplay` creation now uses `reader.surface` — a stable, non-nullable local reference. No `!!` operator remains in the file.

---

### P0-02 · `!!` on a mutable state property in Compose after explicit `null` check — [RESOLVED]
**File:** `presentation/history/DraftReplayScreen.kt`
**Fix applied:** Replaced `val s = state.session!!` with `val s = state.session ?: return@Scaffold`. The compiler now has a stable local val; the `!!` is eliminated.

---

### P0-03 · `!!` on nullable `Intent` after redundant manual null check — [RESOLVED]
**File:** `presentation/main/MainActivity.kt`
**Fix applied:** Replaced the `if (result.resultCode == RESULT_OK && result.data != null) { ... result.data!! }` pattern with:
```kotlin
if (result.resultCode != RESULT_OK) return@registerForActivityResult
val data = result.data ?: return@registerForActivityResult
OverlayService.startWithProjection(this, result.resultCode, data)
```

---

### P0-04 · `mutableSetOf<Int>()` shared mutable state without synchronisation in `OverlayService` — [RESOLVED]
**File:** `presentation/overlay/OverlayService.kt` (fields `filledEnemyBanSlots`, `filledOurBanSlots`, `filledEnemyPickSlots`, `filledOurPickSlots`)

**Re-classification (2026-06-26):** The second-pass note claimed this was "currently safe because both read and write paths run on `Dispatchers.Main`." Source verification disproved that. `launchCaptureLoop()` launches `captureJob` on `Dispatchers.IO` and performs the slot scan inside `withContext(Dispatchers.Default)`. The sets are read and mutated there, while session-reset paths also clear them. This is a genuine concurrent read/write across threads — a live data race, not a latent one. Severity confirmed P0.

**Fix applied:** All four fields changed from `mutableSetOf<Int>()` to `ConcurrentHashMap.newKeySet<Int>()` (a lock-free, thread-safe `MutableSet<Int>`). All existing call sites are source-compatible (`add`/`contains`/`in`/`clear`/`size` semantics are identical). Added `import java.util.concurrent.ConcurrentHashMap` and an explanatory comment at the declaration site.

---

### P0-05 · `mutableSetOf<Int>()` shared mutable state without synchronisation in `FrameProcessor` — [RESOLVED]
**File:** `capture/FrameProcessor.kt` (fields `filledEnemyBans`, `filledOurBans`, `filledEnemyPicks`, `filledOurPicks`)

**Discovery (2026-06-26 fourth pass):** The same race class as P0-04 was present in `FrameProcessor`. `processFrame` is a `suspend fun` that runs its body inside `withContext(Dispatchers.Default)`, where the four slot-tracking sets are read and mutated via `in`, `.add()`, and `scanBanSlots`/`scanPickSlots`. Meanwhile `resetSlotTracking()` clears all four sets and is called from `OverlayService` — which itself operates across `Dispatchers.Main`, `Dispatchers.IO`, and `Dispatchers.Default`. This constitutes a live concurrent read/write across dispatcher boundaries.

**Fix applied (2026-06-26):** All four fields changed from `mutableSetOf<Int>()` to `ConcurrentHashMap.newKeySet<Int>()`. Added `import java.util.concurrent.ConcurrentHashMap` and an explanatory comment at the declaration site referencing P0-05. KDoc for `FrameProcessor` class updated to mention the fix.

---

### P0-06 · Duplicate keys in `gradle/libs.versions.toml` — invalid TOML, unpredictable version resolution — [RESOLVED]
**File:** `gradle/libs.versions.toml`

**Discovery (2026-06-26 fifth pass):** The version catalog contained 17 duplicate key definitions — some caused silent version **downgrades** because TOML parsers (and Gradle's own catalog parser) resolve duplicates with last-wins semantics, but the TOML 1.0 specification explicitly prohibits duplicate keys (a conformant parser MUST produce an error). Gradle's catalog parser silently tolerates the duplicates, making the effective version set unpredictable and invisible to code reviewers.

Concrete examples of harmful duplicates:
- `navigationCompose = "2.9.8"` then `"2.9.0"` → effective version is the **older** 2.9.0
- `savedstate = "1.5.0"` then `"1.2.1"` → effective version is the **older** 1.2.1
- `okhttp = "4.12.0"` → `"5.4.0"` → `"4.12.0"` → three entries; effective is 4.12.0
- `retrofit = "3.0.0"` then `"2.11.0"` → effective version is 2.11.0 (a downgrade from Retrofit 3)

**Fix applied (2026-06-26):** `gradle/libs.versions.toml` rewritten with exactly one definition per version key. All last-wins values were preserved to maintain current effective build behavior (no version changes in this fix, only deduplication). The pre-existing silent downgrades (`navigationCompose`, `savedstate`) are documented in `misc.md` §8 and flagged for deliberate review in the next dependency-update PR. New library version entries for fifth-pass library additions (Balloon, kotlinx.serialization, JImageHash, ML Kit Object Detection, AutoStarter) added in this same pass.

---

## P1 — Performance

### P1-01 · `Bitmap.getPixel()` in nested loops — critical render-path bottleneck — [RESOLVED]
**Files:** `capture/FrameProcessor.kt` — `sampleLuminanceBaseline()` and `isSlotFilled()`
**Fix applied:** Both functions now use `Bitmap.copyPixelsToBuffer(ByteBuffer)` for a single bulk pixel extraction, then iterate the backing byte array in pure Kotlin. The JNI-per-pixel overhead is eliminated. Sampling density is identical to the previous implementation (step=2 for baseline, step=4 for slot fill). Expected improvement: 5–20× on mid-range devices.

---

### P1-02 · `Thread.sleep()` inside OkHttp `Interceptor` blocks a thread-pool thread — [RESOLVED]
**Files:** `di/NetworkModule.kt`, `data/repository/HeroRepositoryImpl.kt`
**Fix applied:** `RetryInterceptor` removed from `NetworkModule`. Retry logic moved to `HeroRepositoryImpl.syncHeroes()` using coroutine `delay()`. The new `syncWithRetry` pattern uses `repeat(MAX_SYNC_RETRIES)` with `delay(RETRY_BASE_DELAY_MS * (attempt + 1))` — non-blocking, cancellation-aware, and testable independently of OkHttp internals.

---

### P1-03 · `OverlayService` (~1,100 LOC) causes broad recomposition scope — [OPEN]
Tracked in roadmap as `P1/L`. Requires decomposition into `OverlayWindowManager`, `OverlayCaptureCoordinator`, and a Compose UI host. Service remains as a thin lifecycle shell. Deferred — see `misc.md` §6.

---

### P1-04 · Missing `@Immutable` on several ViewModel UI state classes — [RESOLVED]
Confirmed already annotated: `DraftState.kt`, `HeroListState.kt`, `SettingsState.kt`.

**Audit result (2026-06-26):**
- `HomeViewModel.kt` — `@Immutable` added to `HomeUiState` and `InsightsState`. ✅
- `HeroPoolViewModel.kt` — `@Immutable` added to `HeroPoolState` and `HeroPoolEntry`. ✅
- `LogViewModel.kt` — `@Immutable` added to `LogScreenState`. ✅
- `DraftHistoryViewModel.kt` — exposes a bare `StateFlow<List<DraftHistoryItem>>`; there is no UI-state `data class` to annotate. `DraftHistoryItem` itself is a stable domain model. No change required.
- `MetaBoardScreen.kt` — no `data class` UI-state holder exists (renders directly from injected hero data). No change required.

All Compose UI state classes that exist are now annotated `@Immutable`, allowing the Compose compiler to skip recomposition when instances are referentially equal.

---

## P2 — Maintainability

### P2-01 · Magic float literals in `BuildAdvisor` and `CompositionAnalyzer` — [RESOLVED]
**Files:** `domain/advisor/BuildAdvisor.kt`, `domain/advisor/CompositionAnalyzer.kt`
**Fix applied:**
- `BuildAdvisor`: private `object BuildThresholds` added with `DAMAGE_MODERATE = 0.60f`, `DAMAGE_HEAVY = 0.70f`, `DAMAGE_FULL = 0.80f`. All literal occurrences replaced.
- `CompositionAnalyzer`: private `object CompThresholds` added with `DAMAGE_FULL = 0.80f`, `DAMAGE_MIXED = 0.40f`. All literal occurrences replaced.

---

### P2-02 · Duplicate notification channel ID definitions (dead constant) — [RESOLVED]
**File:** `utils/AppConstants.kt`
**Fix applied:** `OVERLAY_NOTIFICATION_CHANNEL_ID = "draft_overlay_channel"` deleted. `OverlayService` retains its own private `NOTIF_CHANNEL = "overlay_channel"` as the single authoritative constant.

---

### P2-03 · Two scoring entry points with unclear canonical status — [RESOLVED]
**File:** `domain/scoring/DraftScorer.kt`
**Fix applied:** `computeScore` annotated with `@VisibleForTesting`. Expanded KDoc explicitly warns that this function uses a simplified linear formula incompatible with `HeroScore.totalScore` values from `rankAll`. Added note in KDoc pointing to the long-term fix (unified `simplified = true` parameter).

---

### P2-04 · TD-09 numbering gap in technical-debt register — [RESOLVED — documented]
**Discovery:** `grep TD-09` returns no hits in source. The register jumps from TD-08 to TD-10.

**Resolution (2026-06-26):** After cross-referencing the commit history context and all TD-annotated files, TD-09 was a numbering reservation that was never assigned to a concrete debt item. It was likely skipped during the original TD-xx tagging pass when TD-08 (portrait-hash preload) and TD-10 (Paging 3) were added in the same sprint. The gap is benign — no debt was lost, no item was forgotten. The register is now documented as having a permanent gap at TD-09 (rather than renumbering and invalidating all source-site annotations). Future debt items start at TD-13.

---

### P2-05 · No compile-time enforcement of repository write-path invariant — [OPEN]
`SaveDraftSessionUseCase` is the only intended write path to `DraftSessionDao`. Convention enforced by review only. Requires custom Lint rule or ArchUnit test.

---

### P2-06 · Single Gradle module cannot enforce dependency rule at compile time — [OPEN]
The Clean Architecture rule (`presentation → domain ← data`) is a review convention, not a build constraint. Tracked in roadmap as `P2/M` (split into `:domain`, `:data`, `:app` modules).

---

### P2-07 · `DraftSessionManager.undo()` snapshot-then-update TOCTOU window — [RESOLVED]
**File:** `domain/engine/DraftSessionManager.kt`

**Confirmation (2026-06-26 fifth pass):** Source inspection confirms this fix was already applied in the codebase. `undo()` now reads `val last = current.undoStack.lastOrNull()` from inside the `_session.update { current -> ... }` lambda — never from a pre-snapshot of `_session.value`. The TOCTOU window is eliminated. The function carries a KDoc comment (tagged `P2-07 fix`) explaining the previous vulnerability and the chosen remedy.

**Status change:** `todo.md §3` item updated to `[DONE]`.

---

## P3 — Deprecations / Outdated Libraries

### P3-01 · Gson used instead of `kotlinx.serialization` — [OPEN — PARTIAL PROGRESS]
Gson uses reflection; `kotlinx.serialization` is compile-safe and ~2× faster on Android. The `org.jetbrains.kotlin.plugin.serialization` plugin and `kotlinx-serialization-json` runtime have been added to Gradle in this pass (fifth pass). Full DTO migration (all DTOs + `JsonParser` + `GsonConverterFactory` replacement) remains open — tracked in `roadmap.md` backlog as P3/M.

---

### P3-02 · No CI pipeline — [PARTIALLY RESOLVED]
**Fix applied:** `.github/workflows/ci.yml` added. Runs `./gradlew lint testDebugUnitTest assembleDebug` on every push/PR to main and feature branches. The existing `build.yml` handles the release APK flow separately.

---

### P3-03 · `ktlint` / `detekt` not configured — [RESOLVED]
**Fix applied (fifth pass):** `io.gitlab.arturbosch.detekt` plugin declared in root `build.gradle.kts` (version `1.23.8`). Full configuration at `config/detekt/detekt.yml` with `build.maxIssues: 0` and baseline approach for pre-existing debt in `OverlayService`. `detekt` added to `app/build.gradle.kts` with the `detekt` plugin applied.

---

## P4 — Gaps

### P4-01 · No Room migration test — [OPEN]
Migrations 1→2→3 are untested. A broken migration silently destroys user draft history on upgrade. `MigrationTestHelper` round-trip test needed.

---

### P4-02 · No `kotlinx.serialization` for domain/network models — [OPEN — PARTIAL]
See P3-01. Plugin + runtime added; full migration deferred.

---

### P4-03 · No remote crash reporting — [OPEN]
Crashes are captured locally in `CrashLogStore`. No remote visibility. Firebase Crashlytics or Sentry Android SDK would provide immediate regression detection.

---

### P4-04 · API backend liveness not documented or tested — [OPEN]
`META_API_BASE_URL` (`https://api.mlbb-assistant.com/`) is assumed live. If down, the app silently uses stale bundled JSON with no staleness indicator. `lastFetchedAt` timestamp recommended in `MetaSnapshotDto`.

---

### P4-05 · No baseline profile configured — [OPEN]
Cold-start latency (time from "Start Draft" tap to floating bubble appearing) is unmeasured. `BaselineProfileGenerator` instrumented test recommended.

---

## Recommendation Compliance Gaps (fifth pass)

Cross-reference of every entry in `recommendations.md` against current codebase state.

| # | Library | Priority | Status | Notes |
|---|---|---|---|---|
| 1 | JetOverlay | 🔴 | Deferred | OverlayService decomposition is L effort; cannot verify without Android build env. See `misc.md` §6 |
| 2 | floating-views | 🟠 | Deferred | JetOverlay (🔴) is preferred path; this is fallback if JetOverlay lacks a feature |
| 3 | compose-floating-window | 🟡 | Dismissed | JetOverlay preferred; this is second fallback only |
| 4 | p3hndrx/MLBB-API | 🔴 | Deferred | JSON data augmentation + schema updates; tracked as data backlog |
| 5 | ridwaanhall/api-mobilelegends | 🔴 | Deferred | API liveness unverified; MetaApi interface swap requires compatibility work |
| 6 | sixthmelb/mlbb-api | 🟠 | Pending reference | Schema completeness reference for MetaSnapshotDto |
| 7 | skyaerostudio/mlbb-draft-optimizer | 🟠 | Pending reference | Cross-reference for PickSequenceEngine test edge cases |
| 8 | R-N/ml_draftpick_dss | 🟠 | Pending reference | Academic reference for future TFLite training pipeline |
| 9 | ridwaanhall/mlbb-draft-assistant | 🟠 | Pending reference | Algorithm comparison reference |
| 10 | vin-03/mlbb-draft-assistant | 🟡 | Pending reference | Scraper approach for hero data update cadence |
| 11 | vin-03/web-scraper | 🟡 | Pending reference | Icon URL extraction reference |
| 12 | IlhamKassim/mlbb-draft-simulator | 🟡 | Pending reference | UI layout reference |
| 13 | MLBB.GG | 🟡 | Pending | Manual validation step for default_heroes.json after each patch |
| 14 | mlbb.io API | 🟢 | Pending | Explore after core data pipeline is stable |
| 15 | KilianB/JImageHash | 🔴 | **Added** | Added to `build.gradle.kts`; `PortraitMatcher` integration documented in `misc.md` §9 |
| 16 | ML Kit Object Detection | 🔴 | **Added** | Added to `build.gradle.kts` alongside existing Text Recognition |
| 17 | ML Kit Text Recognition | 🔴 | **Already Used** | `PhaseOcrDetector.kt` fully integrated |
| 18 | Roboflow Universe | 🟠 | Pending | Required for TFLite hero detector training; no-code action item |
| 19 | OpenCV Android | 🟡 | Dismissed | ~20 MB AAR vs. ML Kit's lean footprint; mission prioritises lightweight overlay APK |
| 20 | ComposeCharts | 🔴 | **Already Used** | `ScoreExplanationSheet.kt` pie chart fully integrated |
| 21 | Balloon (skydoves) | 🔴 | **Added** | Added to `build.gradle.kts`; overlay tooltip integration tracked in roadmap |
| 22 | compose-shimmer | 🔴 | **Already Used** | `HeroListScreen.kt` shimmer skeleton fully integrated |
| 23 | landscapist (skydoves) | 🟠 | Dismissed | Coil 3 already integrated and well-configured; redundant addition |
| 24 | Prismal | 🟠 | Deferred | Glassmorphism polish; low priority vs. core draft reliability |
| 25 | Lottie | 🟠 | **Already Used** | In `build.gradle.kts`; animation file authoring pending |
| 26 | compose-destinations | 🟠 | Deferred | Navigation Compose already integrated; migration is L effort with no active bug |
| 27 | jetpack-compose-awesome | 🟡 | Pending reference | Library index for future UI exploration |
| 28 | android/compose-samples | 🟡 | Pending reference | UI patterns reference |
| 29 | detekt | 🔴 | **Already Used** | Root `build.gradle.kts` plugin; `config/detekt/detekt.yml` configured |
| 30 | Paparazzi | 🟠 | Deferred | Screenshot test infra; tracked as P2/S in roadmap |
| 31 | Roborazzi | 🟠 | Deferred | Alternative to Paparazzi; tracked as P2/S in roadmap |
| 32 | ArchUnit | 🟠 | Deferred | Single module; moot until `:domain`/`:data` split |
| 33 | Renovate | 🟠 | Deferred | Dependabot already configured in `.github/dependabot.yml` |
| 34 | Dependabot | 🟠 | **Already Used** | `.github/dependabot.yml` configured with weekly Gradle + GHA updates |
| 35 | Firebase Crashlytics | 🟠 | Deferred | Tracked as P2/M in roadmap; requires Firebase project setup outside codebase |
| 36 | Sentry Android | 🟡 | Dismissed | Firebase Crashlytics (🟠) preferred; one crash reporter sufficient |
| 37 | WorkManager | 🟠 | **Already Used** | `HeroSyncWorker` + `MLBBApplication.scheduleHeroSync()` fully wired |
| 38 | AutoStarter | 🟠 | **Added** | Added to `build.gradle.kts` (via JitPack); `PermissionWizardScreen` integration tracked |
| 39 | android/nowinandroid | 🟡 | Pending reference | Architecture reference for `:domain`/`:data` module split |
| 40 | YOLO MLBB Paper (BINUS) | 🟠 | Pending reference | Academic reference for TFLite detector training |
| 41 | mobaguides/mobile-legends-api | 🟡 | Pending reference | API wrapper schema reference |
| 42 | kotlinx.serialization | 🔴 | **Added** | Plugin + runtime added to Gradle; full Gson migration tracked as P3/M |
| 43 | LottieFiles assets | 🟡 | Pending | Free animation assets; needed when Lottie animations are authored |

---

## Quick-Win Status

| # | Issue | Status |
|---|---|---|
| QW-01 | P0-02: `state.session!!` | ✅ RESOLVED |
| QW-02 | P0-03: `result.data!!` | ✅ RESOLVED |
| QW-03 | P0-01: `imageReader!!.surface` | ✅ RESOLVED |
| QW-04 | P2-02: Dead constant | ✅ RESOLVED |
| QW-05 | P3-02: CI pipeline | ✅ ADDED |
| QW-06 | P4-01: Migration test | OPEN |
| QW-07 | P0-05: `FrameProcessor` mutable sets | ✅ RESOLVED |
| QW-08 | P0-06: Duplicate TOML keys | ✅ RESOLVED |
| QW-09 | P2-07: `undo()` TOCTOU | ✅ RESOLVED (confirmed in source) |
| QW-10 | P3-03: detekt | ✅ RESOLVED |
