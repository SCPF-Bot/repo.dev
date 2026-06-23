# MLBB Assistant — Codebase Audit

**Completed:** June 2026 · Six-pass audit and refactoring.  
**Standard:** Clean Architecture + MVI (UDF), Compose-first, Kotlin 2.1, AGP 8.10.

---

## Issue Resolution Matrix

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 1 | **CRITICAL** | `HeroDao.getTopMetaHeroes` sorted by `tier ASC` on VARCHAR — alphabetical order wrong | ✅ Fixed |
| 2 | **HIGH** | `OverlayService` ~600-line God class violating SRP | ⚠️ Interface extraction done; full decomposition in roadmap |
| 3 | **HIGH** | `DraftState.suggestions` was `List<Pair<Hero, Double>>` — discarded badge label and reason | ✅ Fixed — now `List<HeroScore>` |
| 4 | **HIGH** | `DraftSessionRepository` / `SaveDraftSessionUseCase` absent — DAO existed but was never written to | ✅ Fixed |
| 5 | **HIGH** | OkHttp `5.0.0-alpha.14` pre-release in production | ✅ Fixed — stable `4.12.0` |
| 6 | **MEDIUM** | `BASE_URL` hardcoded in `NetworkModule` | ✅ Fixed — `BuildConfig.META_API_BASE_URL` |
| 7 | **MEDIUM** | `AppDatabase` had `exportSchema = false`, no migrations | ✅ Fixed |
| 8 | **MEDIUM** | `SharedPreferences` used for `wizard_done` flag — inconsistent with DataStore | ✅ Fixed |
| 9 | **MEDIUM** | `DraftViewModel` held redundant in-memory hero list alongside Room Flow | ✅ Fixed |
| 10 | **MEDIUM** | `AppDatabase.build()` companion bypassed `MIGRATION_1_2` in `DatabaseModule` | ✅ Fixed — companion removed |
| 11 | **MEDIUM** | `WizardPreference` DataStore name `"mlbb_prefs"` diverged from `AppModule`'s `"mlbb_preferences"` | ✅ Fixed |
| 12 | **MEDIUM** | `DraftScoreCalculator.calcMetaAdherence()` divided by 4; `Tier.UNKNOWN` (order=5) yielded −0.25 | ✅ Fixed — `TIER_MAX_ORDER` constant |
| 13 | **MEDIUM** | `DraftScorer.scoreMeta()` same `/ 4` bug — negative tier contribution for `Tier.B` and `UNKNOWN` | ✅ Fixed |
| 14 | **LOW** | `Tier.fromString` returned `B` for unknown values — silent data loss | ✅ Fixed — `UNKNOWN` catch-all |
| 15 | **LOW** | No `@Stable`/`@Immutable` on `HeroScore`, `BanSuggestion`, `CompositionProfile`, `FinalDraftScore`, `BuildAdvice` | ✅ Fixed |
| 16 | **LOW** | ProGuard stripped `Timber.e` calls — audit log silent on release | ✅ Fixed — `e` excluded from rule |
| 17 | **INFO** | No `GetDraftHistoryUseCase` — presentation injected DAO directly | ✅ Added |
| 18 | **INFO** | No counter-pick warning surface for picks already made | ✅ Added — `CompositionAnalyzer.getCounterPickWarnings` |
| 19 | **INFO** | No offline/connectivity indicator | ✅ Added — `ConnectivityBanner` composable |
| 20 | **INFO** | No timestamp formatting utility — `timestamp` displayed as raw Long | ✅ Added — `DateFormatter` (thread-safe `java.time`) |
| 21 | **INFO** | `NetworkResult` lacked functional helpers | ✅ Added — `fold`, `getOrNull` |

---

## Architecture — Before / After

**Before:**
```
Presentation ──► ViewModel ──► UseCase ──► DraftSessionManager (concrete)
                                        └► DraftSessionDao (never called)
OverlayService (God class: window + capture + compose + recs + phase)
SharedPreferences + DataStore mixed · OkHttp alpha
DraftState.suggestions = List<Pair<Hero, Double>>
AppDatabase: two construction paths (DI module + companion — migration bypass)
WizardPreference DataStore name mismatch → two files on disk
Tier scoring ÷ 4 → Tier.UNKNOWN yields −0.25
```

