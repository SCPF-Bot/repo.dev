# MLBB Draft Assistant

A real-time Mobile Legends: Bang Bang draft companion app — overlay-based, offline-first, CV-powered hero recommendations during live drafts.

## Run & Operate

- `./gradlew assembleDebug` — build debug APK
- `./gradlew build` — full build + tests  
- `./gradlew detekt` — static analysis  
- `./gradlew detektBaseline` — regenerate detekt baseline

## Stack

- **Language:** Kotlin 2.1.0 (AGP 9.2.1, compileSdk 37, minSdk 29)
- **UI:** Jetpack Compose (Material 3), Lottie
- **Architecture:** Multi-module MVVM + Clean Architecture, Hilt DI
- **CV:** TFLite (MobileNetV3Small), ML Kit Text Recognition, MediaProjection
- **DB:** Room + Drizzle migrations, DataStore Preferences
- **Network:** Retrofit + kotlinx.serialization, OkHttp
- **Background:** WorkManager (HeroSyncWorker, TelemetryWorker)
- **Debug:** Pluto (floating bubble), Timber

## Where things live

| Path | Module | Contents |
|---|---|---|
| `core/scoring/` | `:core:scoring` | Domain models, DraftScorer, DraftSessionManager, advisors, use cases |
| `core/data/` | `:core:data` | Room DB, DataStore, Retrofit, repository implementations |
| `core/cv/` | `:core:cv` | FrameProcessor, SlotRegions, HeroClassifier, ScreenCaptureManager |
| `core/designsystem/` | `:core:designsystem` | Compose theme, common components |
| `feature/overlay/` | `:feature:overlay` | Floating overlay UI, OverlayService |
| `feature/draft/` | `:feature:draft` | Manual draft screen, DraftViewModel |
| `feature/settings/` | `:feature:settings` | Settings screen, calibration UI |
| `app/` | `:app` | MLBBApplication, Hilt DI modules, navigation, remaining screens |
| `docs/v.3.0.md` | — | Master plan (Phase 1 ✅ complete) |
| `docs/ADR-001-*.md` | — | Architecture Decision Records |

## Architecture decisions

- **Phase 1 complete:** Monolith dismantled into 7 + 1 Gradle modules per NiA structure. See `docs/ADR-001-phase1-modularization.md`.
- **Package names unchanged:** Source files keep `com.mlbb.assistant.*` package names regardless of module. Avoids import rename sweep; Gradle boundaries enforce separation.
- **DI stays in `:app`:** All Hilt `@Module` classes remain in the composition root for Phase 1. Cross-module scoping is correct; per-module DI is a Phase 5 concern.
- **`:core:scoring` constraint:** Zero `android.*` imports. `androidx.paging` + `androidx.annotation` are permitted until KMP extraction (future ADR).
- **App manifest is single source of truth:** Library manifests contain only package namespace to avoid AGP merge conflicts.

## Product

Real-time overlay that reads the MLBB draft screen via `MediaProjection`, identifies hero portraits with a MobileNetV3Small TFLite classifier, and shows ranked hero recommendations scored by meta stats, synergy, and counter relationships.

## Roadmap

- ✅ **Phase 1** — Multi-module foundation (complete)
- 🔲 **Phase 2** — YOLOv8-Nano dynamic slot detection (replaces hardcoded `SlotRegions`)
- 🔲 **Phase 3** — Crowdsourced telemetry + on-device SLM (Gemma 2B)
- 🔲 **Phase 4** — Safe Zone calibration + haptic feedback
- 🔲 **Phase 5** — Roborazzi screenshot tests, Baseline Profiles, R8 full mode

## Gotchas

- Never run `./gradlew` commands that need `BUILD_TOOLS_VERSION` without an Android SDK installed.
- `TFLite` assets must be `noCompress` in AGP config — the Interpreter memory-maps the APK asset directly.
- AGP 9.x auto-applies `kotlin.android`; adding it explicitly causes a registration error.
- JImageHash uses `java.awt` (unavailable on Android) — loaded via reflection with `runCatching` for graceful fallback to dHash.
- Room schema files live in `app/schemas/` (app module KSP arg); `:core:data` has its own `schemas/` for new migrations post-Phase 1.
