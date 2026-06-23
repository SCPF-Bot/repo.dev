# Architecture Overview — MLBB Draft Assistant

> **Status:** Living document. Update whenever a module boundary, dependency, or
> data-flow contract changes. Last reconciled against source at app `versionName 2.0.0`
> (`versionCode 2`).

This document describes **what the app is, how it is structured, how data flows
through it, and why each architectural decision was made**. It is the canonical
map of the codebase. For the feature catalogue see [`features.md`](./features.md),
for the forward plan see [`roadmap.md`](./roadmap.md), and for the actionable
backlog see [`todo.md`](./todo.md).

---

## 1. What the app is

**MLBB Draft Assistant** is a native Android application that provides real-time,
explainable drafting guidance for *Mobile Legends: Bang Bang* during the hero
ban/pick phase. It renders an always-on floating overlay on top of the game,
uses a computer-vision pipeline (MediaProjection + perceptual hashing) to detect
picks and bans autonomously, and surfaces ranked hero recommendations scored by
meta strength, synergy, and counter value.

- **Package:** `com.mlbb.assistant`
- **Min SDK:** 29 (Android 10) · **Target/Compile SDK:** 36
- **Language:** Kotlin 2.1.0 · **Build:** AGP 8.10.1, Gradle KTS, version catalog
- **UI:** Jetpack Compose + Material 3 (Compose BOM 2025.05.01)
- **Module layout:** single Gradle module `:app` (root project `MLBB Assistant 2.0`)

The product thesis (full detail in [`MISSION.md`](./MISSION.md)) is **overlay-first**:
the floating bubble and MiniWidget are the primary product; the in-app screens
exist to support them.

---

## 2. Architectural style

The app follows **Clean Architecture** with a **unidirectional data flow (MVI/UDF)**
presentation layer.

```
┌──────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION                                  │
│  Jetpack Compose screens ── observe ──▶ StateFlow<UiState>             │
│  ViewModels (Hilt) ── emit intents ──▶ UseCases                       │
│  OverlayService (foreground) hosts the overlay Compose tree           │
└───────────────────────────────┬──────────────────────────────────────┘
                                 │ depends on (interfaces only)
┌───────────────────────────────▼──────────────────────────────────────┐
│                            DOMAIN  (pure Kotlin, no Android imports)   │
│  model/      Hero, Lane, Tier, Proficiency, DraftOutcome, ...         │
│  engine/     DraftSessionManager, RankRuleEngine, PickSequenceEngine, │
│              WeightCalibrator, DraftPatternAnalyzer                    │
│  scoring/    DraftScorer, ScoreWeights, HeroScore                     │
│  advisor/    CompositionAnalyzer, BanRecommender, BuildAdvisor,       │
│              EnemyIntentAnalyzer, WinConditionGenerator, ...          │
│  usecase/    GetSuggestions, SyncHeroes, SaveDraftSession, ...        │
│  repository/ HeroRepository, DraftSessionRepository (interfaces)      │
└───────────────────────────────┬──────────────────────────────────────┘
                                 │ implemented by
┌───────────────────────────────▼──────────────────────────────────────┐
│                              DATA                                      │
│  local/database/   Room: AppDatabase, DAOs, Entities, Converters      │
│  local/datastore/  Preferences DataStore (single delegate)            │
│  local/crashlog/   CrashLogStore + Timber AppLogTree                  │
│  remote/api/       Retrofit MetaApi (GET /v1/meta/snapshot)           │
│  remote/dto/       MetaSnapshotDto                                     │
│  repository/       HeroRepositoryImpl, DraftSessionRepositoryImpl      │
│  export/           DraftExporter                                       │
└───────────────────────────────┬──────────────────────────────────────┘
                                 │ feeds
┌───────────────────────────────▼──────────────────────────────────────┐
│                       CAPTURE / SERVICE (Android edge)                 │
│  capture/   FrameProcessor, PhaseDetector, PortraitMatcher,           │
│             PerceptualHash, RankDetector, SlotRegions, ...            │
│  service/   ScreenCaptureManager (MediaProjection), VoiceAlertService,│
│             MLBBAccessibilityService                                   │
└────────────────────────────────────────────────────────────────────────┘
```

### Dependency rule
Source dependencies always point **inward**: `presentation → domain ← data`.
The `domain` layer has **zero Android imports** and is unit-testable on the JVM.
The `capture` and `service` packages sit at the Android edge and feed observations
into the domain `DraftSessionManager`.

---

## 3. Layer-by-layer map

### 3.1 Domain layer (`domain/`)

Pure Kotlin business logic. No `android.*` imports.

