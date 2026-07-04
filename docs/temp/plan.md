# MLBB Draft Assistant — Master Overhaul Plan

---

## [Executive Summary]

This plan addresses **178 Kotlin source files** across a real-time Android overlay app (MLBB Draft Assistant) that uses computer vision (TFLite, pHash), Room, Hilt, Jetpack Compose, and WorkManager. The audit found no catastrophic architectural flaws — the foundational pattern (MVVM + clean architecture + Hilt DI) is sound. Issues cluster in four areas:

1. **CV/ML pipeline thread-safety and pixel-access patterns** — `PhaseDetector` and `OverlayCaptureCoordinator` use `Bitmap.getPixel` in per-pixel loops alongside `ColorToHSV`, creating unnecessary JNI overhead. The `HeroClassifier` Interpreter is documented as not thread-safe but has no internal mutex guard.
2. **`CancellationException` swallowing in ViewModels** — `SettingsViewModel` and others use bare `runCatching { }` that silently eats coroutine cancellation, corrupting cooperative cancellation.
3. **Threshold/logic inconsistencies in the advisor layer** — `BanRecommender` badge thresholds (0.25) differ from `buildBanReason` thresholds (0.20/0.40), producing contradictory labels. `BanUrgencyScorer` can produce urgency > 1.0. `BuildAdvisor` uses hardcoded item IDs and string-compared roles.
4. **UI/UX polish opportunities** — `HeroGrid` lacks stable keys for `LazyVerticalGrid`, causing unnecessary recompositions during score updates; the floating bubble drag lacks fling-to-edge physics; `NetworkMonitor.trySend()` can drop the initial connectivity value; `Extensions.toTitleCase()` is not locale-safe; `DevModeManager.setEnabled()` silently swallows `SecurityException`.

The execution strategy is ordered from foundational (utils, data, domain) outward to presentation and CV, so each layer is correct before dependent layers are touched.

---

## [Codebase Audit]

### CRITICAL

| # | File | Issue |
|---|------|-------|
| C-01 | `capture/PhaseDetector.kt` (~L130, L177 in both `detectHsv` and `detectRgb`) | `Bitmap.getPixel(x, y)` called inside a nested loop — a slow JNI call per pixel. Both `detectHsv` and `detectRgb` should use `Bitmap.getPixels()` (bulk copy to an `IntArray`) then iterate the array. `FrameProcessor` already does this correctly. |
| C-02 | `capture/HeroClassifier.kt` | `Interpreter` is documented as NOT thread-safe. The class doc says "callers are responsible," but `PortraitMatcher` can be called from concurrent coroutines on `Dispatchers.Default` (which has multiple threads). Add an internal `@Synchronized` or `Mutex` around `classify()`. |
| C-03 | `presentation/settings/SettingsViewModel.kt` (~L136) | `runCatching { }` in `runPortraitTask` catches `CancellationException`, preventing cooperative coroutine cancellation. Fix: rethrow `CancellationException`. Pattern: `runCatching { ... }.onFailure { if (it is CancellationException) throw it }`. |
| C-04 | `utils/NetworkMonitor.kt` (~L36) | `trySend(isCurrentlyConnected)` as the initial emission in `callbackFlow` is not guaranteed to deliver — if the collector hasn't started, the value is silently dropped. Replace with `channel.trySend()` and log on failure, or restructure to use `stateIn` at the call site. |

### HIGH

