# [REVIEW] DEPENDENCY_GRAPH.md — Fan-In / Fan-Out Map

_Fan-In = how many other files import this file. Fan-Out = how many files this file imports._
_Files with Fan-In > 5 are architectural bottlenecks — refactor LAST._
_Leaf nodes (Fan-Out ≈ 0, Fan-In ≤ 1) — refactor FIRST._

---

## Critical Core Files (Fan-In ≥ 5) — Touch Last

| File | Fan-In (approx.) | Notes |
|---|---|---|
| `domain/model/Hero.kt` | ~20 | Used in almost every layer. Any change cascades everywhere. |
| `domain/engine/DraftSessionManager.kt` | ~8 | Shared singleton — DraftViewModel, OverlayService, DraftHistoryVM, etc. |
| `domain/usecase/GetHeroesUseCase.kt` | ~6 | Used by DraftVM, HeroListVM, HomeVM, OverlayService |
| `presentation/common/components/HeroPortrait.kt` | ~10 | Used by HeroGrid, DraftScreen, HomeScreen, overlay components |
| `presentation/common/theme/Color.kt` | ~15 | Imported directly in many screens (bypassing M3 colorScheme in places) |
| `domain/scoring/DraftScorer.kt` | ~5 | GetSuggestionsUseCase, OverlayService |
| `data/local/database/DraftSessionDao.kt` | ~4 | Used by repositories **AND** directly by HomeVM & SettingsVM ⚠️ |

---

## Leaf Nodes (Fan-Out ≈ 0) — Refactor First

| File | Fan-In | Fan-Out | Notes |
|---|---|---|---|
| `utils/NetworkResult.kt` | ~2 | 0 | Pure sealed class utility |
| `utils/DateFormatter.kt` | ~2 | 0 | Pure Kotlin utility |
| `domain/model/DraftOutcome.kt` | ~4 | 0 | Enum only |
| `domain/model/Proficiency.kt` | ~3 | 0 | Enum only |
| `domain/scoring/ScoreWeights.kt` | ~5 | 0 | Data class only |
| `capture/PhaseDetectionConfig.kt` | ~3 | 0 | Constants object |
| `capture/SlotRegions.kt` | ~2 | 0 | Data class only |
| `domain/advisor/CompositionArchetype.kt` | ~2 | 0 | Enum |

---

## Feature Dependency Map

### Feature: Draft Screen (`DraftScreen` / `DraftViewModel`)
Depends on: `GetHeroesUseCase` → `HeroRepository` → `HeroDao`
           `GetSuggestionsUseCase` → `DraftScorer`
           `SaveDraftSessionUseCase` → `DraftSessionRepository` → `DraftSessionDao`
           `DraftSessionManager` (singleton, shared)

### Feature: Home Screen (`HomeScreen` / `HomeViewModel`)
Depends on: `GetHeroesUseCase` → `HeroRepository` → `HeroDao`
           **`DraftSessionDao` (direct — VIOLATION)** ← should use `DraftSessionRepository`
           `InsightsState` (internal data class)

### Feature: Settings (`SettingsScreen` / `SettingsViewModel`)
Depends on: `DataStore<Preferences>`
           `SyncHeroesUseCase`
           **`DraftSessionDao` (direct — VIOLATION)** ← should use `DraftSessionRepository`
           `WeightCalibrator`
           `@ApplicationContext`

### Feature: Hero Explorer (`HeroListScreen` / `HeroListViewModel`)
Depends on: `GetHeroesUseCase`, `SyncHeroesUseCase`
Note: `HeroListViewModel` is ALSO used by `MetaBoardScreen` and `HeroDetailScreen`
      in `AppNavGraph` — each destination creates its own VM instance, so heroes
      are loaded independently per screen (potential double load).

### Feature: Overlay (`OverlayService`)
Depends on: `DraftSessionManager`, `GetHeroesUseCase`, `DataStore<Preferences>`,
            `ScreenCaptureManager`, `PortraitMatcher`, `PhaseDetector`, `BanRecommender`,
            `CompositionAnalyzer`, `DraftScorer`, `ScoreWeights`
Note: OverlayService is the most complex file. No ViewModel wraps it (correct for a Service).

### Feature: Draft History (`DraftHistoryScreen`)
Depends on: `GetDraftHistoryUseCase` → `DraftSessionRepository` → `DraftSessionDao`
            `DraftSessionManager`

---

## Architectural Violations

| # | Violation | Severity | Files Involved |
|---|---|---|---|
| V-01 | ViewModel injects DAO directly (bypasses repository) | High | `HomeViewModel` injects `DraftSessionDao` |
| V-02 | ViewModel injects DAO directly (bypasses repository) | High | `SettingsViewModel` injects `DraftSessionDao` |
| V-03 | Domain model imports Compose runtime annotations | Medium | `Hero.kt` imports `@Immutable`, `@Stable` |
| V-04 | Domain scorer imports Compose runtime annotation | Medium | `DraftScorer.kt` imports `@Stable` for `HeroScore` |
| V-05 | VM specifies Dispatchers.IO for use-case call | Low | `DraftViewModel.saveSession()` |
| V-06 | Unused imports in component | Low | `ConnectivityBanner.kt` — unused `hiltViewModel`, `collectAsStateWithLifecycle` |
| V-07 | Shared VM reused across unrelated screens | Medium | `HeroListViewModel` shared by MetaBoard + HeroDetail |
| V-08 | Dynamic color overrides brand palette on API 31+ | Medium | `Theme.kt` |
| V-09 | Unused XML colors (template leftovers) | Low | `colors.xml` |
| V-10 | DataStore preference keys duplicated across files | Low | `SettingsViewModel` keys vs `PreferencesDataStore` fields |
