# Roadmap — MLBB Draft Assistant

> **Status:** Living roadmap. Pair with [`features.md`](./features.md) (what exists),
> [`todo.md`](./todo.md) (granular backlog), and [`overview.md`](./overview.md) (architecture).
>
> **Conventions:** `[COMPLETED]` shipped and stable · `[ACTIVE]` in progress · `[BACKLOG]` planned.
> For `[ACTIVE]` items: **Effort** S/M/L · **Blocked By** lists hard technical prerequisites.

---

## Release history

| Version | Code | Theme |
|---|---|---|
| 2.0.0 | 2 | Clean-architecture rewrite: overlay-first product, CV pipeline, scoring engine, TD-xx remediation. **Current.** |
| 1.x | 1 | Initial release (superseded). |

---

## [COMPLETED]

### Foundation
- Clean Architecture + MVI/UDF layering (`domain` is Android-free)
- Hilt DI with single-source modules (`AppModule`, `DatabaseModule`, `NetworkModule`, `RepositoryModule`, `OverlayModule`)
- Room v3 with exported schema + migrations 1→2→3
- Single DataStore delegate (no duplicate-delegate crash)
- Retrofit + OkHttp meta sync with local JSON seed fallback
- Version catalog (`libs.versions.toml`) as dependency source of truth — deduplicated (P0-06)
- Timber + file-backed `CrashLogStore` (mutex-guarded writes, TD-11)
- Localization scaffold (EN/FIL/ID/MS/TH/VI)
- Unit-test suite for domain (scoring, engine, advisor, hashing)

### Autonomous awareness
- MediaProjection screen capture + foreground service (Android 14+ compliant)
- Phase detection from banner colours (`PhaseDetector`)
- Perceptual-hash portrait matching (`PortraitMatcher` + `PerceptualHash`)
- Hybrid dHash + histogram matching (TD-08)
- Normalised luminance slot-fill detection (TD-04)
- Config-driven CV thresholds (TD-03, `PhaseDetectionConfig`)
- Rank detection (`RankDetector`) and first-pick detection (`FirstPickDetector`)
- **OCR-assisted ban round 1/2 disambiguation via ML Kit Text Recognition** — `PhaseOcrDetector` fully wired; no reflection, direct imports, lazy recognizer init
- Per-aspect-ratio slot-region calibration (functional; needs broader device validation)

### Deeper intelligence
- Multi-factor scoring with adaptive weights
- Dataset-derived dynamic bounds (TD-05)
- Patch-velocity multiplier
- Composition archetype recognition
- Enemy intent inference (`EnemyIntentAnalyzer`)
- Win-condition generation (`WinConditionGenerator`)
- Personal hero-pool proficiency weighting (TD-02)
- Build/item advice with situational items
- Absolute/reactive ban split (`BanRecommender.rankSplit`) — separates must-ban from context-sensitive bans

### Historical feedback loop
- Draft history persistence (`draft_sessions`)
- Match-outcome recording + simulation exclusion
- Draft replay viewer (`DraftReplayScreen`)
- Weight self-calibration (`WeightCalibrator`)
- Draft pattern analysis (`DraftPatternAnalyzer`)

### Frictionless deployment
- Guided permission wizard (ordered least→most intrusive)
- Wizard progress persistence
- One-tap overlay start from app
- Bubble position persistence across sessions (TD-12)
- Session phase/rank/first-pick serialised to DataStore (survive OS kills)
- "Relaunch Overlay" notification action

### Background sync
- **WorkManager TD-13**: `HeroSyncWorker` runs every 24 h on network; `HiltWorkerFactory` wired through `MLBBApplication`; `ExistingPeriodicWorkPolicy.KEEP` prevents duplicate scheduling

### TD-xx remediation pass
- TD-01: `hasCCUlt` field replaces hardcoded CC name list
- TD-02: Personal hero-pool proficiency multiplier + 6-item builds
- TD-03: CV thresholds centralised in `PhaseDetectionConfig`
- TD-04: Normalised luminance slot-fill threshold
- TD-05: Dataset-derived dynamic scoring bounds
- TD-06: Explicit `Dispatchers.IO` in repository suspend fns
- TD-07: SavedStateHandle-backed search/filter in `HeroPoolViewModel`
- TD-08: Parallel lazy portrait-hash preload + hybrid match
- TD-09: *(gap — permanently unassigned; see `findings.md` P2-04)*
- TD-10: Paging 3 hero grid
- TD-11: Mutex-guarded crash-log writes
- TD-12: Bubble position persistence
- TD-13: WorkManager periodic hero-data sync (`HeroSyncWorker`)

