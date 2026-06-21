# DEPENDENCY GRAPH — Fan-In / Fan-Out Analysis
_Generated: 2026-06-21 | Phase 1 Step 7_

## Critical Core Files (Fan-In > 5) — Refactor LAST

| File | Fan-In | Notes |
|---|---|---|
| `domain/model/Hero.kt` | ~20 | Used by nearly every layer — scoring, advisors, UI, DB mappers |
| `domain/engine/DraftSessionManager.kt` | ~10 | `DraftSession` and `DraftAction` types consumed by overlay, draft screen, advisors |
| `domain/scoring/DraftScorer.kt` | ~8 | `HeroScore` consumed by overlay, draft screen, suggestion card |
| `domain/advisor/CompositionAnalyzer.kt` | ~6 | Used by MiniWidget, BanPhaseContent, DraftScorer |
| `presentation/common/theme/Color.kt` | ~25 | All overlay and screen composables import brand colors |
| `presentation/common/components/HeroPortrait.kt` | ~8 | Used by MiniWidget, HeroGrid, SuggestionCard, BanPhaseContent |

## Leaf Nodes (Fan-Out=0, Fan-In ≤ 1) — Refactor FIRST

| File | Fan-In | Notes |
|---|---|---|
| `utils/DateFormatter.kt` | ~3 | Pure utility — safe to refactor early |
| `utils/Extensions.kt` | ~5 | Pure utility — safe to refactor early |
| `utils/NetworkResult.kt` | ~4 | Simple sealed class — safe |
| `utils/JsonParser.kt` | 1 | Only called by `HeroRepositoryImpl` |
| `domain/model/DraftOutcome.kt` | ~3 | Simple enum |
| `domain/model/Proficiency.kt` | ~3 | Simple enum |
| `data/export/DraftExporter.kt` | 1 | Only called by export use case |
| `service/VoiceAlertService.kt` | 0 | **No callers found — dead code candidate** |

## Feature → File Dependency Map

| Feature | Primary Files |
|---|---|
| Hero draft overlay | `OverlayService`, `MiniWidget`, `DraftPanel`, `BanPhaseContent`, `PickPhaseContent`, `TradingPhaseContent`, `FinalReportContent`, `FloatingBubble` |
| Draft scoring & suggestions | `DraftScorer`, `DraftScoreCalculator`, `DraftSessionManager`, `GetSuggestionsUseCase`, `ScoreWeights` |
| Hero database | `HeroEntity`, `HeroDao`, `HeroRepositoryImpl`, `JsonParser`, `default_heroes.json`, `SyncHeroesUseCase` |
| Screen capture / OCR | `FrameProcessor`, `PhaseDetector`, `PhaseOcrDetector`, `PortraitMatcher`, `PerceptualHash`, `RankDetector`, `SlotRegions`, `draft_ui_map.json` |
| Settings & calibration | `SettingsScreen`, `SettingsViewModel`, `SettingsState`, `WeightCalibrator`, `PreferencesDataStore` |
| Draft history & replay | `DraftHistoryScreen`, `DraftReplayScreen`, `DraftHistoryViewModel`, `DraftSessionEntity`, `DraftSessionDao`, `DraftSessionRepositoryImpl` |
| Navigation | `AppNavGraph`, `AppRoute`, `AppShell` |

## Layer Dependency Order (Bottom-Up)

```
utils/              ← No dependencies (leaf)
domain/model/       ← Depends on: nothing (pure Kotlin)
domain/repository/  ← Depends on: domain/model
domain/usecase/     ← Depends on: domain/model, domain/repository
domain/scoring/     ← Depends on: domain/model
domain/advisor/     ← Depends on: domain/model, domain/scoring
domain/engine/      ← Depends on: domain/model, domain/advisor
data/remote/        ← Depends on: domain/model
data/local/         ← Depends on: domain/model
data/repository/    ← Depends on: domain/repository, data/local, data/remote
di/                 ← Depends on: all above
service/            ← Depends on: domain, data
presentation/       ← Depends on: domain, di, service
```

