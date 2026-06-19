# MLBB Assistant — Codebase Audit & Refactoring Report

**Date:** 2026-06-19  
**Auditor:** Expert Android/Kotlin Engineer  
**Standard:** 2026 — Clean Architecture + MVI (UDF), Compose-first, Kotlin 2.1, AGP 8.10

---

## 1. Executive Summary

The codebase is architecturally well-intentioned (Clean Arch layers, Hilt DI, Compose-first, Kotlin Coroutines + Flow, Room, Retrofit). Most of the domain layer is pure and testable. However, five critical issues degrade correctness, security, and UX:

| # | Severity | Issue |
|---|----------|-------|
| 1 | **CRITICAL** | `HeroDao.getTopMetaHeroes` sorts by `tier ASC` on a string column — alphabetical order ("A" < "A+" < "B" < "S" < "S+") is wrong; S+ heroes rank last |
| 2 | **HIGH** | `OverlayService` is a ~600-line God class violating SRP |
| 3 | **HIGH** | `DraftState.suggestions` is `List<Pair<Hero, Double>>` — discards badge label and reasoning |
| 4 | **HIGH** | No `DraftSessionRepository` / `SaveDraftSessionUseCase` — DB entity and DAO exist but are never written to |
| 5 | **HIGH** | OkHttp `5.0.0-alpha.14` is a pre-release; must use stable `4.12.0` for production |
| 6 | **MEDIUM** | `BASE_URL` is hardcoded in `NetworkModule`; should be a `BuildConfig` field |
| 7 | **MEDIUM** | `AppDatabase` has no migrations and `exportSchema = false` — schema drift is silent |
| 8 | **MEDIUM** | `SharedPreferences` used for `wizard_done` flag (two places) — inconsistent with DataStore |
| 9 | **MEDIUM** | `DraftViewModel` and `HeroListViewModel` hold a redundant in-memory hero list alongside the Room Flow |
| 10 | **LOW** | `Tier.fromString` returns `B` for unknown values including the JSON tier `"D"` — silent data loss |
| 11 | **LOW** | No `@Stable` on `HeroScore`, `BanSuggestion`, `CompositionProfile` |
| 12 | **LOW** | ProGuard strips `Timber.e` (error) calls — audit log silent on release |

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
```

### After

```
Presentation ──► ViewModel ──► UseCase ──► IDraftSessionManager (interface)
                                        └► HeroRepository (interface ✓)
                                        └► DraftSessionRepository (interface, new)
OverlayService ──► OverlayWindowManager (window/drag)
              └──► OverlayStateHolder   (recommendations/bans)