### P0/P1 crash-safety & performance remediation pass (2026-06-23)
- **A1 [COMPLETED]** — P0-01: `imageReader!!.surface` → local `val reader` in `ScreenCaptureManager`
- **A1 [COMPLETED]** — P0-02: `state.session!!` → `?: return@Scaffold` in `DraftReplayScreen`
- **A1 [COMPLETED]** — P0-03: `result.data!!` → `?: return@registerForActivityResult` in `MainActivity`
- **A2 [COMPLETED]** — P1-01: `Bitmap.getPixel()` loops → `copyPixelsToBuffer` + byte-array iteration in `FrameProcessor`
- **A3 [COMPLETED]** — P1-02: `RetryInterceptor` (Thread.sleep) removed; coroutine `delay`-based retry in `HeroRepositoryImpl`

### P2 maintainability pass (2026-06-23)
- **[COMPLETED]** P2-01: Magic float thresholds extracted into `BuildThresholds` and `CompThresholds`
- **[COMPLETED]** P2-02: Dead constant `AppConstants.OVERLAY_NOTIFICATION_CHANNEL_ID` deleted
- **[COMPLETED]** P2-03: `DraftScorer.computeScore` annotated `@VisibleForTesting`

### CI (2026-06-23)
- **[COMPLETED]** P3-02 (partial): `.github/workflows/ci.yml` added
- **[COMPLETED]** Dependabot: `.github/dependabot.yml` added with weekly Gradle + GHA update schedule

### Thread-safety & Compose-stability pass (2026-06-26)
- **[COMPLETED]** P0-04: `OverlayService` slot sets → `ConcurrentHashMap.newKeySet()`
- **[COMPLETED]** P1-04: `@Immutable` added to all remaining Compose UI-state classes

### Thread-safety continuation & documentation pass (2026-06-26)
- **[COMPLETED]** P0-05: `FrameProcessor` internal slot sets → `ConcurrentHashMap.newKeySet()`
- **[COMPLETED]** P2-04: TD-09 numbering gap documented and formally resolved

### Build hygiene & static analysis pass (2026-06-26, fifth pass)
- **[COMPLETED]** P0-06: Duplicate keys in `libs.versions.toml` deduped; last-wins values preserved; new library entries added
- **[COMPLETED]** P2-07: `DraftSessionManager.undo()` TOCTOU confirmed resolved in source (reads stack from inside `_session.update` lambda)
- **[COMPLETED]** P3-03: `detekt` plugin added to root `build.gradle.kts`; `config/detekt/detekt.yml` fully configured

### OSS library adoption — first wave (2026-06-26, fifth pass)
- **[COMPLETED]** ComposeCharts — `ScoreExplanationSheet` pie chart confirmed in source
- **[COMPLETED]** compose-shimmer — `HeroListScreen` shimmer skeleton confirmed in source
- **[COMPLETED]** ML Kit Text Recognition — `PhaseOcrDetector` confirmed in source
- **[COMPLETED]** WorkManager + Hilt Work — confirmed in `build.gradle.kts` + `MLBBApplication`
- **[COMPLETED]** Lottie — confirmed in `build.gradle.kts` (animation assets pending)
- **[COMPLETED]** Balloon (skydoves) — added to `libs.versions.toml` + `build.gradle.kts`
- **[COMPLETED]** kotlinx.serialization — plugin + runtime added to Gradle; full migration deferred
- **[COMPLETED]** KilianB/JImageHash — added to `libs.versions.toml` + `build.gradle.kts`
- **[COMPLETED]** ML Kit Object Detection (custom) — added to `libs.versions.toml` + `build.gradle.kts`
- **[COMPLETED]** AutoStarter (judemanutd) — added to `libs.versions.toml` + `build.gradle.kts`

---

## [RECOMMENDATION ADOPTION]

> All 🔴 Critical entries from `docs/temp/recommendations.md` tracked here as active work items.

### RA-01 — JetOverlay integration (🔴 Critical)
**Effort:** L · **Blocked by:** Android build environment for runtime verification
**Status:** Deferred. See `misc.md` §6. Will be tracked here until OverlayService decomposition (P1-03) begins.
Files: `presentation/overlay/OverlayService.kt`

### RA-02 — p3hndrx/MLBB-API hero/item data (🔴 Critical)
**Effort:** M · **Blocked by:** backend API stability verification
**Status:** Deferred. Augments `default_heroes.json` with base-stat data for `BuildAdvisor`.
Files: `data/remote/api/`, `res/raw/default_heroes.json`

