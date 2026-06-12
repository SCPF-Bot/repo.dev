# Changelog

| File Path | Change Type | Reason |
|---|---|---|
| `gradle.properties` | MODIFY | Missing `#` comment prefix caused Gradle parse failure. |
| `gradle/wrapper/gradle-wrapper.properties` | MODIFY | `android.useAndroidX=true` does not belong in wrapper properties. |
| `gradle/libs.versions.toml` | MODIFY | Remove unused `work-runtime-ktx` and `ksp-symbol-processing-api` catalog entries. |
| `.github/workflows/build.yml` | MODIFY | Update `setup-gradle` action from deprecated `v3` to `v4`. |
| `app/src/main/java/com/mlbb/assistant/presentation/main/MainActivity.kt` | MODIFY | `enableEdgeToEdge()` must be called after `super.onCreate()`. |
| `app/src/main/java/com/mlbb/assistant/presentation/overlay/OverlayService.kt` | MODIFY | `ON_CREATE` lifecycle event must be dispatched after `super.onCreate()`. |
| `app/src/main/java/com/mlbb/assistant/data/local/database/Converters.kt` | MODIFY | `toIntList` can return null from Gson; add `?: emptyList()` guard. |
| `app/src/main/java/com/mlbb/assistant/presentation/herolist/HeroListScreen.kt` | MODIFY | Remove unused `import androidx.compose.material3.Snackbar`. |
| `app/src/main/java/com/mlbb/assistant/presentation/draft/DraftViewModel.kt` | MODIFY | Dispatch `updateSuggestions` to `Dispatchers.Default`; cancel stale jobs. |
| `app/src/main/res/drawable/ic_launcher_background.xml` | ADD | Launcher icon background required by adaptive icon; was missing. |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | ADD | Launcher icon foreground required by adaptive icon; was missing. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | ADD | Adaptive launcher icon for API 26+; `@mipmap/ic_launcher` was unresolved. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | ADD | Adaptive round launcher icon for API 26+; `@mipmap/ic_launcher_round` was unresolved. |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | ADD | Vector fallback launcher icon for API 24–25 (minSdk=24). |
| `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml` | ADD | Vector fallback round launcher icon for API 24–25 (minSdk=24). |
| `app/src/test/java/com/mlbb/assistant/domain/scoring/DraftScorerTest.kt` | ADD | Unit tests for `DraftScorer` covering all scoring branches. |
