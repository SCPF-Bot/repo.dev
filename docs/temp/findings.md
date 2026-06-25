# Audit Findings — MLBB Draft Assistant
> Generated: 2026-06-23 · Last reconciled: 2026-06-26 · Source reconciled against `versionName 2.0.0` (versionCode 2)
> Kotlin 2.1.0 · AGP 8.10.1 · Min SDK 29 · Target SDK 36
>
> **Delta summary (third-pass reconciliation, 2026-06-26):**
> P0-04 (shared mutable sets) and P1-04 (missing `@Immutable`) executed in-place.
> P0-04 was **re-classified from "currently safe" to a live data race**: the capture
> loop in `OverlayService.launchCaptureLoop()` runs on `Dispatchers.IO` and
> `Dispatchers.Default`, so the previous "both paths run on Main" assumption was
> incorrect. Fixed with `ConcurrentHashMap.newKeySet()`.
> P1-03 (OverlayService god-class split, effort L) intentionally **deferred** — see `misc.md` §1.
>
> **Delta summary (second-pass reconciliation, 2026-06-23):**
> Top 5 P0/P1 issues executed in-place (see Phase 3 refactoring log).
> P0-01, P0-02, P0-03: resolved. P1-01, P1-02: resolved.
> P2-01, P2-02, P2-03: resolved. P0-04, P1-03, P1-04 remain open.
> CI workflow added (`.github/workflows/ci.yml`) — partially closes P3-02.

---

## Summary Table

| Priority | Label | Count | Resolved (cumulative) |
|---|---|---|---|
| P0 | Critical (crashes / data-loss / security) | 4 | 4 (P0-01, P0-02, P0-03, **P0-04**) |
| P1 | Performance | 4 | 3 (P1-01, P1-02, **P1-04**) |
| P2 | Maintainability | 6 | 3 (P2-01, P2-02, P2-03) |
| P3 | Deprecations / outdated dependencies | 3 | 1 partial (P3-02 CI added) |
| P4 | Gaps (missing best-practice infrastructure) | 5 | 0 |
| **Open** | | **11** | — |

> Resolved this pass (2026-06-26): **P0-04, P1-04**. Open count: 13 → 11.
> P1-03 remains open but is now formally **deferred** with rationale in `misc.md` §1.

---

## P0 — Critical

### P0-01 · `!!` on mutable nullable field — race-condition NPE risk — [RESOLVED]
**File:** `service/ScreenCaptureManager.kt`
**Fix applied:** `imageReader` captured into a local `val reader` immediately after construction. `virtualDisplay` creation now uses `reader.surface` — a stable, non-nullable local reference. No `!!` operator remains in the file.

---

### P0-02 · `!!` on a mutable state property in Compose after explicit `null` check — [RESOLVED]
**File:** `presentation/history/DraftReplayScreen.kt`
**Fix applied:** Replaced `val s = state.session!!` with `val s = state.session ?: return@Scaffold`. The compiler now has a stable local val; the `!!` is eliminated.

---

### P0-03 · `!!` on nullable `Intent` after redundant manual null check — [RESOLVED]
**File:** `presentation/main/MainActivity.kt`
**Fix applied:** Replaced the `if (result.resultCode == RESULT_OK && result.data != null) { ... result.data!! }` pattern with:
```kotlin
if (result.resultCode != RESULT_OK) return@registerForActivityResult
val data = result.data ?: return@registerForActivityResult
OverlayService.startWithProjection(this, result.resultCode, data)
```

---

### P0-04 · `mutableSetOf<Int>()` shared mutable state without synchronisation — [RESOLVED]
**File:** `presentation/overlay/OverlayService.kt` (fields `filledEnemyBanSlots`, `filledOurBanSlots`, `filledEnemyPickSlots`, `filledOurPickSlots`)