| Package | Key types | Responsibility |
| --- | --- | --- |
| `model/` | `Hero`, `Lane`, `Tier`, `CoreItem`, `Proficiency`, `DraftOutcome`, `DraftHistoryItem` | Immutable domain entities. `Hero` carries stats (`winRate`, `pickRate`, `banRate`, `patchTrend`), relationships (`counters`, `counteredBy`, `synergies`), and flags (`isOP`, `hasCCUlt`, `isToxicMechanic`). |
| `engine/` | `DraftSessionManager`, `DraftSession`, `DraftPhase`, `Rank`, `RankRuleEngine`, `PickSequenceEngine`, `WeightCalibrator`, `DraftPatternAnalyzer` | The **draft state machine** and rules. `DraftSessionManager` owns a `StateFlow<DraftSession>` with ban/pick records, an undo stack, and outcome/simulation flags. |
| `scoring/` | `DraftScorer`, `ScoreWeights`, `HeroScore` | The recommendation engine. Multi-factor scoring with adaptive weights and dataset-derived bounds. |
| `advisor/` | `CompositionAnalyzer`, `CompositionArchetype`, `BanRecommender`, `BuildAdvisor`, `EnemyIntentAnalyzer`, `WinConditionGenerator`, `DraftScoreCalculator` | Higher-level draft intelligence built on top of scoring. |
| `usecase/` | `GetSuggestionsUseCase`, `GetHeroesUseCase`, `GetPagedHeroesUseCase`, `GetDraftHistoryUseCase`, `SaveDraftSessionUseCase`, `SyncHeroesUseCase`, `ToggleOverlayUseCase` | Single-responsibility orchestrators bridging presentation and repositories. |
| `repository/` | `HeroRepository`, `DraftSessionRepository` | Abstractions implemented in the data layer. |
| `OverlayController` | interface | Domain-side contract for toggling the overlay, decoupling use cases from the Android service. |

### 3.2 Data layer (`data/`)

| Package | Key types | Notes |
| --- | --- | --- |
| `local/database/` | `AppDatabase` (v3), `HeroDao`, `DraftSessionDao`, `HeroPoolDao`, `HeroEntity`, `DraftSessionEntity`, `HeroPoolEntity`, `Converters` | Room with `exportSchema = true` (schemas committed under `/schemas`). Constructed **only** via `DatabaseModule` which applies migrations 1→2→3. |
| `local/datastore/` | `PreferencesDataStore` | A single `preferencesDataStore` delegate (`appDataStore`) provided exclusively by `AppModule` to avoid duplicate-delegate `IllegalStateException`. |
| `local/preferences/` | `WizardPreference` | Onboarding progress flags. |
| `local/crashlog/` | `CrashLogStore`, `AppLogTree` | File-backed crash/log store with a mutex for concurrent writes; `AppLogTree` is a Timber tree. |
| `remote/api/` | `MetaApi` | `GET v1/meta/snapshot` — the single network contract. |
| `remote/dto/` | `MetaSnapshotDto` | Wire model + `toEntity()` mapping. |
| `repository/` | `HeroRepositoryImpl`, `DraftSessionRepositoryImpl` | Network-with-local-fallback sync; Paging 3 source for the hero grid. |
| `export/` | `DraftExporter` | Serializes a draft session for sharing. |

**Seed & fallback data:**
- `res/raw/default_heroes.json` (~73 KB) — bundled hero roster used when the
  network sync fails and the DB is empty.
- `assets/draft_ui_map.json` (~7 KB) — screen-region coordinate map for the CV
  pipeline.

### 3.3 Capture pipeline (`capture/`)

The autonomous detection stack. Pure-ish image processing, fed by
`ScreenCaptureManager`.

| Type | Responsibility |
| --- | --- |
| `ScreenCaptureManager` (in `service/`) | Owns the `MediaProjection` + `ImageReader`, emits `Bitmap` frames. |
| `FrameProcessor` | Per-frame orchestrator. Throttles by phase, detects phase + ban-button visibility, scans ban/pick slots for new fills using a **normalised luminance threshold** (robust to HDR/auto-brightness). |
| `PhaseDetector` / `PhaseOcrDetector` / `PhaseDetectionConfig` | Classify the current draft phase from banner colours; config constants centralised. |
| `PortraitMatcher` + `PerceptualHash` | Identify which hero occupies a slot via dHash + histogram (hybrid) matching against preloaded portrait hashes. |
| `SlotRegions` | Normalised rectangles for every ban/pick slot and the action button; crops frames. |
| `RankDetector` / `FirstPickDetector` | Infer rank tier and which side picks first. |

### 3.4 Service layer (`service/` + `presentation/overlay/`)

