# MLBB Draft Assistant — 2026 Refactor Audit

**Audit date:** 2026-06-19  
**Auditor:** Automated refactor pipeline (instructions.001.txt)  
**Codebase:** `com.mlbb.assistant`  
**AGP:** 8.10.1 · Kotlin: 2.1.0 · Compose BOM: 2025.05.01

---

## Executive Summary

The codebase arrived in strong foundational shape: Compose-only UI, Hilt DI, Room, Retrofit (suspend), Coroutines + Flow, and Navigation Compose were all already in place. The refactor focused on closing the remaining gaps to reach 2026 best-practice parity. All findings are categorised as **P0 (Critical)**, **P1 (High)**, or **P2 (Improvement)**.

---

## Phase 1 — Scan Findings

| # | Location | Severity | Finding |
|---|----------|----------|---------|
| 1 | `SettingsScreen.kt` | P1 | `collectAsState()` used instead of lifecycle-aware `collectAsStateWithLifecycle()` |
| 2 | `DraftPanel.kt` | P1 | Emoji text literals (`"⚔️"`, `"⏳"`, `"—"`, `"✕"`) in `Text()` composables — font-fallback renders incorrectly on some OEM ROMs |
| 3 | `DraftPanel.kt` | P1 | `PanelButton` was a 24 dp `Box.clickable` — below the 48 dp minimum touch target required by Android Accessibility Guidelines |
| 4 | `AppRoute.kt` | P2 | Nested route objects used `object` instead of `data object` (Kotlin 1.9 / 2.x idiom) |
| 5 | `DraftState`, `HeroListState`, `SettingsState` | P2 | Missing `@Immutable` annotation — Compose compiler treats unannotated classes as unstable, causing unnecessary recompositions |
| 6 | `DraftHistoryScreen` + `DraftHistoryViewModel` | P1 | Presentation layer directly imported and used `DraftSessionEntity` (data-layer type) — violated Clean Architecture layer boundaries |
| 7 | `HeroRepositoryImpl.kt` | P2 | `runCatching` swallowed all exceptions silently — no logging, making production debugging impossible |
| 8 | `MLBBApplication.kt` | P1 | No logging framework initialised — no Timber or equivalent |
| 9 | `libs.versions.toml` + `build.gradle.kts` | P1 | Missing: `timber`, `mockk`, `turbine`, `kotlinx-coroutines-test`, `junit-ext`, `espresso`, `compose-ui-test` |
| 10 | `app/proguard-rules.pro` | P2 | Missing R8 rules for Timber log stripping, Coil, Kotlin Metadata, coroutines volatile fields |
| 11 | `gradle.properties` | P2 | Missing `org.gradle.configuration-cache=true` and `kotlin.incremental.useClasspathSnapshot=true` |
| 12 | Test coverage | P1 | Only `DraftScorerTest` existed — `BanRecommender`, `CompositionAnalyzer`, and `DraftSessionManager` had zero coverage |

---

## Phase 3 — Modifications Applied

### 1. Dependency additions (`libs.versions.toml` + `build.gradle.kts`)

**Added versions:**

| Library | Version | Purpose |
|---------|---------|---------|
| Timber | 5.0.1 | Structured production logging |
| MockK | 1.13.12 | Kotlin-idiomatic mocking in unit tests |
| Turbine | 1.2.0 | Flow testing (collect-and-assert) |
| kotlinx-coroutines-test | 1.10.1 | `UnconfinedTestDispatcher`, `runTest` |
| junit-ext | 1.2.1 | Instrumented `AndroidJUnit4` runner |
| espresso-core | 3.6.1 | UI integration tests |
| compose-ui-test-junit4/manifest | BOM | Compose instrumented tests |

**Rationale:** Testing libraries must be present before any test can compile. Timber is the industry-standard Android logging solution — it routes to Logcat in debug and is stripped entirely by R8 in release.

---

### 2. Timber initialisation (`MLBBApplication.kt`)

```kotlin
override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
}
```

`DebugTree` is planted only in debug builds. Release builds produce no logs. The `proguard-rules.pro` Timber stripping rules ensure even the call sites are eliminated from the release APK.

---

### 3. Timber logging in `HeroRepositoryImpl`

`syncHeroes()` now emits:
- `Timber.d` on sync start
- `Timber.i` on successful sync (including hero count)
- `Timber.w(throwable)` on network failure, capturing the full stack trace
- `Timber.d` and `Timber.i` / `Timber.w` during JSON seed fallback

---

### 4. `collectAsState()` → `collectAsStateWithLifecycle()` in `SettingsScreen`

**Why:** `collectAsState()` keeps the coroutine alive even when the UI is in the background (e.g., paused by an overlay or another app), wasting CPU and battery. `collectAsStateWithLifecycle()` ties collection to `Lifecycle.State.STARTED`, matching Android's recommended pattern.