**Re-classification (2026-06-26):** The second-pass note claimed this was "currently safe because both read and write paths run on `Dispatchers.Main`." Source verification disproved that. `launchCaptureLoop()` launches `captureJob` on `Dispatchers.IO` (line ~795) and performs the slot scan inside `withContext(Dispatchers.Default)` (line ~804). The sets are read and mutated there (`i !in filledEnemyBanSlots`, `filledEnemyBanSlots.add(i)`, `.size`, `.clear()`), while session-reset paths also clear them. This is a genuine concurrent read/write across threads — a live data race, not a latent one. Severity confirmed P0.

**Fix applied:** All four fields changed from `mutableSetOf<Int>()` to `ConcurrentHashMap.newKeySet<Int>()` (a lock-free, thread-safe `MutableSet<Int>`). All existing call sites are source-compatible (`add`/`contains`/`in`/`clear`/`size` semantics are identical). Added `import java.util.concurrent.ConcurrentHashMap` and an explanatory comment at the declaration site.

---

## P1 — Performance

### P1-01 · `Bitmap.getPixel()` in nested loops — critical render-path bottleneck — [RESOLVED]
**Files:** `capture/FrameProcessor.kt` — `sampleLuminanceBaseline()` and `isSlotFilled()`
**Fix applied:** Both functions now use `Bitmap.copyPixelsToBuffer(ByteBuffer)` for a single bulk pixel extraction, then iterate the backing byte array in pure Kotlin. The JNI-per-pixel overhead is eliminated. Sampling density is identical to the previous implementation (step=2 for baseline, step=4 for slot fill). Expected improvement: 5–20× on mid-range devices.

---

### P1-02 · `Thread.sleep()` inside OkHttp `Interceptor` blocks a thread-pool thread — [RESOLVED]
**Files:** `di/NetworkModule.kt`, `data/repository/HeroRepositoryImpl.kt`
**Fix applied:** `RetryInterceptor` removed from `NetworkModule`. Retry logic moved to `HeroRepositoryImpl.syncHeroes()` using coroutine `delay()`. The new `syncWithRetry` pattern uses `repeat(MAX_SYNC_RETRIES)` with `delay(RETRY_BASE_DELAY_MS * (attempt + 1))` — non-blocking, cancellation-aware, and testable independently of OkHttp internals.

---

### P1-03 · `OverlayService` (~1,100 LOC) causes broad recomposition scope — [OPEN]
Tracked in roadmap as `P1/L`. Requires decomposition into `OverlayWindowManager`, `OverlayCaptureCoordinator`, and a Compose UI host. Service remains as a thin lifecycle shell.

---

### P1-04 · Missing `@Immutable` on several ViewModel UI state classes — [RESOLVED]
Confirmed already annotated: `DraftState.kt`, `HeroListState.kt`, `SettingsState.kt`.

**Audit result (2026-06-26):**
- `HomeViewModel.kt` — `@Immutable` added to `HomeUiState` and `InsightsState`. ✅
- `HeroPoolViewModel.kt` — `@Immutable` added to `HeroPoolState` and `HeroPoolEntry`. ✅
- `LogViewModel.kt` — `@Immutable` added to `LogScreenState`. ✅
- `DraftHistoryViewModel.kt` — exposes a bare `StateFlow<List<DraftHistoryItem>>`; there is no UI-state `data class` to annotate. `DraftHistoryItem` itself is a stable domain model. No change required.
- `MetaBoardScreen.kt` — no `data class` UI-state holder exists (renders directly from injected hero data). No change required.

All Compose UI state classes that exist are now annotated `@Immutable`, allowing the Compose compiler to skip recomposition when instances are referentially equal.

---

## P2 — Maintainability

### P2-01 · Magic float literals in `BuildAdvisor` and `CompositionAnalyzer` — [RESOLVED]
**Files:** `domain/advisor/BuildAdvisor.kt`, `domain/advisor/CompositionAnalyzer.kt`
**Fix applied:**
- `BuildAdvisor`: private `object BuildThresholds` added with `DAMAGE_MODERATE = 0.60f`, `DAMAGE_HEAVY = 0.70f`, `DAMAGE_FULL = 0.80f`. All literal occurrences replaced.
- `CompositionAnalyzer`: private `object CompThresholds` added with `DAMAGE_FULL = 0.80f`, `DAMAGE_MIXED = 0.40f`. All literal occurrences replaced.

