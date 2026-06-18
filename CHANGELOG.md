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

## Round 5 — CI resource-linking failure
| File | Change | Reason |
|---|---|---|
| `res/values/themes.xml` | Change parent from `Theme.Material3.DayNight.NoActionBar` to `android:Theme.Material.Light.NoActionBar`; add dark `windowBackground` / status-bar / nav-bar colours | `Theme.Material3.DayNight.NoActionBar` is an XML style from the View-based `com.google.android.material:material` library, which is not in the project's dependencies. The project only has `androidx.compose.material3:material3` (Compose-side), which ships no XML themes. `android:Theme.Material.Light.NoActionBar` is built into the Android SDK (API 21+), requires no extra dependency, and is the correct base for a Compose-only app. Dark `windowBackground` (#0D0D14 = `SurfaceDark`) prevents the white system window from flashing before Compose renders. |


| File | Change | Reason |
|---|---|---|
| `libs.versions.toml` | Separate garbled `datastore-preferences` + `savedstate` lines | Malformed TOML prevented Gradle dependency resolution |
| `DraftScorerTest.kt` | Rewrite test class: use object directly, rename `computeScore`, fix `ScoreWeights` keys, add missing `Hero` constructor fields | Four independent compilation errors |
| `HeroDetailScreen.kt` | Add `@Composable` to `clickableNoRipple`; remove no-op `border(bottomWidth)` overload, implement real bottom-border via `drawBehind` | `remember{}` outside `@Composable` is a compilation error; tab indicator was never rendered |
| `DraftScreen.kt` | Fix `hiltViewModel` import → `androidx.hilt.navigation.compose` | Deprecated/removed package; previously noted in Round 2 but not applied |
| `HeroListScreen.kt` | Fix `hiltViewModel` import → `androidx.hilt.navigation.compose` | Same as above |
| `SettingsScreen.kt` | Fix `hiltViewModel` import → `androidx.hilt.navigation.compose` | Same as above |
| `HomeScreen.kt` | Fix `hiltViewModel` import → `androidx.hilt.navigation.compose`; stabilise `recentlyUsed` with `remember` | Import wrong; `shuffled()` in composition caused recomposition instability |
| `PermissionWizardScreen.kt` | Replace broken `.then(Modifier.let { clickable{}.let { _ -> it } })` with `.clickable {}` | `{ _ -> it }` discarded the clickable Modifier; wizard buttons were non-interactive |
| `FinalReportContent.kt` | Same clickable fix in `ActionButton` | Same broken pattern; New Draft / Close buttons were non-interactive |
| `DraftViewModel.kt` | Fix `ScoreWeights.normalized(meta, counter, synergy)` → use named args | counter and synergy weights were swapped |
| `PreferencesDataStore.kt` | Align key strings to match `SettingsViewModel` (`weight_meta` / `weight_counter` / `weight_synergy`) | Mismatched keys meant saved weights were never shared between the two classes |
| `ScoreWeights.kt` | Fix `require` to use `abs(sum - 1.0f) < 0.01f` | One-sided check allowed sum = 0.0 through |
| `MLBBAccessibilityService.kt` | Add `@Volatile` to `isMLBBForeground` | Shared mutable companion-object var accessed from multiple threads without synchronisation |
| `HeroDao.kt` | Replace string `ORDER BY tier ASC` with explicit `CASE` ordering | Alphabetical string sort placed S+ heroes last; correct order is S+ > S > A+ > A > B |
| `HeroGrid.kt` | Remove `.filter { it.id !in disabledIds \|\| true }` dead filter line | `\|\| true` made condition always true; graying is handled in `HeroGridCell` |
| `SettingsScreen.kt` | Remove unused `val ranks` | Allocated a list on every recomposition without ever using it |
| `BuildAdvisor.kt` | Remove three `Triple.component{1,2,3}()` operator extensions | Shadow identical stdlib members; cause "extension shadows member" warnings |
| `Screen.kt` | Delete file | Sealed class `Screen` is completely unreferenced; app uses `AppScreen` enum |
| `ScreenCaptureManager.kt` | Replace deprecated `defaultDisplay.getRealMetrics()` with `WindowManager.getCurrentWindowMetrics()` | APIs deprecated since API 30/31 |
| `RoleDashboard.kt` | Replace `Image + rememberAsyncImagePainter` with `AsyncImage` | Inconsistent with all other image loading in the project; simpler API |
