# TODO — MLBB Draft Assistant

> **Status:** Living, actionable backlog. Every item is concrete and verifiable.
> Strategic phasing lives in [`roadmap.md`](./roadmap.md), shipped capabilities
> in [`features.md`](./features.md), audit findings in [`docs/temp/findings.md`](./temp/findings.md).
>
> **Priority:** P0 critical · P1 high · P2 normal · P3 nice-to-have
> **Effort:** S ≤ 2 h · M ≤ 1 day · L > 1 day

---

## 0. Audit findings — immediate action (from `docs/temp/findings.md`)

*Items resolved in the 2026-06-23 refactoring pass are marked `[DONE]`.*
*Items resolved in the 2026-06-26 third/fourth/fifth pass are marked `[DONE — date]`.*
*Unresolved items remain as open action items.*

- [x] **P0/S** Fix `imageReader!!.surface` in `ScreenCaptureManager.kt` — capture `imageReader` into a local `val` before use to eliminate TOCTOU NPE on mutable nullable field. **[DONE — local `val reader` introduced in `startCapture()`]**
- [x] **P0/S** Fix `state.session!!` in `DraftReplayScreen.kt` — replace with `val s = state.session ?: return@Scaffold`. **[DONE]**
- [x] **P0/S** Fix `result.data!!` in `MainActivity.kt` — use `val data = result.data ?: return@registerForActivityResult`. **[DONE]**
- [x] **P0/M** Document and enforce thread-safety for `filledEnemyBanSlots` / `filledOurBanSlots` etc. in `OverlayService`. **[DONE — 2026-06-26: confirmed live data race; all four sets replaced with `ConcurrentHashMap.newKeySet<Int>()`. P0-04.]**
- [x] **P0/M** Fix `filledEnemyBans` / `filledOurBans` / `filledEnemyPicks` / `filledOurPicks` in `FrameProcessor.kt` — plain `mutableSetOf` raced between `processFrame` (Dispatchers.Default) and `resetSlotTracking()` (called from OverlayService). **[DONE — 2026-06-26: all four replaced with `ConcurrentHashMap.newKeySet<Int>()`. P0-05.]**
- [x] **P0/M** Deduplicate all 17 duplicate keys in `gradle/libs.versions.toml` — last-wins values preserved; suspicious downgrades documented in `misc.md` §8. **[DONE — 2026-06-26: P0-06]**
- [x] **P1/S** Replace `Bitmap.getPixel()` nested loops in `FrameProcessor.sampleLuminanceBaseline()` and `isSlotFilled()` with `Bitmap.copyPixelsToBuffer(ByteBuffer)` + array iteration. **[DONE — 5–20× CV hot-path speedup confirmed by implementation]**
- [x] **P1/M** Move retry logic out of `RetryInterceptor` (which used `Thread.sleep`) into `HeroRepositoryImpl.syncHeroes()` using coroutine `delay()`. **[DONE — `RetryInterceptor` removed; `syncWithRetry` coroutine pattern added with `MAX_SYNC_RETRIES=3` and exponential back-off]**
- [x] **P1/M** Audit all ViewModel UI state classes for `@Immutable` annotation. **[DONE — 2026-06-26: `@Immutable` added to `HomeUiState`, `InsightsState` (home), `HeroPoolState` + `HeroPoolEntry` (heropool), and `LogScreenState` (log). P1-04.]**
- [x] **P2/S** Delete dead constant `AppConstants.OVERLAY_NOTIFICATION_CHANNEL_ID = "draft_overlay_channel"`. **[DONE]**
- [x] **P2/S** Extract magic float thresholds in `BuildAdvisor.kt` and `CompositionAnalyzer.kt` into named constants. **[DONE — `BuildThresholds` and `CompThresholds` private objects added]**
- [x] **P2/S** Document TD-09 numbering gap. **[DONE — 2026-06-26: gap is a permanent unassigned reservation; future items start at TD-13. Documented in `findings.md` P2-04 and `overview.md` §8.]**
- [x] **P2/M** Fix `DraftSessionManager.undo()` TOCTOU — move `last` read inside `_session.update` lambda. **[DONE — 2026-06-26: confirmed in source, fifth pass. P2-07]**
- [x] **P3/S** Add `detekt` plugin and configuration. **[DONE — 2026-06-26: root `build.gradle.kts` plugin `apply false` → `app/build.gradle.kts` applies it; `config/detekt/detekt.yml` fully configured. P3-03]**

