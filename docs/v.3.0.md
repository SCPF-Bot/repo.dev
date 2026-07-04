MLBB Draft Assistant V2.0: The Greenfield Overhaul Master Plan
Document Status: Genesis Document (V2.0)
Target Audience: Human Architects, Agentic AI Coding Assistants, Future Maintainers
Paradigm: Greenfield Rewrite, Multi-Module, AI-Native, Offline-First, Telemetry-Driven

## 1. Executive Summary & Vision

The V1.0 codebase successfully proved the concept of an autonomous, overlay-based MLBB draft assistant using `MediaProjection`, hardcoded `SlotRegions`, and a MobileNetV3Small + pHash fallback pipeline. However, V1.0 is fundamentally limited by its fragility to aspect-ratio changes, its single-module architecture, and its reliance on static, hardcoded meta-data.

V2.0 is a complete paradigm shift. We are moving from a reactive, coordinate-based script to a proactive, spatially-aware, AI-native companion. This master plan dictates the systematic destruction of the V1.0 monolith and the construction of a highly modular, Kotlin Multiplatform-ready, and perpetually self-improving architecture.

## 2. Core Strategic Pillars

### Pillar I: Computer Vision & Machine Learning (The "Eyes")

The V1.0 reliance on `SlotRegions` (hardcoded X/Y coordinates) and frame-by-frame analysis is obsolete.

- **Dynamic UI Element Detection (YOLOv8-Nano)**: Replace hardcoded coordinates with a lightweight, custom-trained YOLOv8-Nano object detection model. The model will dynamically identify bounding boxes for `[ban_slot, pick_slot, timer, phase_banner, enemy_hero, ally_hero]`. This makes the app 100% resolution, aspect-ratio, and UI-layout agnostic.
- **Synthetic Data Generation**: To eliminate the bottleneck of manual screenshot collection, implement a programmatic synthetic data generator that creates 2,000+ annotated training images by procedurally drawing UI elements with randomized jitter. This provides perfect annotations automatically and enables rapid model iteration.
- **Temporal Action Localization (Frame-Buffer Voting)**: MLBB features "hero reveal" animations that cause false positives in frame-by-frame CV. Implement a temporal buffer (e.g., a 15-frame sliding window) that requires a hero detection to achieve a >80% consensus over 0.5 seconds before locking it into the draft state.
- **Hardware-Accelerated Inference**: Mandate the use of TFLite GPU Delegates (or Hexagon DSP for Snapdragon) to drop inference latency from ~40ms to <5ms, eliminating battery drain and overlay stutter.

### Pillar II: Architecture & Modularity (The "Skeleton")

The V1.0 single `:app` module is a bottleneck for compile times, testing, and boundary enforcement.

- **"Now in Android" (NiA) Multi-Module Structure**:
  - `:app` (Entry point, DI wiring)
  - `:feature:overlay` (Floating UI, JetOverlay integration)
  - `:feature:draft` (Manual draft fallback, state machine UI)
  - `:feature:settings` (Calibration, safe-zones, telemetry toggles)
  - `:core:cv` (YOLO, TFLite, Temporal Buffer, MediaProjection)
  - `:core:scoring` (Draft math, weights, archetypes)
  - `:core:data` (Room, DataStore, Telemetry, Network)
  - `:core:designsystem` (Shared Compose UI, Material 3)
- **Kotlin Multiplatform (KMP) Extraction**: The `:core:scoring` and `:core:domain` logic contains zero Android dependencies. By formalizing this as a KMP module, we future-proof the drafting engine for iOS and Desktop companion apps.

### Pillar III: Data, Intelligence & Telemetry (The "Brain")

The V1.0 reliance on static JSON files and hardcoded counter-matrices is brittle.