| Type | Responsibility |
| --- | --- |
| `OverlayService` | Foreground service (`specialUse|mediaProjection`) hosting the overlay Compose tree, the draggable bubble, MiniWidget, and phase-specific panels. Persists bubble position to DataStore (TD-12). ~1,100 LOC — the largest single file. |
| `OverlayPermissionActivity` | Transparent activity that requests "Draw over other apps". |
| `MLBBAccessibilityService` | Detects MLBB foreground/draft context. |
| `VoiceAlertService` | `TextToSpeech` turn announcements; shut down in `MainActivity.onDestroy`. |

### 3.5 Presentation layer (`presentation/`)

MVI screens, each with `Screen` + `ViewModel` (+ `State` where applicable).

- **Shell & nav:** `AppShell`, `AppShellViewModel`, `AppNavGraph`, `AppRoute`.
- **Screens:** `home`, `draft`, `herolist`, `herodetail`, `heropool`, `history`
  (+ `DraftReplayScreen`), `metaboard`, `settings`, `log`, `welcome`
  (`PermissionWizardScreen`), `main` (`MainActivity`).
- **Overlay UI:** `FloatingBubble`, `MiniWidget`, `DraftPanel`, and phase content
  composables (`BanPhaseContent`, `PickPhaseContent`, `TradingPhaseContent`,
  `FinalReportContent`) + `WidgetHeaderBar`, `WidgetScorePanel`.
- **Common:** themed primitives (`MLBBButton`, `MLBBTextField`, `HeroGrid`,
  `HeroPortrait`, `RoleDashboard`, `ConnectivityBanner`, `LoadingSpinner`,
  `BackButton`) and theme (`Color`, `Theme`, `Type`).

### 3.6 Dependency injection (`di/`)

Hilt, all installed in `SingletonComponent`:

| Module | Provides |
| --- | --- |
| `AppModule` | The single `DataStore<Preferences>`, `DraftSessionManager`, `VoiceAlertService`. |
| `DatabaseModule` | `AppDatabase` (with migrations), all DAOs. |
| `NetworkModule` | OkHttp, Retrofit, `MetaApi`. |
| `RepositoryModule` | Binds repository interfaces to impls. |
| `OverlayModule` | Overlay-related bindings / `OverlayController`. |

### 3.7 Utilities (`utils/`)

`AppConstants`, `DateFormatter` (java.time only — no `SimpleDateFormat`),
`Extensions`, `JsonParser`, `NetworkMonitor`, `NetworkResult`.

---

## 4. The scoring engine (the heart of the product)

`DraftScorer.score(...)` produces a `HeroScore` per candidate. The formula:

$$\text{total} = \text{meta}\cdot w_m + \text{synergy}\cdot w_s + \text{counter}\cdot w_c + \text{role}\cdot 0.15 + \text{flex}\cdot 0.10 + \text{safe}\cdot 0.10$$

clamped to `[0, 1]`, then multiplied by the personal-pool proficiency multiplier.

- **Weights** (`ScoreWeights`) default to meta **0.40** / synergy **0.30** /
  counter **0.30**, validated to sum to 1.0 at construction. Presets:
  `META_HEAVY`, `COUNTER_HEAVY`, `SYNERGY_HEAVY`.
- **Adaptive weights:** as the draft progresses (`pickIndex / maxPickIndex`),
  meta weight decays and synergy + counter rise — late picks react to the board.
- **Dynamic bounds (TD-05):** `computeBounds()` derives win-rate median ± half-IQR
  and 90th-percentile ban/pick caps from the live pool, replacing hardcoded
  thresholds so scoring stays calibrated across patches.
- **Meta sub-score:** win contribution (0.35) + ban (0.30) + pick (0.15) +
  tier (0.20), times a ±15% patch-velocity multiplier.
- **Positional modifiers:** first-pick favours flexibility; last-pick favours
  safety against the now-known enemy comp.
- **Explainability:** every score carries a `badgeLabel`
  (`↑ RISING / ◆ META / ◈ SYNERGY / ◉ COUNTER / ◎ BALANCED`) and a human
  `reason` string.

`CompositionAnalyzer` complements scoring with archetype detection, lane-coverage
gaps, damage-profile balance, CC/mobility/sustain levels, strengths/weaknesses,
and live counter-pick warnings.

---

## 5. The draft state machine

`DraftPhase`: `IDLE → SETUP → BAN_ROUND_1 → (BAN_ROUND_2) → PICK → TRADING → COMPLETE`.

- `RankRuleEngine` encodes ban structures: Epic & below = 6 bans / no round 2;
  Legend = 8 bans / round 2 (1 each); Mythic+ = 10 bans / round 2 (2 each).
- `PickSequenceEngine` models the **1-2-2-2-2-1** turn order, flagging double
  picks and the strategically critical first/last picks.
- `DraftSessionManager` is the single mutator: records bans/picks, maintains an
  **undo stack**, supports trading-phase hero swaps, records match outcome, and
  can upgrade an unknown rank from the observed ban count.