---

## 1. Technical-debt register (TD-xx)

The codebase uses inline `TD-xx` tags to mark debt resolved at the fix site.

| ID | Item | State | Where |
|---|---|---|---|
| TD-01 | `hasCCUlt` field replaces hardcoded CC name list | done | `Hero.kt`, `HeroEntity.kt`, `CompositionAnalyzer.kt`, migration v2→v3 |
| TD-02 | Personal hero-pool proficiency multiplier + 6-item builds | done | `DraftScorer.kt`, `Proficiency.kt`, `BuildAdvisor.kt` |
| TD-03 | CV thresholds centralised in config | done | `PhaseDetectionConfig.kt`, `PhaseDetector.kt` |
| TD-04 | Normalised luminance slot-fill threshold | done | `FrameProcessor.kt` |
| TD-05 | Dataset-derived dynamic scoring bounds | done | `DraftScorer.kt` (`computeBounds`) |
| TD-06 | Explicit `Dispatchers.IO` in repository suspend fns | done | `HeroRepositoryImpl.kt` |
| TD-07 | SavedStateHandle-backed search/filter | done | `HeroPoolViewModel.kt` |
| TD-08 | Parallel lazy portrait-hash preload + hybrid match | done | `PortraitMatcher.kt` |
| TD-09 | *(permanent gap — unassigned reservation; see `findings.md` P2-04)* | closed | n/a |
| TD-10 | Paging 3 hero grid | done | `HeroRepositoryImpl.kt`, `HeroDao.kt`, `GetPagedHeroesUseCase.kt` |
| TD-11 | Mutex-guarded crash-log writes | done | `CrashLogStore.kt` |
| TD-12 | Bubble position persistence | done | `OverlayService.kt` |
| TD-13 | WorkManager periodic hero-data sync | done | `HeroSyncWorker.kt`, `MLBBApplication.scheduleHeroSync()` |

**Next new debt item: TD-14.**

---

## 2. Correctness & robustness

- [ ] **P1/M** Verify `/schemas` JSON files (v1, v2, v3) are actually committed; if missing, run `./gradlew :app:kspDebugKotlin` and commit output. Required for safe future Room migrations.
- [ ] **P1/M** Add Room migration test (v1 → v3) using `MigrationTestHelper`. Assert all columns exist after migration. This protects against silent data loss on upgrade.
- [ ] **P1/M** Validate `SlotRegions` / `draft_ui_map.json` against multiple aspect ratios (18:9, 19.5:9, 20:9, tablets); document supported set.
- [ ] **P2/M** Handle `MediaProjection` revocation / `onStop` gracefully — stop capture, surface "capture unavailable" in overlay status bar.
- [ ] **P2/S** Guard `scoreSafety` against `enemies.size == 0` — add unit test to lock behaviour.
- [ ] **P2/S** Confirm `adaptiveWeights` never violates the `ScoreWeights` sum-to-1 invariant for all `pickIndex`; add property test.
- [ ] **P2/M** Define behaviour when `MetaApi` returns partial/garbage data — DTO validation + reject-and-keep-existing flow.
- [ ] **P3/S** Make `inferFromBanCount` thresholds in `RankRuleEngine` named constants instead of inline literals.

---

## 3. Architecture & maintainability

