# MLBB Draft Assistant

An AI-powered, **overlay-first** draft assistant for *Mobile Legends: Bang Bang*.
It renders an always-on floating overlay during the ranked draft phase, uses a
computer-vision pipeline (MediaProjection + perceptual hashing) to detect hero
picks and bans in real time, and surfaces **explainable** hero recommendations
scored by meta strength, synergy, and counter value — with a full manual
fallback when screen capture is unavailable.

- **Package:** `com.mlbb.assistant` · **Version:** 2.0.0 (`versionCode 2`)
- **Min SDK:** 29 (Android 10) · **Target/Compile SDK:** 36
- **Language:** Kotlin 2.1.0 · **UI:** Jetpack Compose + Material 3

---

## Documentation

| Doc | Purpose |
| --- | --- |
| [`docs/overview.md`](docs/overview.md) | Full architecture map, layer breakdown, data-flow scenarios, and design decisions |
| [`docs/features.md`](docs/features.md) | Catalogue of every implemented feature (large and small) |
| [`docs/roadmap.md`](docs/roadmap.md) | Phased plan, release history, and forward-looking ideas |
| [`docs/todo.md`](docs/todo.md) | Actionable backlog + the `TD-xx` technical-debt register |
| [`docs/MISSION.md`](docs/MISSION.md) | Product thesis, core beliefs, and long-term vision |

---

## Tech stack

- **Architecture:** Clean Architecture + MVI/UDF (Android-free `domain` layer)
- **DI:** Hilt 2.55
- **Persistence:** Room 2.7.1 (schema export + migrations) · DataStore Preferences 1.1.4
- **Networking:** Retrofit 2.11 + OkHttp 4.12 · Coil 3.1 for images
- **Async:** Kotlin Coroutines + Flow · Paging 3.3.6
- **Vision:** MediaProjection + ImageReader · perceptual hashing (dHash + histogram)
- **Logging:** Timber + file-backed `CrashLogStore`
- **Testing:** JUnit4, MockK, Turbine, coroutines-test, Robolectric, Compose UI test

Dependencies are managed centrally in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

---

## Project layout

```
app/src/main/java/com/mlbb/assistant/
├── domain/        # pure Kotlin: model, engine, scoring, advisor, usecase, repository
├── data/          # Room, DataStore, Retrofit, repositories, export, crashlog
├── capture/       # CV pipeline: FrameProcessor, PhaseDetector, PortraitMatcher, ...
├── service/       # ScreenCaptureManager, VoiceAlertService, AccessibilityService
├── presentation/  # Compose screens + ViewModels, overlay UI, navigation, theme
├── di/            # Hilt modules
└── utils/         # constants, date, json, network helpers
```

See [`docs/overview.md`](docs/overview.md) for the detailed map.

---

## Build & run

> Requires Android Studio (latest stable), JDK 17, and an Android device/emulator
> on API 29+. The overlay and screen-capture features require a **physical
> device** (MediaProjection is unreliable on emulators).

```bash
# from repo.dev/
./gradlew assembleDebug          # build the debug APK
./gradlew installDebug           # install on a connected device
./gradlew testDebugUnitTest      # run JVM unit tests (domain layer)
./gradlew lint                   # Android lint
```

The meta API base URL is provided via `BuildConfig.META_API_BASE_URL` and can be
overridden per build variant. When the network sync is unavailable, the app
seeds heroes from the bundled `res/raw/default_heroes.json`.

---

## First run

On first launch, the **Permission Wizard** walks through the permissions the
overlay needs, ordered least-to-most intrusive:

1. Draw over other apps (overlay)
2. Accessibility service (detect MLBB foreground)
3. Screen capture consent (autonomous detection)
4. Battery-optimisation exemption (keep the service alive)

Every autonomous capability has a manual equivalent, so the app remains fully
usable without granting screen capture.

---

## Contributing

1. Pick an item from [`docs/todo.md`](docs/todo.md).
2. Keep the dependency rule intact: `presentation → domain ← data`; never add
   `android.*` imports to `domain/`.
3. When you create technical debt, add a `TD-xx` tag at the fix site **and** a
   row in the `todo.md` register in the same commit.
4. Update [`docs/features.md`](docs/features.md) when a capability ships and flip
   the matching checkbox in [`docs/roadmap.md`](docs/roadmap.md).
