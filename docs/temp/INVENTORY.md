# INVENTORY.md — Full File Inventory
_Generated: 2026-06-21 | Phase 1 Step 1_

## Kotlin Source Files (`app/src/main/java/`)

### Root
- `MLBBApplication.kt`
- `AppDataStore.kt`

### capture/
- `FirstPickDetector.kt`
- `FrameProcessor.kt`
- `PerceptualHash.kt`
- `PhaseDetectionConfig.kt`
- `PhaseDetector.kt`
- `PhaseOcrDetector.kt`
- `PortraitMatcher.kt`
- `RankDetector.kt`
- `SlotRegions.kt`

### data/export/
- `DraftExporter.kt`

### data/local/crashlog/
- `AppLogTree.kt`
- `CrashLogStore.kt`

### data/local/database/
- `AppDatabase.kt`
- `Converters.kt`
- `DraftSessionDao.kt`
- `DraftSessionEntity.kt`
- `HeroDao.kt`
- `HeroEntity.kt`
- `HeroPoolDao.kt`
- `HeroPoolEntity.kt`

### data/local/datastore/
- `PreferencesDataStore.kt`

### data/local/preferences/
- `WizardPreference.kt`

### data/remote/api/
- `MetaApi.kt`

### data/remote/dto/
- `MetaSnapshotDto.kt` (contains `MetaSnapshotDto` + `HeroDto`)

### data/repository/
- `DraftSessionRepositoryImpl.kt`
- `HeroRepositoryImpl.kt`

### di/
- `AppModule.kt`
- `DatabaseModule.kt`
- `NetworkModule.kt`
- `OverlayModule.kt`
- `RepositoryModule.kt`

### domain/advisor/
- `BanRecommender.kt`
- `BuildAdvisor.kt`
- `CompositionAnalyzer.kt`
- `CompositionArchetype.kt`
- `DraftScoreCalculator.kt`
- `EnemyIntentAnalyzer.kt`
- `WinConditionGenerator.kt`

### domain/engine/
- `DraftPatternAnalyzer.kt`
- `DraftSessionManager.kt`
- `PickSequenceEngine.kt`
- `RankRuleEngine.kt`
- `WeightCalibrator.kt`

### domain/model/
- `DraftHistoryItem.kt`
- `DraftOutcome.kt`
- `Hero.kt` (contains `Hero`, `CoreItem`, `Lane`, `Tier`)
- `Proficiency.kt`

### domain/
- `OverlayController.kt`

### domain/repository/
- `DraftSessionRepository.kt`
- `HeroRepository.kt`

### domain/scoring/
- `DraftScorer.kt` (contains `HeroScore`, `ScoreBounds`, `DraftScorer`)
- `ScoreWeights.kt`

### domain/usecase/
- `GetDraftHistoryUseCase.kt`
- `GetHeroesUseCase.kt`
- `GetPagedHeroesUseCase.kt`
- `GetSuggestionsUseCase.kt`
- `SaveDraftSessionUseCase.kt`
- `SyncHeroesUseCase.kt`
- `ToggleOverlayUseCase.kt`

### presentation/common/components/
- `BackButton.kt`
- `ConnectivityBanner.kt`
- `HeroGrid.kt`
- `HeroPortrait.kt`
- `LoadingSpinner.kt`
- `MLBBButton.kt`
- `MLBBTextField.kt`
- `RoleDashboard.kt`

### presentation/common/theme/
- `Color.kt`
- `Theme.kt`
- `Type.kt`

### presentation/draft/
- `DraftScreen.kt`
- `DraftState.kt`
- `DraftViewModel.kt`
- `ScoreExplanationSheet.kt`

### presentation/draft/components/
- `HeroChip.kt`
- `SuggestionCard.kt`

### presentation/herodetail/
- `HeroDetailScreen.kt`

### presentation/herolist/
- `HeroListScreen.kt`
- `HeroListState.kt`
- `HeroListViewModel.kt`

### presentation/heropool/
- `HeroPoolScreen.kt`
- `HeroPoolViewModel.kt`

### presentation/history/
- `DraftHistoryScreen.kt`
- `DraftHistoryViewModel.kt`
- `DraftReplayScreen.kt`

### presentation/home/
- `HomeScreen.kt`
- `HomeViewModel.kt`

### presentation/log/
- `LogScreen.kt`
- `LogViewModel.kt`

### presentation/main/
- `MainActivity.kt`

### presentation/metaboard/
- `MetaBoardScreen.kt`

### presentation/navigation/
- `AppNavGraph.kt`
- `AppRoute.kt`

### presentation/overlay/
- `BanPhaseContent.kt`
- `DraftPanel.kt`
- `FinalReportContent.kt`
- `FloatingBubble.kt`
- `MiniWidget.kt` ⚠️ 1202 lines
- `OverlayPermissionActivity.kt`
- `OverlayService.kt` ⚠️ 991 lines
- `PickPhaseContent.kt`
- `TradingPhaseContent.kt`

### presentation/settings/
- `SettingsScreen.kt` ⚠️ 1060 lines
- `SettingsState.kt`
- `SettingsViewModel.kt`

### presentation/shell/
- `AppShell.kt`

### presentation/welcome/
- `PermissionWizardScreen.kt` ⚠️ 418 lines

### service/
- `MLBBAccessibilityService.kt`
- `ScreenCaptureManager.kt`
- `VoiceAlertService.kt`

### utils/
- `DateFormatter.kt`
- `Extensions.kt`
- `JsonParser.kt`
- `NetworkMonitor.kt`
- `NetworkResult.kt`

## Test Files (`app/src/test/`)
- `capture/PerceptualHashTest.kt`
- `domain/advisor/BanRecommenderTest.kt`
- `domain/advisor/CompositionAnalyzerTest.kt`
- `domain/engine/DraftSessionManagerTest.kt`
- `domain/engine/DraftSessionSerializationTest.kt`
- `domain/engine/PickSequenceEngineTest.kt`
- `domain/engine/RankRuleEngineTest.kt`
- `domain/scoring/DraftScorerTest.kt`

## Resource Files (`app/src/main/res/`)

### values/
- `colors.xml` (2 entries: black, white — theme colors live in Kotlin Color.kt)
- `strings.xml` (~80 string entries, fully externalized)
- `themes.xml`

### values-*/
- `values-fil/strings.xml` — Filipino
- `values-in/strings.xml` — Indonesian
- `values-ms/strings.xml` — Malay
- `values-th/strings.xml` — Thai
- `values-vi/strings.xml` — Vietnamese

### drawable/
- `ic_launcher_background.xml`
- `ic_launcher_foreground.xml`

### mipmap-anydpi/, mipmap-anydpi-v26/
- `ic_launcher.xml`
- `ic_launcher_round.xml`

### raw/
- `default_heroes.json` (116 KB, 6377 lines, pretty-printed ⚠️)

### xml/
- `accessibility_service_config.xml`
- `file_paths.xml`

## Asset Files (`app/src/main/assets/`)
- `draft_ui_map.json` (9.9 KB, pretty-printed with `_comment` keys ⚠️)

## Build Files
- `build.gradle.kts` (root)
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `settings.gradle.kts`

---
**Total Kotlin source files:** ~95  
**Total resource files:** ~20  
**Total data assets:** 2  