**After:**
```
Presentation ──► ViewModel ──► UseCase ──► IDraftSessionManager (interface)
                                        └► DraftSessionRepository (new interface)
                                        └► GetDraftHistoryUseCase (new)
OverlayService ──► OverlayWindowManager (window/drag)
              └──► OverlayStateHolder   (recs/bans)
DataStore only, single file "mlbb_preferences"
OkHttp 4.12.0 stable + RetryInterceptor · BuildConfig.BASE_URL
DraftState.suggestions = List<HeroScore> (badge + scores + reason)
AppDatabase: single construction path via DatabaseModule + MIGRATION_1_2
Tier scoring ÷ TIER_MAX_ORDER (5) → always [0.0, 1.0]
@Stable on all advisory output types
ConnectivityBanner · DateFormatter (DateTimeFormatter) · NetworkResult.fold/getOrNull
```

---

## UX / Accessibility

| Item | Status |
|------|--------|
| Edge-to-edge (`enableEdgeToEdge()`) | ✅ |
| TalkBack — bottom nav `contentDescription` | ✅ Fixed |
| TalkBack — `DraftScreen` icon descriptions | ✅ Fixed |
| TalkBack — `LoadingSpinner` description | ✅ Fixed |
| TalkBack — `ConnectivityBanner` live region | ✅ Fixed |
| Touch targets ≥ 48 dp (`MLBBButton`) | ✅ Fixed |
| `HeroPortrait` empty slots use Material Icons | ✅ Fixed |
| Text scaling via `MaterialTheme.typography` | ✅ Fixed |
| Dark/Light theme | ⚠️ Dark-only; light scheme not yet defined (roadmap) |

---

## Security

| Item | Status |
|------|--------|
| `BuildConfig.DEBUG` logging gate | ✅ |
| ProGuard/R8 rules (Gson, Hilt, Retrofit, Room) | ✅ |
| Timber error log preserved in release (`w`, `e`) | ✅ |
| `BASE_URL` in `BuildConfig` | ✅ |
| SSL pinning | ❌ Not yet — `OkHttpClient.certificatePinner()` needed for production |
| `exportSchema = true`, schema committed to `/schemas/` | ✅ |
| `android:allowBackup = false` | ✅ |
| DataStore single file, single construction path | ✅ |

---

## Pass 7 — User-Configurable Scoring Everywhere (Mission Pillars 1 & 4)

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 22 | **CRITICAL** | `SettingsViewModel.runCalibration()` (runs on every Settings open) built `ScoreWeights(meta, counter, synergy)` directly from the three independently-persisted sliders. Their raw sum drifts from 1.0, so `ScoreWeights.init` threw `IllegalArgumentException` and crashed the app. | ✅ Fixed — switched to non-throwing `ScoreWeights.normalized()` |
| 23 | **HIGH** | `OverlayService.refreshRecommendations()` hardcoded `ScoreWeights.DEFAULT`. The overlay is the *primary product* (Pillar 1) yet ignored the user's weight sliders (Pillar 4) — tuning Settings had zero effect on live draft/ban advice. | ✅ Fixed — overlay observes `PreferencesDataStore.scoreWeightsFlow` |
| 24 | **HIGH** | In-app `DraftViewModel` exposed an unused `setWeights()` that nothing called, so the draft screen also scored on `ScoreWeights.DEFAULT`. | ✅ Fixed — reactive `observeScoringConfig()`; dead `setWeights()` removed |
| 25 | **MEDIUM** | Personal hero pool (`HeroPoolDao` / `Proficiency`, TD-02) was implemented end-to-end but the resulting `poolMap` was never passed to `DraftScorer` anywhere — un-pooled heroes were never downweighted in real recommendations. | ✅ Fixed — both overlay and draft VM now feed the live `poolMap` into scoring |

**New single source of truth:** `PreferencesDataStore.scoreWeightsFlow: Flow<ScoreWeights>` combines the three weight keys through `ScoreWeights.normalized()` (can never throw) and is collected by both `OverlayService` and `DraftViewModel`, so Settings sliders and the Hero Pool screen now drive recommendations consistently across the whole app without a restart.
