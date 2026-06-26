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
*Items resolved in the 2026-06-26 third/fourth pass are marked `[DONE — date]`.*
*Unresolved items remain as open action items.*

- [x] **P0/S** Fix `imageReader!!.surface` in `ScreenCaptureManager.kt` — capture `imageReader` into a local `val` before use to eliminate TOCTOU NPE on mutable nullable field. **[DONE — local `val reader` introduced in `startCapture()`]**
- [x] **P0/S** Fix `state.session!!` in `DraftReplayScreen.kt` — replace with `val s = state.session ?: return@Scaffold`. **[DONE]**
- [x] **P0/S** Fix `result.data!!` in `MainActivity.kt` — use `val data = result.data ?: return@registerForActivityResult`. **[DONE]**
- [x] **P0/M** Document and enforce thread-safety for `filledEnemyBanSlots` / `filledOurBanSlots` etc. in `OverlayService`. **[DONE — 2026-06-26: confirmed live data race; all four sets replaced with `ConcurrentHashMap.newKeySet<Int>()`. P0-04.]**
- [x] **P0/M** Fix `filledEnemyBans` / `filledOurBans` / `filledEnemyPicks` / `filledOurPicks` in `FrameProcessor.kt` — plain `mutableSetOf` raced between `processFrame` (Dispatchers.Default) and `resetSlotTracking()` (called from OverlayService). **[DONE — 2026-06-26: all four replaced with `ConcurrentHashMap.newKeySet<Int>()`. P0-05.]**
- [x] **P1/S** Replace `Bitmap.getPixel()` nested loops in `FrameProcessor.sampleLuminanceBaseline()` and `isSlotFilled()` with `Bitmap.copyPixelsToBuffer(ByteBuffer)` + array iteration. **[DONE — 5–20× CV hot-path speedup confirmed by implementation]**
- [x] **P1/M** Move retry logic out of `RetryInterceptor` (which used `Thread.sleep`) into `HeroRepositoryImpl.syncHeroes()` using coroutine `delay()`. **[DONE — `RetryInterceptor` removed; `syncWithRetry` coroutine pattern added with `MAX_SYNC_RETRIES=3` and exponential back-off]**
- [x] **P1/M** Audit all ViewModel UI state classes for `@Immutable` annotation. **[DONE — 2026-06-26: `@Immutable` added to `HomeUiState`, `InsightsState` (home), `HeroPoolState` + `HeroPoolEntry` (heropool), and `LogScreenState` (log). `DraftHistoryViewModel` exposes a bare `StateFlow<List<DraftHistoryItem>>` (no UI-state class to annotate); `MetaBoardScreen` has no `data class` UI state. `DraftState`/`HeroListState`/`SettingsState` were already annotated. P1-04.]**
- [x] **P2/S** Delete dead constant `AppConstants.OVERLAY_NOTIFICATION_CHANNEL_ID = "draft_overlay_channel"`. **[DONE]**
- [x] **P2/S** Extract magic float thresholds in `BuildAdvisor.kt` and `CompositionAnalyzer.kt` into named constants. **[DONE — `BuildThresholds` and `CompThresholds` private objects added]**
- [x] **P2/S** Document TD-09 numbering gap. **[DONE — 2026-06-26: gap is a permanent unassigned reservation; future items start at TD-13. Documented in `findings.md` P2-04 and `overview.md` §8.]**

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