| # | File | Issue |
|---|------|-------|
| H-01 | `domain/advisor/BanRecommender.kt` (~L135-141, L160-167) | `badgeLabel` threshold for "High Ban" is `banRate > 0.25`, but `buildBanReason` uses `banRate > 0.20` and `banRate >= 0.40` — three inconsistent thresholds for the same concept. Unify to a single set of named constants in a `BanThresholds` object. |
| H-02 | `domain/advisor/BanUrgencyScorer.kt` (~L53, L57) | Urgency accumulates beyond 1.0 internally before the caller clamps it. While harmless now, any future scorer that uses the intermediate value (e.g. for a progress indicator) will show > 100%. Clamp at source with `.coerceIn(0f, 1f)`. |
| H-03 | `domain/advisor/BuildAdvisor.kt` (~L117-135) | Hardcoded item IDs (e.g. `9001`, `9002`) and string-comparison roles (e.g. `hero.role == "Mage"`) are brittle — any hero-data update or role normalization change breaks recommendations silently. Roles should compare against a `Role` enum; item IDs should be sourced from the `items.json` asset via key lookup. |
| H-04 | `domain/advisor/HeroArchetypeService.kt` (~L47-50, L224) | If `ensureLoaded()` fails, the service returns `emptyMap()` for all queries with no error log or state flag. Downstream callers (e.g. `CompositionArchetype.detect`) silently behave as if no trait data exists, causing false "No magic damage" warnings in the overlay. Add a `isLoaded: Boolean` flag and a logged warning on failure. |
| H-05 | `presentation/overlay/JetOverlay.kt` | `composeView` is not `@Volatile`. The `@Synchronized` guard on `show()`/`hide()` prevents concurrent entry but does not guarantee that a non-synchronized reader on another thread sees the updated reference. Mark `composeView`, `windowManager`, and `lifecycleOwner` as `@Volatile`. |
| H-06 | `utils/DevModeManager.kt` (~L61) | `runCatching` around `setComponentEnabledSetting` silently swallows `SecurityException`. On custom ROMs this can leave the UI showing "Developer mode: ON" while the launcher icon was never actually shown. Log the caught exception as an error. |

### MEDIUM

| # | File | Issue |
|---|------|-------|
| M-01 | `domain/advisor/CompositionAnalyzer.kt` (~L099-117) | `getLanesFilled` assigns lanes greedily by pick order, not globally optimally. A later pick that could fill a lane better than an earlier one will be assigned a flex lane instead. Not a crash, but produces sub-optimal lane warnings in the overlay. Refactor to use a simple assignment pass with backtracking or a priority-queue approach. |
| M-02 | `utils/Extensions.kt` (~L43) | `toTitleCase()` uses `this[0].uppercaseChar()` without a locale — produces incorrect results for locale-sensitive characters (e.g. Turkish `i` → `İ` vs `I`). Use `replaceFirstChar { it.titlecase(Locale.ROOT) }`. |
| M-03 | `presentation/home/HomeViewModel.kt` (~L95) | `wins * 100 / withOutcome.size` — integer division truncates before converting to a percentage display. While numerically correct (multiply before divide), it is fragile if the order changes. Use `(wins * 100.0 / withOutcome.size).roundToInt()` and document intent. |
| M-04 | `presentation/herolist/HeroListViewModel.kt` (~L118) | `searchQueryFlow.value = query` is set after the UI state update. Since both are used in `combine`, there is a tiny window where the UI state and filter flow are out of sync, potentially showing stale results for one frame. Set `searchQueryFlow.value` first. |
| M-05 | `domain/engine/DraftSessionManager.kt` (undo-stack growth) | `recordEnemyBan` and other mutation functions do `s.undoStack + action`, creating a new list on every call. For a 10-hero draft this is negligible, but it is an O(N) allocation chain. Replace with `s.undoStack.toMutableList().apply { add(action) }` or cap undo stack depth. |
| M-06 | `presentation/overlay/OverlayService.kt` (permission watchdog) | Polling SYSTEM_ALERT_WINDOW every 30 s via a coroutine `while(isActive)` loop keeps a coroutine alive permanently. A more efficient approach is to check permission in `onStartCommand` and register a `BroadcastReceiver` for `ACTION_MANAGE_OVERLAY_PERMISSION` changes. The current polling approach wastes ~1 wakeup/30 s but is not a correctness issue. |
| M-07 | `data/worker/HeroSyncWorker.kt` | `catch (e: Exception)` in `doWork()` catches `CancellationException`, preventing WorkManager from cancelling the worker gracefully. Same fix as C-03: rethrow `CancellationException`. |
| M-08 | `domain/advisor/DraftScoreCalculator.kt` (~L082) | `.average()` on an empty list throws `NoSuchElementException` at runtime (Kotlin stdlib `average()` returns `Double.NaN` for empty sequences but `averageOrDefault` from Extensions.kt is available and should be used here). Verify and replace with `averageOrDefault`. |