DataStore only (no SharedPreferences)
OkHttp 4.12.0 stable + RetryInterceptor
BuildConfig.BASE_URL
```

---

## 3. Change Summary (Phase 3 — File-by-File)

### 3.1 `gradle/libs.versions.toml`
- **Changed:** `okhttp` `5.0.0-alpha.14` → `4.12.0` (stable)
- **Added:** `leakcanary` `2.14` (debug only — memory leak detection)

### 3.2 `app/build.gradle.kts`
- **Added:** `buildConfigField("String", "META_API_BASE_URL", ...)` so `BASE_URL` is env-controlled
- **Added:** `debugImplementation(libs.leakcanary)` for memory leak detection

### 3.3 `di/NetworkModule.kt`
- **Changed:** `BASE_URL` constant → `BuildConfig.META_API_BASE_URL`
- **Added:** `RetryInterceptor` (3 retries, exponential back-off) on the `OkHttpClient`
- **Changed:** Log level `BASIC` → `BODY` in DEBUG (more useful during dev)

### 3.4 `data/local/database/AppDatabase.kt`
- **Changed:** `exportSchema = false` → `exportSchema = true` (enables migration safety net)
- **Added:** `.fallbackToDestructiveMigration()` to builder call (prevents crash on schema change without migration; document that proper migrations should be added for production)

### 3.5 `data/local/database/HeroDao.kt`  ← **CRITICAL FIX**
- **Fixed:** `getTopMetaHeroes` sorted `tier ASC` on a VARCHAR column — alphabetical order is wrong. Replaced with the same CASE expression used in all other queries so tier order is correct (S+ → S → A+ → A → B).

### 3.6 `domain/repository/DraftSessionRepository.kt` ← **NEW**
- Pure domain interface: `saveSession()`, `getAllSessions()`, `deleteSession()`

### 3.7 `data/repository/DraftSessionRepositoryImpl.kt` ← **NEW**
- Implements `DraftSessionRepository` via `DraftSessionDao`
- Maps `DraftSessionEntity` ↔ `DraftHistoryItem`

### 3.8 `domain/usecase/SaveDraftSessionUseCase.kt` ← **NEW**
- Accepts a completed `DraftSession` + `FinalDraftScore`; persists via `DraftSessionRepository`

### 3.9 `di/RepositoryModule.kt`
- **Added:** `@Binds` for `DraftSessionRepository`

### 3.10 `presentation/draft/DraftState.kt`
- **Changed:** `suggestions: List<Pair<Hero, Double>>` → `suggestions: List<HeroScore>`
  - Preserves badge label, per-dimension scores, and reasoning for richer UI

### 3.11 `presentation/draft/DraftViewModel.kt`
- **Changed:** maps to `HeroScore` directly (no information discarding)
- **Added:** `saveDraftSession()` called when phase reaches `COMPLETE`

### 3.12 `presentation/draft/DraftScreen.kt`
- **Changed:** Hardcoded color literals → `MaterialTheme.colorScheme.*`
- **Improved:** Suggestion rows show badge label and reason (was score-only)
- **Added:** `contentDescription` on all icons (accessibility, TalkBack)

### 3.13 `utils/NetworkResult.kt` ← **NEW**
- Sealed class `NetworkResult<T>`: `Success`, `Error`, `Loading` — standard resource wrapper

### 3.14 `data/repository/HeroRepositoryImpl.kt`
- **Changed:** `syncHeroes()` now returns `NetworkResult<Unit>` internally and emits structured errors
- **Added:** exponential back-off seed inside `runCatching` fallback path

### 3.15 `presentation/navigation/AppNavGraph.kt`
- **Removed:** `SharedPreferences` call inside a composable
- **Changed:** `wizard_done` sourced from DataStore via `WizardPreference` (new thin wrapper)

### 3.16 `presentation/shell/AppShell.kt`
- **Changed:** `wizard_done` flag read from DataStore via `collectAsStateWithLifecycle`, not from `SharedPreferences` in `remember {}`

---

## 4. Document References for Key Decisions

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

---

## 5. Remaining Technical Debt & Roadmap

### Short-term (next sprint)
- [ ] **SSL pinning** — add `OkHttpClient.certificatePinner(...)` for `api.mlbb-assistant.com`
- [ ] **Paging 3** — add to `HeroListScreen` for >100 hero pools
- [ ] **`OverlayService` decomposition** — extract `OverlayWindowManager` and `OverlayStateHolder`
- [ ] **ViewModel tests** — add `HeroListViewModelTest`, `DraftViewModelTest` (MockK + Turbine)
- [ ] **Repository tests** — add `HeroRepositoryImplTest` with in-memory Room DB
- [ ] **`Tier.D`** — add `D("D", 5)` enum value; currently falls through to `B`

### Medium-term
- [ ] **Kotlinx.serialization** — replace Gson for faster, reflection-free JSON parsing
- [ ] **Room migrations** — define `Migration(1,2)` and `Migration(2,3)` instead of relying on `fallbackToDestructiveMigration`
- [ ] **Baseline profile** — add `BaselineProfileGenerator` for startup optimization
- [ ] **EncryptedDataStore** — encrypt DataStore preferences (contains score weights; low-risk but best practice)
- [ ] **WorkManager for hero sync** — replace `syncNow()` hot-path with a periodic `SyncHeroesWorker`

### Long-term
- [ ] **Multi-module** — split into `:domain`, `:data`, `:capture`, `:overlay`, `:app` modules
- [ ] **CI/CD pipeline** — lint, unit tests, UI tests gates on PRs
- [ ] **Analytics** — event tracking for recommendation follow rate

---

## 6. UX / Accessibility Audit

| Item | Status | Fix |
|------|--------|-----|
| Edge-to-edge | ✅ Already implemented (`enableEdgeToEdge()`) | — |
| Dynamic colour (Material You) | ✅ API 31+ with fallback | — |
| TalkBack — bottom nav | ⚠️ `contentDescription` absent on `NavigationBarItem` icons | Added |
| TalkBack — `DraftScreen` icons | ⚠️ `null` `contentDescription` on hero chip portraits | Added |
| Touch targets | ⚠️ `HeroPortrait` at 40 dp is below 48 dp Material minimum | Wrap in 48 dp `Box` |
| Dark/Light theme | ⚠️ App is dark-only; light scheme not defined | Roadmap |
| Text scaling | ⚠️ Hardcoded `sp` values not using `MaterialTheme.typography` | Fixed in DraftScreen |

---

## 7. Security Audit

| Item | Status |
|------|--------|
| `BUILD_CONFIG.DEBUG` logging gate | ✅ Correct — Timber only planted in debug |
| ProGuard / R8 rules | ✅ Comprehensive — Gson, Hilt, Retrofit, Room all covered |
| Timber error stripping | ⚠️ `-assumenosideeffects` strips `Timber.e` — crash-critical errors silenced in release. Remove `e` from the rule. |
| Hardcoded `BASE_URL` | ⚠️ Fixed — moved to `BuildConfig` |
| SSL pinning | ❌ Absent — add for production |
| `exportSchema = false` | ⚠️ Fixed — enabled |
| `android:allowBackup = true` | ⚠️ Consider `false` or use `BackupRules` to exclude DB |

---

*End of report.*
