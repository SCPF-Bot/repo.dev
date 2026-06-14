# MLBB Assistant — Complete Rebuild Changelog

## Round 1 — Bug fixes (pre-build)
| File | Change | Reason |
|---|---|---|
| `HeroListScreen.kt` | coil → coil3 import | Coil 3.x package rename |
| `OverlayService.kt` | startForeground type | API 34+ FOREGROUND_SERVICE_TYPE_SPECIAL_USE |
| `NetworkModule.kt` | Remove Gson FQN | Proper import statement |
| `build.gradle.kts` | Add debugImplementation compose-ui-tooling | TOML entry was unused |
| `proguard-rules.pro` | Add Gson TypeToken rules | Release build crash |
| `extract.yml` | Uncomment find command | Shell comments made step no-op |
| `build.yml` | Add SDK 36 install step | CI missing platform |

## Round 2 — CI failures
| File | Change | Reason |
|---|---|---|
| `build.gradle.kts` | excludes → pickFirsts | OkHttp/jspecify META-INF conflict |
| `build.gradle.kts` | configurations.all exclude jspecify | Belt-and-suspenders eviction |
| `DraftScreen.kt` | hiltViewModel import path | Deprecated package |
| `HeroListScreen.kt` | hiltViewModel import path | Deprecated package |
| `SettingsScreen.kt` | hiltViewModel import path | Deprecated package |

## Round 3 — Full feature rebuild
| Area | Files Created/Modified |
|---|---|
| **Domain models** | `Hero.kt`, `Lane`, `Tier`, `HeroRole`, `CoreItem` |
| **Data layer** | `HeroEntity.kt`, `Converters.kt`, `AppDatabase.kt` v2, `HeroDao.kt`, `DraftSessionEntity.kt`, `DraftSessionDao.kt`, `MetaSnapshotDto.kt`, `HeroRepositoryImpl.kt`, `PreferencesDataStore.kt` |
| **Core engines** | `RankRuleEngine.kt`, `PickSequenceEngine.kt`, `DraftSessionManager.kt` |
| **Screen capture** | `FrameProcessor.kt`, `PortraitMatcher.kt`, `PerceptualHash.kt`, `PhaseDetector.kt`, `SlotRegions.kt`, `ScreenCaptureManager.kt` |
| **Domain advisors** | `CompositionAnalyzer.kt`, `BanRecommender.kt`, `BuildAdvisor.kt`, `DraftScoreCalculator.kt`, `DraftScorer.kt`, `ScoreWeights.kt` |
| **Use cases** | `GetHeroesUseCase.kt`, `GetSuggestionsUseCase.kt`, `SyncHeroesUseCase.kt`, `ToggleOverlayUseCase.kt` |
| **Overlay system** | `OverlayService.kt`, `FloatingBubble.kt`, `DraftPanel.kt`, `BanPhaseContent.kt`, `PickPhaseContent.kt`, `TradingPhaseContent.kt`, `FinalReportContent.kt` |
| **Common UI** | `HeroPortrait.kt`, `HeroGrid.kt`, `RoleDashboard.kt`, `Color.kt`, `Theme.kt`, `Type.kt` |
| **Main app** | `MainActivity.kt`, `HomeScreen.kt`, `HeroListScreen.kt`, `HeroDetailScreen.kt`, `MetaBoardScreen.kt`, `DraftHistoryScreen.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `DraftScreen.kt`, `DraftViewModel.kt` |
| **Welcome** | `PermissionWizardScreen.kt` |
| **Services** | `MLBBAccessibilityService.kt`, `VoiceAlertService.kt` |
| **DI** | `AppModule.kt`, `DatabaseModule.kt`, `RepositoryModule.kt` |
| **Config** | `AndroidManifest.xml`, `libs.versions.toml`, `build.gradle.kts`, `proguard-rules.pro`, `strings.xml`, `accessibility_service_config.xml` |
| **Data** | `default_heroes.json` — 130 heroes, full schema |

**Total Kotlin files: 76**
**Total features implemented: 49**