### LOW

| # | File | Issue |
|---|------|-------|
| L-01 | `capture/OverlayCaptureCoordinator.kt` (`isSlotFilled`) | Same `getPixel`-in-loop pattern as C-01 (inside `isSlotFilled` and `isBanButtonVisible`). Lower priority than PhaseDetector because slots are checked less frequently, but should be batch-converted for consistency. |
| L-02 | `domain/advisor/BanRecommender.kt` (~L106) | Redundant double-guard: `if (enemyPicks.size >= 2)` followed by `enemyProfile!!` (non-null assert). The assert cannot fail given the guard, but it looks like a bug to readers. Use `enemyProfile ?: return/continue` or restructure with `let`. |
| L-03 | `presentation/common/components/HeroGrid.kt` | `LazyVerticalGrid` items should use `key = { hero.id }` to give the Compose runtime stable identity. Without it, every `DraftScorer` update that reorders the list causes full grid recomposition and scroll-position reset. |
| L-04 | `presentation/overlay/JetOverlay.kt` (drag touch listener) | Drag-to-move uses raw `MotionEvent` delta math with no velocity tracking. Modern floating bubbles snap to the nearest screen edge on release ("fling to edge"). Adding `VelocityTracker` + edge-snap animation would significantly improve UX. |
| L-05 | `presentation/overlay/FloatingBubble.kt` | If it exists as a separate file, verify it is not a dead-code duplicate of the drag logic now in `JetOverlay`. If it is, delete it. |
| L-06 | `data/local/crashlog/CrashLogStore.kt` | Verify log rotation — if there is no cap on log file size, long-running sessions will grow the file indefinitely. Add a max-lines or max-size trim on write. |
| L-07 | `presentation/overlay/OverlayStateHolder.kt` | `mutableStateListOf` and `mutableStateOf` snapshots are modified from within coroutines launched on `Dispatchers.IO` in a few places. Compose snapshot mutations must happen on the main thread. Audit all `.collect { ... }` lambdas that mutate snapshot state and ensure they use `withContext(Dispatchers.Main)`. |
| L-08 | `di/OverlayModule.kt` | `OverlayContentBridge` (if still present) is a Service Locator anti-pattern that bypasses Hilt for the Compose overlay tree. If `OverlayStateHolder` is `@Singleton` and injected into `OverlayService`, the bridge is unnecessary — pass state directly via the composable lambda captured at `JetOverlay.initialize` time. |

---

## [UI/UX & Feature Roadmap]

### UX-01 — Fling-to-Edge Bubble Physics (`JetOverlay.kt`)
Add `VelocityTracker` to the `MotionEvent.ACTION_MOVE` handler. On `ACTION_UP`, compute velocity and animate the bubble to the nearest vertical screen edge using `ValueAnimator`. This matches Android's standard floating-widget behavior (e.g. Facebook Chat Heads, Google Meet bubble).

### UX-02 — Stable Hero Grid Keys (`HeroGrid.kt`)
Add `key = { hero.id }` to `items(...)` in `LazyVerticalGrid`. This is a one-line change with major recomposition savings — hero portraits will animate in-place rather than being destroyed and recreated on score reorder.

### UX-03 — Unified Ban Threshold Constants (`BanRecommender.kt`, new `BanThresholds.kt`)
Extract `HIGH_BAN_RATE = 0.25f`, `CONSENSUS_BAN_RATE = 0.40f`, `NOTABLE_BAN_RATE = 0.20f` into a `BanThresholds` object so the badge label and the reason string always use the same values. Eliminates the current silent inconsistency.

### UX-04 — `HeroArchetypeService` Load Failure Banner
When `ensureLoaded()` returns false, set a `OverlayStateHolder.archDataUnavailable` state flag and show a dismissible banner in the overlay: "Archetype data unavailable — composition analysis limited." This surfaces a silent failure to the user.