---

## 6. Data flow scenarios

**Autonomous detection (happy path):**
```
MLBB on screen
  → ScreenCaptureManager (MediaProjection) emits Bitmap
  → FrameProcessor: detect phase + new filled slots
  → PortraitMatcher: identify hero in each new slot
  → DraftSessionManager.recordEnemyBan/Pick(...)
  → DraftScorer.rankAll(...) over available pool
  → OverlayService renders updated suggestions in the MiniWidget
```

**Manual fallback (no capture consent):** the user taps slots in the overlay /
draft screen; the identical `DraftSessionManager` + `DraftScorer` path runs.

**Hero data sync:**
```
SyncHeroesUseCase → HeroRepositoryImpl.syncHeroes()
  → MetaApi.getMetaSnapshot()           [network]
      success → heroDao.replaceAll(...)
      failure → if DB empty, seed from res/raw/default_heroes.json
```

**History persistence:** `SaveDraftSessionUseCase` is the **only** write path to
`DraftSessionDao`. ViewModels never touch the DAO directly; history is read via
`GetDraftHistoryUseCase`.

---

## 7. Persistence & storage

| Store | Tech | Contents |
| --- | --- | --- |
| Relational | Room v3 | `heroes`, `draft_sessions`, `hero_pool` |
| Key-value | DataStore Preferences | settings, wizard progress, bubble position, score weights |
| Files | `CrashLogStore` | rolling crash/debug logs |

Migrations: 1→2 and 2→3 (the v2→v3 adds `hasCCUlt`). Schema JSON is exported and
intended to be committed for safe future migrations.

---

## 8. Cross-cutting decisions (the "why")

1. **Domain purity** — no Android in `domain/` so the engine is JVM-unit-testable
   (see `src/test`: `DraftScorerTest`, `PickSequenceEngineTest`,
   `RankRuleEngineTest`, `DraftSessionManagerTest`, `CompositionAnalyzerTest`,
   `BanRecommenderTest`, `PerceptualHashTest`, serialization test).
2. **Single DataStore delegate** — exactly one `preferencesDataStore` to prevent
   the multiple-delegate crash.
3. **Single DB construction path** — `DatabaseModule` only; the old companion
   factory that bypassed migrations was removed.
4. **`toEntity()` mirrors `toDomain()`** — mapping symmetry is a maintenance
   invariant; adding a field requires updating both.
5. **`java.time` only** — `DateFormatter` uses `DateTimeFormatter` (thread-safe);
   `SimpleDateFormat` is banned.
6. **Explicit IO dispatch (TD-06)** — repository suspend functions wrap
   `withContext(Dispatchers.IO)` defensively.
7. **Config-driven CV** — detection thresholds live in `PhaseDetectionConfig`
   (TD-03/04) so patch recalibration doesn't require touching detector logic.
8. **Foreground-service correctness** — `specialUse|mediaProjection` type and the
   `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` declaration satisfy Android 14+ rules.

The recurring `TD-xx` tags throughout the source are a **technical-debt tracking
scheme**; each resolved item is annotated at its fix site. See [`todo.md`](./todo.md)
for the live register.

---

## 9. Build, tooling & dependencies

- **Version catalog:** `gradle/libs.versions.toml` is the single source of truth.
- **Key libraries:** Hilt 2.55, Room 2.7.1, Retrofit 2.11 + OkHttp 4.12, Coil 3.1,
  Paging 3.3.6, DataStore 1.1.4, Coroutines 1.10.1, Timber 5.0.1, Navigation
  Compose 2.9.0.
- **Test stack:** JUnit4, MockK, Turbine, coroutines-test, Robolectric;
  Espresso + Compose UI test for instrumentation.
- **Release build:** R8 minify + resource shrink + ProGuard rules.
- **API base URL:** `BuildConfig.META_API_BASE_URL`, overridable per variant.

---

## 10. Localization

String resources are localized into Filipino (`values-fil`), Indonesian
(`values-in`), Malay (`values-ms`), Thai (`values-th`), and Vietnamese
(`values-vi`) — the core MLBB markets — alongside the default English
(`values`, 75 strings).

---

## 11. Known limitations / sharp edges

- `OverlayService` is large (~1,100 LOC) and mixes service lifecycle, window
  management, and UI hosting — a prime refactor target (see `todo.md`).
- CV detection accuracy depends on device resolution and ROM; `SlotRegions` /
  `draft_ui_map.json` may need recalibration per aspect ratio.
- The `MetaApi` backend is a single endpoint; there is no auth or caching layer
  beyond the local DB fallback.
- Network base URL points at a production host that must exist for live sync;
  otherwise the bundled JSON seed is authoritative.
