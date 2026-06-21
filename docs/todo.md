# MLBB Draft Assistant — Master TODO Checklist

> Auto-derived from `ROADMAP.md` and direct code inspection. Every actionable item from every phase and the perpetual track is listed here as a checkbox.
>
> **Legend:** ✅ = completed and merged · unchecked box = not yet done.
>
> This file is the flat task view. For rationale, implementation notes, and phase context, see `ROADMAP.md`.

---

## Phase 0 — Stabilisation (Baseline) ✅ All Done

### Crash Fixes
- [x] Fix `DraftOutcome.DRAW` missing `when` branch in `DraftExporter.kt` (lines 104, 109)
- [x] Fix `DraftOutcome.DRAW` missing `when` branch in `DraftReplayScreen.kt` (line 227)
- [x] Fix DataStore multi-instance `IllegalStateException` — introduce single `AppDataStore.kt` delegate
- [x] Fix FGS `mediaProjection` crash on API 34+ — gate `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API level
- [x] Call `upgradeFgToProjection()` from `onStartCommand` after projection token is confirmed

### Onboarding
- [x] Reorder wizard steps: Overlay → Background Running → Battery → Auto-Start → Restricted Settings → Accessibility
- [x] Remove Screen Capture and Notifications wizard steps

### Overlay UX
- [x] Make touch listener mode-aware (bubble mode vs. widget mode)
- [x] Call `expandToWidget()` on tap in bubble mode
- [x] Dispatch synthetic `ACTION_CANCEL` to Compose when drag threshold is exceeded in widget mode

### CV Pipeline
- [x] Correct `SlotRegions.kt` from portrait-oriented (wrong) to landscape 1600×720 calibrated coordinates
- [x] Add normalized fraction coordinates for all ban slots (R1/R2, ally/enemy, slots 0–4)
- [x] Add normalized fraction coordinates for all pick slots (ally/enemy, slots 0–4)
- [x] Add normalized fraction coordinates for timer, phase banner, center display, rank emblem, first-pick indicator
- [x] Create `app/src/main/assets/draft_ui_map.json` with full region map (normalized + absolute pixels)

### Advisory Engine
- [x] Add `CompositionArchetype` enum with `DIVE`, `POKE`, `TURTLE`, `WOMBO_COMBO`, `SPLIT_PUSH`, `BALANCED`
- [x] Add `display`, `icon`, `winCondition`, `counterCondition` fields to `CompositionArchetype`
- [x] Implement priority-ordered `CompositionArchetype.detect()` companion function
- [x] Wire `CompositionAnalyzer.detectArchetype()` (calls `detect()` internally)
- [x] Replace hardcoded CC hero name list in `CompositionAnalyzer` with `hero.hasCCUlt` field _(TD-01)_

### MiniWidget Redesign
- [x] Both phase panels (BAN + PICK) always visible — never hidden
- [x] Sequential phase activation: BAN active → dimmed when PICK phase begins
- [x] Animated `PhasePanel` card with `animateFloatAsState` alpha and `animateColorAsState` border transitions
- [x] Left accent bar drawn on active `PhasePanel` via `drawBehind`
- [x] `BanSlotRow` — 5 ally ban slots + 5 enemy ban slots with divider
- [x] `PickSlotRow` — 5 ally pick slots + 5 enemy pick slots with divider
- [x] `SlotDot` composable — filled (colored bg + hero initial) vs. empty (dim outline)
- [x] `SlotGroup` composable with per-team label and 5-dot row, pads to 5 if fewer slots exist
- [x] Turn badge in BAN panel when ban phase is active ("YOUR TURN TO BAN" / "Enemy is banning…")
- [x] `RecommendedRow` in BAN panel — 2 rows of 3 hero chips when `banSuggestions` is non-empty
- [x] Turn indicator in PICK panel showing pick number (e.g., "Pick 3/10")
- [x] Trading phase banner shown when `session.phase == DraftPhase.TRADING`
- [x] `RecommendedRow` in PICK panel — 2 rows of 3 hero chips when `recommendations` is non-empty
- [x] `enemyWarnings.first()` shown below pick recommendations when warnings exist
- [x] `CompositionInsightsPanel` — rendered only when at least one hero has been picked
- [x] Composition insights: enemy archetype chip (icon + display name)
- [x] Composition insights: our team archetype chip (icon + display name)
- [x] Composition insights: win condition text box for our team (only when ourPickedHeroes non-empty)
- [x] `BottomActionBar` with [⏹ Min] [↩ Undo] [📊 Score] [✕ Close] buttons
- [x] Undo button dimmed when `session.undoStack` is empty
- [x] `IdleBody` with "Waiting for draft to begin…" placeholder
- [x] `IdleBody` team toggle (Ally / Enemy first) with visual selection state
- [x] `IdleBody` "▶ START DRAFT" button wired to `onStartDraft`
- [x] `CompleteBody` with "Draft complete ✅" message and close button
- [x] `WidgetHeader` with drag handle, "MLBB DRAFT ASSISTANT" label, phase badge, Minimize and Close buttons

### OverlayService Wiring
- [x] Add `onUndo = { draftSessionManager.undo() }` to `MiniWidget` call site
- [x] Add `onScoreDetails = { handleScoreDetails() }` to `MiniWidget` call site
- [x] Implement `handleScoreDetails()` — launch main app via `getLaunchIntentForPackage` with `FLAG_ACTIVITY_NEW_TASK`

---

## Phase 1 — Core Excellence (Months 0–3)

### 3.1 Overlay Hardening

- [ ] Serialize `DraftSession` to JSON (Kotlinx Serialization) on every `StateFlow` emission
- [ ] Write serialized session to DataStore on IO dispatcher (not Main)
- [ ] On `OverlayService.onCreate()`, rehydrate session from DataStore if non-IDLE/non-COMPLETE session exists
- [ ] Skip `initSession()` on restoration; jump directly to serialized phase
- [ ] Write integration test: kill and relaunch service, assert session equality (`DraftSessionSerializationTest`)
- [ ] Add `NotificationCompat.Action` ("▶ Relaunch Overlay") to FGS notification
- [ ] Wire action to `PendingIntent.getService()` with `ACTION_RELAUNCH` extra
- [ ] Show relaunch action only when service is running but overlay window is not visible
- [ ] Add coroutine watchdog in `OverlayService` polling `AccessibilityManager.isEnabled()` every 30 s
- [ ] Emit `ServiceEvent.AccessibilityDied` when accessibility service is killed mid-session
- [ ] Show in-overlay warning banner (below recommendation panel) with "Open Settings" deep link
- [ ] Cancel watchdog coroutine as a child of `serviceScope`
- [ ] Re-check `Settings.canDrawOverlays()` in `OverlayService.onStartCommand()`
- [ ] If overlay permission revoked: show system notification → wizard at specific revoked step
- [ ] Persist bubble position `overlayX`/`overlayY` to DataStore on `ACTION_UP` after drag _(TD-12)_
- [ ] Read saved bubble position from DataStore in `OverlayService.onCreate()` and set `LayoutParams` _(TD-12)_

### 3.2 CV Pipeline Improvements

- [ ] Consume `SlotRegions.rankEmblem` in auto-detection logic
- [ ] Implement k-nearest-centroid classifier over Lab colour space for rank emblem colour
- [ ] Map detected rank to `Rank` enum via `draftSessionManager.setRank()`
- [ ] Detect rank only once per session (on first non-IDLE phase frame)
- [ ] Fall back silently to manual rank toggle if confidence < 0.7
- [ ] Consume `SlotRegions.firstPickIndicator`
- [ ] Classify first-pick direction by left-half vs. right-half luminance asymmetry
- [ ] Auto-set `ourTeamFirst` in session manager when confidence ≥ 0.6
- [ ] Show manual toggle as confirmation step (not hidden entirely) when confidence < 0.6
- [ ] Extend `LruCache` to `LruCache<Int, PortraitSignature>` (holds dHash + colour histogram)
- [ ] Implement colour histogram computation: 8×8×8 RGB bins, cosine similarity
- [ ] Apply secondary histogram pass only when dHash similarity is in the 0.70–0.85 ambiguous zone
- [ ] Accept match only when dHash ≥ 0.72 AND histogram cosine similarity ≥ 0.75
- [ ] Validate `requiresConfirmation = true` rate drops below 8% in testing
- [ ] Add `mlkit-text-recognition` dependency to `app/build.gradle.kts`
- [ ] Implement ML Kit OCR pass reading "BAN PHASE 1" / "BAN PHASE 2" text from timer region
- [ ] Wire OCR as definitive phase signal when colour-based detection returns `UNKNOWN` or `SETUP`
- [ ] Define `R > 160` and `B > 140` as named constants in `PhaseDetectionConfig` object _(TD-03)_
- [ ] Add calibration test validating `PhaseDetectionConfig` constants against reference screenshots _(TD-03)_
- [ ] Normalise `isSlotFilled()` luminance threshold against a sampled background region _(TD-04)_
- [ ] Move `preloadHashes()` to a lazy background `Job` with `Deferred<Boolean>` _(TD-08)_
- [ ] Await the `Deferred` only when the first portrait match is requested _(TD-08)_

### 3.3 Advisory Engine Improvements

- [ ] Replace binary `isFirstPick`/`isLastPick` bonuses in `DraftScorer` with continuous pick-index ramp
- [ ] Implement `alphaSync(i)`, `alphaCounter(i)`, `alphaMeta(i)` formulas
- [ ] Unit test: synergy weight at index 0 equals default; at index 4 equals `default + 0.20`
- [ ] Split `BanRecommender` output into absolute bans and reactive bans lists
- [ ] Label absolute bans with "🔒 Always Ban" (criteria: `isToxicMechanic || isOP || banRate > 0.40`)
- [ ] Up-weight reactive bans based on detected enemy first-pick direction
- [ ] Render absolute bans first, then reactive bans in MiniWidget ban panel
- [ ] Compute `metaScore` normalisation bounds dynamically from 5th–95th percentile of dataset _(TD-05)_

### 3.4 UX Polish

- [ ] Replace `ACTION_ACCESSIBILITY_SETTINGS` with OEM-specific intent (Xiaomi: `com.miui.accessibility/.AccessibilityManagerActivity`)
- [ ] Add shimmy text on Restricted Settings wizard step explaining exact tap target
- [ ] On wizard `onResume`, re-evaluate permission state and auto-advance if granted

### 3.5 Score Explanation Bottom Sheet

- [ ] Implement `ModalBottomSheet` triggered by long-press on any hero chip in MiniWidget
- [ ] Show full score breakdown (meta, synergy, counter sub-scores, role bonus)
- [ ] List all synergy matches (hero name + portrait)
- [ ] List all counter matches (hero name + portrait)
- [ ] Show raw stats (win rate, ban rate, pick rate, tier)

### 3.6 Service IO Audit

- [ ] Audit all `OverlayService` IO call sites for use of `Dispatchers.Main` _(TD-06)_
- [ ] Wrap all DataStore writes from service with `withContext(Dispatchers.IO)` _(TD-06)_
- [ ] Wrap all Room queries from service with `withContext(Dispatchers.IO)` _(TD-06)_

### 3.7 Settings Validation

- [ ] Add `validate()` call before constructing `ScoreWeights` in Settings save path _(TD-09)_
- [ ] Show user-visible error if weights do not sum to 1.0 before saving _(TD-09)_

### 3.8 CrashLogStore Thread Safety

- [ ] Add `Mutex` to `CrashLogStore.appendSync()` to prevent interleaved writes _(TD-11)_

### 3.9 Testing

- [ ] Write `DraftScorerTest` — all-enemy-countered hero score > zero; UNKNOWN tier = 0.0 not negative; weights sum to 1.0; score clamping
- [ ] Write `BanRecommenderTest` — top 3 sorted correctly; `isToxicMechanic` adds 0.30; absolute vs. reactive split
- [ ] Write `CompositionAnalyzerTest` — full physical warning fires at ≥ 80%; CC level thresholds; one test per archetype
- [ ] Write `PickSequenceEngineTest` — 1-2-2-2-2-1 pattern; both first-picker sides; index 0 = isFirstPick; index 9 = isLastPick
- [ ] Write `RankRuleEngineTest` — ban counts at all ranks; `inferFromBanCount` edge cases
- [ ] Write `DraftSessionManagerTest` — `undo()` reverses last action; `swapOurHeroes` emits correct state; `upgradeRankFromObservedBans`
- [ ] Write `FrameProcessorTest` — duplicate slot detection; throttle timing; slot reset on new session
- [ ] Write `PerceptualHashTest` — same image → Hamming distance 0; known pairs → expected distance
- [ ] Write `DraftSessionSerializationTest` — serialize + deserialize produces equal `DraftSession`
- [ ] Write `OverlayServiceIntegrationTest` — service restarts mid-draft and restores session state
- [ ] Add JUnit 5 + `kotlinx-coroutines-test` to `testImplementation` dependencies
- [ ] Add Espresso + Hilt testing dependencies for instrumented tests

---

## Phase 2 — Intelligence Deepening (Months 3–6)

### 4.1 Personal Hero Pool

- [ ] Design and implement `HeroPoolEntity` Room table (heroId, isOwned, proficiency enum)
- [ ] Write Room migration for `HeroPoolEntity`
- [ ] Create `HeroPool` domain model and `Proficiency` enum (None / Learning / Comfortable / Mastered)
- [ ] Implement `GetHeroPoolUseCase`
- [ ] Implement `SaveHeroPoolUseCase`
- [ ] Add "My Pool" tab to Hero Explorer screen
- [ ] Add Owned toggle per hero row in "My Pool" tab
- [ ] Add Proficiency selector per hero row (None / Learning / Comfortable / Mastered)
- [ ] Wire pool multipliers into `DraftScorer` (0.0 / 0.5 / 0.85 / 1.0)
- [ ] Dim un-pooled heroes in recommendations with "❌ Not in your pool" badge
- [ ] Show "Pool import hint" banner on first open of pool screen
- [ ] Link banner to MLBB social profile share export flow

### 4.2 Patch Velocity Scoring

- [ ] Implement `patchVelocityMultiplier` in `DraftScorer.metaScore()` using `hero.patchTrend`
- [ ] Clamp `patchTrend` to `[-0.10, +0.10]` before multiplier calculation
- [ ] Apply `velocityBonus = 1.0 + (trend / 0.10f) * 0.15f` to meta score
- [ ] Add secondary "📈 Rising" badge when `patchTrend > +0.03`
- [ ] Render "📈 Rising" badge in MiniWidget chips, meta board, and hero detail screen

### 4.3 Enemy Intent Inference

- [ ] Create `EnemyIntentAnalyzer` in domain layer
- [ ] Implement transition probability table for early-pick pattern → archetype mapping
- [ ] Return top 2–3 most likely archetypes after first enemy pick or second enemy ban
- [ ] Surface inferred intent as one-line label in MiniWidget active body
- [ ] Wire `EnemyIntentAnalyzer` output into `BanRecommender` reactive ban list
- [ ] Elevate archetype-countering heroes when enemy intent is detected

### 4.4 Win Condition Generator

- [ ] Create `WinConditionGenerator` in domain layer
- [ ] Implement rule-based template system: "Your team [strength]. [Action] [condition]. [Avoid scenario]."
- [ ] Generate win condition after 5th ally pick or on `DraftPhase.COMPLETE`
- [ ] Render win condition in a gold accent card at COMPLETE overlay state
- [ ] Render win condition in `DraftHistoryScreen` session card

### 4.5 Build Advisor Expansion

- [ ] Extend `BuildAdvisor` to produce a full 6-item build
- [ ] Core items (3): always built, from `hero.coreItems`
- [ ] Situational item 4: selected based on enemy archetype
- [ ] Luxury items 5–6: "If ahead: [item]" / "If behind: [item]" dual options
- [ ] Create `BuildPanel` composable for COMPLETE overlay state
- [ ] Add "Build" tab to `HeroDetailScreen`
- [ ] Create `Item` domain class and persist items in Room _(TD-02)_
- [ ] Create `Spell` domain class and persist spells in Room _(TD-02)_
- [ ] Write Room migration for `Item` and `Spell` tables _(TD-02)_
- [ ] Replace all hardcoded item/spell strings in `BuildAdvisor` with domain class references _(TD-02)_
- [ ] Add `recommendedEmblem` field to `BuildAdvice` output
- [ ] Implement emblem tree recommendation (Mage / Physical / Tank) with two talent nodes
- [ ] Render emblem recommendation in build panel

### 4.6 Localisation

- [ ] Extract all hardcoded `Text("…")` strings to `res/values/strings.xml`
- [ ] Extract all recommendation reason strings to parameterised string resources
- [ ] Extract all composition warning strings to string resources
- [ ] Extract all win condition templates to string resources
- [ ] Create `values-in/strings.xml` with machine-translated Indonesian strings
- [ ] Create `values-fil/strings.xml` with machine-translated Filipino/Tagalog strings
- [ ] Create `values-th/strings.xml` with machine-translated Thai strings
- [ ] Create `values-vi/strings.xml` with machine-translated Vietnamese strings
- [ ] Create `values-ms/strings.xml` with machine-translated Malay strings
- [ ] Create hero name locale override table (hero names are proper nouns — do not translate)

### 4.7 Navigation SavedStateHandle

- [ ] Change `heroId` in `AppNavGraph` from plain `Int` argument to `SavedStateHandle` key _(TD-07)_

### 4.8 Draft History Pick Persistence Fix

- [ ] Wire `ourPickedHeroes` IDs into `DraftSessionEntity.yourPickIds` in `DraftSessionRepositoryImpl`
- [ ] Wire `enemyPickedHeroes` IDs into `DraftSessionEntity.enemyPickIds` in `DraftSessionRepositoryImpl`
- [ ] Verify pick history is correctly read back from Room in `DraftHistoryScreen`

---

## Phase 3 — Personalisation & Feedback Loop (Months 6–12)

### 5.1 Match Outcome Tracking

- [ ] Add `outcome: Outcome?` nullable field to `DraftSessionEntity`
- [ ] Write Room migration for `outcome` column
- [ ] Implement outcome prompt bottom sheet ("WON ✅" / "LOST ❌") in COMPLETE overlay state
- [ ] Show outcome prompt when app is re-opened shortly after a completed draft
- [ ] Persist outcome to Room without treating null as a loss
- [ ] After 10+ sessions with outcomes: compute recommendation-follow win rate from Room queries
- [ ] After 10+ sessions with outcomes: compute override win rate from Room queries
- [ ] After 10+ sessions with outcomes: compute strongest archetype (win rate by archetype)
- [ ] After 10+ sessions with outcomes: compute weakest archetype
- [ ] Render "Your Insights" card on Home Screen when 10+ sessions recorded

### 5.2 Adaptive Scoring Weights

- [ ] Create `WeightCalibrator` in domain layer
- [ ] Compute Pearson correlation between each weight dimension and win/loss after 20+ sessions
- [ ] Nudge weight by 0.05 when correlation exceeds threshold
- [ ] Constrain nudge to ±10% above user's manual ceiling
- [ ] Run calibration at most once per 5 new sessions
- [ ] Show in-app notification: "Based on your last N drafts, I've slightly increased Counter priority. Tap to review."
- [ ] Add "My Calibration" section to Settings screen
- [ ] Show current effective weights (manual + calibration delta) in calibration section
- [ ] Show calibration history: last 3 adjustment events with date and delta
- [ ] Add "Reset calibration" button returning to pure manual weights

### 5.3 Draft Pattern Recognition

- [ ] Create `DraftPatternAnalyzer` querying last 30 sessions from Room
- [ ] Detect over-ban rate (ban outside top 3 recommendations > 60% of sessions)
- [ ] Detect under-roam rate (ROAM lane unfilled > 40% of sessions)
- [ ] Detect most-frequently-first-picked hero
- [ ] Render tendency as dismissable insight card on Home Screen
- [ ] Show at most 2 tendency cards simultaneously

### 5.4 Draft Replay Viewer

- [ ] Complete `DraftReplayScreen` turn-by-turn animation
- [ ] Reconstruct top-3 recommendations at each turn from stored session data
- [ ] Show per-turn delta: composition score before/after the pick/ban
- [ ] Show "followed recommendation" (green) vs. "override" (amber) indicator per turn
- [ ] Wire `DraftHistoryScreen` row tap to `DraftReplayScreen`

### 5.5 Draft Simulation Mode

- [ ] Add "Simulate Draft" button on Home Screen
- [ ] Route simulation into the same MiniWidget UI flow
- [ ] Present both "YOUR TURN" and "ENEMY TURN" prompts to the single user
- [ ] Run full advisory engine and composition analysis for both teams in simulation
- [ ] Save completed simulation to `DraftHistoryEntity` with `isSimulation = true` flag
- [ ] Filter simulations separately from real drafts in `DraftHistoryScreen` (toggle or tab)

### 5.6 Native Speaker Localisation Review

- [ ] Recruit Indonesian native speaker reviewer
- [ ] Recruit Filipino/Tagalog native speaker reviewer
- [ ] Recruit Thai native speaker reviewer
- [ ] Recruit Vietnamese native speaker reviewer
- [ ] Review and correct all machine-translated strings per locale
- [ ] Validate hero name spellings/pronunciations per locale
- [ ] Maintain locale override table for hero display names

### 5.7 Hero Grid Pagination

- [ ] Add `Pager` and `LazyPagingItems` dependencies _(TD-10)_
- [ ] Migrate `HeroGrid` from flat `LazyColumn` to paginated `LazyPagingItems` _(TD-10)_
- [ ] Update `GetHeroesUseCase` to expose `PagingSource` _(TD-10)_

---

## Phase 4 — Ecosystem Expansion (Months 12–24)

### 6.1 Team Draft Mode

- [ ] Design WiFi P2P or mDNS + TCP socket protocol for session state sharing
- [ ] Implement host role: runs full `DraftSessionManager`, broadcasts state deltas
- [ ] Implement guest role: receives state deltas, runs advisory engine locally
- [ ] Implement QR code generation for session join on host device
- [ ] Implement QR code scanning on guest device
- [ ] Test offline (no internet) — all data stays on local network
- [ ] Design tournament bracket mode UI
- [ ] Implement configurable ban/pick counts and side selection per game
- [ ] Implement Best-of-3 and Best-of-5 format tracking
- [ ] Track game-by-game draft outcomes within tournament

### 6.2 Community Meta Backend

- [ ] Write OpenAPI specification for `GET /v1/meta/snapshot`
- [ ] Define `HeroDto` schema in OpenAPI spec
- [ ] Define `MetaSnapshotDto` schema (heroes array + patch identifier + snapshot timestamp)
- [ ] Define error responses: 404, 503 in OpenAPI spec
- [ ] Set up backend service (Node.js/Express or Kotlin/Ktor)
- [ ] Implement patch-tagged hero stat submission endpoint for trusted contributors
- [ ] Implement OpenAPI schema validation on submission
- [ ] Implement `GET /v1/meta/snapshot` with 24-hour TTL cache
- [ ] Set up initial data ingest from community stats sites (with permission)
- [ ] Document data sourcing ethics and legality constraints

### 6.3 Content Export

- [ ] Implement "Share Draft" button at draft completion
- [ ] Generate styled PNG of full draft using Android `Canvas` API with MLBB gold theme
- [ ] Include both team compositions, archetypes, win condition, and score breakdown in PNG
- [ ] Fire `ACTION_SEND` with generated PNG
- [ ] Add Export button in `DraftHistoryScreen`
- [ ] Export all sessions to CSV in `Downloads/` directory
- [ ] CSV columns: timestamp, rank, ourPicks, enemyPicks, ourBans, enemyBans, draftScore, metaScore, counterScore, synergyScore, followRate, outcome
- [ ] Fire `ACTION_SEND` with generated CSV

### 6.4 Tablet and Foldable Support

- [ ] Add `androidx.window:window` dependency for `WindowSizeClass`
- [ ] Update `HeroDetailScreen` to use side-by-side pane at `WindowSizeClass.EXPANDED`
- [ ] Update `MetaBoard` to render as single-page grid at `WindowSizeClass.EXPANDED`
- [ ] Implement foldable HALF_OPEN (book posture): hero list upper half, detail lower half
- [ ] Expand `MiniWidget` to 400 dp wide on tablets
- [ ] Register `DisplayListener` in `OverlayService` to recalculate `LayoutParams` on fold/unfold

### 6.5 Accessibility (a11y) Audit

- [ ] Add `contentDescription` to every `HeroPortrait` composable (hero name + role + tier)
- [ ] Add `contentDescription` to Minimize button in overlay
- [ ] Add `contentDescription` to Close button in overlay
- [ ] Add `contentDescription` to Undo button in overlay
- [ ] Add `contentDescription` to Score Details button in overlay
- [ ] Wire `QuickHeroChip` to announce hero name and recommendation reason on TalkBack focus
- [ ] Audit all Compose screens: colour is never the sole conveyor of information
- [ ] Audit all touch targets in overlay: ensure ≥ 48 dp × 48 dp (note: some overlay buttons may be smaller — enforce or document exception)
- [ ] Verify ban/pick slot indicators use both colour AND shape/text
- [ ] Run full TalkBack accessibility walkthrough on all main app screens

---

## Phase 5 — Platform Evolution (Months 24–36)

### 7.1 TFLite Portrait Classifier

- [ ] Generate synthetic training dataset (hero portrait art + crop offsets + brightness jitter + blur)
- [ ] Train MobileNetV3-Small on ~130 hero classes
- [ ] Validate: > 98% accuracy on clean crops; > 90% on noisy crops
- [ ] Export to `.tflite` flatbuffer
- [ ] Quantize model to INT8
- [ ] Bundle `.tflite` in `app/src/main/assets/`
- [ ] Integrate TFLite runtime dependency in `build.gradle.kts`
- [ ] Replace dHash + histogram as primary classifier in `PortraitMatcher` with TFLite inference
- [ ] Retain dHash as pre-filter: skip TFLite when dHash similarity < 0.40
- [ ] Validate `requiresConfirmation` rate drops below 2%
- [ ] Add `model_version` field to `/v1/meta/snapshot` response schema
- [ ] Implement model version check at sync time
- [ ] Download new `.tflite` to `filesDir` when `model_version` in response > bundled version
- [ ] Hot-swap model on next `OverlayService` start

### 7.2 Emulator / BlueStacks Support

- [ ] Detect emulator via `Build.HARDWARE` and `Build.FINGERPRINT`
- [ ] Use `TYPE_PHONE` window type on emulators where `TYPE_APPLICATION_OVERLAY` behaves incorrectly
- [ ] Skip "App Auto-Start" wizard step automatically on emulators
- [ ] Define emulator-specific `SlotRegions` coordinate table
- [ ] Implement one-time corner-tap calibration overlay on first emulator draft session
- [ ] Derive fractional slot regions from four corner taps
- [ ] Persist `DeviceProfile` to Room from calibration result
- [ ] Load `DeviceProfile`-specific `SlotRegions` at session start

### 7.3 iOS Feasibility Study

- [ ] Document iOS `SYSTEM_ALERT_WINDOW` equivalent — conclusion: not feasible on stock iOS
- [ ] Document `AccessibilityService` equivalent — conclusion: not available on iOS
- [ ] Document `ReplayKit` limitations for transparent background capture
- [ ] Publish feasibility conclusion and iOS strategy decision (web companion pivot)

### 7.4 Web Companion PWA

- [ ] Set up React + TypeScript PWA project
- [ ] Implement hero explorer in PWA
- [ ] Implement meta board in PWA
- [ ] Implement draft simulation in PWA
- [ ] Connect PWA to meta API backend from Phase 4
- [ ] Explicitly exclude overlay, screen capture, and autonomy features from PWA scope
- [ ] Make PWA installable on iOS Safari and Chrome desktop

### 7.5 Wear OS Companion

- [ ] Set up Wear OS companion module
- [ ] Implement Wear OS tile showing current draft phase
- [ ] Implement Wear OS complication showing top pick recommendation (hero name)
- [ ] Sync session state from phone via `DataClient`
- [ ] Trigger haptic pulse on Wear OS device when it is the player's turn
- [ ] Scope to read-only — no control or input from watch

---

## Perpetual Track — Ongoing Maintenance

### Patch Sync (every MLBB patch, ~biweekly)

- [ ] Update all hero win rates, ban rates, pick rates, tier classifications within 48 h of patch
- [ ] Add new heroes to DB seed data on release
- [ ] Add new hero portraits to TFLite training set
- [ ] Bump TFLite `model_version` after retraining with new heroes
- [ ] Publish new `MetaSnapshotDto` at `/v1/meta/snapshot` with patch tag
- [ ] Review `isToxicMechanic` and `isOP` flags after each patch notes release
- [ ] Review `BuildAdvisor` item and spell lists after item balance patches
- [ ] Review `hasCCUlt` flags after each hero rework or new release

### OEM Compatibility (quarterly or per Android/MLBB update)

- [ ] Test `openAutoStartSettings()` intent actions against latest MIUI firmware
- [ ] Test `openAutoStartSettings()` intent actions against latest ColorOS firmware
- [ ] Test `openAutoStartSettings()` intent actions against latest FuntouchOS firmware
- [ ] Test `openAutoStartSettings()` intent actions against latest EMUI firmware
- [ ] Test `openAutoStartSettings()` intent actions against latest OneUI firmware
- [ ] Verify FGS survives background removal on OEM battery-saver modes (quarterly)
- [ ] Verify `TYPE_APPLICATION_OVERLAY` renders correctly on each new Android major version
- [ ] Verify wizard screenshots match actual MLBB UI after each major MLBB update
- [ ] Verify `SlotRegions` fractions after each major MLBB draft screen layout change

### Security (monthly / per release / quarterly)

- [ ] Run `./gradlew dependencyUpdates` and update dependencies with known CVEs (monthly)
- [ ] Review ProGuard/R8 rules — ensure no sensitive class names in crash logs (per release)
- [ ] Verify `BuildAdvisor` and `DraftScorer` are fully obfuscated (per release)
- [ ] Rotate backend API keys for upstream data sources (quarterly)
- [ ] Inspect network traffic with Charles Proxy / mitmproxy — no undocumented calls (per release)
- [ ] Audit captured frames: never written to external storage, never shared, never retained beyond current frame (per release)

### Performance (ongoing — regressions block release)

- [ ] Profile frame processing latency — target < 300 ms on SD 680 equivalent
- [ ] Profile MiniWidget frame drops — target < 0.1% dropped frames during active draft
- [ ] Profile cold start time — target < 1.5 s to Home Screen
- [ ] Profile `OverlayService` peak RSS — target < 120 MB
- [ ] Profile background CPU when MLBB not in foreground — target < 0.5% battery/hour
- [ ] Profile `GetDraftHistoryUseCase` with 500 sessions — target < 80 ms
- [ ] Use Android Studio Profiler and Macrobenchmark library for all profiling

### Crash Monitoring

- [ ] Integrate Firebase Crashlytics (no PII, no screen content sharing)
- [ ] Define SLO alerting: P0 crash → fix + release within 24 h
- [ ] Define SLO alerting: P1 crash → fix + release within 72 h
- [ ] Define SLO alerting: P2 crash → fix in next scheduled release cycle

### Play Store Compliance

- [ ] File `SYSTEM_ALERT_WINDOW` use-case declaration on Play Store
- [ ] Re-file declaration after any permission scope change
- [ ] Ensure Privacy Policy accurately describes `MEDIA_PROJECTION` usage (RAM-only, not stored/transmitted)
- [ ] Track `targetSdk` — update within 12 months of each new Android release per Google Play policy
- [ ] Audit `uses-feature` manifest: `android:required="false"` on all non-required hardware features
- [ ] Verify `uses-feature` after every manifest change
