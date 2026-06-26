# Features — MLBB Draft Assistant

> **Status:** Living catalogue of every feature currently in the codebase.
> A feature is listed here only if it is **implemented in source**.
> Planned-but-not-built items belong in [`roadmap.md`](./roadmap.md);
> actionable work belongs in [`todo.md`](./todo.md);
> architecture in [`overview.md`](./overview.md).
>
> **Legend:** ✅ implemented · ⚠️ implemented with known limitation · 🧪 covered by unit tests
>
> **Grouping:** **Core** = on the critical user path during a live draft.
> **Edge** = supporting features that enrich the product but are not blocking.
> **Legacy** = deprecated or superseded flows still present in source.

Reconciled against `versionName 2.0.0` (versionCode 2).
Last updated: 2026-06-26 (P0-05 FrameProcessor thread-safety fix — fourth audit pass; see `docs/temp/findings.md` delta summary and `docs/misc.md`).

---

## CORE FEATURES

### 1. Real-time overlay (the primary product)

| # | Feature | Status | Source |
|---|---|---|---|
| 1.1 | Always-on floating bubble during MLBB draft | ✅ | `overlay/FloatingBubble.kt`, `OverlayService.kt` |
| 1.2 | Draggable bubble with position persisted across sessions (TD-12) | ✅ | `OverlayService.kt` (DataStore) |
| 1.3 | Expandable **MiniWidget** showing live suggestions | ✅ | `overlay/MiniWidget.kt`, `components/WidgetScorePanel.kt` |
| 1.4 | Minimise / restore between bubble and widget | ✅ | `OverlayService.kt`, `MiniWidget.kt` |
| 1.5 | Widget header bar (collapse, close, status) | ✅ | `overlay/components/WidgetHeaderBar.kt` |
| 1.6 | Foreground service compliant with Android 14+ (`specialUse\|mediaProjection`) | ✅ | `AndroidManifest.xml`, `OverlayService.kt` |
| 1.7 | Persistent notification with "Relaunch Overlay" action | ✅ | `OverlayService.kt`, `AppConstants.kt` |
| 1.8 | Overlay launched from "Start Draft" with permission check | ✅ | `main/MainActivity.kt` |
| 1.9 | Session state serialised to DataStore (survives OS kill mid-draft) | ✅ | `OverlayService.kt` (KEY_SESSION_PHASE/RANK/FIRST_PICK) |

#### 1.1 Phase-specific overlay content

| # | Feature | Status | Source |
|---|---|---|---|
| 1.10 | Ban-phase panel with ban recommendations | ✅ | `overlay/BanPhaseContent.kt`, `advisor/BanRecommender.kt` |
| 1.11 | Pick-phase panel with ranked hero suggestions | ✅ | `overlay/PickPhaseContent.kt` |
| 1.12 | Trading-phase panel (hero swap guidance) | ✅ | `overlay/TradingPhaseContent.kt` |
| 1.13 | Final draft report panel | ✅ | `overlay/FinalReportContent.kt` |
| 1.14 | Shared draft panel scaffold | ✅ | `overlay/DraftPanel.kt` |

---

### 2. Computer-vision detection pipeline

| # | Feature | Status | Source |
|---|---|---|---|
| 2.1 | Screen capture via MediaProjection + ImageReader | ✅ | `service/ScreenCaptureManager.kt` |
| 2.2 | Per-frame orchestration with phase-aware throttling | ✅ | `capture/FrameProcessor.kt` |
| 2.3 | Draft-phase detection from banner colours | ✅ | `capture/PhaseDetector.kt`, `PhaseDetectionConfig.kt` |
| 2.4 | OCR-assisted phase disambiguation (ban round 1 vs 2) | ✅ | `capture/PhaseOcrDetector.kt` |
| 2.5 | Hero portrait identification (perceptual hash) | 🧪 | `capture/PortraitMatcher.kt`, `PerceptualHash.kt` |
| 2.6 | Hybrid dHash + histogram matching | ✅ | `capture/PortraitMatcher.kt` |
| 2.7 | Parallel lazy preload of portrait hashes (TD-08) | ✅ | `capture/PortraitMatcher.kt` |
| 2.8 | Slot-fill detection via normalised luminance threshold (TD-04, P1-01) | ✅ | `capture/FrameProcessor.kt` (`copyPixelsToBuffer` bulk read) |
| 2.9 | Normalised slot region map (resolution-independent) | ⚠️ | `capture/SlotRegions.kt`, `assets/draft_ui_map.json` |
| 2.10 | Rank detection from emblem region | ✅ | `capture/RankDetector.kt` |
| 2.11 | First-pick side detection | ✅ | `capture/FirstPickDetector.kt` |
| 2.12 | Ban-button visibility detection ("is it our turn?") | ✅ | `capture/FrameProcessor.kt` |
| 2.13 | Emit only newly filled slots (dedupe across frames) | ✅ | `capture/FrameProcessor.kt` |
| 2.14 | Thread-safe slot deduplication in FrameProcessor (P0-05) | ✅ | `capture/FrameProcessor.kt` (`ConcurrentHashMap.newKeySet`) |