---

### P2-02 · Duplicate notification channel ID definitions (dead constant) — [RESOLVED]
**File:** `utils/AppConstants.kt`
**Fix applied:** `OVERLAY_NOTIFICATION_CHANNEL_ID = "draft_overlay_channel"` deleted. `OverlayService` retains its own private `NOTIF_CHANNEL = "overlay_channel"` as the single authoritative constant.

---

### P2-03 · Two scoring entry points with unclear canonical status — [RESOLVED]
**File:** `domain/scoring/DraftScorer.kt`
**Fix applied:** `computeScore` annotated with `@VisibleForTesting`. Expanded KDoc explicitly warns that this function uses a simplified linear formula incompatible with `HeroScore.totalScore` values from `rankAll`. Added note in KDoc pointing to the long-term fix (unified `simplified = true` parameter).

---

### P2-04 · TD-09 numbering gap in technical-debt register — [OPEN]
`grep TD-09` returns no hits. Register gaps from TD-09 to TD-12. Needs reconciliation or documentation of what TD-09 covered.

---

### P2-05 · No compile-time enforcement of repository write-path invariant — [OPEN]
`SaveDraftSessionUseCase` is the only intended write path to `DraftSessionDao`. Convention enforced by review only. Requires custom Lint rule or ArchUnit test.

---

### P2-06 · Single Gradle module cannot enforce dependency rule at compile time — [OPEN]
The Clean Architecture rule (`presentation → domain ← data`) is a review convention, not a build constraint. Tracked in roadmap as `P2/M` (split into `:domain`, `:data`, `:app` modules).

---

## P3 — Deprecations / Outdated Libraries

### P3-01 · Gson used instead of `kotlinx.serialization` — [OPEN]
Gson uses reflection; `kotlinx.serialization` is compile-safe and ~2× faster on Android. Migration tracked in roadmap backlog.

---

### P3-02 · No CI pipeline — [PARTIALLY RESOLVED]
**Fix applied:** `.github/workflows/ci.yml` added. Runs `./gradlew lint testDebugUnitTest assembleDebug` on every push/PR to main and feature branches. The existing `build.yml` handles the release APK flow separately.

---

### P3-03 · `ktlint` / `detekt` not configured — [OPEN]
No automated style or complexity gating. Adding `detekt` with a baseline would catch future magic-literal patterns and function-length violations at commit time.

---

## P4 — Gaps

### P4-01 · No Room migration test — [OPEN]
Migrations 1→2→3 are untested. A broken migration silently destroys user draft history on upgrade. `MigrationTestHelper` round-trip test needed.

---

### P4-02 · No `kotlinx.serialization` for domain/network models — [OPEN]
See P3-01.

---

### P4-03 · No remote crash reporting — [OPEN]
Crashes are captured locally in `CrashLogStore`. No remote visibility. Firebase Crashlytics or Sentry Android SDK would provide immediate regression detection.

---

### P4-04 · API backend liveness not documented or tested — [OPEN]
`META_API_BASE_URL` (`https://api.mlbb-assistant.com/`) is assumed live. If down, the app silently uses stale bundled JSON with no staleness indicator. `lastFetchedAt` timestamp recommended in `MetaSnapshotDto`.

---

### P4-05 · No baseline profile configured — [OPEN]
Cold-start latency (time from "Start Draft" tap to floating bubble appearing) is unmeasured. `BaselineProfileGenerator` instrumented test recommended.

---

## Quick-Win Status

| # | Issue | Status |
|---|---|---|
| QW-01 | P0-02: `state.session!!` | ✅ RESOLVED |
| QW-02 | P0-03: `result.data!!` | ✅ RESOLVED |
| QW-03 | P0-01: `imageReader!!.surface` | ✅ RESOLVED |
| QW-04 | P2-02: Dead constant | ✅ RESOLVED |
| QW-05 | P3-02: CI pipeline | ✅ ADDED |
| QW-06 | P4-01: Migration test | OPEN |
