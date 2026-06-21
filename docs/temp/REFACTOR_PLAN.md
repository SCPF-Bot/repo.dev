# [PLAN] REFACTOR_PLAN.md — Bottom-Up Execution Checklist

_Execution order: Leaf → Domain → Data → DI → ViewModel → UI_
_All items marked [DONE] were verified by grep guardrail checks._

---

## Step 0 — Pre-requisites (Gradle / Resource cleanup)
- [DONE] **0-A** Removed unused legacy colors from `res/values/colors.xml` (`purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700`)

---

## Layer 1 — Domain Purity Fixes

- [DONE] **1-A** `domain/model/Hero.kt` — Removed `@Immutable` and `@Stable` Compose imports. Guardrail: `rg` returns 0 matches for those imports in the domain layer.

- [DONE] **1-B** `domain/scoring/DraftScorer.kt` — Removed `@Stable` from `HeroScore` data class; updated KDoc. Also fixed all four advisor files:
  - `domain/advisor/CompositionAnalyzer.kt` — removed `@Stable` + import from `CompositionProfile`
  - `domain/advisor/BanRecommender.kt` — removed `@Stable` + import from `BanSuggestion`
  - `domain/advisor/BuildAdvisor.kt` — removed `@Stable` + import from `EmblemRecommendation` and `BuildAdvice`
  - `domain/advisor/DraftScoreCalculator.kt` — removed `@Stable` + import from `FinalDraftScore`

---

## Layer 2 — Data Layer

- [DONE] **2-A** `domain/model/DraftHistoryItem.kt` — Added `yourPickIds: List<Int> = emptyList()` field so the domain model carries pick history without callers needing direct DAO access.
- [DONE] **2-B** `data/repository/DraftSessionRepositoryImpl.kt` — Updated `DraftSessionEntity.toDomain()` mapping to include `yourPickIds = yourPickIds`.

---

## Layer 3 — DI
[SKIP] — No hilt module changes required.

---

## Layer 4 — ViewModel Fixes

- [DONE] **4-A** `presentation/home/HomeViewModel.kt` — Replaced `DraftSessionDao` injection with `GetDraftHistoryUseCase`. `computeInsights` now accepts `List<DraftHistoryItem>` and uses the typed `outcome: DraftOutcome` field (no `DraftOutcome.fromString()` call needed).

- [DONE] **4-B** `presentation/settings/SettingsViewModel.kt` — Replaced `DraftSessionDao` injection with `GetDraftHistoryUseCase`. `runCalibration()` now calls `getDraftHistoryUseCase.all().first()` — eliminates the 13-line manual entity→domain mapping that was already handled by the repository.

- [DONE] **4-C** `presentation/draft/DraftViewModel.kt` — Removed `Dispatchers.IO` from `saveSession()`. `SaveDraftSessionUseCase` owns dispatcher selection. KDoc comment added explaining the delegation.

- [DONE] **4-D** `presentation/herolist/HeroListViewModel.kt` — Added `debounce(150L)` on `searchQueryFlow` and moved `applyFilters()` call to `withContext(Dispatchers.Default)` to prevent Main thread blocking on large hero lists. Role filter taps remain undebounced (immediate response expected).

---

## Layer 5 — UI / Presentation Fixes

- [DONE] **5-A** `presentation/common/theme/Theme.kt` — Set `dynamicColor = false` as the default. Extended KDoc explaining that the brand palette must not be overridden by Material You wallpaper colors, and documenting the user-toggle path if needed in future.

- [DONE] **5-B** `presentation/common/components/ConnectivityBanner.kt` — Removed two unused imports (`hiltViewModel`, `collectAsStateWithLifecycle`).

---

## Compilation Guardrail Results

| Batch | Check | Result |
|---|---|---|
| 1 | `rg @Stable/@Immutable in domain/` | ✅ 0 matches |
| 2 | `rg DraftSessionDao in presentation/home/settings/` | ✅ 0 matches |
| 3 | `rg launch(Dispatchers.IO) in presentation/` | ✅ 0 matches (OverlayService hits are Service-layer, not VMs — correct) |
| 4 | `rg dynamicColor =` in Theme.kt | ✅ `= false` |
| 4 | `rg hiltViewModel in ConnectivityBanner.kt` | ✅ 0 matches |
| All | `rg purple_200/teal_200 in res/` | ✅ 0 matches |

---

## Items Intentionally Left Out of Scope

| Item | Reason |
|---|---|
| Centralizing DataStore keys (V-10) | Keys already work correctly. Touching 3+ files atomically is medium risk for low gain. Flagged for dedicated PR. |
| Shared HeroListViewModel for MetaBoard (V-07) | Nav graph scoping change could break back-stack state restoration. Safer alternative is a dedicated `MetaBoardViewModel`. Not a correctness bug — follow-up. |
| Hardcoded strings → strings.xml (17 items) | Localization gap, not a bug. Adding strings requires updating all 6 locale files. Dedicated i18n PR. |