- **Crowdsourced Telemetry Matrix**: Implement an anonymous, opt-in telemetry pipeline. When a user records a match outcome, the app uploads a lightweight payload: `{ pickedHero, enemyHeroes, rank, result }`. Aggregate this via a backend (e.g., Cloudflare Workers + D1) to generate a dynamic, community-driven counter matrix that updates weekly.
- **On-Device Small Language Model (SLM)**: Integrate a quantized SLM (e.g., Gemma 2B or Phi-3 via MediaPipe LLM Inference API) to generate natural language draft advice. Instead of "Ban Fanny", the SLM generates: "Ban Fanny. The enemy mid-laner has an 80% win-rate on her, and your team lacks hard CC to lock her down."

### Pillar IV: Next-Gen UX & Overlay (The "Interface")

The V1.0 overlay is static and visually intrusive.

- **Dynamic "Safe Zones"**: Implement a calibration mode where users can draw "dead zones" over their minimap, skill buttons, and chat. The Compose overlay will dynamically reflow and resize its cards to avoid these zones.
- **Haptic & Spatial Audio Feedback**: Utilize the device's vibration motor and spatial audio. If the enemy picks a hard counter to the user's main hero, trigger a distinct, heavy haptic pattern and a subtle 3D audio cue, allowing the user to receive critical intel without looking away from the game.

### Pillar V: Engineering Excellence (The "Immune System")

The V1.0 testing strategy was limited to pure-Kotlin domain unit tests.

- **Roborazzi Screenshot Testing**: Implement Roborazzi for Compose UI. Every overlay state (Ban Phase, Pick Phase, Trading) must have automated screenshot tests to catch visual regressions.
- **Baseline Profiles**: Generate Baseline Profiles for both Startup and Overlay Expansion to pre-compile critical paths, ensuring the overlay launches instantly without jank.
- **Strict Pre-Commit Hooks**: Enforce Ktlint, Detekt, and binary-compatibility-validator via pre-commit hooks. No messy code enters the repository.

## 3. Phased Execution Roadmap

**Instructions for Agentic AI**: Execute these phases sequentially. Do not begin Phase N+1 until Phase N is fully merged, tested, and verified.

### Phase 1: The Foundation (Modularization & KMP) ✅ COMPLETE (2026-07-04)

**Goal**: Dismantle the monolith and establish the NiA multi-module architecture.

- ✅ **Scaffold Modules**: Created `:core:scoring`, `:core:data`, `:core:cv`, `:core:designsystem`, `:feature:overlay`, `:feature:draft`, `:feature:settings`. Configured `settings.gradle.kts`, version catalog (`android-library` plugin alias), and root `build.gradle.kts`.
- ✅ **Extract Domain & Scoring**: Moved `DraftScorer`, `DraftSessionManager`, `CompositionAnalyzer`, all domain models, advisors, repository interfaces, and use cases into `:core:scoring`. Zero `android.*` imports in source.
- ✅ **Extract Data Layer**: Moved Room (`AppDatabase`, DAOs, Entities), DataStore (`AppDataStore`, `PreferencesDataStore`), Retrofit (`MetaApi`, `MetaSnapshotDto`), and repository implementations into `:core:data`. Repository pattern bridges `:core:data` → `:core:scoring` via `api()` dependency.
- ✅ **Extract CV Layer**: Moved `FrameProcessor`, `SlotRegions`, `HeroClassifier`, `PortraitMatcher`, and all CV components into `:core:cv`. Moved `ScreenCaptureManager` and `MLBBAccessibilityService` into `:core:cv`.
- ✅ **Wire DI**: All Hilt `@Module` classes remain in `:app` (composition root) providing cross-module bindings. See ADR-001 for rationale.

**Definition of Done**: `./gradlew build` passes. The app runs exactly as V1.0, but code is strictly separated.

**ADR**: See `docs/ADR-001-phase1-modularization.md` for architecture decisions and trade-offs.

### Phase 2: The CV Revolution (YOLO & Temporal) 🚧 IN PROGRESS

**Goal**: Eradicate hardcoded coordinates and animation false-positives.