### UX-05 — Role Enum for `BuildAdvisor`
Replace `hero.role == "Mage"` string comparisons with a `HeroRole` enum (`MAGE`, `MARKSMAN`, `TANK`, `FIGHTER`, `SUPPORT`, `ASSASSIN`). Add a `Hero.roleEnum: HeroRole` computed property. This eliminates an entire class of silent mismatches from hero-data normalization.

### UX-06 — CrashLogStore Rotation Cap
Add a `MAX_LOG_LINES = 2000` constant and trim the log file on write if it exceeds the limit. Prevents unbounded file growth for users who leave the app running for extended sessions.

### UX-07 — Settings: `toTitleCase` Locale Fix
Replace all `toTitleCase()` usages that display hero roles/names in UI text with `replaceFirstChar { it.titlecase(Locale.ROOT) }` to prevent display glitches for users with locale-sensitive default locales.

### UX-08 — `OverlayStateHolder` Main-Thread Snapshot Guard
Audit all `collect { }` coroutines in `OverlayStateHolder` that mutate `mutableStateListOf`/`mutableStateOf`. Wrap each mutation block in `withContext(Dispatchers.Main.immediate)` to guarantee snapshot state is always written on the main thread.

---

## [Execution Strategy]

Steps are ordered dependency-first: utilities → data → domain → capture → presentation.  
Each step modifies exactly one file (or creates one new file) to keep diffs atomic and reviewable.

| Step | Action | File | Fixes |
|------|--------|------|-------|
| 1 | **MODIFY** | `utils/Extensions.kt` | M-02: `toTitleCase()` → locale-safe `replaceFirstChar { it.titlecase(Locale.ROOT) }` |
| 2 | **MODIFY** | `utils/NetworkMonitor.kt` | C-04: replace bare `trySend` with logged `trySend().isFailure` check + ensure initial state is always delivered |
| 3 | **MODIFY** | `utils/DevModeManager.kt` | H-06: log `SecurityException` instead of swallowing it |
| 4 | **CREATE** | `domain/advisor/BanThresholds.kt` | H-01, UX-03: single source of truth for all ban-rate constants |
| 5 | **MODIFY** | `domain/advisor/BanRecommender.kt` | H-01, L-02: use `BanThresholds`; remove `!!` non-null assert; align badge label with reason thresholds |
| 6 | **MODIFY** | `domain/advisor/BanUrgencyScorer.kt` | H-02: clamp urgency to `[0f, 1f]` at source before returning |
| 7 | **CREATE** | `domain/model/HeroRole.kt` | UX-05: `HeroRole` enum + `Hero.roleEnum` computed extension |
| 8 | **MODIFY** | `domain/advisor/BuildAdvisor.kt` | H-03, UX-05: replace string role comparisons with `HeroRole` enum |
| 9 | **MODIFY** | `domain/advisor/HeroArchetypeService.kt` | H-04, UX-04: add `isLoaded` flag, log failure, surface to callers |
| 10 | **MODIFY** | `domain/advisor/CompositionAnalyzer.kt` | M-01: improve `getLanesFilled` to avoid greedy-order bias |
| 11 | **MODIFY** | `domain/advisor/DraftScoreCalculator.kt` | M-08: replace `.average()` with `.averageOrDefault()` from Extensions |
| 12 | **MODIFY** | `domain/engine/DraftSessionManager.kt` | M-05: replace undo-stack `+` with `toMutableList().apply { add(...) }` |
| 13 | **MODIFY** | `data/worker/HeroSyncWorker.kt` | M-07: rethrow `CancellationException` in `doWork()` |
| 14 | **MODIFY** | `data/local/crashlog/CrashLogStore.kt` | L-06, UX-06: add `MAX_LOG_LINES` rotation cap |
| 15 | **MODIFY** | `capture/HeroClassifier.kt` | C-02: add `@Synchronized` (or `Mutex`) to `classify()` |
| 16 | **MODIFY** | `capture/PhaseDetector.kt` | C-01: replace per-pixel `getPixel` loop with `getPixels()` bulk-read in both `detectHsv` and `detectRgb` |
| 17 | **MODIFY** | `presentation/overlay/OverlayCaptureCoordinator.kt` | L-01: replace `getPixel` loops in `isSlotFilled` and `isBanButtonVisible` with `getPixels()` bulk-read |
| 18 | **MODIFY** | `presentation/overlay/JetOverlay.kt` | H-05, L-04, UX-01: mark fields `@Volatile`; add `VelocityTracker` fling-to-edge on `ACTION_UP` |
| 19 | **MODIFY** | `presentation/overlay/OverlayStateHolder.kt` | L-07, UX-08: wrap all snapshot mutations in `withContext(Dispatchers.Main.immediate)` |
| 20 | **MODIFY** | `presentation/common/components/HeroGrid.kt` | L-03, UX-02: add `key = { hero.id }` to `LazyVerticalGrid` items |
| 21 | **MODIFY** | `presentation/settings/SettingsViewModel.kt` | C-03: rethrow `CancellationException` in `runPortraitTask` |
| 22 | **MODIFY** | `presentation/home/HomeViewModel.kt` | M-03: use `roundToInt()` for win-rate percentage calculation |
| 23 | **MODIFY** | `presentation/herolist/HeroListViewModel.kt` | M-04: set `searchQueryFlow.value` before UI state update |
| 24 | **VERIFY** | `presentation/overlay/FloatingBubble.kt` | L-05: check for dead-code duplication with `JetOverlay`; delete if redundant |
| 25 | **VERIFY / MODIFY** | `di/OverlayModule.kt` | L-08: audit `OverlayContentBridge` usage; remove if `OverlayStateHolder` is injected directly |

