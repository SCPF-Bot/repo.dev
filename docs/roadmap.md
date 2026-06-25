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
- Version catalog (`libs.versions.toml`) as dependency source of truth
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
- OCR-assisted ban round 1/2 disambiguation (`PhaseOcrDetector`)
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

### TD-xx remediation pass
- TD-01: `hasCCUlt` field replaces hardcoded CC name list
- TD-02: Personal hero-pool proficiency multiplier + 6-item builds
- TD-03: CV thresholds centralised in `PhaseDetectionConfig`
- TD-04: Normalised luminance slot-fill threshold
- TD-05: Dataset-derived dynamic scoring bounds
- TD-06: Explicit `Dispatchers.IO` in repository suspend fns
- TD-07: SavedStateHandle-backed search/filter in `HeroPoolViewModel`
- TD-08: Parallel lazy portrait-hash preload + hybrid match
- TD-10: Paging 3 hero grid
- TD-11: Mutex-guarded crash-log writes
- TD-12: Bubble position persistence

### P0/P1 crash-safety & performance remediation pass (2026-06-23)
- **A1 [COMPLETED]** — P0-01: `imageReader!!.surface` → local `val reader` in `ScreenCaptureManager` — eliminates TOCTOU NPE on the capture pipeline
- **A1 [COMPLETED]** — P0-02: `state.session!!` → `?: return@Scaffold` in `DraftReplayScreen` — eliminates smart-cast crash on mutable StateFlow property
- **A1 [COMPLETED]** — P0-03: `result.data!!` → `?: return@registerForActivityResult` in `MainActivity` — eliminates crash on nullable activity-result intent
- **A2 [COMPLETED]** — P1-01: `Bitmap.getPixel()` loops → `copyPixelsToBuffer` + byte-array iteration in `FrameProcessor` — 5–20× CV hot-path speedup
- **A3 [COMPLETED]** — P1-02: `RetryInterceptor` (Thread.sleep) removed from `NetworkModule`; coroutine `delay`-based retry added to `HeroRepositoryImpl.syncHeroes()` — eliminates OkHttp thread-pool starvation under concurrent requests

### P2 maintainability pass (2026-06-23)
- **[COMPLETED]** P2-01: Magic float thresholds extracted into `BuildThresholds` (BuildAdvisor) and `CompThresholds` (CompositionAnalyzer)
- **[COMPLETED]** P2-02: Dead constant `AppConstants.OVERLAY_NOTIFICATION_CHANNEL_ID` deleted
- **[COMPLETED]** P2-03: `DraftScorer.computeScore` annotated `@VisibleForTesting` with KDoc warning against production use

### CI (2026-06-23)
- **[COMPLETED]** P3-02 (partial): `.github/workflows/ci.yml` added — runs `./gradlew lint testDebugUnitTest assembleDebug` on every push/PR

### Thread-safety & Compose-stability pass (2026-06-26)
- **[COMPLETED]** P0-04: `OverlayService` slot-tracking sets (`filledEnemyBanSlots`, `filledOurBanSlots`, `filledEnemyPickSlots`, `filledOurPickSlots`) migrated from `mutableSetOf` to `ConcurrentHashMap.newKeySet()` — eliminates a live data race between the IO/Default capture loop and session-reset paths
- **[COMPLETED]** P1-04: `@Immutable` added to all remaining Compose UI-state classes (`HomeUiState`, `InsightsState`, `HeroPoolState`, `HeroPoolEntry`, `LogScreenState`)

---

## [ACTIVE]

### A4 — Maintainability: ban value vs. ban urgency separation
**Effort:** M · **Blocked by:** nothing

`BanRecommender` currently combines threat urgency and meta ban-value into a single
recommender. Separate into `BanValueScorer` (is this hero worth banning on meta merit?)
and `BanUrgencyScorer` (is it urgent based on enemy comp so far?).

Files: `domain/advisor/BanRecommender.kt`

---

### A5 — Correctness: commit Room schema JSON files
**Effort:** S · **Blocked by:** nothing

Schema export is configured but it is unconfirmed whether the generated `/schemas/*.json`
files are committed. Committed schema files are required to author safe future migrations.
Run `./gradlew :app:kspDebugKotlin` and commit the output.

Files: `app/schemas/`

---

### A6 — Correctness: personal meta calibration UI
**Effort:** M · **Blocked by:** `WeightCalibrator` (done); needs larger sample set

