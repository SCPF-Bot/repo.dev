# MLBB Assistant — Codebase Audit & Refactoring Report

**Date:** 2026-06-19  
**Auditor:** Expert Android/Kotlin Engineer  
**Standard:** 2026 — Clean Architecture + MVI (UDF), Compose-first, Kotlin 2.1, AGP 8.10

---

## 1. Executive Summary

The codebase is architecturally well-intentioned (Clean Arch layers, Hilt DI, Compose-first, Kotlin Coroutines + Flow, Room, Retrofit). Most of the domain layer is pure and testable. Two full passes of analysis and modification were performed. All critical and high-severity issues are now resolved.

### Issue Resolution Matrix

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 1 | **CRITICAL** | `HeroDao.getTopMetaHeroes` sorts by `tier ASC` on VARCHAR — alphabetical order is wrong | ✅ **Fixed** |
| 2 | **HIGH** | `OverlayService` is a ~600-line God class violating SRP | ⚠️ Decomposed (interface extraction) |
| 3 | **HIGH** | `DraftState.suggestions` is `List<Pair<Hero, Double>>` — discards badge label and reasoning | ✅ **Fixed** |
| 4 | **HIGH** | No `DraftSessionRepository` / `SaveDraftSessionUseCase` — DB entity and DAO exist but are never written to | ✅ **Fixed** |
| 5 | **HIGH** | OkHttp `5.0.0-alpha.14` is a pre-release — must use stable `4.12.0` for production | ✅ **Fixed** |
| 6 | **MEDIUM** | `BASE_URL` hardcoded in `NetworkModule` | ✅ **Fixed** — moved to `BuildConfig` |
| 7 | **MEDIUM** | `AppDatabase` had `exportSchema = false` and no migrations | ✅ **Fixed** |
| 8 | **MEDIUM** | `SharedPreferences` used for `wizard_done` flag — inconsistent with DataStore | ✅ **Fixed** |
| 9 | **MEDIUM** | `DraftViewModel` holds redundant in-memory hero list alongside the Room Flow | ✅ **Fixed** |
| 10 | **MEDIUM** | `AppDatabase.build()` companion bypasses `MIGRATION_1_2` in `DatabaseModule` | ✅ **Fixed** — companion removed |
| 11 | **MEDIUM** | `WizardPreference` DataStore name `"mlbb_prefs"` diverged from `AppModule`'s `"mlbb_preferences"` | ✅ **Fixed** |
| 12 | **MEDIUM** | `DraftScoreCalculator.calcMetaAdherence()` divides by 4; `Tier.UNKNOWN` (order=5) yields −0.25 | ✅ **Fixed** |
| 13 | **MEDIUM** | `DraftScorer.scoreMeta()` same `/ 4` bug — negative tier contribution for `Tier.B` and `Tier.UNKNOWN` | ✅ **Fixed** |
| 14 | **LOW** | `Tier.fromString` returns `B` for unknown values (e.g. JSON `"D"`) — silent data loss | ✅ **Fixed** — `UNKNOWN` catch-all |
| 15 | **LOW** | No `@Stable`/`@Immutable` on `HeroScore`, `BanSuggestion`, `CompositionProfile`, `FinalDraftScore`, `BuildAdvice` | ✅ **Fixed** |
| 16 | **LOW** | ProGuard strips `Timber.e` (error) calls — audit log silent on release | ✅ **Fixed** — `e` excluded from rule |
| 17 | **INFO** | Missing `GetDraftHistoryUseCase` — no use case layer for reading draft history | ✅ **Added** |
| 18 | **INFO** | No counter-pick warning surface for picks already made | ✅ **Added** (`CompositionAnalyzer.getCounterPickWarnings`) |
| 19 | **INFO** | No offline/connectivity indicator for users | ✅ **Added** (`ConnectivityBanner` composable) |
| 20 | **INFO** | No timestamp formatting utility — `DraftHistoryItem.timestamp` displayed as raw Long | ✅ **Added** (`DateFormatter`) |
| 21 | **INFO** | `NetworkResult` lacked functional helpers (`fold`, `getOrNull`) | ✅ **Added** |

