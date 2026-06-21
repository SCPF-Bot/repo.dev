# MLBB Draft Assistant — Product & Engineering Roadmap

> **How to read this document.**
> This roadmap is structured in six phases followed by a perpetual maintenance track. Each phase builds on the previous. Phases are time-estimated from the current baseline (June 2026), but dates are targets, not contracts — they stretch when blockers appear and compress when parallel execution is possible. Every item is tagged **[P0]** (blocking), **[P1]** (high value), or **[P2]** (quality-of-life). Items without a tag are assumed P1. Status badges: ✅ Done · 🚧 In Progress · ⬜ Planned.
>
> A **Perpetual Track** section at the end captures work that has no finish line — patch sync, OEM compatibility, security, performance — and must run in parallel with every named phase.
>
> This document is the single source of truth for what gets built, why it gets built, and in what order. It supersedes any informal todo lists or backlog items. See `docs/todo.md` for the exhaustive flat checkbox list derived from this document.

---

## Table of Contents

1. [Guiding Constraints](#1-guiding-constraints)
2. [Current Baseline — Phase 0: Stabilisation (Complete)](#2-current-baseline--phase-0-stabilisation-complete)
3. [Phase 1 — Core Excellence (Months 0–3)](#3-phase-1--core-excellence-months-03)
4. [Phase 2 — Intelligence Deepening (Months 3–6)](#4-phase-2--intelligence-deepening-months-36)
5. [Phase 3 — Personalisation & Feedback Loop (Months 6–12)](#5-phase-3--personalisation--feedback-loop-months-612)
6. [Phase 4 — Ecosystem Expansion (Months 12–24)](#6-phase-4--ecosystem-expansion-months-1224)
7. [Phase 5 — Platform Evolution (Months 24–36)](#7-phase-5--platform-evolution-months-2436)
8. [Perpetual Track — Ongoing Maintenance](#8-perpetual-track--ongoing-maintenance)
9. [Technical Debt Register](#9-technical-debt-register)
10. [Risk Register](#10-risk-register)
11. [Success Metrics by Phase](#11-success-metrics-by-phase)
12. [Architecture Decision Log](#12-architecture-decision-log)

---

## 1. Guiding Constraints

These are the non-negotiable rules that every roadmap item must respect. If a proposed feature conflicts with a constraint, the feature changes — not the constraint.

| # | Constraint | Rationale |
|---|---|---|
| C-1 | The overlay never taps, types, or injects input into MLBB | Legal and policy compliance; the app is an observer, not a controller |
| C-2 | Every recommendation surfaces a human-readable reason | Players must be able to evaluate and override advice; opaque scoring destroys trust |
| C-3 | The advisory engine works offline from cached meta data | Network unavailability during a ranked match must never silently degrade the experience |
| C-4 | Core features are never gated behind payments or accounts | The tool is a co-pilot — withholding recommendations mid-draft would be hostile UX |
| C-5 | Screen capture requires explicit user consent at runtime | Android MediaProjection policy; the app must never auto-capture without the system dialog |
| C-6 | Every scoring formula is user-inspectable | Weights, score breakdowns, and badge labels must always be visible in the UI |
| C-7 | A manual fallback exists for every autonomous feature | Devices that cannot use screen capture must still access every advisory feature |
| C-8 | The app never reads game memory or intercepts network traffic | Strict ethical and platform boundary: visual observation only |

---

## 2. Current Baseline — Phase 0: Stabilisation (Complete)

Phase 0 represents all work already merged as of **June 2026**. It is documented here as a baseline checkpoint, not as planned work.

### ✅ Completed fixes and features

| Item | Area | Description |
|---|---|---|
| **Bubble drag fix** | Overlay / Touch | Touch listener is now mode-aware: bubble mode claims `ACTION_DOWN` and calls `expandToWidget()` on tap; widget mode returns `false` on DOWN, dispatches synthetic `ACTION_CANCEL` to Compose on drag threshold exceeded |
| **DataStore multi-instance crash** | Data / DI | Introduced `AppDataStore.kt` as the single authoritative `preferencesDataStore` delegate; all callers (`AppModule`, `WizardPreference`, `PreferencesDataStore`) now share the same file-backed instance, eliminating `IllegalStateException` |
| **FGS mediaProjection crash (API 34+)** | Service Lifecycle | `startFg()` now uses `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` only on API 34+; `upgradeFgToProjection()` is called from `onStartCommand` after a valid projection token is confirmed, satisfying Android 14's strict type-at-declaration requirement |
| **Wizard step order** | Onboarding | Wizard is now 6 steps in deliberate order: Overlay → Background Running → Battery → Auto-Start → Restricted Settings → Accessibility; Screen Capture and Notifications steps removed |
| **`DraftOutcome.DRAW` missing `when` branches** | Compilation | Added missing `DRAW` branches to `DraftExporter.kt` (lines 104, 109) and `DraftReplayScreen.kt` (line 227); eliminated compile error |
| **`SlotRegions.kt` landscape calibration** | CV Pipeline | Corrected all slot region coordinates from portrait-oriented (wrong) to landscape-calibrated values based on 1600×720 reference; coordinates stored as both normalized fractions and absolute pixel values |
| **`draft_ui_map.json` asset** | CV Pipeline | Created `app/src/main/assets/draft_ui_map.json` with full normalized + absolute pixel region map for ban slots (R1/R2 ally/enemy), pick slots, timer, phase banner, and center display |
| **`MiniWidget.kt` full redesign** | Overlay UX | Rewrote the expanded widget with sequential phase activation (BAN active → dimmed after pick starts, both always visible), animated `PhasePanel` cards with alpha/border transitions, `SlotGroup` dot rows (5 ally + 5 enemy), `RecommendedRow` (2 rows of 3 chips), `CompositionInsightsPanel` (archetype detection + win condition), and `BottomActionBar` [⏹ Min] [↩ Undo] [📊 Score] [✕ Close] |
| **`OverlayService.kt` callback wiring** | Overlay UX | Added `onUndo = { draftSessionManager.undo() }` and `onScoreDetails = { handleScoreDetails() }` call sites; added `handleScoreDetails()` method that launches the main app via `getLaunchIntentForPackage` |
| **`CompositionArchetype` enum** | Advisory Engine | Full enum with `display`, `icon`, `winCondition`, `counterCondition` fields and priority-ordered `detect()` companion method covering DIVE, POKE, TURTLE, WOMBO_COMBO, SPLIT_PUSH, BALANCED |
| **`CompositionAnalyzer` archetype detection** | Advisory Engine | `detectArchetype(heroes)` wired into `MiniWidget` active body; computed live from `ourPickedHeroes` and `enemyPickedHeroes` |

### Known technical debt carried forward

See [Section 9 — Technical Debt Register](#9-technical-debt-register) for a full catalogue.

---

## 3. Phase 1 — Core Excellence (Months 0–3)

**Goal:** Make the existing feature set robust enough that a first-time user at any rank on any mainstream OEM can complete a full draft session without encountering a crash, a stale UI state, or a confusing permission failure.

This phase is entirely about deepening what already exists — not adding new surface area.

---

### 3.1 Overlay Hardening

#### 3.1.1 ⬜ Overlay state persistence across service restarts [P0]
**What:** Serialize the active `DraftSession` to DataStore on every `StateFlow` emission. On `OverlayService.onCreate()`, rehydrate the session from DataStore if a non-IDLE, non-COMPLETE session exists.

**Why:** Android aggressively kills foreground services on low-memory devices and after certain OEM power-management events. A player who has reached Pick 7/10 and has their service killed mid-draft loses all context. With persistence, the overlay restores to the exact draft state — including all bans, picks, and the undo stack — within 300 ms of relaunch.

**Implementation notes:**
- `DraftSession` must be serializable to JSON (Kotlinx Serialization or Gson; prefer Kotlinx for type safety).
- Serialize on the IO dispatcher inside the `StateFlow` collector to avoid blocking the main thread.
- Write an integration test that kills and relaunches the service and asserts session equality.
- On restoration, skip `initSession()` and jump directly to the serialized phase.

#### 3.1.2 ⬜ One-tap overlay relaunch from notification [P1]
**What:** Add a persistent `NotificationCompat.Action` ("▶ Relaunch Overlay") to the foreground service notification. Tapping it sends a `PendingIntent` to `OverlayService` that reinitialises the overlay window without opening the main app.

**Why:** Players who dismiss the bubble accidentally, or whose overlay is killed by a low-memory event, have to navigate back to the main app and tap Start Draft again. One tap from the notification shade resolves this without breaking game focus.

**Implementation notes:**
- Use `PendingIntent.getService()` with an `ACTION_RELAUNCH` extra.
- The relaunch action must only appear when the service is running but the overlay window is not visible.
- The existing notification channel must not be changed — only add the action to the builder.

#### 3.1.3 ⬜ Accessibility service watchdog [P1]
**What:** Add a coroutine-based watchdog in `OverlayService` that polls `AccessibilityManager.isEnabled()` every 30 seconds. If the service is found to be disabled mid-session, emit a `ServiceEvent.AccessibilityDied` event and show an in-overlay warning banner with a deep link to the accessibility settings.

**Why:** On some OEM skins (MIUI, ColorOS), the accessibility service can be killed and not restarted without user action. Currently the overlay continues running but MLBB auto-detection silently stops working. The watchdog makes the failure visible.

**Implementation notes:**
- The warning banner must not block recommendations — it renders below the active suggestion panel.
- Include an "Open Settings" button that fires `ACTION_ACCESSIBILITY_SETTINGS`.
- The watchdog coroutine must be a child of `serviceScope` so it is cancelled with the service.

#### 3.1.4 ⬜ Permission revocation recovery [P1]
**What:** On `OverlayService.onStartCommand()`, re-check the overlay permission (`Settings.canDrawOverlays()`). If revoked, show a system notification directing the user back to the wizard at the specific revoked step instead of the first step.

**Why:** OS updates and "permission cleanup" tools can silently revoke `SYSTEM_ALERT_WINDOW`. The current code does not detect this — it simply fails to add the view to `WindowManager` and logs the exception. The recovery path must be proactive.

#### 3.1.5 ⬜ Bubble position persistence to DataStore [P1] _(TD-12)_
**What:** On `ACTION_UP` after a drag, write the bubble's `overlayX`/`overlayY` to DataStore. On `OverlayService.onCreate()`, read those values and initialise `WindowManager.LayoutParams` with the saved position.

**Why:** Currently the bubble snaps back to its default corner on every service restart. Players who prefer the bubble in a specific corner must move it every session.

---

### 3.2 CV Pipeline Improvements

#### 3.2.1 ⬜ Rank auto-detection from rank emblem region [P1]
**What:** Consume `SlotRegions.rankEmblem` (already defined but not yet wired). Sample the dominant colour in that region and classify it against MLBB's rank emblem colour palette:
- Warrior/Elite/Master: grey/green palette
- Epic: deep purple/blue
- Legend: teal/cyan
- Mythic: gold/bronze
- Mythical Glory/Immortal: bright gold/white

Map the detected rank to `Rank` enum and call `draftSessionManager.setRank()` automatically — eliminating the manual rank selection in the Idle widget.

**Implementation notes:**
- Use a k-nearest-centroid classifier over Lab colour space for robustness to device brightness settings.
- Only detect once per session — on first non-IDLE phase detection.
- If confidence is below 0.7, fall back to the manual toggle without showing an error.

#### 3.2.2 ⬜ First-pick side auto-detection [P1]
**What:** Consume `SlotRegions.firstPickIndicator`. MLBB renders a first-pick triangle/arrow indicator in the top-centre of the draft screen pointing left (our team goes first) or right (enemy goes first). Sample the pixel cluster at that region and classify direction by comparing left-half vs right-half luminance asymmetry.

**Why:** The "WHO PICKS FIRST?" manual toggle in the idle widget is the most common source of incorrect advisory output. A player who mis-taps it gets backwards pick sequence recommendations for the entire draft.

**Implementation notes:**
- Pair with rank detection — both run on the first usable draft frame.
- If detection confidence < 0.6, show the manual toggle as a confirmation step rather than hiding it entirely.

#### 3.2.3 ⬜ Improved portrait matching — dHash + colour histogram hybrid [P1]
**What:** Extend `PortraitMatcher` with a secondary verification pass using colour histogram comparison (bucket the cropped slot into 8×8×8 RGB bins, compute cosine similarity) when dHash similarity falls between 0.70 and 0.85 (the ambiguous "possible match" zone). Accept a match only when both dHash similarity ≥ 0.72 AND colour histogram similarity ≥ 0.75.

**Why:** dHash alone fails on hero portraits with similar geometric layouts but different colour palettes. The colour histogram check adds a cheap second-pass discriminant.

**Implementation notes:**
- Colour histograms can be precomputed and cached in an extended `LruCache<Int, PortraitSignature>` holding both dHash and histogram.
- Target: reduce `requiresConfirmation = true` rate from ~20% to < 8%.

#### 3.2.4 ⬜ Phase countdown timer OCR for BAN_ROUND_1 vs BAN_ROUND_2 disambiguation [P1]
**What:** Add optional OCR of the countdown timer region using ML Kit's on-device text recogniser to read the ban/pick round indicator text (e.g., "BAN PHASE 1" / "BAN PHASE 2"). Use this as a definitive phase signal rather than inferring round 2 from ban-count alone.

**Implementation notes:**
- ML Kit Text Recognition v2 (Latin script) is lightweight (~1 MB) and runs entirely on-device.
- Only run OCR when colour-based phase detection returns `UNKNOWN` or `SETUP` — not on every frame.
- Add `mlkit-text-recognition` to `dependencies` in `app/build.gradle.kts`.

#### 3.2.5 ⬜ `PhaseDetector` magic number extraction [P1] _(TD-03)_
**What:** Define `R > 160` and `B > 140` as named constants in a `PhaseDetectionConfig` object. Add a calibration test that validates them against reference screenshots.

#### 3.2.6 ⬜ `FrameProcessor` luminance normalisation [P1] _(TD-04)_
**What:** `isSlotFilled()` uses a raw luminance threshold of 40. Normalise luminance against a sampled background region to be device-profile-independent.

#### 3.2.7 ⬜ `PortraitMatcher` lazy hash preload [P0] _(TD-08)_
**What:** `preloadHashes()` is called from `OverlayService.onCreate()` on the IO dispatcher, blocking service startup for up to 2 seconds on first launch. Move to a lazy background `Job` with a `Deferred<Boolean>` that only awaits when a match is first needed.

---

### 3.3 Advisory Engine Improvements

#### 3.3.1 ⬜ Continuous pick-index scoring curve [P1]
**What:** Replace the binary `isFirstPick` and `isLastPick` bonuses in `DraftScorer` with a continuous function over pick index. Synergy and counter weights ramp linearly from their defaults to `default + 0.20` by pick index 4, as more context becomes known.

**Formula:**
```
alphaSync(i)    = min(1.0, defaultSynergy + 0.20 * (i / maxPickIndex))
alphaCounter(i) = min(1.0, defaultCounter + 0.20 * (i / maxPickIndex))
alphaMeta(i)    = 1.0 - (alphaSync(i) - defaultSynergy) - (alphaCounter(i) - defaultCounter)
```

#### 3.3.2 ⬜ Ban priority: absolute vs. reactive modes [P1]
**What:** Split `BanRecommender` into two priority lists:
- **Absolute bans**: `isToxicMechanic = true` OR `isOP = true` OR `banRate > 0.40` — presented first with a "🔒 Always Ban" label.
- **Reactive bans**: heroes elevated because the detected enemy first-pick suggests a direction.

Render absolute bans first, then reactive bans, replacing the previous flat ban list.

#### 3.3.3 ⬜ `DraftScorer` dynamic meta score normalisation [P1] _(TD-05)_
**What:** Compute `metaScore` normalisation bounds dynamically from the 5th–95th percentile of the current hero dataset, rather than hardcoded constants (0.48–0.56 win rate, 0–40% ban rate).

#### 3.3.4 ⬜ Score explanation bottom sheet [P2]
**What:** Long-pressing any hero recommendation chip in the MiniWidget opens a `ModalBottomSheet` showing the full score breakdown, all synergy/counter matches, and raw stats (win rate, ban rate, pick rate, tier).

---

### 3.4 Onboarding and UX Polish

#### 3.4.1 ⬜ OEM-specific deep-link permission wizard steps [P1]
**What:** Replace generic `ACTION_ACCESSIBILITY_SETTINGS` intents with OEM-specific accessibility intent actions (Xiaomi uses `com.miui.accessibility/.AccessibilityManagerActivity`). Add shimmy text for Restricted Settings explaining exactly where to tap.

#### 3.4.2 ⬜ Inline permission status re-check on wizard resume [P1]
**What:** After the user returns from a system settings screen, immediately re-evaluate permission state and auto-advance the wizard step if it is now granted — eliminating the manual "Done / Grant" tap.

---

### 3.5 Testing Foundation [P0]

A feature without a test has no contract. These tests must be written alongside or before the code they verify.

| Test | Type | Status |
|---|---|---|
| `DraftScorerTest` | Unit | ⬜ |
| `BanRecommenderTest` | Unit | ⬜ |
| `CompositionAnalyzerTest` | Unit | ⬜ |
| `PickSequenceEngineTest` | Unit | ⬜ |
| `RankRuleEngineTest` | Unit | ⬜ |
| `DraftSessionManagerTest` | Unit (coroutine) | ⬜ |
| `FrameProcessorTest` | Unit | ⬜ |
| `PerceptualHashTest` | Unit | ⬜ |
| `DraftSessionSerializationTest` | Unit | ⬜ |
| `OverlayServiceIntegrationTest` | Instrumented | ⬜ |

**Framework:** JUnit 5 + Kotlin Coroutines test library (`kotlinx-coroutines-test`) for unit tests; Espresso + Hilt testing for instrumented tests.

---

### 3.6 Service-layer IO audit [P1] _(TD-06)_

**What:** `serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())`. All IO work (DataStore writes, Room queries) called from the service must be explicitly `withContext(IO)`. Audit all service-level IO call sites.

---

### 3.7 Settings weight validation [P0] _(TD-09)_

**What:** Add an explicit `validate()` call in the Settings screen's save path before constructing `ScoreWeights`, preventing a `require()` exception in the domain model at runtime if the slider sum deviates from 1.0.

---

### 3.8 `CrashLogStore` write lock [P1] _(TD-11)_

**What:** Wrap `appendSync()` in a `Mutex` to prevent interleaved log lines from two concurrent crashers.

---

## 4. Phase 2 — Intelligence Deepening (Months 3–6)

**Goal:** Graduate the advisory engine from a single-factor linear scorer to a contextually aware system that understands composition intent, individual hero proficiency, and patch velocity.

---

### 4.1 Personal Hero Pool

#### 4.1.1 ⬜ Hero ownership and proficiency marking [P1]
**What:** Add a hero management screen ("My Pool" tab in Hero Explorer). Each hero row has an Owned toggle and a proficiency selector (None / Learning / Comfortable / Mastered). Persist to Room (`HeroPoolEntity` table: heroId, isOwned, proficiency). Wire `GetHeroPoolUseCase` into `DraftScorer` with pool multipliers (0.0 / 0.5 / 0.85 / 1.0). Un-pooled heroes are shown but dimmed with "❌ Not in your pool" badge.

#### 4.1.2 ⬜ Pool import hint via MLBB profile share [P2]
**What:** After first opening the hero pool screen, show a banner: "Import your hero pool from your MLBB profile? Share your profile data to pre-fill ownership." Links to MLBB social share export flow.

---

### 4.2 Patch Velocity Scoring

#### 4.2.1 ⬜ Explicit patch delta multiplier [P1]
**What:** Add `patchVelocityMultiplier` to `DraftScorer.metaScore()`:
```
val trend = hero.patchTrend.coerceIn(-0.10f, +0.10f)
val velocityBonus = 1.0 + (trend / 0.10f) * 0.15f
metaScore = rawMetaScore * velocityBonus
```

#### 4.2.2 ⬜ "Rising This Patch" badge [P2]
**What:** If `patchTrend > +0.03`, add a secondary "📈 Rising" badge in amber rendered below the primary badge in recommendations, meta board, and hero detail.

---

### 4.3 Enemy Intent Inference

#### 4.3.1 ⬜ Partial composition intent engine [P1]
**What:** Add `EnemyIntentAnalyzer` to the domain layer. After the first enemy pick (or second ban), classify the partial enemy composition into 2–3 most likely archetype targets using a transition probability table. Surface the inferred intent as a one-line label in the MiniWidget: "Enemy intent: 🔎 WOMBO_COMBO likely".

#### 4.3.2 ⬜ Reactive ban adjustment from enemy intent [P1]
**What:** Wire `EnemyIntentAnalyzer` output into `BanRecommender`. If enemy intent = DIVE, elevate anti-dive key-piece heroes to the top of the absolute ban list.

---

### 4.4 Win Condition Statement Generator

#### 4.4.1 ⬜ Draft synthesis sentence [P1]
**What:** Add `WinConditionGenerator` to the domain layer. After the 5th ally pick (or on phase = COMPLETE), generate a one-sentence win condition statement from the team composition using rule-based templates. Render in a gold accent card at the bottom of the COMPLETE overlay state and in `DraftHistoryScreen`.

---

### 4.5 Build Advisor Expansion

#### 4.5.1 ⬜ Full 6-item build with situational swaps [P1]
**What:** Extend `BuildAdvisor` to produce a full 6-item build: 3 core items (always built), 1 situational item selected from enemy archetype, 2 luxury items ("If ahead / If behind" options). Add `BuildPanel` composable in COMPLETE overlay state and a "Build" tab in `HeroDetailScreen`.

#### 4.5.2 ⬜ Introduce `Item` and `Spell` domain classes [P1] _(TD-02)_
**What:** Replace hardcoded spell/item strings in `BuildAdvisor` with properly modelled `Item` and `Spell` domain classes persisted in Room. Any item rename in a patch will then require a single DB migration, not scattered string edits.

#### 4.5.3 ⬜ Emblem recommendation [P2]
**What:** Add `recommendedEmblem` to `BuildAdvice` output. Surface one recommended emblem name + two talent recommendations as plain text.

---

### 4.6 Localisation Foundation [P1]

**What:** Extract all user-visible strings from Compose composables into `res/values/strings.xml`. Create placeholder translations for:
- `values-in/strings.xml` (Indonesian)
- `values-fil/strings.xml` (Filipino/Tagalog)
- `values-th/strings.xml` (Thai)
- `values-vi/strings.xml` (Vietnamese)
- `values-ms/strings.xml` (Malay)

For Phase 2, machine-translate all strings. Native speaker review is a Phase 3 deliverable.

**Implementation notes:**
- All `Text("hardcoded string")` calls must be replaced with `stringResource(R.string.key)`.
- Hero names are proper nouns — do NOT translate them, only translate surrounding UI chrome.

---

### 4.7 Navigation SavedStateHandle migration [P2] _(TD-07)_

**What:** `AppNavGraph` passes `heroId` as a plain `Int` argument. If navigation backstack restoration is needed after process death, this argument must be treated as a `SavedStateHandle` key.

---

### 4.8 `DraftSessionRepositoryImpl` pick persistence fix [P1]

**What:** `yourPickIds`/`enemyPickIds` are currently stubbed as empty lists in history persistence. Wire the actual `ourPickedHeroes`/`enemyPickedHeroes` IDs from the completed session into the entity before writing to Room.

---

## 5. Phase 3 — Personalisation & Feedback Loop (Months 6–12)

**Goal:** Transform historical session data from a read-only record into an active feedback mechanism that adapts the scoring model to the individual player over time.

---

### 5.1 Match Outcome Tracking

#### 5.1.1 ⬜ Win/loss reporting [P0 for this phase]
**What:** Add a match outcome prompt (bottom sheet with "WON ✅" / "LOST ❌") that appears when the COMPLETE overlay body is shown or when the app is re-opened shortly after a draft. Write to `DraftSessionEntity.outcome: Outcome?` (nullable).

#### 5.1.2 ⬜ Post-match score validation insights [P1]
**What:** After 10+ completed sessions with outcomes recorded, display a "Your Insights" card on the Home Screen with: recommendation-follow win rate, override win rate, strongest archetype, weakest archetype — all computed from local Room queries.

---

### 5.2 Adaptive Scoring Weights

#### 5.2.1 ⬜ Personal weight calibration engine [P1]
**What:** Add `WeightCalibrator` to the domain layer. After 20+ sessions with outcomes, compute Pearson correlation between each weight dimension's session score and win/loss. Nudge weights by 0.05 when a clear correlation is found. Constrain nudges to ±10% above the user's manual ceiling. Calibrate at most once per 5 new sessions.

#### 5.2.2 ⬜ Calibration transparency and override [P1]
**What:** Add a "My Calibration" section to Settings showing current effective weights, calibration history (last 3 adjustments with date and delta), and a "Reset calibration" button.

---

### 5.3 Draft Pattern Recognition

#### 5.3.1 ⬜ Personal draft tendency analysis [P2]
**What:** Add `DraftPatternAnalyzer` querying last 30 sessions to identify tendencies (over-ban rate, under-roam rate, first-pick patterns). Surface as dismissable insight cards on the Home Screen (max 2 shown at a time).

---

### 5.4 Match Timeline View

#### 5.4.1 ⬜ Draft replay viewer [P2]
**What:** In `DraftHistoryScreen`, tapping a session opens `DraftReplayScreen` (already partially scaffolded) with per-turn animation showing: what was recommended, what was picked/banned, the composition score delta, and a "followed / override" indicator.

---

### 5.5 Draft Simulation Mode

#### 5.5.1 ⬜ Practice draft without a live game [P1]
**What:** Add a "Simulate Draft" entry point from the Home Screen. Simulation mode uses the exact same MiniWidget UI, presents both team's turns to the single player, runs the full advisory engine, and saves to `DraftHistoryEntity` with `isSimulation = true`.

---

### 5.6 Localisation — Native Speaker Review [P1]

**What:** Recruit native speakers for Indonesian, Filipino/Tagalog, Thai, and Vietnamese. Review and correct all machine-translated strings. Maintain a locale override table for hero display names.

---

### 5.7 Hero grid pagination [P2] _(TD-10)_

**What:** Migrate `HeroGrid` from flat `LazyColumn` to `Pager` + `LazyPagingItems` proactively ahead of hero/skin variant expansion.

---

## 6. Phase 4 — Ecosystem Expansion (Months 12–24)

**Goal:** Expand the app's value proposition beyond the individual player session, supporting team play, content creation, and community-driven meta data.

---

### 6.1 Team Draft Mode

#### 6.1.1 ⬜ Shared draft session via local network [P1]
**What:** Two or more players on the same WiFi network share a draft session. Host runs the full `DraftSessionManager`; guests join via QR code. Guests see live state and recommendations but do not control the session. Uses Android WifiP2P or mDNS + TCP socket — no internet required.

#### 6.1.2 ⬜ Tournament bracket mode [P2]
**What:** Configurable ban/pick counts, side selection per game, Best-of-3 and Best-of-5 formats with game-by-game draft outcome tracking.

---

### 6.2 Community Meta Data Source

#### 6.2.1 ⬜ Meta API backend specification [P0 for this phase]
**What:** Publish a formal OpenAPI specification for `GET /v1/meta/snapshot`. The spec defines `HeroDto`, `MetaSnapshotDto`, and error responses. The app client (`MetaApi` interface) already exists — this item formalises the server contract.

#### 6.2.2 ⬜ Community-maintained meta backend [P1]
**What:** Build a lightweight backend (Node.js/Express or Kotlin/Ktor) that accepts patch-tagged hero stat submissions from trusted contributors, validates against the OpenAPI schema, and publishes at `GET /v1/meta/snapshot` with a 24-hour TTL cache. Ingest from community stats sites (with permission).

---

### 6.3 Content Export

#### 6.3.1 ⬜ Draft summary image export [P2]
**What:** At draft completion, offer "Share Draft" generating a styled PNG of the full draft (both compositions, archetypes, win condition, score breakdown) via Android `Canvas` API.

#### 6.3.2 ⬜ CSV draft history export [P2]
**What:** Export all sessions to CSV in `Downloads/` via `ACTION_SEND`. Columns: timestamp, rank, ourPicks, enemyPicks, ourBans, enemyBans, draftScore, metaScore, counterScore, synergyScore, followRate, outcome.

---

### 6.4 Tablet and Foldable Support [P1]

**What:** Update all Compose screens to use adaptive `WindowSizeClass` layouts. Side-by-side pane on `HeroDetail` at `EXPANDED`. `MiniWidget` expands to 400 dp on tablets. Register a `DisplayListener` in `OverlayService` to recalculate `WindowManager.LayoutParams` when the device folds/unfolds.

---

### 6.5 Accessibility (a11y) Audit [P1]

**What:** Full TalkBack audit: `contentDescription` on every `HeroPortrait` (hero name + role + tier); all icon-only buttons (minimize, close, undo) fully described; `QuickHeroChip` announces hero name and reason when focused; colour never the sole conveyor of information; all touch targets ≥ 48 dp × 48 dp.

---

## 7. Phase 5 — Platform Evolution (Months 24–36)

**Goal:** Expand platform reach beyond Android phone baseline and migrate portrait detection to an ML-based classifier.

---

### 7.1 TensorFlow Lite Portrait Classifier

#### 7.1.1 ⬜ MobileNetV3-Small hero portrait classifier [P1]
**What:** Train MobileNetV3-Small on MLBB hero portrait crops (~130 classes). Target: > 98% accuracy on clean crops, > 90% on noisy crops. Export to INT8-quantised `.tflite` (~3–5 MB). Replace dHash + histogram in `PortraitMatcher` with TFLite as primary classifier; retain dHash as a cheap pre-filter (< 0.40 similarity → skip TFLite). Target `requiresConfirmation` rate < 2%.

#### 7.1.2 ⬜ On-device model update mechanism [P2]
**What:** Check `model_version` in `/v1/meta/snapshot` response. If higher than the bundled version, download the new `.tflite` to `filesDir` and hot-swap on next `OverlayService` start.

---

### 7.2 Android Emulator / BlueStacks Support

#### 7.2.1 ⬜ Emulator detection and overlay adaptation [P1]
**What:** Detect emulator via `Build.HARDWARE`/`Build.FINGERPRINT`. On emulators: use `TYPE_PHONE` window type if needed; skip the "App Auto-Start" wizard step; use emulator-specific `SlotRegions` coordinate table.

#### 7.2.2 ⬜ Emulator-specific slot region calibration [P1]
**What:** On first draft session on an emulator, present a calibration overlay asking the user to tap the corners of the MLBB draft window. Derive correct fractional slot regions and store as `DeviceProfile` in Room.

---

### 7.3 iOS Feasibility Study [P2]

**What:** Evaluate iOS port blockers (no `SYSTEM_ALERT_WINDOW` equivalent, no `AccessibilityService`, `ReplayKit` limitations). Document conclusion and pivot to web companion if overlay is infeasible.

---

### 7.4 Web Companion App

#### 7.4.1 ⬜ Browser-based draft assistant [P2]
**What:** PWA (React + TypeScript) providing hero explorer, meta board, and draft simulation — but not the overlay. Shares the same meta API backend from Phase 4. Target: players studying meta on PC or iOS.

---

### 7.5 Android Watch / Wear OS Companion [P2]

**What:** Wear OS tile and complication showing current draft phase, top pick recommendation (name only), and haptic pulse on player turn. Synced from phone via `DataClient`. Read-only.

---

## 8. Perpetual Track — Ongoing Maintenance

These activities have no end date and run in parallel with every named phase.

---

### 8.1 Patch Sync — Every MLBB Patch (~Biweekly)

| Task | Who/What |
|---|---|
| Hero stat update | Meta backend / sync |
| New hero addition | Codebase + model |
| Meta snapshot publish | Backend |
| `isToxicMechanic` / `isOP` flag review | Manual — after each patch notes release |
| `BuildAdvisor` item list review | Manual — after item balance patches |
| CC hero `hasCCUlt` flag review | Manual — after each hero rework (replaces old hardcoded name list) |

---

### 8.2 OEM Compatibility

| Task | Frequency |
|---|---|
| Auto-start intent table verification | Quarterly |
| FGS kill resistance validation | Quarterly |
| `TYPE_APPLICATION_OVERLAY` behaviour | Per Android major release |
| Wizard screenshot accuracy vs. MLBB layout changes | Per MLBB major update |
| `SlotRegions` fraction verification | Per MLBB major update |

---

### 8.3 Security

| Task | Frequency |
|---|---|
| Dependency audit (`./gradlew dependencyUpdates`) | Monthly |
| ProGuard/R8 rule review | Per release |
| Backend API key rotation | Quarterly |
| Network traffic inspection (Charles Proxy / mitmproxy) | Per release |
| Screen capture scope audit | Per release |

---

### 8.4 Performance Targets

| Metric | Target |
|---|---|
| Frame processing latency (captureFrame → FrameAnalysis emission) | < 300 ms on mid-range (SD 680) |
| MiniWidget frame drop rate during active draft | < 0.1% dropped frames |
| Cold start to Home Screen | < 1.5 s |
| Peak RSS of OverlayService | < 120 MB |
| Background CPU when MLBB not in foreground | < 0.5% battery/hour |
| GetDraftHistoryUseCase with 500 sessions | < 80 ms |

---

### 8.5 Crash Monitoring

- Integrate Firebase Crashlytics (no PII / no screen content sharing) for production crash monitoring alongside the in-app `CrashLogStore`.
- **P0 crash** (launch or active draft): fix and release within 24 hours.
- **P1 crash** (in-app screen, not overlay): fix and release within 72 hours.
- **P2 crash** (rare edge case): fix in next scheduled release cycle.

---

### 8.6 Play Store Compliance

| Item | Action |
|---|---|
| `SYSTEM_ALERT_WINDOW` declaration | Re-file after any permission scope change |
| `MEDIA_PROJECTION` privacy policy accuracy | Captures processed in RAM only — never stored or transmitted |
| `targetSdk` currency | Stay within 1 year of current Android release |
| `uses-feature android:required="false"` | Audit after every manifest change |

---

## 9. Technical Debt Register

| ID | Area | Description | Priority | Status |
|---|---|---|---|---|
| TD-01 | `CompositionAnalyzer` | CC hero list was hardcoded by name. Replaced with `hasCCUlt: Boolean` field on `Hero` model. | High | ✅ Done |
| TD-02 | `BuildAdvisor` | Spell and item recommendations are hardcoded strings. Introduce `Item` and `Spell` domain classes. | High | ⬜ Phase 2 |
| TD-03 | `PhaseDetector` | `R > 160`, `B > 140` are magic numbers. Define as named constants in `PhaseDetectionConfig`. | Medium | ⬜ Phase 1 |
| TD-04 | `FrameProcessor` | `isSlotFilled()` uses raw luminance threshold of 40. Normalise against sampled background. | Medium | ⬜ Phase 1 |
| TD-05 | `DraftScorer` | Meta score normalisation bounds are fixed constants. Compute dynamically from hero dataset. | Medium | ⬜ Phase 2 |
| TD-06 | Overlay service | IO work in `serviceScope` (Main dispatcher) should explicitly use `withContext(IO)`. | Medium | ⬜ Phase 1 |
| TD-07 | Navigation | `heroId` passed as plain `Int` arg — needs `SavedStateHandle` for process-death backstack safety. | Low | ⬜ Phase 2 |
| TD-08 | `PortraitMatcher` | `preloadHashes()` blocks service startup for up to 2 s. Move to lazy background `Job`. | High | ⬜ Phase 1 |
| TD-09 | Settings | Score weights not validated before `ScoreWeights` construction — runtime `require()` risk. | High | ⬜ Phase 1 |
| TD-10 | `HeroGrid` | Flat list rendering — no pagination. Migrate to `Pager` + `LazyPagingItems`. | Low | ⬜ Phase 3 |
| TD-11 | `CrashLogStore` | `appendSync()` not thread-safe. Wrap in `Mutex`. | Medium | ⬜ Phase 1 |
| TD-12 | Overlay drag | Bubble position not persisted to DataStore on `ACTION_UP`. Bubble resets to default corner on restart. | Low | ⬜ Phase 1 |

---

## 10. Risk Register

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-01 | Moonton changes the MLBB draft screen layout | Medium — ~annually | High — `SlotRegions`, `PhaseDetector`, portrait crops all break | Reference screenshots per MLBB version; screenshot regression tests; 48-h fast-follow release |
| R-02 | Android adds new FGS restrictions | Medium — each major release | High — service killed or fails to start | Track Android Beta; test each new version against service lifecycle before public release |
| R-03 | Google Play rejects app for `SYSTEM_ALERT_WINDOW`/`MEDIA_PROJECTION` use | Low | Critical | Detailed use-case declaration; reference similar approved apps; complete Privacy Policy |
| R-04 | Meta data source becomes unavailable or inaccurate | Medium | Medium — degraded recommendations, no crash; offline cache serves stale data | "Data staleness" warning banner when last sync > 7 days old |
| R-05 | dHash portrait matching false positives | Medium | Medium — recommendation computed for wrong hero | Hybrid dHash + histogram (Phase 1); TFLite classifier (Phase 5) |
| R-06 | OEM battery savers kill the overlay service mid-draft | High on MIUI/ColorOS | High — player loses overlay mid-match | Session persistence (3.1.1); notification relaunch action (3.1.2); watchdog (3.1.3) |

---

## 11. Success Metrics by Phase

| Phase | Key Metric | Target |
|---|---|---|
| Phase 0 | Build passes, no crashes on first-run wizard | Zero P0 crashes in first 48 h post-install |
| Phase 1 | Service survives 10-minute MLBB session on MIUI and ColorOS without kill | 100% survival on test devices |
| Phase 1 | Portrait match false-positive rate | < 8% `requiresConfirmation = true` |
| Phase 2 | `WeightCalibrator` converges for 80% of testers after 20 sessions | ≥ 80% user pool shows non-default weights |
| Phase 3 | Draft simulation sessions created per DAU | ≥ 0.5 simulations/DAU/week |
| Phase 4 | Meta snapshot freshness | < 48 h lag from patch release |
| Phase 5 | TFLite portrait classifier `requiresConfirmation` rate | < 2% |

---

## 12. Architecture Decision Log

| ID | Decision | Rationale | Date |
|---|---|---|---|
| AD-01 | Single `AppDataStore` delegate | Eliminates `IllegalStateException` from multiple `preferencesDataStore` instances in the same process | Phase 0 |
| AD-02 | `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` gated on API 34+ | API 34 strict type enforcement; older devices use the legacy single-type FGS start | Phase 0 |
| AD-03 | Overlay uses `TYPE_APPLICATION_OVERLAY` (not `TYPE_PHONE`) | `TYPE_PHONE` is deprecated; `TYPE_APPLICATION_OVERLAY` is the correct post-API-26 system alert window type | Phase 0 |
| AD-04 | Advisory engine is fully offline-capable | A ranked draft cannot be paused waiting for a network call; the engine must function on cached data | Phase 0 |
| AD-05 | `DraftSession` is immutable data class; mutations produce new instances via copy() | Enables safe `StateFlow` emission and trivial undo-stack implementation | Phase 0 |
| AD-06 | `SlotRegions` uses normalized fractions, not absolute pixels | Absolute pixels break across resolutions; fractions are multiplied by actual capture dimensions at runtime | Phase 0 |
| AD-07 | `MiniWidget` phases are always both visible; only visual weight changes | Hiding the inactive panel caused players to think it was unavailable; dimming preserves context without removing affordance | Phase 0 |
| AD-08 | `CompositionArchetype.detect()` uses priority ordering (WOMBO_COMBO first) | Dive comps often also have assassins — checking combo first prevents misclassification | Phase 0 |
