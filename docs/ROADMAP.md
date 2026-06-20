# MLBB Draft Assistant — Product & Engineering Roadmap

> **How to read this document.**
> This roadmap is structured in six phases followed by a perpetual maintenance track. Each phase builds on the previous. Phases are time-estimated from the current baseline (June 2026), but dates are targets, not contracts — they stretch when blockers appear and compress when parallel execution is possible. Every item is tagged **[P0]** (blocking), **[P1]** (high value), or **[P2]** (quality-of-life). Items without a tag are assumed P1.
>
> A **Perpetual Track** section at the end captures work that has no finish line — patch sync, OEM compatibility, security, performance — and must run in parallel with every named phase.
>
> This document is the single source of truth for what gets built, why it gets built, and in what order. It supersedes any informal todo lists or backlog items.

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

Phase 0 represents the work already merged. It is documented here as a baseline checkpoint, not as planned work.

### Completed fixes

| Item | Area | Description |
|---|---|---|
| **Bubble drag fix** | Overlay / Touch | Touch listener is now mode-aware: bubble mode claims `ACTION_DOWN` and calls `expandToWidget()` on tap; widget mode returns `false` on DOWN, dispatches synthetic `ACTION_CANCEL` to Compose on drag threshold exceeded |
| **DataStore multi-instance crash** | Data / DI | Introduced `AppDataStore.kt` as the single authoritative `preferencesDataStore` delegate; all callers (`AppModule`, `WizardPreference`, `PreferencesDataStore`) now share the same file-backed instance, eliminating `IllegalStateException` |
| **FGS mediaProjection crash (API 34+)** | Service Lifecycle | `startFg()` now uses `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` only on API 34+; `upgradeFgToProjection()` is called from `onStartCommand` after a valid projection token is confirmed, satisfying Android 14's strict type-at-declaration requirement |
| **Wizard step order** | Onboarding | Wizard is now 6 steps in deliberate order: Overlay → Background Running → Battery → Auto-Start → Restricted Settings → Accessibility; Screen Capture and Notifications steps removed |

### Known technical debt carried forward