`WeightCalibrator` exists but has no UI surface and no minimum-sample gate. Add a
"Calibrate weights" action in Settings that runs calibration only when ≥ 20 real
(non-simulation) draft history entries exist, then shows a diff of the old vs proposed weights.

Files: `presentation/settings/`, `domain/engine/WeightCalibrator.kt`

---

## [BACKLOG]

### Autonomous awareness
- [ ] Higher-confidence portrait matching via TFLite hybrid model
- [ ] Auto-recalibration of `SlotRegions` from a one-time guided capture
- [ ] Patch-delta weighting from historical snapshots
- [ ] Draft-phase-aware "power spike timing" advice

### Historical feedback loop
- [ ] Tendency feedback surfaced in-app ("you over-ban X role", "you under-roam")
- [ ] Match timeline replay with frame thumbnails

### Frictionless deployment
- [ ] Deep links into exact system-settings pages per OEM (Xiaomi / OPPO / Vivo / Huawei / Samsung / OnePlus)
- [ ] Accessibility-service health watchdog with re-setup at the revoked step
- [ ] One-tap overlay relaunch from the notification after an OS kill (partial — notification action exists; watchdog pending)

### Platform resilience
- [ ] Verified no-capture mode at full feature parity with autonomous mode
- [ ] OEM-specific auto-start workaround matrix maintained per release
- [ ] Honest self-status in the overlay: "capture unavailable", "meta data N days old", "accessibility service off"

### Architecture & code quality
- [x] **P0/M** `OverlayService` shared mutable sets (`filledEnemyBanSlots` etc.) — replaced with `ConcurrentHashMap.newKeySet()` (P0-04). **[COMPLETED 2026-06-26 — confirmed live data race; capture loop runs on IO/Default, not Main.]**
- [ ] **P1/L** Decompose `OverlayService.kt` (~1,100 LOC) into window-manager, capture-loop coordinator, and Compose UI host. Keep the service as a thin lifecycle shell. **[DEFERRED 2026-06-26 — see `misc.md` §1; not safely verifiable without an Android build/CI run.]**
- [x] **P1/M** Audit all ViewModel UI state classes for `@Immutable` annotation (P1-04). **[COMPLETED 2026-06-26 — `HomeUiState`, `InsightsState`, `HeroPoolState`, `HeroPoolEntry`, `LogScreenState` annotated.]**
- [ ] **P2/M** Extract overlay state into dedicated `OverlayStateHolder` (narrow recomposition scope)
- [ ] **P2/M** Introduce `:domain` and `:data` Gradle modules to enforce dependency rule at compile time. **Blocked by:** single-module structure; requires significant build rework.
- [ ] **P3/M** Migrate Gson → `kotlinx.serialization`. **Blocked by:** requires updating all DTOs and switching `GsonConverterFactory`. See `findings.md` P3-01 for library choice.
- [ ] **P2/S** Consolidate `DraftScorer.computeScore` → unified `simplified = true` parameter on `score()` (current `@VisibleForTesting` annotation is the interim fix)

### Testing & CI
- [ ] **P1/M** Room migration test (1→2→3) using `MigrationTestHelper`
- [ ] **P1/M** Compose UI tests for Draft, HeroList, Settings, Permission Wizard
- [ ] **P1/M** Instrumentation test for overlay foreground-service start/stop lifecycle
- [ ] **P2/M** Unit tests for `WeightCalibrator`, `DraftPatternAnalyzer`, `EnemyIntentAnalyzer`, `WinConditionGenerator`, `BuildAdvisor`, `DraftScoreCalculator`
- [ ] **P2/M** `FrameProcessor` slot-dedupe and throttle tests with synthetic bitmaps (Robolectric)
- [ ] **P2/S** `DraftExporter` round-trip serialisation test
- [ ] **P2/S** Add `detekt` with a baseline (catches magic literals, function-length violations)
- [ ] **P3/S** Baseline profile for startup performance

### Observability
- [ ] **P2/M** Remote crash reporting (Firebase Crashlytics or Sentry) gated behind settings toggle
- [ ] **P2/S** "Share logs" action from `LogScreen` via existing FileProvider
- [ ] **P3/S** Structured detection-accuracy event logging (phase-detect confidence, match confidence)

### Data & content
- [ ] **P1/M** Confirm `META_API_BASE_URL` is live or document bundled-seed-only mode with staleness indicator
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
