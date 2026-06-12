# Changelog

| File Path | Change Type | Reason |
|---|---|---|
| `app/src/main/java/com/mlbb/assistant/presentation/herolist/HeroListScreen.kt` | MODIFY | Coil 3.x package changed from `coil` to `coil3`; was a compile error. |
| `app/src/main/java/com/mlbb/assistant/presentation/overlay/OverlayService.kt` | MODIFY | `startForeground` must pass `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+ (targetSdk=36). |
| `app/src/main/java/com/mlbb/assistant/di/NetworkModule.kt` | MODIFY | Replace FQN `com.google.gson.Gson` with proper import statement. |
| `app/build.gradle.kts` | MODIFY | Add missing `debugImplementation(libs.compose.ui.tooling)` declared in TOML but unused. |
| `app/proguard-rules.pro` | MODIFY | Add Gson TypeToken retention rules; missing rules caused release-build JSON parse failure. |
| `.github/workflows/extract.yml` | MODIFY | Uncomment `find` delete command; shell comments made the delete step a no-op. |
| `.github/workflows/build.yml` | MODIFY | Add Android SDK 36 license acceptance and platform installation before build step. |