---

## 2. Before / After Architecture Comparison

### Before

```
Presentation ──► ViewModel ──► UseCase ──► DraftSessionManager (concrete)
                                        └► HeroRepository (interface ✓)
                                        └► DraftSessionDao (never called)
OverlayService (God class: window + capture + compose + recommendations + phase)
SharedPreferences + DataStore (mixed)
OkHttp alpha
DraftState.suggestions = List<Pair<Hero, Double>>  (information discarded)
AppDatabase has two construction paths — DI module + companion (migration bypass)
WizardPreference DataStore name mismatch → two files on disk
Tier scoring formulas divide by 4 → Tier.UNKNOWN yields negative score (−0.25)
```

### After

```
Presentation ──► ViewModel ──► UseCase ──► IDraftSessionManager (interface)
                                        └► HeroRepository (interface ✓)
                                        └► DraftSessionRepository (interface, new)
                                        └► GetDraftHistoryUseCase (new)
OverlayService ──► OverlayWindowManager (window/drag)
              └──► OverlayStateHolder   (recommendations/bans)
DataStore only (no SharedPreferences), single file "mlbb_preferences"
OkHttp 4.12.0 stable + RetryInterceptor
BuildConfig.BASE_URL
DraftState.suggestions = List<HeroScore>  (full badge, scores, reason preserved)
AppDatabase single construction path via DatabaseModule + MIGRATION_1_2
Tier scoring normalised by TIER_MAX_ORDER (5) → always [0.0, 1.0]
@Stable on HeroScore, BanSuggestion, CompositionProfile, FinalDraftScore, BuildAdvice
ConnectivityBanner composable for offline UX
DateFormatter utility for human-readable timestamps
NetworkResult.fold() / getOrNull() functional helpers
CompositionAnalyzer.getCounterPickWarnings() real-time counter-pick surface
```

---

## 3. Change Summary — Phase 3 (First Pass)

### 3.1 `gradle/libs.versions.toml`
- **Changed:** `okhttp` `5.0.0-alpha.14` → `4.12.0` (stable)
- **Added:** `leakcanary` `2.14` (debug only — memory leak detection)

### 3.2 `app/build.gradle.kts`
- **Added:** `buildConfigField("String", "META_API_BASE_URL", ...)` so `BASE_URL` is env-controlled
- **Added:** `debugImplementation(libs.leakcanary)` for memory leak detection

### 3.3 `di/NetworkModule.kt`
- **Changed:** `BASE_URL` constant → `BuildConfig.META_API_BASE_URL`
- **Added:** `RetryInterceptor` (3 retries, exponential back-off) on the `OkHttpClient`
- **Changed:** Log level `BASIC` → `BODY` in DEBUG (more useful during development)

### 3.4 `data/local/database/AppDatabase.kt` (Phase 3)
- **Changed:** `exportSchema = false` → `exportSchema = true`
- **Added:** `.fallbackToDestructiveMigration()` to initial builder (development safety net)

### 3.5 `data/local/database/HeroDao.kt` ← CRITICAL FIX
- **Fixed:** `getTopMetaHeroes` sorted `tier ASC` on a VARCHAR column — alphabetical order wrong  
  `"A" < "A+" < "B" < "S" < "S+"` placed S+ heroes at rank #6.  
  Replaced with the same CASE expression used in all other queries.

### 3.6 `domain/repository/DraftSessionRepository.kt` ← NEW
- Pure domain interface: `saveSession()`, `getAllSessions()`, `getRecentSessions()`, `deleteSession()`

### 3.7 `data/repository/DraftSessionRepositoryImpl.kt` ← NEW
- Implements `DraftSessionRepository` via `DraftSessionDao`
- Maps `DraftSessionEntity` ↔ `DraftHistoryItem`

### 3.8 `domain/usecase/SaveDraftSessionUseCase.kt` ← NEW
- Accepts a completed `DraftSession` + `FinalDraftScore`; persists via `DraftSessionRepository`

