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
| 2.0.0 | 2 | Clean-architecture rewrite: overlay-first product, CV pipeline, scoring engine, crash-safety hardening, UI/UX overhaul. **Current.** |
| 1.x | 1 | Initial release (superseded). |

---

## [COMPLETED]

### Foundation
- Clean Architecture + MVI/UDF layering (`domain` is Android-free)
- Hilt DI with single-source modules (`AppModule`, `DatabaseModule`, `NetworkModule`, `RepositoryModule`, `OverlayModule`)
- Room v3 with exported schema + migrations 1→2→3
- Single DataStore delegate (no duplicate-delegate crash)
- Retrofit + OkHttp meta sync with local JSON seed fallback
- Version catalog (`libs.versions.toml`) as dependency source of truth — deduplicated
- Timber + file-backed `CrashLogStore` (mutex-guarded writes)
- Localization scaffold (EN/FIL/ID/MS/TH/VI)
- Unit-test suite for domain (scoring, engine, advisor, hashing)
- CI workflow (lint + unit tests + debug assemble) + Dependabot weekly updates
- Root `README.md` with build instructions and repo map

### Autonomous awareness
- MediaProjection screen capture + foreground service (Android 14+ compliant)
- Phase detection from banner colours (`PhaseDetector`)
- Perceptual-hash portrait matching (`PortraitMatcher` + `PerceptualHash`)
- Hybrid dHash + histogram matching + 4-algorithm dynamic weight scheme
- Normalised luminance slot-fill detection
- Config-driven CV thresholds (`PhaseDetectionConfig`)
- Rank detection (`RankDetector`) and first-pick detection (`FirstPickDetector`)
- OCR-assisted ban round 1/2 disambiguation — `PhaseOcrDetector` (ML Kit Text Recognition)
- Thread-safe slot deduplication using `ConcurrentHashMap.newKeySet()` in `OverlayService` and `FrameProcessor`

### Deeper intelligence
- Multi-factor scoring with adaptive weights and dataset-derived dynamic bounds
- Patch-velocity multiplier + positional modifiers (first/last pick)
- Composition archetype recognition, enemy intent inference, win-condition generation
- Personal hero-pool proficiency weighting
- Build/item advice with situational items
- Absolute/reactive ban split (`BanRecommender.rankSplit`)
- Ban value vs urgency separation (`BanValueScorer` + `BanUrgencyScorer`)
- Trait-based counter scoring (`TraitCounterEngine` — ported from AlanNobita scoring.py)
- Archetype gap detection: magic-damage gap + frontline-vulnerability warnings (`CompositionAnalyzer`, `HeroArchetypeService`)
- Counter confidence table: `counter_lookup` Room table, `CounterLookupDao`, confidence scores from community dataset

### Historical feedback loop
- Draft history persistence + match-outcome recording + simulation exclusion
- Draft replay viewer (`DraftReplayScreen`)
- Weight self-calibration (`WeightCalibrator`)
- Draft pattern analysis (`DraftPatternAnalyzer`)

### Frictionless deployment
- Guided permission wizard (ordered least→most intrusive) with progress persistence
- One-tap overlay start from app; bubble position persistence; session serialized to DataStore
- "Relaunch Overlay" notification action
- AutoStarter OEM auto-start integration (`PermissionWizardScreen.openAutoStartSettings()`)

### UI/UX overhaul
- JetOverlay integration — `MLBBApplication.initJetOverlay()`; `OverlayService` decomposed into `OverlayStateHolder` + `OverlayCaptureCoordinator` + `DraftOverlayContent` (~250 LOC shell, was ~1,100). See `misc.md` §11.
- Lottie — all 3 animations wired: `BanTurnBanner`, `ScanningPlaceholder`, `PickSuccessOverlay` (auto-dismisses after 1.4 s)
- Balloon — `RecommendationCard` long-press tooltip with meta/synergy/counter scores + reason
- kotlinx.serialization — all JSON entry points migrated (DTOs, `NetworkModule`, `JsonParser`); Gson removal pending smoke test
- ComposeCharts — score explanation pie chart in `ScoreExplanationSheet`
- compose-shimmer — loading skeleton in `HeroListScreen`
- `@Immutable` on all Compose UI state classes