- 🔄 **Synthetic Dataset Generation**: Implement `scripts/generate_synthetic_dataset.py` to procedurally generate 2,000+ annotated training images with randomized UI element positions, eliminating the need for manual screenshot collection. Dataset stored in `scripts/temp/{images,labels}`.
- 🔄 **YOLOv8-Nano Training**: Create `scripts/train.ui.py` to train YOLOv8-Nano on synthetic data with heavy augmentation (brightness, rotation, scale, jitter). Export to INT8 quantized TFLite format for mobile deployment.
- 🔄 **GitHub Actions Integration**: Update CI/CD workflow to automatically generate synthetic data and train YOLO model on every manual trigger. Artifacts uploaded for Android integration.
- ⏳ **Implement `DynamicRegionDetector`**: Replace `SlotRegions.kt` with a class that runs YOLO on the captured frame and outputs dynamic bounding boxes.
- ⏳ **Implement `TemporalConsensusBuffer`**: Create a sliding window that tracks hero detections across 15 frames. Only emit a "Hero Picked" event when the buffer reaches consensus.
- ⏳ **Enable GPU Delegate**: Wire the TFLite GPU delegate for both YOLO and the existing MobileNetV3 classifier.

**Definition of Done**: The app successfully detects heroes on a 21:9 ultrawide emulator and a 16:9 phone without changing a single line of configuration code. The YOLO model achieves >85% mAP on validation set.

**Dependencies**: 
- `scripts/requirements.txt` includes `ultralytics>=8.1.0`
- GitHub Actions workflow `train-tflite.yml` updated with synthetic data generation step
- Artifacts: `runs/detect/mlbb_yolo_v2/weights/best.tflite`

### Phase 3: Intelligence & Telemetry (SLM & Crowdsourcing)

**Goal**: Make the app smarter and self-improving.

- ⏳ **Telemetry Pipeline**: Implement an opt-in `TelemetryWorker` (WorkManager) that batches match outcomes and uploads them to a serverless backend.
- ⏳ **Dynamic Meta Sync**: Update `MetaApi` to fetch the crowdsourced counter-matrix from the backend, falling back to the bundled `default_heroes.json` if offline.
- ⏳ **SLM Integration**: Integrate MediaPipe LLM Inference API. Load a quantized Gemma 2B model.
- ⏳ **Context Prompting**: Create a `DraftPromptBuilder` that feeds the current draft state (allies, enemies, bans) into the SLM to generate a 1-sentence strategic insight.

**Definition of Done**: The overlay displays a natural-language insight generated on-device, and telemetry successfully uploads mock match data.

### Phase 4: Next-Gen UX (Safe Zones & Haptics)

**Goal**: Perfect the overlay experience.

- ⏳ **Safe Zone Calibration UI**: Build a Compose overlay that allows users to drag rectangles over their screen to define "dead zones". Save these as normalized coordinates in DataStore.
- ⏳ **Reflow Engine**: Implement a `SafeZoneModifier` in Compose that dynamically constrains the overlay's `Box` bounds to avoid the dead zones.
- ⏳ **Haptic Feedback Manager**: Create a `HapticAdvisor` that triggers specific `VibrationEffect` primitives based on draft events (e.g., `CLOCK_TICK` for ban timer, `HEAVY_CLICK` for hard counter detected).

**Definition of Done**: The overlay successfully avoids user-defined dead zones, and haptic feedback fires correctly during a simulated draft.

### Phase 5: Hardening & CI/CD

**Goal**: Enterprise-grade reliability.

- ⏳ **Roborazzi Setup**: Add Roborazzi to `:feature:overlay` and `:feature:draft`. Generate baseline screenshots for all draft phases.
- ⏳ **Baseline Profiles**: Use the Macrobenchmark library to generate `baseline-prof.txt` for the app's startup and overlay expansion paths.
- ⏳ **Pre-Commit Hooks**: Configure `lefthook` or `husky` to run Ktlint and Detekt before every commit.
- ⏳ **R8 Full Mode**: Enable R8 full mode and verify that all KMP, Room, and TFLite reflection/serialization works correctly.