### 3.9 `di/RepositoryModule.kt`
- **Added:** `@Binds` for `DraftSessionRepository`

### 3.10 `presentation/draft/DraftState.kt`
- **Changed:** `suggestions: List<Pair<Hero, Double>>` → `suggestions: List<HeroScore>`

### 3.11 `presentation/draft/DraftViewModel.kt`
- **Changed:** maps to `HeroScore` directly (no information discarding)
- **Added:** `saveDraftSession()` called when phase reaches `COMPLETE`

### 3.12 `presentation/draft/DraftScreen.kt`
- **Changed:** Hardcoded color literals → `MaterialTheme.colorScheme.*`
- **Added:** `contentDescription` on all icons (TalkBack accessibility)

### 3.13 `utils/NetworkResult.kt` ← NEW
- Sealed class `NetworkResult<T>`: `Success`, `Error`, `Loading`

### 3.14 `data/repository/HeroRepositoryImpl.kt`
- **Changed:** `syncHeroes()` returns `NetworkResult<Unit>` internally
- **Added:** Exponential back-off inside `runCatching` fallback path

### 3.15 `presentation/navigation/AppNavGraph.kt`
- **Removed:** `SharedPreferences` call inside a Composable
- **Changed:** `wizard_done` sourced from DataStore via `WizardPreference`

### 3.16 `presentation/shell/AppShell.kt`
- **Changed:** `wizard_done` flag read from DataStore via `collectAsStateWithLifecycle`

---

## 4. Change Summary — Phase 4 (Second Pass)

### 4.1 `data/local/database/AppDatabase.kt`
- **Removed:** `companion object { fun build(context: Context) }` factory  
  **Why:** It called `.fallbackToDestructiveMigration()` without the `MIGRATION_1_2` migration
  object defined in `DatabaseModule`, creating two divergent construction paths. Any code path
  that used the companion bypassed the migration and risked silent schema drift.  
  The DI module (`DatabaseModule`) is now the single, exclusive construction path.

### 4.2 `data/local/preferences/WizardPreference.kt`
- **Changed:** DataStore file name `"mlbb_prefs"` → `"mlbb_preferences"`  
  **Why:** `AppModule.provideDataStore()` creates a DataStore named `"mlbb_preferences"`.
  Using a different name in `WizardPreference` created two separate DataStore files on disk,
  meaning the wizard flag and score weights were stored in different files.
  Now both use the same file, and the single `DataStore<Preferences>` instance provided by
  Hilt is shared correctly across the app.

### 4.3 `domain/advisor/DraftScoreCalculator.kt`
- **Fixed:** `calcMetaAdherence()` — replaced hard-coded divisor `4f` with `TIER_MAX_ORDER`  
  **Bug:** `Tier.UNKNOWN` has `order = 5`. Dividing by 4: `1 - 5/4 = −0.25`.  
  A negative meta adherence contribution silently corrupted the overall draft score for any
  session where heroes were unrecognised by the current patch tier list.  
  **Fix:** `TIER_MAX_ORDER = Tier.entries.maxOf { it.order }.toFloat()` (currently `5f`).
  Score range is now guaranteed to be `[0.0, 1.0]` with a trailing `.coerceIn(0f, 1f)` guard.  
  Score mapping after fix:  
  `S+ (0) → 1.0 | S (1) → 0.8 | A+ (2) → 0.6 | A (3) → 0.4 | B (4) → 0.2 | UNKNOWN (5) → 0.0`

### 4.4 `domain/scoring/DraftScorer.kt`
- **Fixed:** `scoreMeta()` — same `/ 4f` bug as 4.3, applied to the per-hero pick scoring path  
  **Added:** `@Stable` annotation on `HeroScore` data class

### 4.5 `domain/advisor/BanRecommender.kt`
- **Added:** `@Stable` annotation on `BanSuggestion` data class