---

## [ACTIVE]

### A4 — Ban value vs. ban urgency separation [COMPLETED 2026-07-01]
`BanValueScorer` (context-free intrinsic value) and `BanUrgencyScorer` (reactive contextual urgency)
implemented as separate objects. `BanRecommender.rankSplit()` now combines both: `finalScore = value + urgency`.
`BanValueScorerTest` covers all three absolute-ban triggers and ordering invariants.
Files: `domain/advisor/BanValueScorer.kt`, `domain/advisor/BanUrgencyScorer.kt`, `BanRecommender.kt`

### A7 — Trait-based counter scoring [COMPLETED 2026-07-01]
`TraitCounterEngine` — Kotlin port of AlanNobita/mlbb_drafter `scoring.py` trait-counter matrix.
7-threat-trait × counter-trait cross-reference; up to +0.45 bonus clamped per hero.
`TraitCounterEngineTest` (14 tests) covers all threat/counter pairs, cap, and `describeCounters`.
Files: `domain/advisor/TraitCounterEngine.kt`

### A8 — Archetype gap detection + HeroArchetypeService [COMPLETED 2026-07-01]
`HeroArchetypeService` (@Singleton Hilt) reads `hero_archetypes.json` at startup.
Provides `computeAllyStateGaps()`, `computeArchetypeMatchupScore()`, `isMagicDamageSource()`,
`isFrontlineHero()`, `uniqueArchetypes()`, `sharedArchetypes()`.
`CompositionAnalyzer.analyze()` now emits two additional gap warnings: no magic damage + squishy comp.
Files: `domain/advisor/HeroArchetypeService.kt`, `domain/advisor/CompositionAnalyzer.kt`

### A9 — Counter confidence table [COMPLETED 2026-07-01]
`CounterLookupEntity` + `CounterLookupDao` + `MIGRATION_3_4` add a confidence-scored hero
counter table seeded from `counter_lookup.json` (1 000+ pairs, empirical win-rate-based).
`AppDatabase` bumped to version 4.
Files: `data/local/database/CounterLookupEntity.kt`, `CounterLookupDao.kt`, `DatabaseModule.kt`

---

### A5 — Commit Room schema JSON files
**Effort:** S · **Blocked by:** nothing
Schema export is configured but it is unconfirmed whether the generated `/schemas/*.json` files are committed. Run `./gradlew :app:kspDebugKotlin` and commit the output.
Files: `app/schemas/`

---

### A6 — Personal meta calibration UI [COMPLETED — verified 2026-07-03]
`CalibrationSection` is wired into `SettingsScreen` (rationale, confidence bar, suggested
weights, Refresh/Apply actions). `WeightCalibrator.MIN_SESSIONS` (10) gates calibration —
below that it surfaces "keep playing to refine" via `calibration_need_more`.
Files: `presentation/settings/SettingsScreen.kt`, `presentation/settings/components/CalibrationSection.kt`, `domain/engine/WeightCalibrator.kt`

---

## [BACKLOG]

### Autonomous awareness
- [x] **TFLite hero portrait classifier** — `HeroClassifier` wraps `mlbb_hero_classifier.tflite` (MobileNetV3Small, [1,224,224,3]→[1,120]) and is the primary matching path in `PortraitMatcher` (TD-15, `misc.md` §13)
- [ ] SSD/YOLO portrait *region* detector — train on 500+ annotated crops; integrate into `HeroPortraitObjectDetector` to replace coordinate-based `SlotRegions` (RA-05 — detection model, separate from classifier)
- [ ] JImageHash FP benchmark against portrait test set; promote WaveletHash + AverageColorHash to primary in `PortraitMatcher` (RA-04)
- [ ] Auto-recalibration of `SlotRegions` from a one-time guided capture
- [ ] Patch-delta weighting from historical snapshots
- [ ] Draft-phase-aware "power spike timing" advice

### Historical feedback loop
- [ ] Tendency feedback surfaced in-app ("you over-ban X role", "you under-roam")
- [ ] Match timeline replay with frame thumbnails