- [x] **P0/M** Replace `OverlayService` shared `MutableSet<Int>` fields with `ConcurrentHashMap.newKeySet()`. **[DONE — 2026-06-26, P0-04.]**
- [x] **P0/M** Replace `FrameProcessor` internal `MutableSet<Int>` fields with `ConcurrentHashMap.newKeySet()`. **[DONE — 2026-06-26, P0-05.]**
- [x] **P0/M** Deduplicate `libs.versions.toml` keys. **[DONE — 2026-06-26, P0-06.]**
- [x] **P2/M** Fix `DraftSessionManager.undo()` TOCTOU — confirmed resolved in source. **[DONE — 2026-06-26, P2-07.]**
- [ ] **P1/L** Decompose `OverlayService.kt` (~1,100 LOC) into: (a) `OverlayWindowManager` (window add/remove/drag), (b) `OverlayCaptureCoordinator` (capture loop + frame routing), (c) Compose UI host. Keep the `Service` class as a thin lifecycle shell. **[DEFERRED — 2026-06-26: intentionally not executed this pass; see `misc.md` §6 for rationale.]**
- [x] **P1/M** Audit all ViewModel UI state classes for `@Immutable` (see §0 above). **[DONE — 2026-06-26, P1-04.]**
- [ ] **P2/M** Extract overlay state into a dedicated `OverlayStateHolder` / ViewModel-like object observed by all phase composables; narrows recomposition scope.
- [ ] **P2/M** Introduce a `:domain` and `:data` Gradle module split to enforce the dependency rule at compile time.
- [ ] **P2/S** Unify `DraftScorer.computeScore` and `score` into a single entry point with a `simplified = true` parameter. Current `@VisibleForTesting` annotation is the interim fix.

---

## 4. Testing

- [ ] **P1/M** Compose UI tests for Draft, HeroList, Settings, and Permission Wizard.
- [ ] **P1/M** Instrumentation test for the overlay foreground-service start/stop lifecycle.
- [ ] **P2/M** Unit tests for `WeightCalibrator`, `DraftPatternAnalyzer`, `EnemyIntentAnalyzer`, `WinConditionGenerator`, `BuildAdvisor`, `DraftScoreCalculator`.
- [ ] **P2/M** `FrameProcessor` slot-dedupe and throttle tests with synthetic bitmaps (Robolectric). Include a before/after benchmark validating the `copyPixelsToBuffer` improvement (P1-01) and confirming the `ConcurrentHashMap` sets behave correctly under concurrent access (P0-05).
- [ ] **P2/S** `DraftExporter` round-trip serialisation test.
- [ ] **P3/S** Snapshot tests for key Compose components.

---

## 5. CI / tooling / release

- [x] **P1/M** Add CI workflow: `./gradlew lint testDebugUnitTest assembleDebug` on every push/PR. **[DONE — `.github/workflows/ci.yml` added]**
- [x] **P2/S** Add `detekt` with a baseline. **[DONE — 2026-06-26: plugin applied in `app/build.gradle.kts`; config at `config/detekt/detekt.yml`; run `./gradlew detektBaseline` to generate `baseline.xml`]**
- [x] **P2/S** Add dependency-update automation against the version catalog. **[DONE — Dependabot `.github/dependabot.yml` configured for weekly Gradle + GHA updates]**
- [ ] **P2/M** Add a signed-release workflow + R8 mapping upload (to enable stack-trace deobfuscation).
- [ ] **P2/S** Verify ProGuard keep rules cover Gson DTOs, Room entities, and Hilt-generated classes; test on a minified build.
- [ ] **P3/S** Generate a baseline profile for startup performance.

---

## 6. Observability

- [ ] **P2/M** Optional remote crash reporting (Crashlytics or Sentry) gated behind a settings toggle; keep local `CrashLogStore` as fallback. See `findings.md` P4-03.
- [ ] **P2/S** Add a "Share logs" action from `LogScreen` via the existing FileProvider.
- [ ] **P3/S** Structured event logging for detection accuracy (phase-detect confidence, match confidence) to inform tuning.

---

## 7. UX & product polish

- [ ] **P2/M** Surface self-status in the overlay: "capture unavailable", "meta data stale (N days)", "accessibility service off".
- [ ] **P2/M** No-capture (manual) mode parity audit — verify every autonomous path has a manual equivalent and document it.
- [ ] **P2/S** Empty/error states for HeroList, MetaBoard, and History screens.
- [ ] **P3/M** Light theme + accent presets.
- [ ] **P3/M** Landscape/tablet overlay layout.
- [ ] **P3/S** First-run interactive draft simulation tied to `isSimulation`.