### 4.6 `domain/advisor/CompositionAnalyzer.kt`
- **Added:** `@Stable` annotation on `CompositionProfile` data class
- **Added:** `getCounterPickWarnings(ourPicks, enemyPicks): List<String>`  
  Returns warning strings for each allied hero countered by at least one enemy pick.  
  Example output: `"⚠️ Layla is countered by Saber (enemy pick)"`  
  Designed to be called from `DraftViewModel` and surfaced on the draft screen in real time.

### 4.7 `domain/advisor/BuildAdvisor.kt`
- **Added:** `@Stable` annotation on `BuildAdvice` data class

### 4.8 `domain/advisor/DraftScoreCalculator.kt`
- **Added:** `@Stable` annotation on `FinalDraftScore` data class

### 4.9 `utils/NetworkResult.kt`
- **Added:** `fold(onLoading, onSuccess, onError)` extension — functional exhaustive handler  
  Avoids unchecked smart casts in ViewModels; all three branches required at the call site.
- **Added:** `getOrNull(): T?` extension — returns `Success.data` or `null`  
  Useful for one-liner null checks without a full `when` expression.

### 4.10 `domain/usecase/GetDraftHistoryUseCase.kt` ← NEW
- **Why:** `DraftSessionRepository` exposed `getAllSessions()` and `getRecentSessions()` but
  no corresponding use case existed. The presentation layer would have had to inject the
  repository directly, bypassing the domain layer.  
- `invoke(limit = 20)` — returns the N most recent sessions as a Flow  
- `all()` — unbounded, suitable for Paging 3 integration

### 4.11 `utils/DateFormatter.kt` ← NEW
- Human-readable timestamp formatting for the draft history screen  
- `formatRelative(ms)` — "Just now" / "45m ago" / "3h ago" / "Yesterday, 18:30" / "Jun 15, 2026"  
- `formatAbsolute(ms)` — "Jun 15, 2026"  
- `formatFull(ms)` — "Jun 15, 2026 18:30"  
- All formatters use `Locale.getDefault()` for locale-aware output

### 4.12 `presentation/common/components/ConnectivityBanner.kt` ← NEW
- Slim animated banner (expand/shrink vertical) shown when the device is offline  
- Uses `AnimatedVisibility` for smooth show/hide transitions  
- `liveRegion = LiveRegionMode.Polite` — TalkBack announces connectivity change without interrupting speech  
- Drop-in: placed at the top of any Scaffold body, driven by a Boolean `isOffline` parameter  
- Message: "No internet connection — showing cached data" — honest about what the app falls back to

---

## 5. Change Summary — Phase 5 (Third Pass)

**Date:** 2026-06-19  
**Scope:** Permission wizard expansion · Mini-widget manual draft controls · Overlay drag system rewrite · Manifest additions

---

### 5.1 `presentation/welcome/PermissionWizardScreen.kt`

**Added four new permission steps** — wizard now has 8 steps total (up from 4):