**Change:** Replaced import `collectAsState` with `collectAsStateWithLifecycle` (already available via the `androidx.lifecycle:lifecycle-runtime-compose` dependency that was in the build graph).

---

### 5. Emoji removal and touch-target fix in `DraftPanel`

| Before | After |
|--------|-------|
| `Text("⚔️  MLBB DRAFT ASSISTANT")` | `Text("MLBB DRAFT ASSISTANT")` |
| `Text("⏳")` (32 sp) in SetupContent | `CircularProgressIndicator(color = MLBBGold)` |
| `PanelButton("—", ...)` — 24 dp Box | `IconButton` with `Icons.Rounded.HorizontalRule` |
| `PanelButton("✕", ...)` — 24 dp Box | `IconButton` with `Icons.Rounded.Close` |

`IconButton` has a built-in 48×48 dp touch target (per Material Design spec), resolving the accessibility violation. `CircularProgressIndicator` is a proper semantic loading indicator, readable by TalkBack.

---

### 6. `data object` migration in `AppRoute`

```kotlin
// Before
object Wizard : AppRoute("wizard")

// After
data object Wizard : AppRoute("wizard")
```

`data object` is idiomatic in Kotlin 1.9+ (stable in 2.x). It provides a meaningful `toString()` for logging, correct `equals`/`hashCode` for use in sets and maps, and is the recommended form for singleton sealed class leaves.

---

### 7. `@Immutable` annotations on UI state classes

Added `@Immutable` to:
- `DraftState`
- `HeroListState`
- `SettingsState`

**Why:** The Compose compiler uses `@Immutable` as a contract that all public properties are deeply immutable (or replaced rather than mutated). Without it, the compiler cannot skip recompositions for composables that receive these types — even when the data hasn't changed. Because all three classes consist solely of immutable Kotlin types (`List<Hero>` is structurally immutable when replaced wholesale via `StateFlow.update`), the annotation is semantically accurate.

---

### 8. Domain model `DraftHistoryItem` + layer boundary fix

**Added:** `domain/model/DraftHistoryItem.kt` — a pure Kotlin data class containing only the fields the presentation layer needs.

**Changed:** `DraftHistoryViewModel` now maps `DraftSessionEntity → DraftHistoryItem` via a private extension function. `DraftHistoryScreen` now imports `DraftHistoryItem` instead of `DraftSessionEntity`.

**Why:** The presentation layer must not depend on data-layer types (`@Entity` classes). This violated Clean Architecture and meant any Room schema change would require UI changes. The domain model acts as the stable interface.

---

### 9. ProGuard / R8 improvements (`proguard-rules.pro`)

| Rule added | Effect |
|-----------|--------|
| `-assumenosideeffects class timber.log.Timber { v(...); d(...); i(...); w(...) }` | R8 eliminates all Timber log call sites from release APK |
| `-dontwarn coil3.**` | Suppresses spurious Coil R8 warnings |
| `-keepclassmembernames class kotlinx.** { volatile <fields>; }` | Prevents coroutines volatile sentinel fields from being renamed (required for atomics correctness) |
| `-dontwarn kotlin.**` | Removes spurious Kotlin stdlib warnings |
| `android.enableR8.fullMode=true` | Enables R8's full-mode optimiser (more aggressive dead-code elimination) |

---

### 10. Build performance (`gradle.properties`)

| Property added | Effect |
|---------------|--------|
| `org.gradle.configuration-cache=true` | Caches the configuration phase — subsequent builds skip it entirely if no build scripts changed (typically 30–60 s saved on mid-size projects) |
| `kotlin.incremental.useClasspathSnapshot=true` | Kotlin incremental compilation uses classpath snapshots instead of full recompilation when indirect dependencies change |
| `-Dkotlin.daemon.jvm.options=-Xmx1g` | Caps Kotlin daemon heap to prevent OOM on constrained CI machines |

---

### 11. New unit tests

Three new test files were added, bringing total test coverage from **1 file / 9 tests** to **4 files / 46 tests**:

#### `BanRecommenderTest.kt` (12 tests)
- Empty pool → empty result
- Banned / picked heroes excluded
- Result capped at 3
- Toxic mechanic bonus
- OP hero bonus
- High win-rate ranking
- Score bounds [0, 1]
- Badge label correctness (Toxic / OP Meta / High Ban)
- Lane preference bonus