### RA-03 — ridwaanhall/api-mobilelegends (🔴 Critical)
**Effort:** M · **Blocked by:** API liveness confirmation, `META_API_BASE_URL` resolution (P4-04)
**Status:** Deferred. Best candidate to replace/supplement `MetaApi.GET /v1/meta/snapshot`.
Files: `data/remote/api/MetaApi.kt`, `data/remote/dto/MetaSnapshotDto.kt`

### RA-04 — KilianB/JImageHash multi-algorithm hashing (🔴 Critical)
**Effort:** M · **Blocked by:** nothing (dependency added)
**Status:** Added to Gradle. Integration into `PortraitMatcher.kt` pending — replace custom dHash with `WaveletHash` + `ColorDifferenceHash` for false-positive reduction.
Files: `capture/PortraitMatcher.kt`, `capture/PerceptualHash.kt`

### RA-05 — ML Kit Object Detection + TFLite hero detector (🔴 Critical)
**Effort:** L · **Blocked by:** training dataset (Roboflow), model export
**Status:** Dependency added to Gradle. Model training + integration into `PortraitMatcher` is a multi-step L-effort pipeline tracked in BACKLOG.
Files: `capture/PortraitMatcher.kt`, `app/src/main/assets/`

### RA-06 — Balloon tooltip for hero suggestions (🔴 Critical)
**Effort:** M · **Blocked by:** nothing (dependency added)
**Status:** Added to Gradle. Integration in `PickPhaseContent.kt` and `SuggestionCard.kt` pending.
Files: `presentation/draft/components/SuggestionCard.kt`, `presentation/overlay/PickPhaseContent.kt`

### RA-07 — kotlinx.serialization full migration (🔴 Critical)
**Effort:** M · **Blocked by:** minified build smoke test
**Status:** Plugin + runtime added to Gradle. Full DTO migration (Gson → kotlinx.serialization, `GsonConverterFactory` → `KotlinSerializationConverterFactory`) tracked in backlog. See `misc.md` §10.
Files: `di/NetworkModule.kt`, `data/remote/dto/MetaSnapshotDto.kt`, `utils/JsonParser.kt`

---

## [ACTIVE]

### A4 — Maintainability: ban value vs. ban urgency separation (structural refinement)
**Effort:** M · **Blocked by:** nothing
**Status:** Partially addressed — `BanRecommender.rankSplit()` already separates absolute from reactive bans. Full separation into dedicated `BanValueScorer` / `BanUrgencyScorer` classes remains open.
Files: `domain/advisor/BanRecommender.kt`

---

### A5 — Correctness: commit Room schema JSON files
**Effort:** S · **Blocked by:** nothing
Schema export is configured but it is unconfirmed whether the generated `/schemas/*.json` files are committed. Run `./gradlew :app:kspDebugKotlin` and commit the output.
Files: `app/schemas/`

---

### A6 — Correctness: personal meta calibration UI
**Effort:** M · **Blocked by:** `WeightCalibrator` (done); needs larger sample set
`WeightCalibrator` exists but has no UI surface and no minimum-sample gate.
Files: `presentation/settings/`, `domain/engine/WeightCalibrator.kt`

---

### A7 — Correctness: DraftSessionManager.undo() atomicity — **[COMPLETED 2026-06-26]**
Fixed in source — `undo()` reads from inside `_session.update` lambda. Marked complete.

---

## [BACKLOG]

### Autonomous awareness
- [ ] Higher-confidence portrait matching via TFLite hybrid model (RA-05 pipeline)
- [ ] JImageHash `WaveletHash` + `ColorDifferenceHash` integration into `PortraitMatcher` (RA-04)
- [ ] Auto-recalibration of `SlotRegions` from a one-time guided capture
- [ ] Patch-delta weighting from historical snapshots
- [ ] Draft-phase-aware "power spike timing" advice

### Historical feedback loop
- [ ] Tendency feedback surfaced in-app ("you over-ban X role", "you under-roam")
- [ ] Match timeline replay with frame thumbnails

### Frictionless deployment
- [ ] Deep links into exact system-settings pages per OEM — `AutoStarter` is added to Gradle (see RA roadmap)
- [ ] Accessibility-service health watchdog with re-setup at the revoked step
- [ ] One-tap overlay relaunch from the notification after an OS kill (partial — notification action exists; watchdog pending)
- [ ] Balloon tooltip on hero suggestion long-press in overlay — `skydoves/Balloon` added to Gradle (RA-06)