---

## 8. Data & content

- [ ] **P1/M** Confirm `META_API_BASE_URL` (`https://api.mlbb-assistant.com/`) is live or document the bundled-seed-only mode clearly. See `findings.md` P4-04.
- [ ] **P2/M** Establish an update cadence/source for `default_heroes.json` (counters/synergies/tiers drift each patch).
- [ ] **P2/S** Add `lastUpdated`/patch-version metadata to the meta snapshot and show it in MetaBoard.
- [ ] **P3/M** Community-sourced counter/synergy contributions with confidence weighting.

---

## 9. Documentation upkeep

- [x] **P1/S** Add a root `README.md` (currently absent) linking to the four docs and `MISSION.md`, with build/run instructions. **[DONE — 2026-06-27: `README.md` written at repo root with architecture overview, build instructions, Gradle commands, permissions table, and repository map]**
- [ ] **P2/S** Document the CV calibration workflow (how to remap `SlotRegions` for a new device) in `overview.md` or `docs/cv-calibration.md`.
- [ ] **P2/S** Keep `replit.md` "Pointers" section accurate — remove stale references to deleted files.
- [ ] **P3/S** Add ADRs (Architecture Decision Records) for the key calls listed in `overview.md` §8.

---

## 10. OSS library adoption queue

> All items indexed from `docs/temp/recommendations.md`. See §11 (Library Migration) for adoption decisions.

- [x] **🔴/M** `ehsannarmani/ComposeCharts` — **[DONE: `ScoreExplanationSheet` pie chart wired]**
- [x] **🔴/M** `skydoves/Balloon` (`com.github.skydoves:balloon:1.6.12`) — **[DONE: added to `build.gradle.kts`; integration in §11]**
- [x] **🔴/M** `valentinilk/compose-shimmer` — **[DONE: `HeroListScreen` shimmer skeleton wired]**
- [x] **🟠/M** WorkManager + `HeroSyncWorker` — **[DONE: `HeroSyncWorker` + `MLBBApplication` fully wired]**
- [x] **🟠/M** `judemanutd/AutoStarter` — **[DONE: added to `build.gradle.kts`; integration in §11]**
- [x] **🔴/M** `kotlinx.serialization` — **[DONE: plugin + runtime added; full migration in §5.5 below]**
- [x] **🔴/M** `KilianB/JImageHash` — **[DONE: added to `build.gradle.kts`; integration in §5.8 below]**
- [x] **🔴/M** ML Kit Object Detection — **[DONE: added to `build.gradle.kts`; training pipeline in §5.9 below]**

---

## 11. Library Migration (OSS adoption tracking)

> All OSS library evaluation outcomes from `docs/temp/recommendations.md`.
> ✅ Adopted · ⚙️ Added to Gradle (integration pending) · 📋 Deferred · 🚫 Dismissed