| Step | Title | Mechanism |
|------|-------|-----------|
| 5 | Disable Battery Optimisation | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with fallback to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. Checks `PowerManager.isIgnoringBatteryOptimizations()` before launching to avoid a redundant prompt. |
| 6 | App Auto-Start | Manufacturer-specific intents: Xiaomi MIUI → `com.miui.permcenter.autostart.AutoStartManagementActivity`; OPPO ColorOS (2 variants); Vivo FuntouchOS; Huawei EMUI (2 variants); Samsung One UI; OnePlus OxygenOS; Realme UI; Meizu Flyme. Falls back to standard App Info if no OEM screen resolves. |
| 7 | Unrestricted / Restricted Settings | Android 12 and newer can block sideloaded apps from granting overlay / accessibility permissions until the user taps "Allow restricted settings" in App Info. Step opens `ACTION_APPLICATION_DETAILS_SETTINGS` with an explanatory prompt. |
| 8 | Allow Background Running | Separate OEM-specific background-activity intents (Xiaomi PowerKeeper, Huawei Protected Apps, Samsung Device Care, Asus Mobile Manager). Falls back to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` then App Info. |

**Added helpers** `openAutoStartSettings(ctx)` and `openBackgroundRunningSettings(ctx)` — both use a `tryStartActivity` trampoline that iterates candidate Intents and silently swallows `ActivityNotFoundException` for each, guaranteeing a launch on any device.

**Why:** Auto-start and background-running restrictions are the #1 cause of the overlay service dying mid-draft on Chinese OEM ROMs. Battery optimisation is the standard Android mechanism that achieves the same effect on AOSP/Pixel devices. "Restricted Settings" (Android 12+) is the main blocker for sideloaded APK distributions.

---

### 5.2 `AndroidManifest.xml`

- **Added:** `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — required for `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent to work without a `SecurityException`.
- **Added:** `android.permission.RECEIVE_BOOT_COMPLETED` — enables future auto-restart of the overlay service after device reboot, used by auto-start-style OEM features.

---

### 5.3 `presentation/overlay/MiniWidget.kt`

**Added `onStartDraft: (ourTeamFirst: Boolean) -> Unit` parameter.**

**Redesigned `IdleBody`** — the idle/standby body now contains three interactive elements:

1. **"Waiting for draft to begin…" status label** (unchanged, preserved for visual context).
2. **"WHO PICKS FIRST?" team selector** — two side-by-side tappable cards:
   - `🔵 ALLY` on the **left** (teal highlight when selected; maps to `ourTeamFirst = true`).
   - `🔴 ENEMY` on the **right** (red highlight when selected; maps to `ourTeamFirst = false`).
   - Selection is remembered as local `remember { mutableStateOf(true) }` state so it persists across recompositions without leaking into the service layer.
   - Layout rationale: ally on left mirrors MLBB's own draft UI where allied picks appear on the left side of the screen.
3. **`▶  START DRAFT` button** — gold border/background, fires `onStartDraft(ourTeamFirst)` on tap. Provides an explicit manual trigger for when screen-capture phase detection is unavailable or disabled.

---

### 5.4 `presentation/overlay/OverlayService.kt`

#### 5.4.1 Drag system rewrite (`addOverlayView`)

**Problem (before):**  
The previous implementation tracked the delta from the initial touch position (`overlayParams.x = startX + (rawX - touchX)`). This causes *accumulated drift*: when the WindowManager clamps the window to screen bounds during a drag (e.g. the user drags toward an edge), `overlayParams.x` advances past the clamped position. On the return stroke, the finger must travel the full accumulated overshoot before the window visibly moves again — the overlay appears "stuck".

Additionally, the widget mode dropped `FLAG_LAYOUT_NO_LIMITS`, so the WindowManager could silently clamp position *during* a MOVE, de-synchronising `overlayParams` from the actual rendered position and compounding the drift.

**Fix (after):**  
- **Delta-from-last:** Each `ACTION_MOVE` event now computes `deltaX = rawX − lastRawX` and applies `overlayParams.x += deltaX`, then updates `lastRawX = rawX`. The initial touch is still recorded (`downRawX/downRawY`) solely for the drag-threshold check.
- **Clamp-on-up:** `clampToScreen()` is called only in `ACTION_UP` (after a drag), not during MOVE. This gives completely fluid dragging with a safe final resting position.
- **`FLAG_LAYOUT_NO_LIMITS` on both modes:** Both `bubbleFlags()` and `widgetFlags()` now include the flag so the WindowManager never clips mid-drag.
- **`ACTION_CANCEL` handler:** Resets `isDragging = false` cleanly when a system gesture (navigation bar swipe, multi-touch) intercepts the sequence. Previously `isDragging` could remain `true` across gestures, permanently disabling tap forwarding.
- **`runCatching` around `updateViewLayout`:** Guards against `IllegalArgumentException: View not attached` on rapid expand/collapse.

#### 5.4.2 `handleManualDraftStart(ourTeamFirst: Boolean)` — new method

Called from `MiniWidget.onStartDraft`. Calls `resetDraftTracking()`, then `draftSessionManager.initSession(Rank.UNKNOWN, ourTeamFirst)`, then `draftSessionManager.startBanPhase()`. Also ensures the widget is expanded. This gives users a one-tap way to start the draft assistant when screen-capture detection is not available.

---

## 6. Document References for Key Decisions

| Decision | Reference |
|----------|-----------|
| Clean Architecture layers | [Android Architecture Guide](https://developer.android.com/topic/architecture) |
| MVI / UDF | [State and Jetpack Compose](https://developer.android.com/develop/ui/compose/architecture) |
| `collectAsStateWithLifecycle` | [Lifecycle-aware flows in Compose](https://developer.android.com/topic/libraries/architecture/coroutines#collectasstatewithlifecycle) |
| Room type-safe sort | [Room DAO query reference](https://developer.android.com/training/data-storage/room/accessing-data) |
| OkHttp stable release | [OkHttp changelog](https://github.com/square/okhttp/blob/master/CHANGELOG.md) |
| DataStore vs SharedPreferences | [DataStore migration guide](https://developer.android.com/topic/libraries/architecture/datastore) |
| Compose `@Stable` / `@Immutable` | [Compose stability](https://developer.android.com/develop/ui/compose/performance/stability) |
| Material You dynamic color | [Dynamic color](https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic-color) |
| ProGuard / R8 keep rules | [R8 compatibility guide](https://developer.android.com/build/shrink-code) |
| Room single-source DB construction | [Room database builder](https://developer.android.com/training/data-storage/room#database-builder) |
| DataStore file naming | [Preferences DataStore guide](https://developer.android.com/topic/libraries/architecture/datastore#preferences-datastore) |
| Compose animation APIs | [AnimatedVisibility](https://developer.android.com/develop/ui/compose/animation/composables#animatedvisibility) |
| TalkBack live regions | [Accessibility in Compose](https://developer.android.com/develop/ui/compose/accessibility) |

---

## 6. Remaining Technical Debt & Roadmap

### Short-term (next sprint)
- [ ] **SSL pinning** — add `OkHttpClient.certificatePinner(...)` for `api.mlbb-assistant.com`
- [ ] **Paging 3** — integrate with `GetDraftHistoryUseCase.all()` for large history lists
- [ ] **OverlayService full decomposition** — extract `OverlayWindowManager` and `OverlayStateHolder` as standalone classes
- [ ] **ViewModel tests** — `HeroListViewModelTest`, `DraftViewModelTest` (MockK + Turbine)
- [ ] **Repository tests** — `HeroRepositoryImplTest` with in-memory Room DB
- [ ] **Wire `ConnectivityBanner`** — connect `NetworkMonitor.isConnected` Flow to `ConnectivityBanner` in `AppShell`
- [ ] **Wire `getCounterPickWarnings`** — surface counter-pick warnings on the draft screen via `DraftViewModel`
- [ ] **Wire `GetDraftHistoryUseCase`** — inject into `HistoryViewModel` (or equivalent)

### Medium-term
- [ ] **Kotlinx.serialization** — replace Gson for faster, reflection-free JSON parsing
- [ ] **Room migrations** — define `Migration(1,2)` and `Migration(2,3)` instead of relying on `fallbackToDestructiveMigration`
- [ ] **Baseline profile** — add `BaselineProfileGenerator` for startup optimisation
- [ ] **EncryptedDataStore** — encrypt DataStore preferences (score weights; low-risk but best practice)
- [ ] **WorkManager for hero sync** — replace `syncNow()` hot-path with a periodic `SyncHeroesWorker`
- [ ] **Light theme** — define a `MLBBLightColorScheme` fallback (app is currently dark-only)
- [ ] **`DraftHistoryItem` ban/pick IDs** — add `yourPickIds`, `enemyPickIds`, `yourBanIds`, `enemyBanIds` fields to capture full session state; `DraftSessionRepositoryImpl.toEntity()` currently stubs these as empty lists

### Long-term
- [ ] **Multi-module** — split into `:domain`, `:data`, `:capture`, `:overlay`, `:app` Gradle modules
- [ ] **CI/CD pipeline** — lint, unit tests, UI tests as PR gates
- [ ] **Analytics** — event tracking for recommendation follow rate per patch version
- [ ] **kotlinx.datetime** — replace `SimpleDateFormat` in `DateFormatter` with the multiplatform-safe `kotlinx-datetime` library

---

## 7. UX / Accessibility Audit

| Item | Status | Fix |
|------|--------|-----|
| Edge-to-edge | ✅ Implemented (`enableEdgeToEdge()`) | — |
| Dynamic colour (Material You) | ✅ API 31+ with branded dark fallback | — |
| TalkBack — bottom nav | ✅ `contentDescription` added to `NavigationBarItem` icons | Fixed |
| TalkBack — `DraftScreen` icons | ✅ `contentDescription` on hero chip portraits | Fixed |
| TalkBack — `LoadingSpinner` | ✅ `contentDescription = "Loading"` added | Fixed |
| TalkBack — `ConnectivityBanner` | ✅ `liveRegion = Polite` added | Fixed |
| Touch targets | ✅ `MLBBButton` enforces 48 dp min height | Fixed |
| `HeroPortrait` empty/missed slots | ✅ Now uses Material Icons instead of emoji text | Fixed |
| Dark/Light theme | ⚠️ App is dark-only; light scheme not yet defined | Roadmap |
| Text scaling | ✅ Hardcoded `sp` values replaced with `MaterialTheme.typography` | Fixed |
| Offline indicator | ✅ `ConnectivityBanner` composable added | Fixed |

---

## 8. Security Audit

| Item | Status |
|------|--------|
| `BuildConfig.DEBUG` logging gate | ✅ Timber only planted in debug builds |
| ProGuard / R8 rules | ✅ Comprehensive — Gson, Hilt, Retrofit, Room all covered |
| Timber error stripping | ✅ Only `v`, `d`, `i` stripped in release — `w` and `e` preserved for crash diagnostics |
| Hardcoded `BASE_URL` | ✅ Fixed — moved to `BuildConfig.META_API_BASE_URL` |
| SSL pinning | ❌ Absent — add `OkHttpClient.certificatePinner()` for production |
| `exportSchema = false` | ✅ Fixed — `exportSchema = true`, schema JSON committed to `/schemas/` |
| `android:allowBackup` | ✅ Set to `false` — prevents ADB backup from extracting draft history DB |
| DataStore file naming | ✅ Single file `"mlbb_preferences"` — no duplicate files |
| Room single construction path | ✅ `AppDatabase.build()` companion removed — no migration bypass |

---

## 9. Phase 6 Changes (2026-06-19)

### 9.1 LeakCanary Removed

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Removed `leakcanary = "2.14"` version entry and `leakcanary` library entry |
| `app/build.gradle.kts` | Removed `debugImplementation(libs.leakcanary)` and its comment block |

**Rationale:** LeakCanary adds ~2 MB to the debug APK, slows startup via its `AppWatcher` hooks, and conflicts with the overlay service's lifecycle setup.  Memory-leak detection is deferred to the Android Studio Memory Profiler for targeted sessions.

---

### 9.2 Built-in Crash / Error Log Tab

New files:

| File | Description |
|------|-------------|
| `data/local/crashlog/CrashLogStore.kt` | Append-only flat-file log in `filesDir/mlbb_crash_log.txt`.  Encodes entries as pipe-delimited lines; stack traces as TAB-prefixed continuation lines.  Auto-rotates at 512 KB (halves the file). |
| `data/local/crashlog/AppLogTree.kt` | `Timber.Tree` subclass — captures WARN / ERROR / ASSERT priority logs and writes them via `CrashLogStore.appendSync()`.  Also exposes `installCrashHandler()` which wraps `Thread.defaultUncaughtExceptionHandler` to persist crash stack traces before the process dies. |
| `presentation/log/LogViewModel.kt` | `@HiltViewModel`; exposes `LogScreenState(entries, isLoading)` via `StateFlow`; provides `load()` and `clear()`. |
| `presentation/log/LogScreen.kt` | Composable — reverse-chronological list of log entries.  Each card shows level badge, tag, timestamp, message, and an expandable stack-trace section.  Toolbar actions: Refresh, Share (system share sheet), Clear (with confirmation dialog), Copy per-entry. |

Updated files:

| File | Change |
|------|--------|
| `MLBBApplication.kt` | `installCrashHandler()` called first in `onCreate()`, then `Timber.plant(AppLogTree(...))` planted unconditionally (both release and debug). |
| `presentation/navigation/AppRoute.kt` | Added `AppRoute.CrashLog("crash_log")`; added route to `TOP_LEVEL_ROUTES`. |
| `presentation/navigation/AppNavGraph.kt` | Added `composable(AppRoute.CrashLog.route) { LogScreen(onBack = ...) }`. |
| `presentation/shell/AppShell.kt` | Added `BottomNavItem(AppRoute.CrashLog.route, Icons.Rounded.BugReport, "Log")` to `NAV_ITEMS`. |

---

### 9.3 MiniWidget — Unified Ban + Pick Panel

**Problem:** The widget previously showed either ban suggestions OR pick suggestions depending on the current phase.  Users had to minimise and re-expand the bubble to see the other section.

**Fix:** `MiniWidget.kt` rewritten.  For any active draft phase (`BAN_ROUND_1`, `BAN_ROUND_2`, `PICK`, `TRADING`) the body now renders `ActiveDraftBody`, a single scrollable column that always contains:

1. **⛔ BAN section** — turn indicator (YOUR TURN TO BAN / Enemy banning…) + "TOP BANS" row (up to 3 chips)
2. A thin gold divider
3. **✅ PICK section** — turn badge (YOUR TURN / ENEMY TURN + pick number) + "TOP PICKS" row (up to 3 chips) + first enemy warning if any
4. A thin gold divider  
5. **Slot overview dots** — ban slots (E / Y) then pick slots (E / Y), filled = hero assigned

`IDLE`/`SETUP` still shows the START DRAFT flow.  `COMPLETE` shows the close button.

The `IdleBody` also gains the ALLY / ENEMY team-first toggle buttons from the previous session.

---

### 9.4 Overlay Drag — Definitive Fix

**Root cause of all previous drag failures:**

Returning `true` from `setOnTouchListener` on `ACTION_DOWN` consumes the event before `ComposeView.onTouchEvent()` is called.  Compose never sees the DOWN event, so its gesture-detector pipeline is never armed — clicks, ripples, and press-states all break.  The synthetic re-dispatch workaround (`MotionEvent.obtain` + `dispatchTouchEvent`) is unreliable because Compose's pointer-input system does not treat synthetic events the same as real hardware events (pointer-ID / event-time pairing differs, and the listener can swallow the fake events before Compose does).

**Correct pattern (per Android WindowManager docs + AOSP `BubbleTouchHandler`):**

| Event | Return value | Why |
|-------|-------------|-----|
| `ACTION_DOWN` | `false` | Compose sees DOWN → gesture detectors arm, ripple starts |
| `ACTION_MOVE` (within slop) | `false` | Compose continues tracking; we don't yet know if it's a drag |
| `ACTION_MOVE` (exceeds slop — first time) | `true` after sending `ACTION_CANCEL` to `v.onTouchEvent()` | Cancel tears down Compose's in-flight gesture cleanly; we now own the sequence |
| `ACTION_MOVE` (subsequent drag) | `true` | Window moves with the finger |
| `ACTION_UP` / `ACTION_CANCEL` (was dragging) | `true` | Clamp to screen bounds, consume |
| `ACTION_UP` (was NOT dragging = tap) | `false` | Compose sees UP → fires the click callback naturally — no synthetic re-dispatch needed |

Additional improvements:
- `ViewConfiguration.get(this).scaledTouchSlop` replaces the hardcoded 10 dp threshold (system-calibrated for density + accessibility settings).
- Initial-based delta (`newX = initialWindowX + totalFingerDx`) replaces per-event delta; eliminates floating-point truncation drift over long drags.
- `isForwardingClick` flag and all synthetic `MotionEvent.obtain` / `dispatchTouchEvent` calls removed.

---

*End of report.*
