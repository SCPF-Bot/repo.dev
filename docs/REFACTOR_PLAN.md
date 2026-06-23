# REFACTOR PLAN — Bottom-Up Execution Checklist
_Generated: 2026-06-21 | Phase 2 | Updated: 2026-06-23_

## Step 0: Pre-requisites
- [x] Dependencies are current (AGP 8.10.1, Kotlin 2.1.0, Compose BOM 2025.05.01, Hilt 2.55)
- [x] No Gradle dependency changes required

## Step 0b: Duplicate Elimination (Pure Kotlin — no Android deps)
- [ ] **[RENAME]** `DraftScoreCalculator` → clarify naming boundary (no logic change) — `[DUPLICATE_ELIMINATION]`
- [x] **[VERIFY_THEN_DELETE]** `mipmap-anydpi/` directory (duplicate of `mipmap-anydpi-v26/`) — deleted per REFACTOR_SUMMARY
- [ ] **[VERIFY_THEN_DELETE]** `colors.xml` vestigial black/white entries

## Step 1 — Data Asset Optimization (no code changes)
- [x] **[MINIFY]** `res/raw/default_heroes.json` — stripped whitespace (done per REFACTOR_SUMMARY)
- [x] **[MINIFY+STRIP]** `assets/draft_ui_map.json` — removed `_comment`/`_calibrated_from` keys, minified (done per REFACTOR_SUMMARY)

## Step 2 — Domain Layer (Leaf Nodes First)
- [x] **[REFACTOR]** `utils/DateFormatter.kt` — uses `java.time.DateTimeFormatter` (thread-safe, minSdk=29 ✅)
- [x] **[AUDIT]** `service/VoiceAlertService.kt` — confirmed live code; injected via `AppModule.provideVoiceAlertService()`, used in `MainActivity.voiceAlertService`. NOT dead code — do not delete.
- [x] **[EXTRACT]** `AppConstants.kt` — `OVERLAY_NOTIFICATION_CHANNEL_ID` and `OVERLAY_NOTIFICATION_ID` centralised

## Step 3 — Presentation Layer (Largest Files)
- [x] **[SPLIT]** `presentation/overlay/MiniWidget.kt` → extracted sub-composables into `presentation/overlay/components/` (`WidgetHeaderBar.kt`, `WidgetScorePanel.kt`)
- [x] **[SPLIT]** `presentation/settings/SettingsScreen.kt` → extracted into `presentation/settings/components/` (`ScreenMappingDialog.kt`, `SettingsPrimitives.kt`)
- [ ] **[REFACTOR]** `presentation/overlay/OverlayService.kt` — extract capture lifecycle management into a helper (still 991 lines; deferred to Phase 1 service hardening)

## Step 4 — Maintainability
- [x] **[MANUAL_REVIEW]** `GetSuggestionsUseCase` — clean; delegates to `DraftScorer.rankAll()` with correct parameters. No redundancy with `DraftViewModel`. No change needed.
- [ ] **[MANUAL_REVIEW]** `isOP` / `isToxicMechanic` fields — used in `BanRecommender` scoring; keep until Phase 2 ban-split feature lands

## Wire-Up Items (completed 2026-06-23)
- [x] **[WIRE]** `DraftHistoryViewModel` — refactored to inject `GetDraftHistoryUseCase` instead of `DraftSessionDao` directly; removes local `toDomain()` and fixes missing `yourPickIds` in history list
- [x] **[WIRE]** `SaveDraftSessionUseCase` — now populates `yourPickIds = ourPicks.map { it.id }` before saving
- [x] **[WIRE]** `DraftSessionRepositoryImpl.toEntity()` — `yourPickIds` now reads from `DraftHistoryItem.yourPickIds` (was hardcoded `emptyList()`)
- [x] **[WIRE]** `DraftState` — added `counterPickWarnings: List<String>` field
- [x] **[WIRE]** `DraftViewModel` — `refreshSuggestions()` now calls `CompositionAnalyzer.getCounterPickWarnings()` and pushes result into `DraftState.counterPickWarnings`
- [x] **[WIRE]** `AppShell` — `ConnectivityBanner` wired via new `AppShellViewModel` (injects `NetworkMonitor`, exposes `isOffline: StateFlow<Boolean>`)
- [x] **[FIX]** `SettingsViewModel.syncNow()` — replaced non-thread-safe `SimpleDateFormat` with `DateFormatter.formatFull()`

## Execution Order Summary
```
Step 1 (data) → Step 2 (utils/domain) → Step 3 (presentation) → Step 4 (review items) → Wire-Up
```

## Remaining Work (Phase 1+)
- OverlayService capture lifecycle extraction (large, deferred)
- `DraftScoreCalculator` rename for naming clarity
- `colors.xml` vestigial entry audit
- Service IO audit (TD-06): wrap DataStore/Room calls with `withContext(IO)` in OverlayService
- Settings weight validation (TD-09): `validate()` before constructing `ScoreWeights`
- Bubble position persistence to DataStore on drag (TD-12)
- All Phase 1+ items in TODO.md