**Definition of Done**: CI pipeline runs screenshot tests, and the release APK passes R8 full-mode minification.

## 4. Perpetual Operations (Day-2 Maintenance)

This section defines the perpetual maintenance cycle to ensure the app never degrades over time.

### 4.1. The Patch-Day SLA (Every 2-4 Weeks)

When Moonton releases a new MLBB patch:

1. **Hero Roster Update**: Run the `scripts/sync_heroes.py` script to scrape the official wiki for new heroes or reworks.
2. **Model Retraining (Hero Classifier)**: If new heroes are added, trigger the Colab notebook or GitHub Actions workflow to retrain the MobileNetV3 classifier and update `hero_classifier_labels.txt`.
3. **Synthetic Data Regeneration**: Run `scripts/generate_synthetic_dataset.py` with updated UI element coordinates if the draft UI layout has changed.
4. **YOLO Model Retraining**: Trigger GitHub Actions workflow `train-tflite.yml` to regenerate the YOLOv8 model with new synthetic data.
5. **Validation**: Run the YOLO model against 50 new screenshots of the updated patch to ensure UI changes haven't broken bounding box detection.

### 4.2. Telemetry Feedback Loop (Weekly)

1. **Aggregate Data**: The backend aggregates the weekly telemetry payloads.
2. **Calculate Win-Rates**: Compute the empirical win-rate of every hero matchup.
3. **Publish Snapshot**: Generate a new `meta_snapshot.json` and push it to the CDN.
4. **App Sync**: The app's `HeroSyncWorker` downloads the new snapshot, updating the `CounterLookupDao` with fresh, community-verified data.

### 4.3. Model Drift Monitoring (Monthly)

1. **Confidence Audits**: Analyze the structured logs (via Firebase Crashlytics/Custom Backend) to track the average confidence score of the YOLO and MobileNet models.
2. **Threshold Tuning**: If average confidence drops below 0.75, trigger a recalibration of the `PhaseDetectionConfig` thresholds or collect more training data for edge-case devices.
3. **Synthetic Data Calibration**: If YOLO detection fails on specific device aspect ratios, adjust the jitter parameters in `generate_synthetic_dataset.py` to better simulate those edge cases.

## 5. Agentic AI Directives (Rules of Engagement)

If you are an AI coding assistant reading this document, you must adhere to the following rules:

1. **No Monolithic Commits**: Never attempt to rewrite the entire app in one prompt. Ask the user to feed you one Phase at a time.
2. **Respect the Boundaries**: Never import `android.content.Context` or `androidx.*` into `:core:scoring`. If you need Android context, pass it via an interface defined in `:core:scoring` and implemented in `:core:data`.
3. **No Hardcoded Coordinates**: If you find yourself writing `x = 1080, y = 400`, stop. You must use the YOLO detection pipeline or normalized percentages.
4. **Offline-First Mandate**: Every feature must work without an internet connection. Network calls (Telemetry, Meta Sync) must be opportunistic and silent.
5. **Document as You Build**: When you complete a phase, update this `MASTER_PLAN.md` to check off the phase, and generate a specific `ADR-XXX.md` (Architecture Decision Record) for any major technical choices you made during implementation.
6. **Test the Edges**: When writing CV or Temporal Buffer code, always write unit tests that simulate edge cases: empty frames, completely black frames, and rapid consecutive frames.
7. **Synthetic-First Approach**: When training CV models, prioritize synthetic data generation over manual screenshot collection. Only collect real screenshots for validation and edge-case fine-tuning.
8. **CI/CD Integration**: All model training pipelines must be automated via GitHub Actions. Manual training is only for experimentation; production models must be reproducible via CI.

---

**End of Master Plan. Awaiting Phase 2 completion.**
