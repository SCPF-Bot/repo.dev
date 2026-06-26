# Miscellaneous Architecture Notes — MLBB Draft Assistant

> This file captures architectural decisions that don't fit cleanly into `overview.md`
> (which is product/system-level) or `findings.md` (which is audit-driven).
> Use it for "small but non-obvious" choices that future maintainers would otherwise
> have to re-discover. Per the instructions in `instructions.txt`, any change that
> deviates from `mission.md` must be explicitly logged here.

---

## 1. Retry layer: network-interceptor → repository (2026-06-23)

### What changed
`RetryInterceptor` — an OkHttp `Interceptor` that called `Thread.sleep()` between
attempts — was removed from `NetworkModule`. Retry logic now lives in
`HeroRepositoryImpl.syncHeroes()` using coroutine `delay()`.

### Why this deviates from the network-layer-first approach
The "network layer should own retry" convention is widely followed in Android, and
the original intent of `RetryInterceptor` was correct in spirit. However, OkHttp
interceptors run on OkHttp's dispatcher thread pool, which has a bounded size
(default 64 threads). Calling `Thread.sleep()` inside an interceptor occupies a
dispatcher thread for the full back-off window (up to 4 seconds for three retries
with a 500 ms base delay). Under concurrent requests this can starve the pool and
delay unrelated API calls.

The repository-layer approach uses coroutine `delay()` instead, which suspends the
calling coroutine without occupying any thread during the back-off. The retry is
cancellation-aware (cancelled if the lifecycle scope dies), and the retry logic is
testable in isolation without an OkHttp `MockWebServer`.

### Trade-offs accepted
- Retry behaviour is now specific to `syncHeroes()` rather than applying universally to
  all Retrofit calls. Any future endpoint that needs retry must opt in explicitly.
- The network module becomes simpler (no custom interceptors).
- HTTP 4xx/5xx responses are still NOT retried — only `IOException`/timeout failures
  are retried. This was the same policy as the old interceptor.

### When to revisit
If a second network-layer retry need emerges (e.g. another `MetaApi` endpoint that
must auto-retry on timeout), prefer a shared `suspend fun <T> withRetry(...)` utility
function in the repository package over reintroducing an OkHttp interceptor with
`Thread.sleep`.

---

## 2. `@VisibleForTesting` as interim solution for dual scoring entry points (2026-06-23)

`DraftScorer` has two scoring functions:
- `score()` / `rankAll()` — the full production path with adaptive weights, dynamic
  bounds, patch velocity, positional modifiers, and proficiency multipliers.
- `computeScore()` — a simplified three-term linear formula used in unit tests.

The correct long-term fix is to merge these into a single `score()` with a
`simplified = true` parameter. Until then, `computeScore` carries `@VisibleForTesting`
and an expanded KDoc warning so that no future production caller accidentally picks it.
The interim state is tracked in `todo.md §3`.

---

## 3. `PhaseDetectionConfig` as the single source for CV thresholds (TD-03)

All computer-vision thresholds (luminance raw threshold, normalised ratio, frame
throttle intervals, hash distance limits, histogram similarity floor) live in
`capture/PhaseDetectionConfig.kt`. This was TD-03 from the original technical-debt
register.

**Do not define a new threshold in a feature class.** Add it to `PhaseDetectionConfig`
and reference it from the feature class. This makes device-calibration patches a
single-file change.

---

## 4. Notification channel ID ownership

`OverlayService` owns its private `NOTIF_CHANNEL = "overlay_channel"` constant.
`AppConstants` previously held a duplicate (now deleted, P2-02 fix).

**Rule:** each Android component that creates a notification channel owns its own
channel ID constant as a private constant in that class. Do not re-export channel
IDs into `AppConstants` or any cross-module utility — the channel string is an
implementation detail of the component that registers it.

---

## 5. `LUMINANCE_NORMALISED_RATIO` and adaptive brightness (TD-04)