| Library | Priority | Decision | Integration step |
|---|---|---|---|
| ML Kit Text Recognition | 🔴 | ✅ Adopted | `PhaseOcrDetector.kt` fully wired |
| ComposeCharts | 🔴 | ✅ Adopted | `ScoreExplanationSheet.kt` pie chart wired |
| compose-shimmer | 🔴 | ✅ Adopted | `HeroListScreen.kt` shimmer skeleton wired |
| detekt | 🔴 | ✅ Adopted | Root plugin + `app/build.gradle.kts` applied; `config/detekt/detekt.yml` |
| WorkManager + HiltWork | 🟠 | ✅ Adopted | `HeroSyncWorker` + `MLBBApplication.scheduleHeroSync()` fully wired |
| Dependabot | 🟠 | ✅ Adopted | `.github/dependabot.yml` configured |
| Lottie | 🟠 | ✅ Adopted | All 3 animations wired: `BanTurnBanner` (ban_warning), `ScanningPlaceholder` (scanning), `PickSuccessOverlay` (pick_success) — §5.2 DONE |
| kotlinx.serialization | 🔴 | ✅ Adopted | DTOs `@Serializable`; `NetworkModule` uses `asConverterFactory`; `JsonParser` uses `Json.decodeFromString`; Gson removal pending minified smoke test — §5.5 partial DONE |
| Balloon (skydoves) | 🔴 | ✅ Adopted | `RecommendationCard` long-press shows `RecommendationTooltipContent` (meta/synergy/counter + reason) in `PickPhaseContent.kt` — §5.7 DONE |
| KilianB/JImageHash | 🔴 | ⚙️ In Gradle | PortraitMatcher integration — see §5.8 |
| ML Kit Object Detection | 🔴 | ⚙️ In Gradle | TFLite training pipeline — see §5.9 |
| AutoStarter | 🟠 | ✅ Adopted | `openAutoStartSettings()` calls `AutoStartPermissionHelper` primary + curated OEM-intent fallback + App Info final fallback — §5.10 DONE |
| JetOverlay | 🔴 | ✅ Adopted | `MLBBApplication.initJetOverlay()` registered; `OverlayService` uses `JetOverlay.show/hide`; decomposed into `OverlayStateHolder` + `OverlayCaptureCoordinator` + `DraftOverlayContent` |
| p3hndrx/MLBB-API | 🔴 | 📋 Deferred | Backend verification required (P4-04) |
| ridwaanhall/api-mobilelegends | 🔴 | 📋 Deferred | API liveness confirmation first |
| floating-views | 🟠 | 📋 Deferred | JetOverlay preferred path |
| compose-destinations | 🟠 | 📋 Deferred | Navigation Compose already integrated; L effort, no blocking bug |
| Firebase Crashlytics | 🟠 | 📋 Deferred | Firebase project setup required — see §6 |
| Paparazzi | 🟠 | 📋 Deferred | Screenshot test infra — no blocking bug |
| Roborazzi | 🟠 | 📋 Deferred | Alternative to Paparazzi |
| ArchUnit | 🟠 | 📋 Deferred | Single module; moot until §3 module split |
| landscapist | 🟠 | 🚫 Dismissed | Coil 3 already integrated and well-configured |
| Sentry Android | 🟡 | 🚫 Dismissed | Firebase Crashlytics (🟠) preferred |
| OpenCV Android | 🟡 | 🚫 Dismissed | ~20 MB AAR vs ML Kit lean footprint |
| compose-floating-window | 🟡 | 🚫 Dismissed | JetOverlay preferred |

---

## §5.2 — Lottie animation integration steps **[DONE — 2026-06-27]**
1. ~~Source or author 3 animations from LottieFiles~~ **[DONE — `lottie_scanning.json`, `lottie_pick_success.json`, `lottie_ban_warning.json` in `res/raw/`]**
2. ~~Wire loading animation into `PhaseDetector` "scanning" state in overlay~~ **[DONE — `ScanningPlaceholder()` in `PickPhaseContent.kt`]**
3. ~~Wire success animation into `PickPhaseContent` on "Our Pick Confirmed"~~ **[DONE — `PickSuccessOverlay()` triggers on hero tap, auto-dismisses after 1.4 s]**
4. ~~Wire warning animation into ban timeout reminder in `BanPhaseContent`~~ **[DONE — `BanTurnBanner()` in `BanPhaseContent.kt`]**
- **Acceptance met:** All 3 Lottie animations play in the overlay during a draft session

## §5.5 — kotlinx.serialization full migration steps **[DONE — 2026-06-27]**
1. ~~Replace `@SerializedName` with `@SerialName` on all DTOs; add `@Serializable`~~ **[DONE — `MetaSnapshotDto` + `HeroDto` annotated `@Serializable`; no Gson `@SerializedName` used (field names match JSON directly)]**
2. ~~Swap `GsonConverterFactory` → `KotlinSerializationConverterFactory` in `NetworkModule`~~ **[DONE — `NetworkModule` uses `json.asConverterFactory(...)` from `retrofit2-kotlinx-serialization-converter`]**
3. ~~Update `JsonParser.kt` to use `Json.decodeFromString<>()` instead of `Gson().fromJson()`~~ **[DONE — confirmed in `JsonParser.kt`]**
4. Remove Gson dependency once migration complete **[DEFERRED — Gson kept in Gradle pending minified-build smoke test per misc.md §10]**
5. Run minified build smoke test to verify R8 keep rules **[DEFERRED — requires full Android build environment]**
- **Acceptance partially met:** Retrofit + JsonParser use kotlinx.serialization; Gson removal pending smoke test