---

### 3. Scoring & recommendation engine

| # | Feature | Status | Source |
|---|---|---|---|
| 3.1 | Multi-factor hero scoring (meta + synergy + counter + role) | 🧪 | `scoring/DraftScorer.kt` |
| 3.2 | Configurable, validated weights summing to 1.0 | ✅ | `scoring/ScoreWeights.kt` |
| 3.3 | Named presets: META_HEAVY / COUNTER_HEAVY / SYNERGY_HEAVY | ✅ | `scoring/ScoreWeights.kt` |
| 3.4 | Adaptive weight ramp across pick index | ✅ | `scoring/DraftScorer.kt` (`adaptiveWeights`) |
| 3.5 | Dataset-derived dynamic bounds (median ± IQR, p90 caps) (TD-05) | ✅ | `scoring/DraftScorer.kt` (`computeBounds`) |
| 3.6 | Patch-velocity multiplier (±15%) | ✅ | `scoring/DraftScorer.kt` (`scoreMeta`) |
| 3.7 | First-pick flexibility bonus | ✅ | `scoring/DraftScorer.kt` (`scoreFlexibility`) |
| 3.8 | Last-pick safety bonus | ✅ | `scoring/DraftScorer.kt` (`scoreSafety`) |
| 3.9 | Personal hero-pool proficiency multiplier (TD-02) | ✅ | `scoring/DraftScorer.kt`, `model/Proficiency.kt` |
| 3.10 | Human-readable recommendation reasons | ✅ | `scoring/DraftScorer.kt` (`buildReason`) |
| 3.11 | Badge classification (RISING/META/SYNERGY/COUNTER/BALANCED) | ✅ | `scoring/DraftScorer.kt` |
| 3.12 | Whole-pool ranking, banned heroes filtered out | ✅ | `scoring/DraftScorer.kt` (`rankAll`) |
| 3.13 | Lightweight linear scoring path for tests only (`@VisibleForTesting`, P2-03) | 🧪 | `scoring/DraftScorer.kt` (`computeScore`) |

---

### 4. Draft intelligence (advisor layer)

| # | Feature | Status | Source |
|---|---|---|---|
| 4.1 | Composition archetype detection | 🧪 | `advisor/CompositionAnalyzer.kt`, `CompositionArchetype.kt` |
| 4.2 | Damage-profile balance (physical vs magic %) | ✅ | `advisor/CompositionAnalyzer.kt` |
| 4.3 | CC / mobility / sustain level classification | ✅ | `advisor/CompositionAnalyzer.kt` |
| 4.4 | Lane-coverage gap detection (missing lanes) | ✅ | `advisor/CompositionAnalyzer.kt` |
| 4.5 | Composition strengths & weaknesses generation | ✅ | `advisor/CompositionAnalyzer.kt` |
| 4.6 | Live counter-pick warnings for our picks | ✅ | `advisor/CompositionAnalyzer.kt` |
| 4.7 | Ban recommendations (flat rank + absolute/reactive split) | 🧪 | `advisor/BanRecommender.kt` (`rank` + `rankSplit`) |
| 4.8 | Build / item advice (3 core + 3 situational) (TD-02) | ✅ | `advisor/BuildAdvisor.kt` |
| 4.9 | Enemy intent inference | ✅ | `advisor/EnemyIntentAnalyzer.kt` |
| 4.10 | Win-condition generation | ✅ | `advisor/WinConditionGenerator.kt` |
| 4.11 | Aggregate draft score calculation | ✅ | `advisor/DraftScoreCalculator.kt` |

---

### 5. Draft state machine & rules