### Frictionless deployment
- [ ] Deep links into exact system-settings pages per OEM
- [ ] Accessibility-service health watchdog with re-setup at the revoked step
- [ ] One-tap overlay relaunch from notification after an OS kill (notification action exists; watchdog pending)

### Platform resilience
- [ ] Verified no-capture mode at full feature parity with autonomous mode
- [ ] OEM-specific auto-start workaround matrix maintained per release
- [ ] Honest self-status in the overlay: "capture unavailable", "meta data N days old", "accessibility service off"

### Architecture & code quality
- [ ] **P2/M** Introduce `:domain` and `:data` Gradle modules to enforce the dependency rule at compile time
- [ ] **P3/M** Full Gson removal — pending minified-build smoke test (see `todo.md` §5, `misc.md` §10)
- [ ] **P2/S** Consolidate `DraftScorer.computeScore` → unified `simplified = true` parameter (see `misc.md` §2)

### Testing & CI
- [ ] **P1/M** Room migration test (1→2→3) using `MigrationTestHelper`
- [ ] **P1/M** Compose UI tests for Draft, HeroList, Settings, Permission Wizard
- [ ] **P1/M** Instrumentation test for overlay foreground-service start/stop lifecycle
- [x] **P2/M** Unit tests for `WeightCalibrator`, `EnemyIntentAnalyzer`, `WinConditionGenerator` — ✅ 2026-07-01 (5 test classes, 49 tests total)
- [x] **P2/M** Unit tests for `DraftPatternAnalyzer`, `BuildAdvisor`, `DraftScoreCalculator` — ✅ 2026-07-03
- [ ] **P2/M** `FrameProcessor` slot-dedupe and throttle tests with synthetic bitmaps (Robolectric)
- [ ] **P2/S** `DraftExporter` round-trip serialisation test
- [ ] **P2/S** detekt baseline — run `./gradlew detektBaseline` and commit `config/detekt/baseline.xml`
- [ ] **P3/S** Baseline profile for startup performance

### Observability
- [ ] **P2/M** Remote crash reporting (Firebase Crashlytics) gated behind settings toggle
- [ ] **P2/S** "Share logs" action from `LogScreen` via existing FileProvider
- [ ] **P3/S** Structured detection-accuracy event logging

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

### Documentation
- [ ] **P2/S** CV calibration guide — how to remap `SlotRegions` for a new device
- [ ] **P3/S** Architecture Decision Records (ADRs) for the key calls in `overview.md` §8

---

## Recommendation Adoption Status (RA-xx)

Items originally from `docs/temp/recommendations.md`.

| # | Recommendation | Status |
|---|---|---|
| RA-01 | JetOverlay overlay SDK | ✅ Completed — see `misc.md` §11 |
| RA-02 | p3hndrx/MLBB-API hero/item data | 📋 Deferred — backend stability verification required |
| RA-03 | ridwaanhall/api-mobilelegends | 📋 Deferred — API liveness confirmation first |
| RA-04 | KilianB/JImageHash multi-algorithm hashing | ⚙️ In Gradle — FP benchmark pending (see `misc.md` §9) |
| RA-05 | TFLite hero portrait classifier (`HeroClassifier`) | ✅ Completed — `mlbb_hero_classifier.tflite` integrated; see TD-15 and `misc.md` §13. SSD detection model (portrait *region* bounding boxes) remains as backlog. |
| RA-06 | Balloon tooltip for hero suggestions | ✅ Completed — `RecommendationCard` long-press in `PickPhaseContent.kt` |
| RA-07 | kotlinx.serialization full migration | ✅ Completed (partial) — all entry points migrated; Gson removal pending smoke test |

---

## How to use this file

1. When a planned item ships, move it into `[COMPLETED]` **and** add a row to [`features.md`](./features.md).
2. Break any `[BACKLOG]` item into concrete steps in [`todo.md`](./todo.md) before starting work.
3. Move items to `[ACTIVE]` only when actively being worked, with an estimated completion.
4. Keep effort and blocker fields accurate — stale estimates lead to bad planning.