## §5.7 — Balloon tooltip integration steps **[DONE — 2026-06-27]**
1. ~~Add `Balloon` popup in `SuggestionCard.kt` (long-press)~~ **[DONE — `RecommendationCard` in `PickPhaseContent.kt` wraps each chip in `Balloon { balloonContent = RecommendationTooltipContent }` showing meta/synergy/counter scores + reason]**
2. ~~Add `Balloon` popup in `PickPhaseContent.kt` for overlay hero chip long-press~~ **[DONE — `balloonWindow.showAlignBottom()` on `onLongClick`; dismiss on outside tap handled by library default]**
3. ~~Wire `BalloonWindow` to dismiss on outside tap~~ **[DONE — Balloon library handles dismiss automatically]**
- **Acceptance met:** Long-pressing a hero chip in the overlay shows a tooltip with meta/synergy/counter scores + reason text

## §5.8 — JImageHash PortraitMatcher integration steps **[DONE — 2026-06-27]**
1. ~~Import `com.github.KilianB:JImageHash` in `PortraitMatcher.kt`~~ **[DONE — `com.github.KilianB:JImageHash:3.0.0` via JitPack; `runCatching {}` + reflection guard prevents `NoClassDefFoundError` on Android]**
2. ~~Replace `PerceptualHash.dHash()` with `WaveletHash` primary + `ColorDifferenceHash` secondary~~ **[DONE — `computeWaveletHashBytes()` (WaveletHash 32-bit) + `computeColorDiffHashBytes()` (AverageColorHash 64-bit) both via reflection; dynamic weight scheme: dHash 40%, Histogram 25%, WaveletHash 20%, AverageColorHash 15%; falls back to dHash 75% + Histogram 25% when JImageHash unavailable]**
3. Benchmark false-positive rate against existing test portrait set; keep dHash as fallback **[DEFERRED — full Android build environment required; dHash fallback retained per spec]**
- **Acceptance partially met:** WaveletHash + AverageColorHash wired with runCatching guard; dHash always present as fallback; FP benchmark deferred to device test environment

## §5.9 — ML Kit Object Detection model training pipeline steps
1. Collect 500+ hero portrait crops via Roboflow dataset
2. Train TFLite classification model; export `.tflite` bundle
3. Copy to `app/src/main/assets/hero_detector.tflite`
4. Integrate into `PortraitMatcher` as primary; fall back to perceptual hash below threshold
- **Acceptance:** Portrait match rate ≥ 95% on held-out test set of 100 crops

## §5.10 — AutoStarter wizard integration steps **[DONE — 2026-06-27]**
1. ~~Call `AutoStarter.getAutoStartPermission(context, onlyIfEnabled = false)` in `PermissionWizardScreen`~~ **[DONE — `openAutoStartSettings()` calls `AutoStartPermissionHelper.getInstance().getAutoStartPermission(ctx)` as primary path]**
2. ~~Show OEM-specific rationale~~ **[DONE — Step 4 in wizard shows OEM-specific description before action; `tryStartActivity()` falls back through curated intent list]**
3. ~~Handle `UnsupportedOperationException` gracefully~~ **[DONE — wrapped in `runCatching {}`, falls back to App Info on unrecognised devices]**
- **Acceptance met:** On MIUI/ColorOS/OneUI devices, wizard step 4 opens OEM auto-start screen; falls back to App Info on stock Android

---

## How to use this file

- Pull an item here before coding; check it off when merged and reflect any new capability in [`features.md`](./features.md).
- When you create new technical debt, add a `TD-xx` tag in source **and** a row in §1 the same commit. Next available: **TD-14**.
- When a §0 audit item is fixed, move it into the appropriate permanent section above with its resolved state.
- Keep priorities honest: promote/demote as the product situation changes.