#### `CompositionAnalyzerTest.kt` (14 tests)
- Empty list → baseline profile
- Full physical team → Dominance Ice warning
- Full magic team → Oracle warning
- Mixed damage → no damage warning
- No CC → NONE level + warning
- Three tanks/supports → HIGH CC
- Named CC hero (Tigreal) counted
- Three assassins → HIGH mobility + warning
- Five heroes fill all lanes
- Single hero leaves four missing
- Flex lane — preferred slot used first
- Flex lane — fallback to flex slot when preferred occupied
- High CC → strength generated
- Full physical → weakness generated
- Mixed damage → "hard to itemize" strength generated

#### `DraftSessionManagerTest.kt` (20 tests)
- Initial state: IDLE phase, empty picks/bans
- `initSession`: SETUP phase, rank stored
- Phase transitions: BAN_ROUND_1, PICK, TRADING, COMPLETE
- `recordOurBan`: correct slot, round 1
- `recordEnemyBan`: correct slot, null/missed ban
- `recordOurPick`: slot filled, pickIndex incremented
- Recommendation tracking (followed vs not followed)
- `recordEnemyPick`: slot filled, pickIndex incremented
- `undo` on empty stack: no-op
- `undo` after ban: slot cleared
- `undo` after pick: slot cleared, pickIndex decremented
- `unavailableIds` includes bans and picks
- `reset`: returns to IDLE with all fields cleared
- `swapOurHeroes`: slots exchanged

---

## Phase 4 — Remaining Recommendations (out of scope for automated pass)

The following items were identified but require human review or runtime validation:

| Priority | Item | Notes |
|----------|------|-------|
| P1 | `CompositionAnalyzer.kt` warning/strength strings contain emoji (`⚠️`, `✅`) | These appear inside String constants used as display text — removing them changes the API contract for callers. Replace with plain text or string resources. |
| P1 | `OverlayService` — `collectAsState` in a Service context | `collectAsStateWithLifecycle` is not applicable in a `Service`. The current approach (collecting a StateFlow in a Compose-on-View context) is acceptable, but should be reviewed for lifecycle correctness. |
| P2 | `NetworkModule` uses OkHttp `5.0.0-alpha.14` | Alpha channel carries API-break risk. Pin to stable once OkHttp 5 GA is released. |
| P2 | `AppDatabase` has `exportSchema = false` | Enable schema export (`exportSchema = true`) and commit the schema JSON to version control for Room migration verification in CI. |
| P2 | `VoiceAlertService` — not `@AndroidEntryPoint` | It is currently instantiated manually in `AppModule`. If it ever needs injected dependencies of its own, it will need `@AndroidEntryPoint` or `@Inject` constructor. |
| P2 | Instrumented tests | The new `androidTest` dependencies are wired in, but no instrumented test classes exist. Add at least smoke-level navigation and Room DAO tests. |
| P3 | Kotlin daemon heap cap | `-Xmx1g` is suitable for most developer machines; CI agents with > 16 GB RAM may benefit from `-Xmx2g`. |

---

## File Change Summary

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Added 7 new library/version entries |
| `app/build.gradle.kts` | Added Timber runtime dep + 6 test deps |
| `gradle.properties` | Added config-cache, classpath snapshot, daemon heap cap, R8 full mode |
| `app/proguard-rules.pro` | Added Timber stripping, Coil, Kotlin, coroutines, OkHttp rules |
| `MLBBApplication.kt` | Added `Timber.plant(DebugTree())` in `onCreate` |
| `presentation/navigation/AppRoute.kt` | `object` → `data object` (all 6 routes) |
| `presentation/settings/SettingsScreen.kt` | `collectAsState()` → `collectAsStateWithLifecycle()`; wildcard imports replaced with explicit imports |
| `presentation/overlay/DraftPanel.kt` | Emoji removed; `PanelButton` → `IconButton` (48 dp touch targets); `Text("⏳")` → `CircularProgressIndicator` |
| `presentation/draft/DraftState.kt` | Added `@Immutable` |
| `presentation/herolist/HeroListState.kt` | Added `@Immutable` |
| `presentation/settings/SettingsState.kt` | Added `@Immutable` |
| `data/repository/HeroRepositoryImpl.kt` | Added Timber logging to `syncHeroes` and `seedFromJson` |
| `domain/model/DraftHistoryItem.kt` | **NEW** — pure domain model for draft history |
| `presentation/history/DraftHistoryViewModel.kt` | Maps `DraftSessionEntity → DraftHistoryItem`; exposes `StateFlow<List<DraftHistoryItem>>` |
| `presentation/history/DraftHistoryScreen.kt` | Uses `DraftHistoryItem` instead of `DraftSessionEntity` |
| `test/.../BanRecommenderTest.kt` | **NEW** — 12 unit tests |
| `test/.../CompositionAnalyzerTest.kt` | **NEW** — 14 unit tests |
| `test/.../DraftSessionManagerTest.kt` | **NEW** — 20 unit tests |