| # | Feature | Status | Source |
|---|---|---|---|
| 5.1 | Draft session state with `StateFlow` | ✅ | `engine/DraftSessionManager.kt` |
| 5.2 | Phase progression IDLE→SETUP→BANS→PICK→TRADING→COMPLETE | 🧪 | `engine/DraftSessionManager.kt` |
| 5.3 | Rank-aware ban structures (6/8/10 bans) | 🧪 | `engine/RankRuleEngine.kt` |
| 5.4 | Banner-slot rules per rank (who may ban) | ✅ | `engine/RankRuleEngine.kt` |
| 5.5 | 1-2-2-2-2-1 pick sequence modelling | 🧪 | `engine/PickSequenceEngine.kt` |
| 5.6 | Double-pick / first-pick / last-pick flags | ✅ | `engine/PickSequenceEngine.kt` |
| 5.7 | Undo stack for bans/picks/swaps | 🧪 | `engine/DraftSessionManager.kt` |
| 5.8 | Trading-phase hero swap | ✅ | `engine/DraftSessionManager.kt` |
| 5.9 | Missed-ban (timeout) sentinel handling | ✅ | `engine/DraftSessionManager.kt` |
| 5.10 | Rank inference/upgrade from observed ban count | 🧪 | `engine/DraftSessionManager.kt`, `RankRuleEngine.kt` |
| 5.11 | Match-outcome recording (win/loss/unknown) | ✅ | `engine/DraftSessionManager.kt`, `model/DraftOutcome.kt` |
| 5.12 | Simulation mode (excluded from history/calibration) | ✅ | `engine/DraftSessionManager.kt` |
| 5.13 | Rank parsing from freeform/OCR string | ✅ | `engine/RankRuleEngine.kt` (`fromString`) |
| 5.14 | Weight self-calibration from history | ✅ | `engine/WeightCalibrator.kt` |
| 5.15 | Draft pattern analysis (tendencies) | ✅ | `engine/DraftPatternAnalyzer.kt` |

---

## EDGE FEATURES

### 6. In-app screens

| # | Screen | Status | Source |
|---|---|---|---|
| 6.1 | App shell + bottom/nav scaffold | ✅ | `shell/AppShell.kt`, `navigation/AppNavGraph.kt`, `AppRoute.kt` |
| 6.2 | Home dashboard | ✅ | `home/HomeScreen.kt`, `HomeViewModel.kt` |
| 6.3 | Draft screen (manual draft + suggestions) | ✅ | `draft/DraftScreen.kt`, `DraftViewModel.kt`, `DraftState.kt` |
| 6.4 | Score explanation bottom sheet | ✅ | `draft/ScoreExplanationSheet.kt` |
| 6.5 | Hero suggestion cards + chips | ✅ | `draft/components/SuggestionCard.kt`, `HeroChip.kt` |
| 6.6 | Hero list / explorer (paged, TD-10) | ✅ | `herolist/HeroListScreen.kt`, `HeroListViewModel.kt`, `HeroListState.kt` |
| 6.7 | Hero detail (stats, items, spells, relationships) | ✅ | `herodetail/HeroDetailScreen.kt` |
| 6.8 | Personal hero pool management with search/filter | ✅ | `heropool/HeroPoolScreen.kt`, `HeroPoolViewModel.kt` |
| 6.9 | Draft history list | ✅ | `history/DraftHistoryScreen.kt`, `DraftHistoryViewModel.kt` |
| 6.10 | Draft replay viewer | ✅ | `history/DraftReplayScreen.kt` |
| 6.11 | Meta board (tier list / meta snapshot) | ✅ | `metaboard/MetaBoardScreen.kt` |
| 6.12 | Settings (weights, presets, toggles) | ✅ | `settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsState.kt` |
| 6.13 | Screen-region mapping dialog (CV calibration) | ✅ | `settings/components/ScreenMappingDialog.kt` |
| 6.14 | Permission wizard onboarding | ✅ | `welcome/PermissionWizardScreen.kt` |
| 6.15 | Crash/debug log viewer | ✅ | `log/LogScreen.kt`, `LogViewModel.kt` |

---

### 7. Hero data & persistence

| # | Feature | Status | Source |
|---|---|---|---|
| 7.1 | Room database v3 (heroes, draft_sessions, hero_pool) | ✅ | `data/local/database/AppDatabase.kt` |
| 7.2 | Schema export + migrations 1→2→3 | ✅ | `di/DatabaseModule.kt`, `/schemas` |
| 7.3 | Meta snapshot sync over Retrofit | ✅ | `data/remote/api/MetaApi.kt`, `HeroRepositoryImpl.kt` |
| 7.4 | Local JSON seed fallback when offline/empty | ✅ | `res/raw/default_heroes.json`, `utils/JsonParser.kt` |
| 7.5 | Paging 3 hero grid for large rosters (TD-10) | ✅ | `HeroRepositoryImpl.kt`, `GetPagedHeroesUseCase.kt` |
| 7.6 | Hero search & top-meta queries | ✅ | `data/local/database/HeroDao.kt` |
| 7.7 | Draft session persistence (single write path via SaveDraftSessionUseCase) | ✅ | `SaveDraftSessionUseCase.kt`, `DraftSessionRepositoryImpl.kt` |
| 7.8 | Draft export/share | ✅ | `data/export/DraftExporter.kt` |
| 7.9 | Hero-pool proficiency storage | ✅ | `data/local/database/HeroPoolDao.kt`, `HeroPoolEntity.kt` |

---

### 8. Permissions & onboarding