See [Section 9 — Technical Debt Register](#9-technical-debt-register) for a full catalogue.

---

## 3. Phase 1 — Core Excellence (Months 0–3)

**Goal:** Make the existing feature set robust enough that a first-time user at any rank on any mainstream OEM can complete a full draft session without encountering a crash, a stale UI state, or a confusing permission failure.

This phase is entirely about deepening what already exists — not adding new surface area.

---

### 3.1 Overlay Hardening

#### 3.1.1 Overlay state persistence across service restarts [P0]
**What:** Serialize the active `DraftSession` to DataStore on every `StateFlow` emission. On `OverlayService.onCreate()`, rehydrate the session from DataStore if a non-IDLE, non-COMPLETE session exists.

**Why:** Android aggressively kills foreground services on low-memory devices and after certain OEM power-management events. A player who has reached Pick 7/10 and has their service killed mid-draft loses all context. With persistence, the overlay restores to the exact draft state — including all bans, picks, and the undo stack — within 300 ms of relaunch.

**Implementation notes:**
- `DraftSession` must be serializable to JSON (Kotlinx Serialization or Gson; prefer Kotlinx for type safety).
- Serialize on the IO dispatcher inside the `StateFlow` collector to avoid blocking the main thread.
- Write an integration test that kills and relaunches the service and asserts session equality.
- On restoration, skip `initSession()` and jump directly to the serialized phase.

#### 3.1.2 One-tap overlay relaunch from notification [P1]
**What:** Add a persistent `NotificationCompat.Action` ("▶ Relaunch Overlay") to the foreground service notification. Tapping it sends a `PendingIntent` to `OverlayService` that reinitialises the overlay window without opening the main app.

**Why:** Players who dismiss the bubble accidentally, or whose overlay is killed by a low-memory event, have to navigate back to the main app and tap Start Draft again. One tap from the notification shade resolves this without breaking game focus.

**Implementation notes:**
- Use `PendingIntent.getService()` with an `ACTION_RELAUNCH` extra.
- The relaunch action must only appear when the service is running but the overlay window is not visible.
- The existing notification channel must not be changed — only add the action to the builder.

#### 3.1.3 Accessibility service watchdog [P1]
**What:** Add a coroutine-based watchdog in `OverlayService` that polls `AccessibilityManager.isEnabled()` every 30 seconds. If the service is found to be disabled mid-session, emit a `ServiceEvent.AccessibilityDied` event and show an in-overlay warning banner with a deep link to the accessibility settings.

**Why:** On some OEM skins (MIUI, ColorOS), the accessibility service can be killed and not restarted without user action. Currently the overlay continues running but MLBB auto-detection silently stops working. The watchdog makes the failure visible.

**Implementation notes:**
- The warning banner must not block recommendations — it renders below the active suggestion panel.
- Include an "Open Settings" button that fires `ACTION_ACCESSIBILITY_SETTINGS`.
- The watchdog coroutine must be a child of `serviceScope` so it is cancelled with the service.

#### 3.1.4 Permission revocation recovery [P1]
**What:** On `OverlayService.onStartCommand()`, re-check the overlay permission (`Settings.canDrawOverlays()`). If revoked, show a system notification directing the user back to the wizard at the specific revoked step instead of the first step.

**Why:** OS updates and "permission cleanup" tools can silently revoke `SYSTEM_ALERT_WINDOW`. The current code does not detect this — it simply fails to add the view to `WindowManager` and logs the exception. The recovery path must be proactive.

---

### 3.2 CV Pipeline Improvements

#### 3.2.1 Rank auto-detection from rank emblem region [P1]
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

#### 3.2.2 First-pick side auto-detection [P1]
**What:** Consume `SlotRegions.firstPickIndicator`. MLBB renders a first-pick triangle/arrow indicator in the top-centre of the draft screen pointing left (our team goes first) or right (enemy goes first). Sample the pixel cluster at that region and classify direction by comparing left-half vs right-half luminance asymmetry.

**Why:** The "WHO PICKS FIRST?" manual toggle in the idle widget is the most common source of incorrect advisory output. A player who mis-taps it gets backwards pick sequence recommendations for the entire draft.

**Implementation notes:**
- Pair with rank detection — both run on the first usable draft frame.
- If detection confidence < 0.6, show the manual toggle as a confirmation step rather than hiding it entirely.

#### 3.2.3 Improved portrait matching — dHash + colour histogram hybrid [P1]
**What:** Extend `PortraitMatcher` with a secondary verification pass using colour histogram comparison (bucket the cropped slot into 8×8×8 RGB bins, compute cosine similarity) when dHash similarity falls between 0.70 and 0.85 (the ambiguous "possible match" zone). Accept a match only when both dHash similarity ≥ 0.72 AND colour histogram similarity ≥ 0.75.

**Why:** dHash alone fails on hero portraits with similar geometric layouts but different colour palettes — many mage heroes in MLBB share the same facing angle and background style, producing false matches. The colour histogram check adds a cheap second-pass discriminant.

**Implementation notes:**
- Colour histograms can be precomputed and cached in the same `LruCache<Int, Long>` (extend to `LruCache<Int, PortraitSignature>` where `PortraitSignature` holds both dHash and histogram).
- Target: reduce `requiresConfirmation = true` rate from ~20% to < 8% in testing.

#### 3.2.4 Phase countdown timer OCR for BAN_ROUND_1 vs BAN_ROUND_2 disambiguation [P1]
**What:** Add optional OCR of the countdown timer region (top-centre, below the first-pick indicator) using ML Kit's on-device text recogniser to read the ban/pick round indicator text (e.g., "BAN PHASE 1" / "BAN PHASE 2" printed above the timer). Use this as a definitive phase signal rather than inferring round 2 from ban-count alone.

**Why:** The current `toDraftPhase()` method preserves `BAN_ROUND_2` context by checking if the session is already in that state. This works correctly in most cases but can de-sync if the service is restarted mid-ban. OCR provides a ground-truth phase signal independent of session state.

**Implementation notes:**
- ML Kit Text Recognition v2 (Latin script) is lightweight (~1 MB) and runs entirely on-device.
- Only run OCR when colour-based phase detection returns `UNKNOWN` or `SETUP` — not on every frame.
- Add `mlkit-text-recognition` to `dependencies` in `app/build.gradle.kts`.

---

### 3.3 Advisory Engine Improvements

#### 3.3.1 Continuous pick-index scoring curve [P1]
**What:** Replace the binary `isFirstPick` and `isLastPick` bonuses in `DraftScorer` with a continuous function over pick index:
- **Synergy weight ramp**: starts at the default (e.g., 0.30) and linearly increases to `defaultSynergyWeight + 0.20` by pick index 4 (as more allies are known).
- **Counter weight ramp**: starts at the default and linearly increases to `defaultCounterWeight + 0.20` by pick index 4 (as more enemies are known).
- **Flexibility penalty**: applied to the first pick as a multiplier on counteredBy count, but decreasing to zero by pick index 2.

**Formula:**
```
alphaSync(i)  = min(1.0, defaultSynergy + 0.20 * (i / maxPickIndex))
alphaCounter(i) = min(1.0, defaultCounter + 0.20 * (i / maxPickIndex))
alphaMeta(i)  = 1.0 - (alphaSync(i) - defaultSynergy) - (alphaCounter(i) - defaultCounter)
```

**Why:** At pick index 0 (first pick), only enemy first-pick context is available; synergy and counter calculations are near-meaningless. At pick index 8 (second-to-last), four enemies and four allies are known — synergy and counter calculations are highly meaningful. Fixing weights across all pick indices under-weighs synergy/counter at end-draft.

#### 3.3.2 Composition archetype detection and labelling [P1]
**What:** Add a `CompositionArchetype` enum and a `detectArchetype(heroes: List<Hero>): CompositionArchetype` function to `CompositionAnalyzer`. Archetypes:

| Archetype | Detection Rule |
|---|---|
| `DIVE` | ≥ 1 Tank + ≥ 1 Assassin + CC level ≥ MEDIUM |
| `POKE` | ≥ 2 heroes with high pick rate at MID/GOLD + CC level ≤ LOW |
| `TURTLE` | ≥ 2 Tanks or Fighters + sustain level ≥ HIGH |
| `WOMBO_COMBO` | ≥ 3 heroes with known area CC (Tigreal, Atlas, Khufra, Aurora) |
| `SPLIT_PUSH` | ≥ 1 EXP-lane Fighter with flex lane on GOLD or MID |
| `BALANCED` | No dominant pattern |

Surface the detected archetype in `CompositionProfile` with a one-line win condition and a one-line counter condition. Render it in the MiniWidget active body (collapsed into 1 line: "⚔️ Enemy looks like: DIVE — bring CC").

#### 3.3.3 Ban priority: absolute vs. reactive modes [P1]
**What:** Split `BanRecommender` into two priority lists:
- **Absolute bans**: heroes that should always be banned regardless of enemy picks (isToxicMechanic = true OR isOP = true OR banRate > 0.40). These are presented first with a "🔒 Always Ban" label.
- **Reactive bans**: heroes elevated because the detected enemy first-pick suggests a direction. If the enemy picked a Tank, up-weight Assassin counters. If the enemy picked a Mage, up-weight anti-magic heroes.

Render absolute bans first, then reactive bans. The previous flat ban list is replaced by these two labelled groups.

**Why:** A player who is seeing the Reactive list can make a contextual decision. A player who sees an Absolute ban should ignore it only with a strong reason.

---

### 3.4 Onboarding and UX Polish

#### 3.4.1 Deep-link permission wizard steps [P1]
**What:** Replace top-level `ACTION_MANAGE_OVERLAY_PERMISSION`, `ACTION_ACCESSIBILITY_SETTINGS`, and `ACTION_APPLICATION_DETAILS_SETTINGS` intents with the most specific available deep links for each OEM:
- Overlay permission: already uses package-specific URI — no change needed.
- Battery optimisation: already uses `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + URI — no change needed.
- Accessibility: add OEM-specific accessibility intent actions (Xiaomi uses `com.miui.accessibility/.AccessibilityManagerActivity`).
- Restricted settings: detect Android 12+ and open the app info page directly (already done); add shimmy text explaining exactly where to tap.

#### 3.4.2 Inline permission status re-check [P1]
**What:** After the user returns from a system settings screen (via `onActivityResult` or `onResume` in the wizard), immediately re-evaluate the permission state and auto-advance the wizard step if it is now granted — eliminating the manual "Done / Grant" tap.

**Why:** Players tap "Done" even when the permission is not yet granted. Auto-advancing only when permission is confirmed prevents false completion.

#### 3.4.3 Score explanation bottom sheet [P2]
**What:** Long-pressing any hero recommendation chip in the MiniWidget opens a `ModalBottomSheet` showing:
- The full score breakdown (meta sub-score, synergy sub-score, counter sub-score, role bonus).
- All synergy matches found (hero names + portrait).
- All counter matches found (hero names + portrait).
- The raw stats (win rate, ban rate, pick rate, tier).

**Why:** The badge label (META / SYNERGY / COUNTER) is concise but tells the player nothing about magnitude. A player who wants to understand *why* the app recommends hero X over hero Y at a glance should be able to.

---

### 3.5 Testing Foundation [P0]

A feature without a test has no contract. These tests must be written alongside or before the code they verify.

| Test | Type | What it verifies |
|---|---|---|
| `DraftScorerTest` | Unit | Scores for all-enemy-countered hero > zero; UNKNOWN tier produces 0.0 not negative; weights sum to 1.0; clamping |
| `BanRecommenderTest` | Unit | Top 3 sorted correctly; isToxicMechanic adds 0.30; absolute vs. reactive split |
| `CompositionAnalyzerTest` | Unit | Full physical warning fires at ≥ 80%; CC level thresholds; archetype detection (one test per archetype) |
| `PickSequenceEngineTest` | Unit | 1-2-2-2-2-1 pattern; both first-picker sides; index 0 = isFirstPick; index 9 = isLastPick |
| `RankRuleEngineTest` | Unit | Ban counts at all ranks; inferFromBanCount edge cases |
| `DraftSessionManagerTest` | Unit (coroutine) | undo() reverses last action; swapOurHeroes emits correct state; upgradeRankFromObservedBans |
| `FrameProcessorTest` | Unit | Duplicate slot detection; throttle timing; slot reset on new session |
| `PerceptualHashTest` | Unit | Same image → Hamming distance 0; known pairs → expected distance |
| `DraftSessionSerializationTest` | Unit | Serialize + deserialize DraftSession produces equal object |
| `OverlayServiceIntegrationTest` | Instrumented | Service restarts mid-draft and restores session state |

**Framework:** JUnit 5 + Kotlin Coroutines test library (`kotlinx-coroutines-test`) for unit tests; Espresso + Hilt testing for instrumented tests.

---

## 4. Phase 2 — Intelligence Deepening (Months 3–6)

**Goal:** Graduate the advisory engine from a single-factor linear scorer to a contextually aware system that understands composition intent, individual hero proficiency, and patch velocity.

---

### 4.1 Personal Hero Pool

#### 4.1.1 Hero ownership and proficiency marking [P1]
**What:** Add a hero management screen (accessible from Hero Explorer as a "My Pool" tab). Each hero row has:
- **Owned toggle**: marks heroes the player actually owns in-game.
- **Proficiency selector**: None / Learning / Comfortable / Mastered.

Persist ownership and proficiency in Room (`HeroPoolEntity` table: heroId, isOwned, proficiency enum).

Expose `GetHeroPoolUseCase` and wire it into `DraftScorer`. Heroes not in the pool receive a `poolMultiplier = 0.0`; Learning = 0.5; Comfortable = 0.85; Mastered = 1.0. The total score becomes `rawScore × poolMultiplier`. An un-pooled hero is still shown in recommendations but visually dimmed with a "❌ Not in your pool" badge.

**Why:** Recommending an S+ tier hero the player has never played destroys trust immediately. Pool awareness converts the app from a generic meta tool into a personalised coach.

#### 4.1.2 Pool import hint via Google Play Games [P2]
**What:** After the hero pool screen is first opened, show a banner: "Import your hero pool from your MLBB profile? Share your profile data to pre-fill ownership." This is advisory only — it links to the MLBB social share export as plain text that the user can paste in, not an API integration (Moonton has no public profile API).

---

### 4.2 Patch Velocity Scoring

#### 4.2.1 Explicit patch delta multiplier [P1]
**What:** The `Hero.patchTrend` field already exists. Add a `patchVelocityMultiplier` to `DraftScorer.metaScore()`:

```
val trend = hero.patchTrend.coerceIn(-0.10f, +0.10f)   // cap at ±10 pp
val velocityBonus = 1.0 + (trend / 0.10f) * 0.15f       // ±15% multiplier
metaScore = rawMetaScore * velocityBonus
```

A hero whose win rate rose 5% this patch gets a 7.5% meta score boost. A falling hero gets a 7.5% penalty.

**Why:** A hero at 51% win rate but rising fast is more relevant than a hero at 54% win rate but being nerfed. The current model treats them identically on meta score.

#### 4.2.2 "Rising This Patch" badge [P2]
**What:** If `patchTrend > +0.03` (3% win rate increase this patch), the hero gets a secondary badge "📈 Rising" in amber rendered below the primary badge. This appears in recommendations, the meta board, and hero detail.

---

### 4.3 Enemy Intent Inference

#### 4.3.1 Partial composition intent engine [P1]
**What:** Add `EnemyIntentAnalyzer` to the domain layer. After the first enemy pick (or second ban, since ban patterns also signal intent), classify the partial enemy composition into 2–3 most likely archetype targets using a simple transition probability table:

```
if (enemy picks Atlas OR Tigreal in pick 1-2)
    → likely building: WOMBO_COMBO, DIVE
    → up-weight CC heroes in ally recommendations by +0.15

if (enemy picks Marksman in early picks)
    → likely building: POKE or TURTLE
    → up-weight burst damage heroes

if (enemy has 3+ melee fighters)
    → likely building: DIVE or SPLIT_PUSH
    → up-weight area-damage and kite heroes
```

Surface the inferred enemy intent as a one-line label in the MiniWidget: "Enemy intent: 🔎 WOMBO_COMBO likely".

#### 4.3.2 Reactive ban adjustment from enemy intent [P1]
**What:** Wire `EnemyIntentAnalyzer` output into `BanRecommender`. If enemy intent = DIVE, elevate anti-dive key-piece heroes (e.g., Atlas — the combo enabler — moves to top of absolute ban list). This is the "ban urgency" dimension described in VISION.md.

---

### 4.4 Win Condition Statement Generator

#### 4.4.1 Draft synthesis sentence [P1]
**What:** Add `WinConditionGenerator` to the domain layer. After the 5th ally pick (or on phase = COMPLETE), generate a one-sentence win condition statement from the team composition:

Template system (not ML — rule-based templates for reliability):
```
Your team [primary_strength]. [Action_verb] [when/if condition]. [Avoid scenario].
```

Examples:
- "Your team wins sustained teamfights. Force extended fights near the turtle. Avoid early split-push games."
- "Your team excels at wombo-combo initiations. Engage only when Tigreal and Atlas ults are both up. Never fight without both ults available."
- "Your team is a poke composition. Whittle them down before contesting lords. Avoid all-ins until they are below 70% HP."

Render the win condition in a gold accent card at the bottom of the COMPLETE overlay state and in the DraftHistoryScreen card.

---

### 4.5 Build Advisor Expansion

#### 4.5.1 Full 6-item build with situational swaps [P1]
**What:** Extend `BuildAdvisor` to produce a full 6-item build rather than a 3-item adjusted list. Structure:
- **Core items (3)**: always built regardless of enemy composition (from `hero.coreItems`).
- **Situational item 4**: selected based on enemy archetype (anti-CC item if enemy is CC-heavy, etc.).
- **Luxury item 5–6**: selected based on game state heuristics (ahead/behind indicators are not detectable, so surface two options: "If ahead: [item]" / "If behind: [item]").

Persist the full build display in a new `BuildPanel` composable rendered in the COMPLETE overlay state and a new "Build" tab in HeroDetailScreen.

#### 4.5.2 Emblem recommendation [P2]
**What:** Add `recommendedEmblem` field to `BuildAdvice` output. MLBB has three emblem trees (Mage, Physical/Marksman, Tank/Support) each with talent nodes. Surface one recommended emblem name + two talent recommendations as plain text (e.g., "Mage emblem: Impure Rage + Focusing Mark").

---

### 4.6 Localisation Foundation [P1]

**What:** Extract all user-visible strings from Compose composables into `res/values/strings.xml`. Create placeholder translations for:
- `values-in/strings.xml` (Indonesian — largest MLBB player base)
- `values-fil/strings.xml` (Filipino/Tagalog — second largest)
- `values-th/strings.xml` (Thai)
- `values-vi/strings.xml` (Vietnamese)
- `values-ms/strings.xml` (Malay)

For Phase 2, machine-translate all strings. Native speaker review and copywriting is a Phase 3 deliverable.

**Why:** Over 60% of MLBB's ranked player base is in Southeast Asia. A tool in their native language has dramatically higher adoption and trust than one that forces English.

**Implementation notes:**
- All `Text("hardcoded string")` calls must be replaced with `stringResource(R.string.key)`.
- Recommendation reason strings, composition warnings, and win condition templates need parameterised string resources (`getString(R.string.synergy_reason, allyName, enemyName)`).
- hero names are proper nouns — do NOT translate them, only translate surrounding UI chrome.

---

## 5. Phase 3 — Personalisation & Feedback Loop (Months 6–12)

**Goal:** Transform the historical session data from a read-only record into an active feedback mechanism that adapts the scoring model to the individual player over time.

---

### 5.1 Match Outcome Tracking

#### 5.1.1 Win/loss reporting [P0 for this phase]
**What:** Add a match outcome prompt that appears when the COMPLETE overlay body is shown (or when the app is re-opened shortly after a draft). A bottom sheet with two options: "WON ✅" / "LOST ❌". The outcome is written to `DraftSessionEntity.outcome: Outcome?` (nullable — a player who skips reporting should not pollute the dataset with nulls treated as losses).

**Why:** Without win/loss data, the historical scoring analysis is correlation without validation. With it, the system can begin identifying which weight configurations actually correlate with wins for this player.

#### 5.1.2 Post-match score validation [P1]
**What:** After 10+ completed sessions with outcome recorded, display a "Your Insights" card on the Home Screen:
- "Drafts where you followed recommendations: X% win rate"
- "Drafts where you overrode recommendations: Y% win rate"
- "Your strongest archetype: DIVE (Z% win rate)"
- "Your weakest archetype: POKE (W% win rate)"

These metrics require no server-side computation — they are computed from local Room queries.

---

### 5.2 Adaptive Scoring Weights

#### 5.2.1 Personal weight calibration engine [P1]
**What:** Add `WeightCalibrator` to the domain layer. After 20+ sessions with outcomes, compute the Pearson correlation between each weight dimension's session score and the win/loss binary for that session. If counter score correlates more strongly with wins than the current counter weight implies, nudge the counter weight upward by 0.05 (max nudge per calibration cycle). Apply the inverse for dimensions that do not correlate with wins.

Constraints:
- Weights are still bounded by the user's manual settings as a ceiling. If the user set counter weight to 20%, the calibrator cannot push it above 30% (= manual setting + 10% cap).
- Calibration runs at most once per 5 new sessions, not after every game.
- Display calibration results to the user: "Based on your last 25 drafts, I've slightly increased Counter priority for you. Tap to review."

#### 5.2.2 Calibration transparency and override [P1]
**What:** Add a "My Calibration" section to Settings showing:
- Current effective weights (manual + calibration adjustments).
- The calibration history (last 3 adjustment events with date and delta).
- A "Reset calibration" button that returns to pure manual weights.

---

### 5.3 Draft Pattern Recognition

#### 5.3.1 Personal draft tendency analysis [P2]
**What:** Add `DraftPatternAnalyzer` that queries the last 30 sessions and identifies tendencies:
- Over-ban rate: if the player bans heroes that were not in the top 3 recommendations in > 60% of sessions → flag "You often ban heroes outside the top recommendations — are you targeting specific players?"
- Under-roam rate: if the ROAM lane is unfilled in > 40% of ally compositions → flag "Your team frequently drafts without a roam."
- First-pick pattern: which hero the player picks first most often.

Surface tendencies as dismissable insight cards on the Home Screen (one card per tendency, max 2 shown at a time).

---

### 5.4 Match Timeline View

#### 5.4.1 Draft replay viewer [P2]
**What:** In `DraftHistoryScreen`, tapping a session opens a `DraftReplayScreen` that animates through the draft turn by turn. Each turn shows:
- What was recommended (top 3 at that moment, reconstructed from stored session data).
- What was actually picked/banned.
- The composition score delta from that pick/ban.
- A green or amber indicator for "followed recommendation" vs. "override".

**Implementation notes:**
- `DraftSessionEntity` already stores picks/bans in order. Replay is a presentation-layer concern — no new data storage needed.
- The composition score delta requires re-running `DraftScorer` on the partial composition at each turn. This is computationally cheap (< 2 ms per hero on modern hardware).

---

### 5.5 Draft Simulation Mode

#### 5.5.1 Practice draft without a live game [P1]
**What:** Add a "Simulate Draft" button in the app (from Home Screen, separate from the live overlay flow). The simulation mode:
- Uses the exact same overlay MiniWidget UI.
- Presents both "YOUR TURN" prompts and "ENEMY TURN" prompts to the player, who manually inputs all 10 picks and all bans.
- Runs the full advisory engine, composition analysis, and win condition generation for both teams.
- Saves the simulated session to `DraftHistoryEntity` with an `isSimulation = true` flag.

**Why:** Players want to practice draft theory, study counter relationships, and experiment with compositions outside of ranked games. Simulation mode makes the app useful 24/7, not just during the 2 minutes of a real draft.

---

### 5.6 Native Speaker Localisation Review [P1]

Recruit native speakers for:
- Indonesian (highest priority)
- Filipino/Tagalog
- Thai
- Vietnamese

Have all user-visible strings reviewed and corrected. Hero name pronunciations/spellings may differ by locale — maintain a locale override table for hero display names.

---

## 6. Phase 4 — Ecosystem Expansion (Months 12–24)

**Goal:** Expand the app's value proposition beyond the individual player session, supporting team play, content creation, and community-driven meta data.

---

### 6.1 Team Draft Mode

#### 6.1.1 Shared draft session via local network [P1]
**What:** Two or more players on the same WiFi network can share a draft session. One player hosts (runs the full `DraftSessionManager`); others join by scanning a QR code. Guests see the live session state (bans, picks, current turn) and recommendations on their own device but do not control the session.

**Why:** In coordinated ranked play (5-stack), a team strategist running the assistant can share live recommendations with the whole team without everyone needing their own setup.

**Technical approach:**
- Use Android's built-in WifiP2P (WiFi Direct) or a simple mDNS + TCP socket server hosted by the session owner.
- No internet required — all data stays on the local network.
- Guest devices run the full advisory engine locally (they each have the hero data); only session state (bans, picks, phase) is transmitted.

#### 6.1.2 Tournament bracket mode [P2]
**What:** A configurable draft mode where the admin sets custom ban counts, pick counts, and side selection per game. Supports Best-of-3 and Best-of-5 formats. Tracks historical game-by-game draft outcomes within the tournament.

---

### 6.2 Community Meta Data Source

#### 6.2.1 Meta API backend specification [P0 for this phase]
**What:** Publish a formal OpenAPI specification for the `GET /v1/meta/snapshot` endpoint the app already consumes. The spec defines:
- `HeroDto`: all fields that map to the domain `Hero` model.
- `MetaSnapshotDto`: list of heroes + patch identifier + snapshot timestamp.
- Error responses: 404 (no snapshot available), 503 (maintenance).

**Why:** The app already has the client contract defined (`MetaApi` interface). What does not exist is a backend that serves it. This item documents the contract so that a backend implementation (internal or community-contributed) can be built to it.

#### 6.2.2 Community-maintained meta snapshot [P1]
**What:** Build a lightweight backend service (Node.js/Express or Kotlin/Ktor) that:
- Accepts patch-tagged hero stat submissions from trusted contributors.
- Validates submissions against the OpenAPI schema.
- Publishes them at `GET /v1/meta/snapshot`.
- Caches the response with a 24-hour TTL.

The app already has `SyncHeroesUseCase` and `MetaApi` — the backend is the only missing piece for live meta data.

**Initial data source:** Scrape from community-maintained MLBB stats sites (with permission), or ingest data from the [MLBB Fandom Wiki](https://mobile-legends.fandom.com) / [MLBB.gg](https://mlbb.gg) tier lists.

**Ethics and legality:** Moonton does not provide a public match data API. All stats must be derived from community observation or public web pages — never from game process memory, network interception, or replay file parsing.

---

### 6.3 Content Export

#### 6.3.1 Draft summary image export [P2]
**What:** At draft completion, offer "Share Draft" which generates a styled PNG image of the full draft (both team compositions, composition archetypes, win condition text, draft score breakdown) suitable for Discord/Twitter sharing. Implement using `Canvas` API with the same MLBB gold theme.

#### 6.3.2 CSV draft history export [P2]
**What:** In DraftHistoryScreen, add an Export button that writes all sessions to a CSV file in `Downloads/` and fires `ACTION_SEND`. Columns: timestamp, rank, ourPicks, enemyPicks, ourBans, enemyBans, draftScore, metaScore, counterScore, synergyScore, followRate, outcome.

---

### 6.4 Tablet and Foldable Support [P1]

**What:** Update all Compose screens to use adaptive layouts:
- `WindowSizeClass.EXPANDED` (tablets ≥ 840 dp): side-by-side pane layout on HeroDetail (portrait on left, stats on right), MetaBoard rendered as a single-page grid instead of tabs.
- Foldable HALF_OPEN mode (book posture): treat upper half as hero list, lower half as detail view.
- MiniWidget: on tablets, the widget can expand to a larger 400 dp wide panel showing more recommendations simultaneously.

**Implementation notes:**
- Use `WindowSizeClass` from `androidx.window:window` (already likely in catalog).
- The overlay `WindowManager` layout params must read screen width at service creation time — on foldables, this can change mid-session (folding/unfolding). Register a `DisplayListener` to recalculate params.

---

### 6.5 Accessibility (a11y) Audit [P1]

**What:** Conduct a full TalkBack accessibility audit across all screens:
- Every `HeroPortrait` composable must have a `contentDescription` that includes hero name, role, and tier.
- All icon-only buttons (minimize, close, undo) must have `contentDescription`.
- `QuickHeroChip` in the MiniWidget must announce the hero name and recommendation reason when focused.
- Colour is never the sole conveyor of information (ban/pick indicators use both colour AND shape/text).
- Touch targets ≥ 48 dp × 48 dp enforced everywhere (some icon buttons in the overlay may be smaller — audit these).

---

## 7. Phase 5 — Platform Evolution (Months 24–36)

**Goal:** Expand the platform reach beyond the current Android phone baseline, and migrate the advisory engine toward higher-confidence ML-based detection.

---

### 7.1 TensorFlow Lite Portrait Classifier

#### 7.1.1 MobileNetV3-Small hero portrait classifier [P1]
**What:** Train a MobileNetV3-Small model on MLBB hero portrait crops (one class per hero — currently ~130 classes). Target accuracy: > 98% on a held-out test set of clean portrait crops; > 90% on artificially noisy crops (brightness-shifted, slightly blurred).

**Training data:** Synthetic dataset generated by programmatically applying standard MLBB portrait art with random crop offsets, brightness jitter, and blur — no real screen captures required.

**Deployment:** Export to `.tflite` flatbuffer, quantize to INT8, bundle in `assets/`. Expected model size: 3–5 MB. Expected inference time: < 10 ms on a mid-range device (Snapdragon 680).

**Integration:** Replace the dHash + histogram hybrid in `PortraitMatcher` with TFLite inference as the primary classifier. Retain dHash as a cheap pre-filter to skip TFLite inference when dHash similarity < 0.40.

**Target:** `requiresConfirmation` rate < 2%.

#### 7.1.2 On-device model update mechanism [P2]
**What:** The `.tflite` model must be patchable without a full app release. Implement a model version check at sync time: if `GET /v1/meta/snapshot` response includes a `model_version` field higher than the bundled model version, download the new model to `filesDir` and hot-swap it on the next `OverlayService` start.

---

### 7.2 Android Emulator / BlueStacks Support

#### 7.2.1 Emulator detection and overlay adaptation [P1]
**What:** Detect whether the app is running on an x86 emulator (BlueStacks, LDPlayer, MuMu Player) by checking `Build.HARDWARE` and `Build.FINGERPRINT`. On emulators:
- `SYSTEM_ALERT_WINDOW` behaves differently — the overlay must use `TYPE_PHONE` instead of `TYPE_APPLICATION_OVERLAY` on some emulator versions.
- Screen capture dimensions may differ from the MLBB game window (which runs in a sub-window on desktop emulators). The `SlotRegions` fractions need an emulator-specific coordinate table.
- The wizard step "App Auto-Start" is irrelevant on emulators — skip it automatically.

#### 7.2.2 Emulator-specific slot region calibration [P1]
**What:** Add a one-time calibration step (triggered automatically on first draft session on an emulator) that presents a calibration overlay asking the user to tap the corners of the MLBB draft window. The app derives the correct fractional slot regions from these four taps and stores them as a `DeviceProfile` in Room.

---

### 7.3 iOS Feasibility Study [P2]

**What:** Evaluate the technical feasibility of an iOS port. Document blockers:
- iOS does not have a `SYSTEM_ALERT_WINDOW` equivalent; overlay apps on iOS are not possible in the same way. The app's overlay-first architecture has no iOS analogue without a jailbreak.
- iOS has no `AccessibilityService` equivalent for detecting MLBB foreground state.
- iOS `ReplayKit` requires explicit start/stop and cannot run as a transparent background service.

**Conclusion likely:** A native iOS implementation of the same overlay concept is not feasible on stock iOS. A companion web app (see 7.4) may serve iOS users.

---

### 7.4 Web Companion App

#### 7.4.1 Browser-based draft assistant [P2]
**What:** A progressive web app (PWA) that provides all in-app screen functionality (hero explorer, meta board, draft simulation) but not the overlay (which requires native Android). Target: players studying meta on PC or iOS devices.

**Stack:** React + TypeScript, sharing the same meta API backend from Phase 4.

**Scope explicitly excluded:** The overlay, screen capture, and autonomy features. These remain Android-only.

---

### 7.5 Android Watch / Wear OS Companion [P2]

**What:** A Wear OS tile and complication that shows:
- Current draft phase (synced from the phone via `DataClient`).
- The top pick recommendation (hero name only — portrait is too small).
- A haptic pulse when it is the player's turn.

Scope: read-only companion. No input or control.

---

## 8. Perpetual Track — Ongoing Maintenance

These activities have no end date. They run in parallel with every named phase.

---

### 8.1 Patch Sync — Every MLBB Patch (~Biweekly)

| Task | Who/What | Details |
|---|---|---|
| Hero stat update | Meta backend / sync | All win rates, ban rates, pick rates, tier classifications updated from community sources within 48 h of a new patch |
| New hero addition | Codebase + model | Add new hero to DB seed data; add portrait to TFLite training set; bump model version |
| Meta snapshot publish | Backend | New `MetaSnapshotDto` published at `/v1/meta/snapshot` with patch tag |
| Outdated `isToxicMechanic` / `isOP` flags | Manual review | These flags are editorial — review after each patch notes release |
| `BuildAdvisor` item list | Manual review | Core items and situational items may change with item balance patches |
| CC hero hardcoded list | Manual review | `CompositionAnalyzer` has a hardcoded list of CC heroes — review after each hero rework |

---

### 8.2 OEM Compatibility

| Task | Frequency | Details |
|---|---|---|
| Auto-start intent table verification | Quarterly | Test `openAutoStartSettings()` intent actions against latest MIUI, ColorOS, FuntouchOS, EMUI, OneUI firmwares |
| FGS kill resistance validation | Quarterly | Verify that the overlay service survives background process removal on all OEM battery-saver modes |
| `TYPE_APPLICATION_OVERLAY` behaviour | Per Android major release | Verify overlay rendering on each new Android version in emulator before release |
| Wizard screenshot accuracy | Per MLBB update | MLBB occasionally changes its draft screen layout — verify `SlotRegions` fractions after each major MLBB update |

---

### 8.3 Security

| Task | Frequency | Details |
|---|---|---|
| Dependency audit | Monthly | `./gradlew dependencyUpdates` — update all dependencies with known CVEs immediately; minor updates monthly |
| ProGuard/R8 rule review | Per release | Verify that no sensitive class names are leaked in crash logs; ensure `BuildAdvisor` and `DraftScorer` are fully obfuscated |
| Backend API key rotation | Quarterly | If the meta backend uses any API keys for upstream data sources, rotate them on a fixed schedule |
| Network traffic inspection | Per release | Use Charles Proxy / mitmproxy to verify the app makes no undocumented network calls |
| Screen capture scope | Per release | Audit that captured frames are never written to external storage, shared with third parties, or retained beyond the current frame |

---

### 8.4 Performance

| Task | Metric | Target |
|---|---|---|
| Frame processing latency | Wall time from `captureFrame()` call to `FrameAnalysis` emission | < 300 ms at all times on mid-range device (SD 680 equivalent) |
| Overlay UI jank | Frame drop rate in the MiniWidget during active draft | < 0.1% dropped frames (1-in-1000) |
| App startup time | Cold start to Home Screen visible | < 1.5 s on mid-range device |
| Memory — overlay service | Peak RSS of `OverlayService` process | < 120 MB (including Compose, ImageReader buffer, LruCache) |
| Battery consumption | Background CPU when MLBB is not in foreground | < 0.5% battery/hour |
| DB query latency | `GetDraftHistoryUseCase` load time with 500 sessions | < 80 ms |

Profile with Android Studio Profiler and the Macrobenchmark library. Regressions against these targets block release.

---

### 8.5 Crash Monitoring

- Integrate Firebase Crashlytics (or an equivalent crash reporting SDK that does not share screen content or PII) for production crash monitoring. The in-app `CrashLogStore` remains as the user-accessible log — Crashlytics is the developer-accessible aggregate.
- Define SLOs:
  - **P0 crash** (crash on launch or crash during active draft): fix and release within 24 hours of detection.
  - **P1 crash** (crash in an in-app screen, not the overlay): fix and release within 72 hours.
  - **P2 crash** (rare edge-case crash): fix in next scheduled release cycle.

---

### 8.6 Play Store Compliance

| Item | Details |
|---|---|
| Sensitive permissions declaration | `SYSTEM_ALERT_WINDOW` requires a Play Store declaration explaining use case; re-file after any permission scope changes |
| `MEDIA_PROJECTION` policy compliance | Ensure the app's `Privacy Policy` accurately describes what screen captures are used for; captures are processed only in RAM and never stored or transmitted |
| Target SDK | Track `targetSdk` to within 1 year of the current Android release per Google Play policy (e.g., when Android 16 ships, bump `targetSdk` to 35 within 12 months) |
| `uses-feature` declarations | Ensure `android:required="false"` on all hardware features not strictly required (camera, telephony, etc.) to maximise device compatibility |

---

## 9. Technical Debt Register

Items that are not bugs and not new features, but known quality gaps that will compound if unaddressed.

| ID | Area | Description | Priority | Phase to Address |
|---|---|---|---|---|
| TD-01 | `CompositionAnalyzer` | CC hero list is hardcoded by name (Tigreal, Atlas, etc.). New CC heroes added in patches will be missed until manually updated. Replace with a `hasCCUlt: Boolean` field on `Hero` model. | High | Phase 1 |
| TD-02 | `BuildAdvisor` | Spell and item recommendations are hardcoded strings, not linked to any data model. Any item rename in a patch silently produces wrong recommendations. Introduce `Item` and `Spell` domain classes, persisted in Room. | High | Phase 2 |
| TD-03 | `PhaseDetector` | Red/blue threshold values (R > 160, B > 140) are magic numbers tuned for one device. Define them as named constants in a `PhaseDetectionConfig` object. Add a calibration test that validates them against reference screenshots. | Medium | Phase 1 |
| TD-04 | `FrameProcessor` | `isSlotFilled()` uses a raw luminance threshold of 40, which will be wrong on devices with non-standard colour profiles or if MLBB changes its draft slot background colour. Normalise luminance against a sampled background region. | Medium | Phase 1 |
| TD-05 | `DraftScorer` | `metaScore` normalisation bounds (0.48–0.56 win rate, 0–40% ban rate) are fixed constants. If the meta shifts dramatically, these bounds become wrong and the score distribution collapses to one end. Compute bounds dynamically from the current hero dataset (5th–95th percentile). | Medium | Phase 2 |
| TD-06 | Overlay service | `serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())`. Main dispatcher is correct for UI but all IO work (DataStore writes, Room queries called from the service) should be explicitly `withContext(IO)`. Audit all service-level IO calls. | Medium | Phase 1 |
| TD-07 | Navigation | `AppNavGraph` passes `heroId` as a plain `Int` argument via `navigateToHeroDetail(heroId)`. If navigation backstack restoration is ever needed (process death), this argument must be treated as a `SavedStateHandle` key. | Low | Phase 2 |
| TD-08 | `PortraitMatcher` | `preloadHashes()` is called from `OverlayService.onCreate()` on the IO dispatcher, which blocks service startup for up to 2 seconds on first launch. Move to a lazy background Job with a `Deferred<Boolean>` that only awaits when a match is first needed. | High | Phase 1 |
| TD-09 | Settings | Scoring weight sliders are coupled by a UI-level validation (sum ≠ 100% warning), but the `ScoreWeights` domain class enforces the invariant with a `require()` at construction. If the UI allows saving weights that do not sum to 1.0, the domain model will throw at runtime. Add an explicit `validate()` before saving. | High | Phase 1 |
| TD-10 | `HeroGrid` | Heroes are loaded as a flat list and rendered without pagination. At 130+ heroes this is fine today, but adding skins/variants would bloat this. Migrate to `Pager` + `LazyPagingItems` proactively. | Low | Phase 3 |
| TD-11 | `CrashLogStore` | `appendSync()` writes on the calling thread with no lock. Two concurrent crashers could interleave log lines. Wrap the file write in a `Mutex`. | Medium | Phase 1 |
| TD-12 | Overlay drag | After a drag, the bubble position is saved to `overlayX`/`overlayY` instance variables but is not persisted to DataStore. On service restart, the bubble returns to its default corner. Persist position to DataStore on `ACTION_UP`. | Low | Phase 1 |

---

## 10. Risk Register

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-01 | **Moonton changes the MLBB draft screen layout** | Medium — happens ~annually with major UI updates | High — `SlotRegions` fractions, `PhaseDetector` thresholds, and portrait region crops all break | Maintain a set of reference screenshots from each MLBB version; add screenshot regression tests; publish a fast-follow release within 48 h of a breaking MLBB update |
| R-02 | **Android adds new FGS restrictions** | Medium — happens with each major Android release | High — service may be killed during draft or fail to start | Track Android Beta releases; test each new Android version against the service lifecycle before it ships publicly; maintain an FGS compatibility matrix |
| R-03 | **Google Play rejects the app for `SYSTEM_ALERT_WINDOW` or `MEDIA_PROJECTION` use** | Low — the app's use case (game assistant overlay) is a well-established Play Store category | Critical | Prepare a detailed use-case declaration; reference similar approved apps (Discord overlay, coach apps); ensure the Privacy Policy is complete and accurate |
| R-04 | **Meta data source becomes unavailable or inaccurate** | Medium — community data sources are volunteer-maintained | Medium — recommendations degrade but do not crash; offline cache serves stale data | Add a "Data staleness" warning banner when the last sync is > 7 days old; document the meta API contract clearly so community contributors can maintain the backend |
| R-05 | **dHash portrait matching false positives cause incorrect hero detection** | Medium — affects heroes with visually similar portraits | Medium — recommendation is computed for the wrong hero; player may not notice | Hybrid dHash + histogram (Phase 1); TFLite classifier (Phase 5); always show matched hero name in slot dots so player can visually confirm |
| R-06 | **OEM battery optimisation kills the service during a draft** | High on Xiaomi/OPPO/Vivo without proper auto-start setup | High — mid-draft service kill with no recovery | Overlay state persistence (Phase 1 TD); one-tap relaunch notification (Phase 1); watchdog service (Phase 1) |
| R-07 | **Personal hero pool data is wrong (player marks wrong heroes as owned)** | Medium — user input error | Low — recommendations are sub-optimal but not harmful | Pool weight multiplier is additive, not exclusive — un-pooled heroes still appear with a visual warning, allowing manual override |
| R-08 | **Calibrated weights diverge from user preference** | Low — calibration moves slowly (0.05 per cycle) | Low | Calibration is fully visible and resettable (Section 5.2.2); weights cannot move beyond the user's manual ceiling |
| R-09 | **Win condition generator produces wrong or misleading advice** | Medium — rule-based templates can produce nonsensical combinations | Medium — player acts on wrong advice | Templates are conservative and general (not highly specific); all win condition text is labelled as an AI suggestion, not a guarantee; future versions validated against high-ELO community review |
| R-10 | **Screen capture frames inadvertently stored or transmitted** | Very Low — current code never persists frames | Critical if it occurred — privacy violation | Security audit (Perpetual Track 8.3); verify no frame data escapes the `FrameProcessor` coroutine; add automated static analysis rule to block `File.write` or Retrofit calls that take `Bitmap` arguments |

---

## 11. Success Metrics by Phase

Success metrics are observable, measurable, and tied to the goals of each phase.

### Phase 1 — Core Excellence
| Metric | Target |
|---|---|
| Overlay service kill rate during a ranked draft | < 2% of sessions (measured via session completion rate) |
| `requiresConfirmation` rate from portrait matcher | < 8% of hero detections |
| Permission wizard completion rate (step 1 → step 6) | > 75% of new installs |
| First-draft success rate (user completes their first session without restarting) | > 80% |
| Crash-free session rate | > 98.5% |

### Phase 2 — Intelligence Deepening
| Metric | Target |
|---|---|
| Enemy intent prediction accuracy (archetype matches actual enemy comp at draft end) | > 65% |
| Build advisor relevance (user does not manually override recommended item 4+) | > 60% of sessions |
| Localised installs (SE Asian locales combined) | > 40% of total installs |
| Average session score improvement over first 10 sessions | > 5 points (0–100 scale) for returning users |

### Phase 3 — Personalisation & Feedback Loop
| Metric | Target |
|---|---|
| Win rate of sessions following ≥ 60% of recommendations (vs. sessions following < 40%) | > +8% differential |
| Match outcome reporting rate | > 50% of completed sessions |
| Calibration-adjusted weight improvement (correlation between calibrated weight score and win rate vs. default weight score) | > +0.05 Pearson coefficient improvement |
| Simulation mode sessions per active user per week | > 2 |

### Phase 4 — Ecosystem Expansion
| Metric | Target |
|---|---|
| Team draft mode adoption | > 15% of active users in any given week |
| Meta data freshness (time from MLBB patch release to updated snapshot available) | < 48 hours |
| Tablet/foldable user session duration vs. phone | Within 10% (i.e., tablet layout does not degrade engagement) |
| Draft summary image shares | > 5% of completed sessions |

### Phase 5 — Platform Evolution
| Metric | Target |
|---|---|
| TFLite portrait classifier accuracy | > 98% on clean crops, > 90% on noisy crops |
| `requiresConfirmation` rate (post-TFLite) | < 2% |
| Emulator support adoption | > 10% of all installations |
| Model hot-swap success rate (no app update required) | > 99.5% |

### Perpetual Track
| Metric | Target |
|---|---|
| Time to updated meta snapshot after MLBB patch | < 48 hours |
| Time to fix for P0 crash | < 24 hours from detection |
| Dependency CVEs at critical/high severity outstanding > 30 days | 0 |
| OEM compatibility coverage (major OEMs tested per release) | 6 OEMs (Xiaomi, OPPO, Vivo, Huawei, Samsung, OnePlus) |

---

## 12. Architecture Decision Log

Decisions made during the life of this project that future maintainers need to understand. Not implementation details — decisions that would be costly to reverse.

| ID | Decision | Rationale | Reversibility |
|---|---|---|---|
| AD-01 | Single `ComposeView` for both bubble and widget modes (content swap, not view swap) | Avoids flicker and state loss from `addView`/`removeView`; preserves window position across mode changes | Hard — changing to two views requires careful position sync between the two views |
| AD-02 | View-level touch handler owns bubble drag; Compose owns widget button clicks | Resolves the race condition between `View.onTouchEvent` drag detection and `Modifier.clickable`; required separate code paths per mode | Medium — a future Compose version may expose a lower-level gesture API that unifies these |
| AD-03 | `AppDataStore.kt` as a single extension property delegate | The `IllegalStateException` thrown by multiple `preferencesDataStore` delegates targeting the same file makes multi-delegate architecture impossible | Hard — the single-delegate pattern is now the only correct approach for this file |
| AD-04 | FGS starts with `SPECIAL_USE` only, upgrades to `MEDIA_PROJECTION` after token | Android 14 requirement: MediaProjection token must be present when FGS declares the type; starting without capture intent prevents crashes on devices that have not consented | Hard — this ordering is mandated by Android 14 behaviour, not a design preference |
| AD-05 | `ScoreWeights` enforces summation invariant at construction via `require()` | Fail-fast at the domain layer rather than silently producing scores that do not sum to 100% | Soft — can relax to a warning if normalization becomes automatic |
| AD-06 | dHash (64-bit difference hash) as the primary portrait matching algorithm | O(1) per comparison, hardware-independent, no model file required, cache-friendly as a `Long` in `LruCache` | Soft — Phase 5 migrates to TFLite; dHash becomes a pre-filter |
| AD-07 | Room for draft session persistence; DataStore for preferences | Room provides relational queries and migrations for session history; DataStore provides coroutine-native key-value access for settings — mixing them correctly separates structured vs. unstructured data | Soft — could consolidate to Room-only if DataStore becomes a maintenance burden |
| AD-08 | Clean Architecture (domain / data / presentation) with Hilt DI | Enables unit testing of the scoring engine without Android instrumentation; allows future backend or data source swaps without touching the advisory logic | Hard to reverse fully — the domain/data split is pervasive; Hilt annotations are in every class |
| AD-09 | Fractional (0.0–1.0) slot coordinates in `SlotRegions` | Resolution-independent — the same fractions work on 720p, 1080p, and 1440p screens without modification | Hard — switching to absolute pixel coordinates would require a device resolution table and break on every new device |
| AD-10 | `StateFlow<DraftSession>` as single source of truth for draft state | All consumers (MiniWidget, DraftScreen, OverlayService, recommendation engine) observe the same stream; no possibility of divergent state between UI and engine | Medium — replacing with MVI `StateFlow<ViewState>` per screen is possible but requires significant refactor |

---

*Last updated: June 2026. This document is a living artifact — update it when phases shift, when new decisions are made, or when a risk materialises. Do not let it go stale: a stale roadmap is worse than no roadmap.*