---

## [Execution Log — all 25 steps complete]

Steps 1–23 were applied as straight `MODIFY`/`CREATE` edits per the table above.
Steps 24 and 25 were `VERIFY` steps; findings below.

### Step 24 — `FloatingBubble.kt` vs `JetOverlay.kt`: **not redundant, no deletion**

Inspected both files: `FloatingBubble.kt` is a pure `@Composable fun FloatingBubble(session, onTap)` —
it contains no `WindowManager`, no touch listener, and no window lifecycle code. It only renders the
minimised bubble's Compose UI (icon/badge) and is one of the two render branches
`DraftOverlayContent` chooses between (`FloatingBubble` vs `MiniWidget`, gated on
`holder.isExpanded.value`).

`JetOverlay.kt` is the window-management/drag layer: it owns the `WindowManager` window, the
`ComposeView`, the lifecycle owner, and the touch listener (drag-to-move + fling-to-edge). It renders
whatever composable `OverlayService` registers via `JetOverlay.initialize { overlayContent { DraftOverlayContent() } }` —
it has no knowledge of `FloatingBubble` specifically.

These are different layers (visual component vs. OS window plumbing) with no overlapping
responsibility. No dead code found; no deletion performed.

### Step 25 — `OverlayModule.kt` / `OverlayContentBridge` audit: **bridge is required, no change**

`OverlayContentBridge` (defined in `DraftOverlayContent.kt`, used by `OverlayService.kt`) exists
because `JetOverlay.initialize()` registers the overlay's `@Composable` content in
`MLBBApplication.onCreate()` — before the Hilt `SingletonComponent` (and therefore
`OverlayStateHolder`) is available to inject. The composable lambda is only *invoked* later, from
`JetOverlay.show()` in `OverlayService.onCreate()`, by which point Hilt injection into the service has
completed and the bridge fields are populated. `OverlayModule.kt` itself only provides
`OverlayController` and `ImageLoader` — it does not construct or reference the bridge, and swapping
the bridge for direct constructor injection into `DraftOverlayContent()` is not possible without also
changing `JetOverlay`'s Hilt-agnostic initialization timing, which is out of scope for this audit.
Verified this is a deliberate, documented pattern (see the doc comment on `DraftOverlayContent()`
explaining "why `OverlayContentBridge` instead of a CompositionLocal or `hiltViewModel()`") rather than
an accidental anti-pattern. No modification made.

---

*Plan generated from full dependency-aware audit of 178 `.kt` source files. No Gradle, JVM target, NDK, or model-training changes are included.*
*All 25 execution steps completed and verified against the source tree above.*