**Next new debt item: TD-13.**

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
- [ ] **P1/L** Decompose `OverlayService.kt` (~1,100 LOC) into: (a) `OverlayWindowManager` (window add/remove/drag), (b) `OverlayCaptureCoordinator` (capture loop + frame routing), (c) Compose UI host. Keep the `Service` class as a thin lifecycle shell. **[DEFERRED — 2026-06-26: intentionally not executed this pass; see `misc.md` §6 for rationale.]**
- [x] **P1/M** Audit all ViewModel UI state classes for `@Immutable` (see §0 above). **[DONE — 2026-06-26, P1-04.]**
- [ ] **P2/S** Fix `DraftSessionManager.undo()` snapshot read — move `last` read inside `_session.update` lambda to eliminate TOCTOU window (P2-07). Low risk today (Main-only callers) but fragile.
- [ ] **P2/M** Extract overlay state into a dedicated `OverlayStateHolder` / ViewModel-like object observed by all phase composables; narrows recomposition scope.
- [ ] **P2/S** Audit that no ViewModel touches a DAO directly (enforce `SaveDraftSessionUseCase` as the only write path) — add a lint or ArchUnit test.
- [ ] **P2/M** Introduce a `:domain` and `:data` Gradle module split to enforce the dependency rule at compile time.
- [ ] **P2/S** Unify `DraftScorer.computeScore` and `score` into a single entry point with a `simplified = true` parameter. Current `@VisibleForTesting` annotation is the interim fix.
- [ ] **P3/M** Migrate Gson → `kotlinx.serialization` across DTOs + `JsonParser`. See `findings.md` P3-01 for migration steps.

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
- [ ] **P2/S** Add `detekt` with a baseline — will catch magic literals, long-function violations, and complexity issues automatically.
- [ ] **P2/S** Add dependency-update automation against the version catalog (Renovate or Dependabot).
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

- [ ] **P1/S** Add a root `README.md` (currently absent) linking to the four docs and `MISSION.md`, with build/run instructions.
- [ ] **P2/S** Document the CV calibration workflow (how to remap `SlotRegions` for a new device) in `overview.md` or `docs/cv-calibration.md`.
- [ ] **P2/S** Keep `replit.md` "Pointers" section accurate — remove stale references to deleted files.
- [ ] **P3/S** Add ADRs (Architecture Decision Records) for the key calls listed in `overview.md` §8.

---

## 10. OSS library adoption queue

*Sourced from `docs/temp/recommendations.md`. Items ordered by priority rating.*

- [ ] **🔴/M** `ehsannarmani/ComposeCharts` (`io.github.ehsannarmani:compose-charts:0.1.3`) — add radial/pie score breakdown chart to `ScoreExplanationSheet`. Shows meta/synergy/counter proportion visually. Direct service of Core Belief #3.
- [ ] **🔴/M** `skydoves/Balloon` (`com.github.skydoves:balloon:1.6.12`) — add tooltip on hero suggestion long-press in `SuggestionCard` / `PickPhaseContent` overlays. Shows score rationale without navigating away.
- [ ] **🔴/M** `valentinilk/compose-shimmer` (`com.valentinilk.shimmer:shimmer:1.3.0`) — shimmer loading placeholder for `HeroListScreen`, `MetaBoardScreen`, and `DraftHistoryScreen` while data loads.
- [ ] **🟠/M** WorkManager + `HeroSyncWorker` — schedule `SyncHeroesUseCase` as a 24-hour periodic background job using `androidx.work:work-runtime-ktx:2.10.0`. Prevents hero data going stale across patch cycles.
- [ ] **🟠/M** `judemanutd/AutoStarter` (`com.github.judemanutd:autostarter:1.1.0`) — OEM-specific auto-start settings navigation in `PermissionWizardScreen`. Addresses top-5 SEA OEMs (Xiaomi, OPPO, Vivo, Huawei, Samsung).
- [ ] **🔴/M** `kotlinx.serialization` — replace Gson in DTOs + `JsonParser`. Tracked also as P3-01.

---

## How to use this file

- Pull an item here before coding; check it off when merged and reflect any new capability in [`features.md`](./features.md).
- When you create new technical debt, add a `TD-xx` tag in source **and** a row in §1 the same commit. Next available: **TD-13**.
- When a §0 audit item is fixed, move it into the appropriate permanent section above with its resolved state.
- Keep priorities honest: promote/demote as the product situation changes.
