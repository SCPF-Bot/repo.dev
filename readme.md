MLBB_Assistant_Refactored/
├── .github/
│   └── workflows/
│       └── build.yml
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── gradlew
├── gradlew.bat
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/mlbb/assistant/
        │   ├── MLBBApplication.kt
        │   ├── di/
        │   │   ├── AppModule.kt
        │   │   ├── DatabaseModule.kt
        │   │   ├── NetworkModule.kt
        │   │   └── RepositoryModule.kt
        │   ├── data/
        │   │   ├── local/
        │   │   │   ├── database/
        │   │   │   │   ├── AppDatabase.kt
        │   │   │   │   ├── Converters.kt
        │   │   │   │   ├── HeroDao.kt
        │   │   │   │   └── HeroEntity.kt
        │   │   │   └── datastore/
        │   │   │       └── PreferencesDataStore.kt
        │   │   ├── remote/
        │   │   │   ├── api/
        │   │   │   │   ├── MetaApi.kt
        │   │   │   │   └── MockApi.kt
        │   │   │   ├── dto/
        │   │   │   │   └── MetaSnapshotDto.kt
        │   │   │   └── NetworkResult.kt
        │   │   └── repository/
        │   │       ├── HeroRepository.kt
        │   │       └── HeroRepositoryImpl.kt
        │   ├── domain/
        │   │   ├── model/
        │   │   │   └── Hero.kt
        │   │   ├── scoring/
        │   │   │   ├── DraftScorer.kt
        │   │   │   └── ScoreWeights.kt
        │   │   └── usecase/
        │   │       ├── GetHeroesUseCase.kt
        │   │       ├── GetSuggestionsUseCase.kt
        │   │       ├── SyncHeroesUseCase.kt
        │   │       └── ToggleOverlayUseCase.kt
        │   ├── presentation/
        │   │   ├── common/
        │   │   │   ├── components/
        │   │   │   │   ├── LoadingSpinner.kt
        │   │   │   │   ├── MLBBButton.kt
        │   │   │   │   └── MLBBTextField.kt
        │   │   │   ├── theme/
        │   │   │   │   ├── Color.kt
        │   │   │   │   ├── Theme.kt
        │   │   │   │   └── Type.kt
        │   │   │   └── utils/
        │   │   │       └── Screen.kt
        │   │   ├── main/
        │   │   │   ├── MainActivity.kt
        │   │   │   └── MainViewModel.kt
        │   │   ├── herolist/
        │   │   │   ├── HeroListScreen.kt
        │   │   │   ├── HeroListState.kt
        │   │   │   └── HeroListViewModel.kt
        │   │   ├── draft/
        │   │   │   ├── DraftScreen.kt
        │   │   │   ├── DraftState.kt
        │   │   │   ├── DraftViewModel.kt
        │   │   │   └── components/
        │   │   │       ├── HeroChip.kt
        │   │   │       └── SuggestionCard.kt
        │   │   ├── settings/
        │   │   │   ├── SettingsScreen.kt
        │   │   │   ├── SettingsState.kt
        │   │   │   └── SettingsViewModel.kt
        │   │   └── overlay/
        │   │       ├── OverlayPermissionActivity.kt
        │   │       └── OverlayService.kt
        │   └── utils/
        │       ├── Extensions.kt
        │       ├── JsonParser.kt
        │       └── NetworkMonitor.kt
        └── res/
            ├── values/
            │   ├── colors.xml
            │   ├── strings.xml
            │   └── themes.xml
            └── raw/
                └── default_heroes.json