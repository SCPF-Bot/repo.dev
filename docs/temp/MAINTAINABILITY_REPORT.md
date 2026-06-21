# MAINTAINABILITY REPORT
_Generated: 2026-06-21 | Phase 1 Step 5_

## Large Files (> 300 lines)

| File | Lines | Issue |
|---|---|---|
| `presentation/overlay/MiniWidget.kt` | 1,202 | Single Composable function file — needs sub-component extraction |
| `presentation/settings/SettingsScreen.kt` | 1,060 | Monolithic settings screen — needs section extraction |
| `presentation/overlay/OverlayService.kt` | 991 | Service + coroutine logic mixed — needs helper class extraction |
| `presentation/welcome/PermissionWizardScreen.kt` | 418 | Acceptable; wizard steps could be extracted to `components/` |
| `presentation/history/DraftReplayScreen.kt` | 335 | Acceptable |
| `presentation/herodetail/HeroDetailScreen.kt` | 312 | Acceptable |
| `presentation/home/HomeScreen.kt` | 302 | Acceptable |
| `presentation/log/LogScreen.kt` | 300 | Acceptable |
| `domain/scoring/DraftScorer.kt` | 283 | Acceptable for a pure-Kotlin scorer |

## Null Safety

| File | `!!` count | `lateinit` count | Assessment |
|---|---|---|---|
| `presentation/main/MainActivity.kt` | 1 | 1 | Low risk — Activity lifecycle |
| `capture/ScreenCaptureManager.kt` | 1 | 0 | Low risk |
| `presentation/history/DraftReplayScreen.kt` | 1 | 0 | Low risk |
| `presentation/overlay/OverlayService.kt` | 0 | 7 | ⚠️ `lateinit` overuse in Service — late-init fields should be `var?` with null checks or refactored with lazy delegation |

**Overall null-safety is excellent.** Only 3 `!!` usages across the entire codebase.

## Dead Code Detection

- No unused `object` singletons found.
- `GetSuggestionsUseCase` — injects no repository; logic may be partially redundant with `DraftViewModel`. **Flag for review.**
- `VoiceAlertService.kt` — no callers found via grep. **Likely dead code.** Needs confirmation before deletion.

## High Coupling (> 10 imports)

- `MiniWidget.kt` — ~60 imports (Compose + domain types). Expected for a large overlay composable. Splitting sub-components will reduce this naturally.
- `SettingsScreen.kt` — ~70 imports. Same pattern; extraction will resolve.

## Domain Purity

✅ **PASS** — No `android.*` imports found in `domain/` package Kotlin files.

Comments in `OverlayController.kt`, `SaveDraftSessionUseCase.kt`, `ToggleOverlayUseCase.kt` explicitly document this constraint. Architecture is clean.

## Threading / Coroutine Health

| Pattern | Count | Assessment |
|---|---|---|
| `GlobalScope` | 0 | ✅ None |
| `runBlocking` | 0 | ✅ None |
| `viewModelScope` | Multiple ViewModels | ✅ Correct |
| `lifecycleScope` | `OverlayService` | ✅ Correct for Service |

## SimpleDateFormat (Thread Safety)

`DateFormatter.kt` uses `SimpleDateFormat` which is not thread-safe. Since minSdk = 29 (> 26), this can be replaced with `java.time.format.DateTimeFormatter` for correctness and performance.

## Architecture Pattern Health

| Layer | Pattern | Status |
|---|---|---|
| UI | Jetpack Compose + `hiltViewModel()` | ✅ Modern |
| State | `StateFlow` / `collectAsStateWithLifecycle` | ✅ Recommended |
| ViewModels | `viewModelScope` + Hilt injection | ✅ Correct |
| Data | Room + Retrofit + DataStore | ✅ Modern |
| DI | Hilt 2.55 | ✅ Current |
| Async | Coroutines/Flow throughout | ✅ Correct |

No LiveData found — full StateFlow migration already complete.

## Priority Refactor Targets

1. **HIGH:** Split `MiniWidget.kt` (1202 lines) into sub-composable files
2. **HIGH:** Split `SettingsScreen.kt` (1060 lines) into section composables
3. **HIGH:** Extract coroutine/capture helpers from `OverlayService.kt` (991 lines)
4. **MEDIUM:** Replace `SimpleDateFormat` with `java.time` in `DateFormatter.kt`
5. **MEDIUM:** Audit `VoiceAlertService.kt` for dead code
6. **LOW:** Extract notification channel ID constant from `OverlayService.kt`

