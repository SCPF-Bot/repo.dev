# [REVIEW] INVENTORY.md — Full Source File List

_Generated: Phase 1, MLBB Assistant — repo.dev_

---

## Kotlin Source Files — `app/src/main/java/`

### Application Root
- `MLBBApplication.kt` — @HiltAndroidApp Application class, Timber init
- `AppDataStore.kt` — Singleton DataStore extension property

### Capture Layer (`capture/`)
- `FirstPickDetector.kt`
- `FrameProcessor.kt` — MutableSharedFlow<FrameAnalysis>
- `PerceptualHash.kt`
- `PhaseDetectionConfig.kt`
- `PhaseDetector.kt`
- `PhaseOcrDetector.kt`
- `PortraitMatcher.kt`
- `RankDetector.kt`
- `SlotRegions.kt`

### Data Layer (`data/`)
- `data/export/DraftExporter.kt`
- `data/local/crashlog/AppLogTree.kt`
- `data/local/crashlog/CrashLogStore.kt`
- `data/local/database/AppDatabase.kt` — @Database, Room entities
- `data/local/database/Converters.kt`
- `data/local/database/DraftSessionDao.kt`
- `data/local/database/DraftSessionEntity.kt`
- `data/local/database/HeroDao.kt`
- `data/local/database/HeroEntity.kt`
- `data/local/database/HeroPoolDao.kt`
- `data/local/database/HeroPoolEntity.kt`
- `data/local/datastore/PreferencesDataStore.kt`
- `data/local/preferences/WizardPreference.kt`
- `data/remote/api/MetaApi.kt` — Retrofit interface
- `data/remote/dto/MetaSnapshotDto.kt`
- `data/repository/DraftSessionRepositoryImpl.kt`
- `data/repository/HeroRepositoryImpl.kt`

### DI Layer (`di/`)
- `di/AppModule.kt` — DataStore, DraftSessionManager, VoiceAlertService
- `di/DatabaseModule.kt` — Room DB + all DAOs
- `di/NetworkModule.kt` — OkHttp, Retrofit, Gson
- `di/OverlayModule.kt`
- `di/RepositoryModule.kt`

### Domain Layer (`domain/`)
- `domain/advisor/BanRecommender.kt`
- `domain/advisor/BuildAdvisor.kt`
- `domain/advisor/CompositionAnalyzer.kt`
- `domain/advisor/CompositionArchetype.kt`
- `domain/advisor/DraftScoreCalculator.kt`
- `domain/advisor/EnemyIntentAnalyzer.kt`
- `domain/advisor/WinConditionGenerator.kt`
- `domain/engine/DraftPatternAnalyzer.kt`
- `domain/engine/DraftSessionManager.kt` — StateFlow<DraftSession>
- `domain/engine/PickSequenceEngine.kt`
- `domain/engine/RankRuleEngine.kt`
- `domain/engine/WeightCalibrator.kt`
- `domain/model/DraftHistoryItem.kt`
- `domain/model/DraftOutcome.kt`
- `domain/model/Hero.kt` ⚠️ imports androidx.compose.runtime.Immutable/Stable
- `domain/model/Proficiency.kt`
- `domain/OverlayController.kt`
- `domain/repository/DraftSessionRepository.kt`
- `domain/repository/HeroRepository.kt`
- `domain/scoring/DraftScorer.kt` ⚠️ imports androidx.compose.runtime.Stable
- `domain/scoring/ScoreWeights.kt`
- `domain/usecase/GetDraftHistoryUseCase.kt`
- `domain/usecase/GetHeroesUseCase.kt`
- `domain/usecase/GetPagedHeroesUseCase.kt`
- `domain/usecase/GetSuggestionsUseCase.kt`
- `domain/usecase/SaveDraftSessionUseCase.kt`
- `domain/usecase/SyncHeroesUseCase.kt`
- `domain/usecase/ToggleOverlayUseCase.kt`