### Platform resilience
- [ ] Verified no-capture mode at full feature parity with autonomous mode
- [ ] OEM-specific auto-start workaround matrix maintained per release
- [ ] Honest self-status in the overlay: "capture unavailable", "meta data N days old", "accessibility service off"

### Architecture & code quality
- [x] **P0/M** `OverlayService` shared mutable sets → `ConcurrentHashMap.newKeySet()` (P0-04). **[COMPLETED]**
- [x] **P0/M** `FrameProcessor` internal mutable sets → `ConcurrentHashMap.newKeySet()` (P0-05). **[COMPLETED]**
- [x] **P0/M** `libs.versions.toml` duplicate key deduplication (P0-06). **[COMPLETED]**
- [ ] **P1/L** Decompose `OverlayService.kt` (~1,100 LOC) into window-manager, capture-loop coordinator, and Compose UI host. **[DEFERRED — see `misc.md` §6]**
- [x] **P1/M** Audit all ViewModel UI state classes for `@Immutable` annotation (P1-04). **[COMPLETED]**
- [ ] **P2/M** Extract overlay state into dedicated `OverlayStateHolder` (narrow recomposition scope)
- [ ] **P2/M** Introduce `:domain` and `:data` Gradle modules to enforce dependency rule at compile time.
- [ ] **P3/M** Full Gson → `kotlinx.serialization` migration (RA-07). Plugin + runtime added (P3-01 partial).
- [ ] **P2/S** Consolidate `DraftScorer.computeScore` → unified `simplified = true` parameter

### Testing & CI
- [ ] **P1/M** Room migration test (1→2→3) using `MigrationTestHelper`
- [ ] **P1/M** Compose UI tests for Draft, HeroList, Settings, Permission Wizard
- [ ] **P1/M** Instrumentation test for overlay foreground-service start/stop lifecycle
- [ ] **P2/M** Unit tests for `WeightCalibrator`, `DraftPatternAnalyzer`, `EnemyIntentAnalyzer`, `WinConditionGenerator`, `BuildAdvisor`, `DraftScoreCalculator`
- [ ] **P2/M** `FrameProcessor` slot-dedupe and throttle tests with synthetic bitmaps (Robolectric)
- [ ] **P2/S** `DraftExporter` round-trip serialisation test
- [ ] **P2/S** Add `detekt` baseline (run `./gradlew detektBaseline` to generate)
- [ ] **P3/S** Baseline profile for startup performance

### Observability
- [ ] **P2/M** Remote crash reporting (Firebase Crashlytics) gated behind settings toggle
- [ ] **P2/S** "Share logs" action from `LogScreen` via existing FileProvider
- [ ] **P3/S** Structured detection-accuracy event logging

### Data & content
- [ ] **P1/M** Confirm `META_API_BASE_URL` is live or document bundled-seed-only mode with staleness indicator (P4-04)
- [ ] **P2/M** `lastUpdated`/patch-version metadata in meta snapshot + displayed in MetaBoard
- [ ] **P2/M** Update cadence/source for `default_heroes.json` (counters/synergies/tiers drift each patch)
- [ ] **P3/M** Community-sourced counter/synergy data with confidence weighting

### UX & product polish
- [ ] **P2/M** Empty/error states for HeroList, MetaBoard, and History
- [ ] **P2/M** No-capture (manual) mode parity audit
- [ ] **P3/M** Light theme + accent presets
- [ ] **P3/M** Landscape/tablet overlay layout
- [ ] **P3/S** First-run interactive draft simulation tied to `isSimulation`
- [ ] **P3/M** Shareable post-draft report cards
- [ ] **P3/M** Cloud sync of hero pool + settings across devices
- [ ] **P3/S** In-overlay quick notes / callouts for team comms
- [ ] **P3/S** Tutorial / interactive first-run draft simulation

### Documentation
- [ ] **P1/S** Root `README.md` with build/run instructions and links to `/docs`
- [ ] **P2/S** CV calibration guide: how to remap `SlotRegions` for a new device
- [ ] **P3/S** Architecture Decision Records (ADRs) for the big calls in `overview.md` §8

---

## How to use this file

1. When a planned item ships, move it into `[COMPLETED]` **and** add a row to [`features.md`](./features.md).
2. Break any `[BACKLOG]` item into concrete steps in [`todo.md`](./todo.md) before starting work.
3. Move items to `[ACTIVE]` only when actively being worked, with an assignee and estimated completion.
4. Keep effort and blocker fields accurate — stale estimates lead to bad sprint planning.
