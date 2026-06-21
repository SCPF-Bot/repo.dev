# REFACTOR PLAN — Bottom-Up Execution Checklist
_Generated: 2026-06-21 | Phase 2_

## Step 0: Pre-requisites
- [x] Dependencies are current (AGP 8.10.1, Kotlin 2.1.0, Compose BOM 2025.05.01, Hilt 2.55)
- [ ] No Gradle dependency changes required

## Step 0b: Duplicate Elimination (Pure Kotlin — no Android deps)
- [ ] **[RENAME]** `DraftScoreCalculator` → clarify naming boundary (no logic change) — `[DUPLICATE_ELIMINATION]`
- [ ] **[VERIFY_THEN_DELETE]** `mipmap-anydpi/` directory (duplicate of `mipmap-anydpi-v26/`)
- [ ] **[VERIFY_THEN_DELETE]** `colors.xml` vestigial black/white entries

## Step 1 — Data Asset Optimization (no code changes)
- [ ] **[MINIFY]** `res/raw/default_heroes.json` — strip whitespace → save ~35-40 KB
- [ ] **[MINIFY+STRIP]** `assets/draft_ui_map.json` — remove `_comment`/`_calibrated_from` keys, minify → save ~2 KB

## Step 2 — Domain Layer (Leaf Nodes First)
- [ ] **[REFACTOR]** `utils/DateFormatter.kt` — replace `SimpleDateFormat` with `java.time.DateTimeFormatter` (T-01)
- [ ] **[AUDIT]** `service/VoiceAlertService.kt` — confirm dead code → delete if zero callers
- [ ] **[EXTRACT]** `AppConstants.kt` — move hardcoded `"draft_overlay_channel"` string constant

## Step 3 — Presentation Layer (Largest Files)
- [ ] **[SPLIT]** `presentation/overlay/MiniWidget.kt` (1202 lines) → extract sub-composables into `presentation/overlay/components/`
  - `IdleContent.kt`
  - `HeaderBar.kt` 
  - `ScorePanelInline.kt`
  - `RecommendationList.kt`
- [ ] **[SPLIT]** `presentation/settings/SettingsScreen.kt` (1060 lines) → extract sections into `presentation/settings/components/`
  - `PermissionSection.kt`
  - `CalibrationSection.kt`
  - `WeightSliderSection.kt`
  - `ExportSection.kt`
- [ ] **[REFACTOR]** `presentation/overlay/OverlayService.kt` (991 lines) — extract capture lifecycle management into a helper

## Step 4 — Maintainability
- [ ] **[MANUAL_REVIEW]** `GetSuggestionsUseCase` — audit for redundancy with `DraftViewModel`
- [ ] **[MANUAL_REVIEW]** `isOP` / `isToxicMechanic` fields — confirm removal feasibility

## Execution Order Summary
```
Step 1 (data) → Step 2 (utils/domain) → Step 3 (presentation) → Step 4 (review items)
```