### Presentation Layer (`presentation/`)
- `presentation/common/components/BackButton.kt`
- `presentation/common/components/ConnectivityBanner.kt` ⚠️ unused imports
- `presentation/common/components/HeroGrid.kt`
- `presentation/common/components/HeroPortrait.kt`
- `presentation/common/components/LoadingSpinner.kt`
- `presentation/common/components/MLBBButton.kt`
- `presentation/common/components/MLBBTextField.kt`
- `presentation/common/components/RoleDashboard.kt`
- `presentation/common/theme/Color.kt`
- `presentation/common/theme/Theme.kt` ⚠️ dynamic color enabled by default (overrides brand)
- `presentation/common/theme/Type.kt`
- `presentation/draft/components/HeroChip.kt`
- `presentation/draft/components/SuggestionCard.kt`
- `presentation/draft/DraftScreen.kt`
- `presentation/draft/DraftState.kt`
- `presentation/draft/DraftViewModel.kt` ⚠️ Dispatchers.IO in saveSession (should be in UseCase)
- `presentation/draft/ScoreExplanationSheet.kt`
- `presentation/herodetail/HeroDetailScreen.kt`
- `presentation/herolist/HeroListScreen.kt`
- `presentation/herolist/HeroListState.kt`
- `presentation/herolist/HeroListViewModel.kt` ⚠️ filter applied synchronously on UI thread
- `presentation/heropool/HeroPoolScreen.kt`
- `presentation/heropool/HeroPoolViewModel.kt`
- `presentation/history/DraftHistoryScreen.kt`
- `presentation/history/DraftHistoryViewModel.kt`
- `presentation/history/DraftReplayScreen.kt`
- `presentation/home/HomeScreen.kt` ⚠️ several hardcoded UI strings not in strings.xml
- `presentation/home/HomeViewModel.kt` ⚠️ injects DraftSessionDao directly (bypasses repository)
- `presentation/log/LogScreen.kt`
- `presentation/log/LogViewModel.kt`
- `presentation/main/MainActivity.kt`
- `presentation/metaboard/MetaBoardScreen.kt`
- `presentation/navigation/AppNavGraph.kt` ⚠️ reuses HeroListViewModel for MetaBoard (loads heroes twice)
- `presentation/navigation/AppRoute.kt`
- `presentation/overlay/BanPhaseContent.kt`
- `presentation/overlay/DraftPanel.kt`
- `presentation/overlay/FinalReportContent.kt`
- `presentation/overlay/FloatingBubble.kt`
- `presentation/overlay/MiniWidget.kt`
- `presentation/overlay/OverlayPermissionActivity.kt`
- `presentation/overlay/OverlayService.kt`
- `presentation/overlay/PickPhaseContent.kt`
- `presentation/overlay/TradingPhaseContent.kt`
- `presentation/settings/SettingsScreen.kt`
- `presentation/settings/SettingsState.kt`
- `presentation/settings/SettingsViewModel.kt` ⚠️ injects DraftSessionDao directly (bypasses repository)
- `presentation/shell/AppShell.kt`
- `presentation/welcome/PermissionWizardScreen.kt`

### Services (`service/`)
- `service/MLBBAccessibilityService.kt`
- `service/ScreenCaptureManager.kt`
- `service/VoiceAlertService.kt`

### Utils (`utils/`)
- `utils/DateFormatter.kt`
- `utils/Extensions.kt`
- `utils/JsonParser.kt`
- `utils/NetworkMonitor.kt`
- `utils/NetworkResult.kt`

---

## Test Files — `app/src/test/`
- `capture/PerceptualHashTest.kt`
- `domain/advisor/BanRecommenderTest.kt`
- `domain/advisor/CompositionAnalyzerTest.kt`
- `domain/engine/DraftSessionManagerTest.kt`
- `domain/engine/DraftSessionSerializationTest.kt`
- `domain/engine/PickSequenceEngineTest.kt`
- `domain/engine/RankRuleEngineTest.kt`
- `domain/scoring/DraftScorerTest.kt`

---

## Resource Files — `app/src/main/res/`
- `values/strings.xml` — Primary English strings
- `values/colors.xml` ⚠️ legacy Material colors (purple_200, etc.) — unused
- `values/themes.xml` — XML window theme (Compose-only app, minimal)
- `values-fil/strings.xml` — Filipino localisation
- `values-in/strings.xml` — Indonesian localisation
- `values-ms/strings.xml` — Malay localisation
- `values-th/strings.xml` — Thai localisation
- `values-vi/strings.xml` — Vietnamese localisation
- `xml/accessibility_service_config.xml`
- `xml/file_paths.xml`
- `drawable/ic_launcher_background.xml`
- `drawable/ic_launcher_foreground.xml`
- `mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml`
- `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`
