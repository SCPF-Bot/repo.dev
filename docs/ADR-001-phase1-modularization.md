# ADR-001: Phase 1 Multi-Module Architecture

**Status:** Accepted  
**Date:** 2026-07-04  
**Phase:** Phase 1 — The Foundation (Modularization & KMP)

---

## Context

The V1.0 codebase was a single `:app` monolith containing all domain logic, data access, computer-vision pipeline, and UI in one Gradle module. This caused:
- Long incremental compile times (entire project recompiled on any change)
- No enforced boundary between pure-Kotlin domain logic and Android-platform code
- Impossible to unit-test domain logic without Robolectric or instrumented tests
- Blocked KMP extraction of the scoring engine for future iOS/Desktop companions

## Decision

Introduce the "Now in Android" (NiA) multi-module architecture as specified in the v.3.0 master plan.

### Module Map

| Module | Kind | Contents | Key Constraint |
|---|---|---|---|
| `:core:scoring` | Android library | `domain/model`, `domain/scoring`, `domain/engine`, `domain/advisor`, `domain/repository` (interfaces), `domain/usecase` | Zero `android.*` imports in source files |
| `:core:data` | Android library | `data/local` (Room, DataStore), `data/remote` (Retrofit), `data/repository` (impl), `data/export` | Depends on `:core:scoring` via `api()` |
| `:core:cv` | Android library | `capture/` (FrameProcessor, SlotRegions, HeroClassifier, etc.), `service/` (ScreenCaptureManager, MLBBAccessibilityService) | TFLite assets stay in `:app` assets folder |
| `:core:designsystem` | Android library | `presentation/common/theme`, `presentation/common/components` | Re-exported via `api(project(":core:scoring"))` for component signatures |
| `:feature:overlay` | Android library | `presentation/overlay/` | Depends on `:core:cv` for OverlayCaptureCoordinator |
| `:feature:draft` | Android library | `presentation/draft/` | Depends on `:core:scoring` + `:core:designsystem` |
| `:feature:settings` | Android library | `presentation/settings/` | Depends on `:core:scoring` + `:core:data` |
| `:app` | Android application | `MLBBApplication`, DI modules, `presentation/navigation`, remaining screens | Composition root — depends on all modules |

### Hilt DI Strategy

All `@Module` classes remain in `:app` for Phase 1. This is valid because:
- `@InstallIn(SingletonComponent::class)` modules can provide bindings for types from any module
- Moving modules to their respective feature/core modules is a Phase 5 hardening concern
- Keeps the DI graph visible in one place during the transition

### Package Names

Source file package declarations are **unchanged** (`com.mlbb.assistant.*`). Files simply live in different Gradle modules. This avoids a rename sweep across all import statements and keeps the diff reviewable.

### AndroidManifest Strategy

The `:app` `AndroidManifest.xml` remains the single source of truth for all component declarations (Services, Activities, permissions). Library module manifests contain only the `package` namespace to avoid AGP manifest-merge conflicts.

## Consequences

**Positive:**
- `:core:scoring` is now boundary-enforced: Gradle will fail if any `android.*` import leaks in
- Feature modules compile independently; a change in `:feature:overlay` does not recompile `:feature:draft`
- Domain unit tests in `:core:scoring` no longer require Robolectric
- Clear foundation for Phase 2 (YOLO integration into `:core:cv`) and Phase 3 (telemetry into `:core:data`)

**Negative / Trade-offs:**
- `:core:scoring` is declared as an Android library (not a pure Kotlin library) because `HeroRepository` uses `androidx.paging.PagingData` and `DraftScorer` uses `@androidx.annotation.VisibleForTesting`. KMP extraction requires removing these two androidx dependencies — deferred to a future ADR.
- All DI modules remain in `:app` — cross-module Hilt scoping is correct but could be better organised.

## Next Steps

- Phase 2: YOLOv8-Nano integration into `:core:cv`, replacing `SlotRegions` with `DynamicRegionDetector`
- Future ADR: Extract `androidx.paging` dependency from `:core:scoring` to enable full KMP conversion