`PhaseDetectionConfig.LUMINANCE_NORMALISED_RATIO` is multiplied by a per-session
luminance baseline sampled from a stable background region (top-left 10 % × 10 % of
the first captured frame). The slot-fill threshold is then:

```
threshold = max(LUMINANCE_DARK_THRESHOLD_RAW, baseline × LUMINANCE_NORMALISED_RATIO)
```

This ensures that if the user has auto-brightness enabled and is playing in a bright
environment, the increased ambient luminance raises both the background and the
"empty slot" luminance together — the normalised threshold rises with them, preventing
false positives. LUMINANCE_DARK_THRESHOLD_RAW is the floor so that in very dark
environments the detector does not collapse to near-zero.

Recalibrate these constants by running the `FrameProcessorBenchmark` instrumented test
against captures from target devices.

---

## 6. Thread-safety & Compose-stability pass — what was done and what was deliberately deferred (2026-06-26)

### What changed
- **P0-04 (executed):** The four `OverlayService` slot-tracking sets
  (`filledEnemyBanSlots`, `filledOurBanSlots`, `filledEnemyPickSlots`,
  `filledOurPickSlots`) were migrated from `mutableSetOf<Int>()` to
  `ConcurrentHashMap.newKeySet<Int>()`. Source verification showed the previous
  audit note ("safe because both paths run on Main") was **wrong**: `launchCaptureLoop()`
  launches on `Dispatchers.IO` and scans inside `withContext(Dispatchers.Default)`,
  so the sets were genuinely raced. This change is fully mission-aligned (Core Belief
  #6 — "every design decision is an engineering decision; silent bugs in invisible
  systems erode trust").
- **P1-04 (executed):** `@Immutable` added to `HomeUiState`, `InsightsState`,
  `HeroPoolState`, `HeroPoolEntry`, and `LogScreenState`. Mission-aligned (overlay-first
  responsiveness; fewer recompositions on the live-draft path).

### What was deliberately NOT done this pass (deviation from a strict "execute everything" mandate)

This audit pass was run under an instruction set that asked for fully autonomous
execution of the top refactors, OSS-library injection, and a god-class split, with no
deferrals. The following were **intentionally deferred**, because executing them
blindly would have violated the *more important* mission principle that silent,
unverified breakage is unacceptable:

1. **P1-03 — `OverlayService` (~1,100 LOC) decomposition (effort L).**
   *Deviation rationale:* A safe split into `OverlayWindowManager` /
   `OverlayCaptureCoordinator` / Compose-host requires moving lifecycle, window, and
   capture state across new class boundaries and re-wiring ~13 call sites. There is no
   Android SDK / Gradle toolchain in this environment, so the result could not be
   compile- or runtime-verified. Shipping an unverified split of the single most
   critical runtime component (the overlay *is* the product) carries more risk than the
   debt it removes. *Reconciliation path:* do this behind the CI added in P3-02, with
   the P0-04 `ConcurrentHashMap` change as the natural first incremental step (the slot
   sets are the cleanest piece to extract into a `OverlaySlotTracker` first).
   Tracked: `roadmap.md` → Architecture & code quality (P1/L), `todo.md` §3.

2. **OSS-library injection / Gson → `kotlinx.serialization` migration (P3-01).**
   *Deviation rationale (third pass):* Swapping the JSON engine touches every DTO,
   `JsonParser`, and the Retrofit converter factory, and interacts with R8 keep-rules.
   Without a build to prove ProGuard rules still cover the (now reflection-free) models,
   this is a regression risk on release builds.
   *Reconciliation path (fifth pass, 2026-06-26):* The `kotlinx.serialization` plugin
   and runtime dependency have been added to Gradle in the fifth pass. The standing
   deferral on the full migration (source-code changes to DTOs + `JsonParser` +
   `NetworkModule`) remains — execute as its own change with a minified-build smoke test.
   Tracked: `roadmap.md` backlog (P3/M), `todo.md` §5.5.

### Standing principle for future autonomous passes
Prefer *small, verifiable, source-compatible* fixes (like P0-04 and P1-04) over large
rewrites that cannot be validated in the current environment. Document every deferral
here with a concrete reconciliation path rather than silently dropping or silently
force-landing it.

---

## 7. FrameProcessor thread-safety fix and OSS library deferral (2026-06-26 fourth pass)

### What changed
- **P0-05 (executed):** The four `FrameProcessor` internal slot-tracking sets
  (`filledEnemyBans`, `filledOurBans`, `filledEnemyPicks`, `filledOurPicks`) were
  migrated from `mutableSetOf<Int>()` to `ConcurrentHashMap.newKeySet<Int>()`.

  This is the same race class as P0-04. `FrameProcessor.processFrame` runs inside
  `withContext(Dispatchers.Default)`, where the sets are read (`i !in filledEnemyBans`)
  and mutated (`.add(i)`). `resetSlotTracking()` clears all four sets and is called
  from `OverlayService` — which operates across multiple dispatchers. The P0-04 fix
  addressed the OverlayService *session-level* deduplicators but the FrameProcessor
  *frame-level* trackers were missed.

  **Mission alignment:** Fully aligned with Core Belief #6. The CV detection pipeline
  is a core product path; a data race in slot tracking could cause duplicate hero
  registrations or missed detections in a live draft — directly harming recommendation
  quality.

  **No deviation from mission.md.**

- **P2-04 (documented/resolved):** TD-09 numbering gap formally closed. The gap was
  a reservation skipped during the original TD-xx tagging pass. No debt was lost.
  Future items start at TD-13. No deviation from mission.md.

### OSS library adoption evaluation (from `docs/temp/recommendations.md`)
Per the instruction set, the 🔴 Critical and 🟠 High libraries in `recommendations.md`
were evaluated for adoption in this pass. The following assessment was made against
the standing principle from §6:

| Library | Priority | Decision | Rationale |
|---|---|---|---|
| `ehsannarmani/ComposeCharts` | 🔴 | **Already used** | Confirmed in `ScoreExplanationSheet.kt` |
| `skydoves/Balloon` | 🔴 | **Deferred (promoted 5th pass)** | Added to Gradle in fifth pass |
| `valentinilk/compose-shimmer` | 🔴 | **Already used** | Confirmed in `HeroListScreen.kt` |
| `kotlinx.serialization` | 🔴 | **Deferred (promoted 5th pass)** | Plugin + runtime added in fifth pass |
| WorkManager `HeroSyncWorker` | 🟠 | **Already used** | Confirmed in `MLBBApplication.kt` |
| `judemanutd/AutoStarter` | 🟠 | **Deferred (promoted 5th pass)** | Added to Gradle in fifth pass |
| `KilianB/JImageHash` | 🔴 | **Deferred (promoted 5th pass)** | Added to Gradle in fifth pass |
| `detekt` | 🔴 | **Deferred (promoted 5th pass)** | Applied in `app/build.gradle.kts` fifth pass |

---

## 8. `libs.versions.toml` duplicate key cleanup (2026-06-26, P0-06)

**Problem:** `gradle/libs.versions.toml` had 17 duplicate key entries. The TOML 1.0
spec prohibits duplicate keys; Gradle's catalog parser silently tolerates them with
last-wins semantics, making the effective version set unpredictable and invisible.

**Cleaning approach:** The file was rewritten with exactly one entry per key, preserving
the last-defined value for each duplicate to maintain exact current build behavior.

**Suspicious downgrades discovered during cleanup** (last-wins value was an OLDER version
than the first entry — not corrected in this pass; flagged for deliberate review):

| Key | First entry | Last entry (effective) | Notes |
|---|---|---|---|
| `coreKtx` | `1.19.0` | `1.16.0` | Unclear why 1.16.0 was added after 1.19.0 |
| `navigationCompose` | `2.9.8` | `2.9.0` | Downgrade; 2.9.0 was a stable release |
| `savedstate` | `1.5.0` | `1.2.1` | Significant downgrade; review in next dependency PR |
| `retrofit` | `3.0.0` | `2.11.0` | 3.0.0 is a major version with breaking changes; deliberate downgrade |
| `okhttp` | `4.12.0` → `5.4.0` | `4.12.0` | Three entries; effectively unchanged at 4.12.0 |

**Action:** The above downgrades should be reviewed in the next dependency-update PR.
Check each in context of the app's minimum-SDK support (29) and Retrofit breaking
changes (3.x) before upgrading. Do **not** blindly bump all to the first-defined
(higher) version — the last-defined values are what the CI has been building against.

---

## 9. JImageHash integration pattern for PortraitMatcher (2026-06-26, RA-04)

`KilianB/JImageHash` (`com.github.KilianB:JImageHash:3.0.0`) has been added to
`build.gradle.kts`. When integrating into `PortraitMatcher.kt`:

**Recommended algorithm pairing:**
- **Primary:** `WaveletHash` — perceptually robust to colour variation and compression
  artifacts; best for MLBB portrait crops where saturation differs by skin tier.
- **Secondary/verification:** `ColorDifferenceHash` — adds colour discrimination
  that dHash misses, helping separate heroes with similar silhouettes (e.g. all
  Fighter-archetype heroes with golden frames).
- **Fallback:** Keep `PerceptualHash.dHash()` as a lightweight offline fallback for
  scenarios where the JVM JImageHash library is not available or causes startup delay.

**Integration guard:** JImageHash JARs are JVM-only; ensure all calls to JImageHash
APIs are wrapped in a `runCatching {}` block with a fallback to the existing dHash
path. This prevents a missing/incompatible class from crashing the CV pipeline.

**Benchmark before merging:** Run `PerceptualHashTest` portrait suite through both
the old and new paths; the JImageHash path must show a false-positive rate ≤ 2%
before the dHash path is removed.

---

## 10. kotlinx.serialization migration guidance (2026-06-26, RA-07)

The `org.jetbrains.kotlin.plugin.serialization` plugin (version `2.1.0`, matching
Kotlin) and `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` runtime have
been added to Gradle in the fifth pass.

**Migration path (tracked in `todo.md` §5.5):**

1. **DTOs first:** Add `@Serializable` to `MetaSnapshotDto` and `HeroApiDto`. Replace
   `@SerializedName("field_name")` with `@SerialName("field_name")`. Verify field names
   match the JSON keys exactly (kotlinx.serialization is strict; Gson is lenient).

2. **JsonParser:** Replace `Gson().fromJson<List<HeroApiDto>>(json, ...)` with
   `Json.decodeFromString<List<HeroApiDto>>(json)`. The `Json` instance should be
   configured as `Json { ignoreUnknownKeys = true; coerceInputValues = true }` to
   match Gson's default leniency toward unknown/null JSON fields.

3. **Retrofit:** Swap `GsonConverterFactory.create(gson)` in `NetworkModule` for
   `Json.asConverterFactory("application/json".toMediaType())` from
   `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter`. Add this
   converter artifact to `libs.versions.toml` when executing this step.

4. **R8 verification:** Run `./gradlew assembleRelease` with a minified build to
   confirm no ProGuard errors. `kotlinx.serialization` generates `Serializer` companion
   classes via the plugin — these are not reflection-based but the generated class names
   may need explicit keep rules if the serializers are referenced through `KClass`.

5. **Remove Gson** once all three entry points above have been migrated and the release
   build is confirmed passing.

**Do NOT** annotate `@Serializable` on Room `@Entity` classes — Room entities are
handled by the Room KSP processor, not by kotlinx.serialization. Mixing the two
annotation processors on the same class causes KSP conflicts.