| # | Feature | Status | Source |
|---|---|---|---|
| 8.1 | Guided permission wizard (least→most intrusive) | ✅ | `welcome/PermissionWizardScreen.kt` |
| 8.2 | Draw-over-other-apps request flow | ✅ | `overlay/OverlayPermissionActivity.kt`, `MainActivity.kt` |
| 8.3 | Accessibility service enablement | ✅ | `service/MLBBAccessibilityService.kt`, `res/xml/accessibility_service_config.xml` |
| 8.4 | Screen-capture consent flow | ✅ | `MainActivity.kt`, `ScreenCaptureManager.kt` |
| 8.5 | Battery-optimisation exemption request | ✅ | manifest + wizard |
| 8.6 | Boot-completed auto-restart capability | ✅ | manifest permission |
| 8.7 | Wizard progress persistence | ✅ | `data/local/preferences/WizardPreference.kt` |

---

### 9. Platform, UX & cross-cutting

| # | Feature | Status | Source |
|---|---|---|---|
| 9.1 | Material 3 theming (color/type/theme) | ✅ | `presentation/common/theme/` |
| 9.2 | Reusable themed components (`MLBBButton`, `HeroGrid`, `HeroPortrait`, etc.) | ✅ | `presentation/common/components/` |
| 9.3 | Voice alerts (TextToSpeech) for turns | ✅ | `service/VoiceAlertService.kt` |
| 9.4 | Connectivity monitoring + banner | ✅ | `utils/NetworkMonitor.kt`, `common/components/ConnectivityBanner.kt` |
| 9.5 | Crash logging (file-backed, mutex-guarded) (TD-11) | ✅ | `data/local/crashlog/CrashLogStore.kt`, `AppLogTree.kt` |
| 9.6 | Timber structured logging | ✅ | throughout |
| 9.7 | Thread-safe date formatting (java.time only) | ✅ | `utils/DateFormatter.kt` |
| 9.8 | `NetworkResult` sealed class with functional fold helpers | ✅ | `utils/NetworkResult.kt` |
| 9.9 | Image loading via Coil 3 + OkHttp network backend | ✅ | `common/components/HeroPortrait.kt`, `HeroGrid.kt` |
| 9.10 | Localization: EN, FIL, ID, MS, TH, VI | ✅ | `res/values-*` |
| 9.11 | FileProvider for debug screenshot sharing | ✅ | `AndroidManifest.xml`, `res/xml/file_paths.xml` |
| 9.12 | R8/ProGuard release hardening | ✅ | `app/build.gradle.kts`, `proguard-rules.pro` |
| 9.13 | BuildConfig-driven API base URL (overridable per variant) | ✅ | `app/build.gradle.kts` |

---

### 10. Test coverage (implemented suites)

| Suite | Target | Status |
|---|---|---|
| `DraftScorerTest` | Scoring math & bounds | 🧪 |
| `PickSequenceEngineTest` | Turn-order correctness | 🧪 |
| `RankRuleEngineTest` | Ban structures per rank | 🧪 |
| `DraftSessionManagerTest` | State transitions & undo | 🧪 |
| `DraftSessionSerializationTest` | Session (de)serialization | 🧪 |
| `CompositionAnalyzerTest` | Comp/lane analysis | 🧪 |
| `BanRecommenderTest` | Ban suggestions | 🧪 |
| `PerceptualHashTest` | Hash matching | 🧪 |

> Coverage is concentrated in the pure-Kotlin domain. UI, capture orchestration,
> and service lifecycle are largely untested — see [`todo.md`](./todo.md) §4.

---

## LEGACY

### L1. Removed: companion-object database factory
The `AppDatabase.getInstance(context)` companion factory that previously lived
in `AppDatabase.kt` was removed. It called `fallbackToDestructiveMigration`
without migration objects, creating a divergent construction path that bypassed
`MIGRATION_1_2` and silently erased user data on version upgrade. All database
construction now flows exclusively through `DatabaseModule`.

### L2. Removed: TD-01 hardcoded CC name list
The original `CompositionAnalyzer` checked whether a hero had crowd-control
abilities by matching against a hardcoded list of hero names. This was replaced
by the `hasCCUlt: Boolean` flag on `HeroEntity` and `Hero` (schema migration
v2→v3), making CC detection data-driven and maintainable without code changes.

### L3. Superseded: static scoring thresholds
Prior to TD-05, `DraftScorer` used hardcoded constants (`winRate ≥ 0.48`,
`banRate ≥ 0.40`, `pickRate ≥ 0.30`) derived from a single patch snapshot.
These were replaced by `computeBounds()` — dataset-derived medians, half-IQR
scales, and 90th-percentile caps — in `versionCode 2`.

### L4. Removed: RetryInterceptor (OkHttp interceptor with Thread.sleep)
The `RetryInterceptor` OkHttp interceptor that blocked a thread-pool thread
during back-off was removed (P1-02). Retry logic moved to `HeroRepositoryImpl`
using coroutine `delay()`.