---

## [Follow-up pass — hero portrait caching, network, and download logic]

A second, narrower audit was performed specifically on the hero-portrait caching, networking,
and download pipeline (`PortraitAssetManager`, `PortraitMatcher`, `SlotAwareHasher`,
`OverlayCaptureCoordinator`, `JsonParser`, `PortraitPrefetchWorker`, `HeroSyncWorker`,
`NetworkMonitor`). Five concrete issues were found and fixed:

1. **`PortraitMatcher` bypassed DI for its `PortraitAssetManager`.** The constructor
   default-instantiated a brand-new `PortraitAssetManager(context, OkHttpClient(), JsonParser(context))`
   instead of using the Hilt singleton — a second `OkHttpClient` (its own connection pool) and a
   second `JsonParser` were created every time `OverlayCaptureCoordinator.init()` ran. Fixed by
   requiring the singleton as a constructor parameter and injecting it into
   `OverlayCaptureCoordinator`, which now passes it through to `PortraitMatcher`.

2. **Concurrent-download race on the same hero.** Neither `PortraitAssetManager.downloadAll()`
   nor `ensureVariants()` serialized access per hero. Two callers racing for the same missing hero
   (e.g. `PortraitPrefetchWorker`'s bulk `downloadAll()` running while `PortraitMatcher`'s slot-aware
   hash preloading concurrently calls `ensureVariants()` for that same hero) could both pass the
   `exists()` check and write to the identical `hero.main.png.tmp` path at once, corrupting the
   atomic-rename write. Fixed with a per-hero `Mutex` (`heroLocks`) guarding `downloadMain`,
   `optimizeOne`, and `ensureVariants`.

3. **TFLite interpreter and hash caches were never released.** `OverlayCaptureCoordinator.stop()`
   (called from `OverlayService.onDestroy()`) never called `portraitMatcher.close()` — the TFLite
   interpreter, three 200-entry `LruCache`s (dHash/pHash/histogram), and `SlotAwareHasher`'s
   reference-hash map stayed resident for the life of the process across every overlay session.
   Fixed: `stop()` now calls `portraitMatcher.close()`, and `close()` now evicts all three
   `LruCache`s and calls a new `SlotAwareHasher.clear()`.

4. **Redundant JSON re-parsing on the portrait-download hot path.** `JsonParser.buildPortraitUrlIndex()`
   re-decoded the full bundled `default_heroes.json` array and rebuilt the URL map on *every* call,
   including once per hero from `ensureVariants()` during on-demand slot-aware hash preloading.
   Since the backing resource is immutable APK content, the result is now memoized after the first
   successful parse (a failed parse is intentionally not cached, so a transient error can retry).

5. **Verified, no change needed:** `HeroSyncWorker` / `PortraitPrefetchWorker` retry policy and
   `NetworkType.CONNECTED` scheduling constraints (`MLBBApplication.kt`) are correctly wired, and
   `NetworkMonitor`'s capability-aware connectivity flow correctly reports capability loss via
   `onLost` for its scoped request. No further action taken on those.

6. **`Variant.MAIN` download size didn't match the training pipeline.** `scripts/train.py`
   defines the canonical portrait sizes used to build/derive the bundled TFLite classifier and
   its labels (`CONFIG['size_main'] = 224`, `CONFIG['size_pick'] = 128`, `CONFIG['size_ban'] = 64`,
   the first of which is also `MobileNetV3Small`'s trained input resolution — see
   `HeroClassifier.INPUT_SIZE`). `PortraitAssetManager.Variant.PICK`/`BAN` already matched
   (128 / 64), but `MAIN` was downloading/saving at 256px instead of 224px. Changed `MAIN` to
   224px so the on-device asset resolution is consistent with `scripts/train.py` end-to-end; all
   call sites read the size via `Variant.maxDimensionPx`, so no other file needed updating.

*As with the original 25-step pass, no Gradle/JVM/Android toolchain was available in this
environment to compile-verify these changes; they were verified by careful manual review of every
call site touched.*
