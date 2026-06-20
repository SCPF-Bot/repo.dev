# Features

An exhaustive catalogue of every feature in the codebase, organized by system. Features are described at the level of user-observable behavior and, where relevant, the mechanism that produces it.

---

## 1. Floating Overlay System

### 1.1 Floating Bubble (Collapsed Mode)
- A 56 dp circular bubble is rendered as a `ComposeView` added directly to `WindowManager` with `TYPE_APPLICATION_OVERLAY`.
- The bubble is draggable to any position on screen. Drag is detected at the View level (not Compose level) by claiming the touch sequence on `ACTION_DOWN`, tracking displacement against `ViewConfiguration.scaledTouchSlop`, and calling `windowManager.updateViewLayout()` on `ACTION_MOVE` with initial-based delta (drift-free arithmetic).
- After a drag, the bubble is clamped to safe screen bounds on `ACTION_UP` via `clampToScreen()`.
- A tap (gesture that does not exceed touch slop) directly calls `expandToWidget()` — no Compose gesture detection involved in bubble mode, eliminating the race condition between view-level drag and Compose's `Modifier.clickable`.
- The bubble pulses with an animated scale (`1.0f → 1.08f`) and glowing ring border during active draft phases (any phase other than IDLE or COMPLETE). The animation is an infinite `EaseInOutSine` tween at 900 ms per cycle.
- The bubble's center character and color reflect the current draft phase: blank "D" during IDLE, phase abbreviations (BAN, BAN R2, PICK, TRADE, DONE) with color-coded accent (red for ban phases, blue for pick, amber for trading).
- The glow ring is 66 dp — larger than the 56 dp bubble — and only rendered during active draft phases. It uses a radial gradient that fades to transparent at its edges.

### 1.2 MiniWidget (Expanded Mode)
- Expanding the overlay switches the `ComposeView` content from `FloatingBubble` to `MiniWidget` without removing and re-adding the view, preserving window position.
- The widget is constrained between 240 dp and 300 dp wide, with a dark semi-transparent background and a 1 dp MLBB gold border.
- The widget is also draggable. In widget mode the touch listener returns `false` on `ACTION_DOWN` (Compose sees it and arms all button/chip gesture detectors). When the drag threshold is exceeded, `ACTION_CANCEL` is dispatched to Compose via `v.onTouchEvent(cancel)` and the listener takes ownership of the sequence.
- After widget drag, `clampToScreen()` constrains the window so the 280 dp × 200 dp widget body does not go off-screen.
- A drag-handle visual (six 2.5 dp dots arranged in a 2 × 3 grid) appears in the header as a cue. The actual drag works on the entire header surface via the View-level touch handler.
- The widget has three body states: IDLE/SETUP, ACTIVE DRAFT, COMPLETE.

### 1.3 Widget Header
- Displays "MLBB DRAFT" in gold and the current phase label in secondary text.
- Minimize button (—): collapses back to bubble via `collapseToBubble()`, which updates `overlayParams.flags` to `bubbleFlags()` and calls `windowManager.updateViewLayout()`.
- Close button (✕): calls `stopSelf()` on the `OverlayService`, tearing down the entire overlay.

### 1.4 Idle/Setup Body
- Shown when `DraftPhase` is `IDLE` or `SETUP`.
- Status banner: "Waiting for draft to begin…"
- "WHO PICKS FIRST?" toggle with two buttons: ALLY (🔵, teal accent) and ENEMY (🔴, red accent). Selection is tracked in local Compose `remember` state and passed to `onStartDraft(ourTeamFirst: Boolean)`.
- START DRAFT button: initiates a draft session via `DraftSessionManager.initSession()`, setting rank, ban structure, and pick sequence.
- Slot overview dots: visual indicators of empty ban and pick slots for both teams (filled dot = hero locked, empty dot = open slot). Rendered as 8 dp circles.

### 1.5 Active Draft Body (unified ban + pick view)
- Displayed for `BAN_ROUND_1`, `BAN_ROUND_2`, `PICK`, and `TRADING` phases.
- Both the BAN section and PICK section are shown simultaneously in a single scrollable column — the user sees the full picture without navigating between tabs.

**BAN section:**
- Section header: "⛔ BAN" in red.
- Animated "YOUR TURN TO BAN" badge that fades in/out via `AnimatedVisibility` with `slideInVertically` and `fadeIn`. Uses a 150 ms enter tween and 100 ms exit tween.
- "Enemy is banning…" text when it is not our turn.
- Top 3 ban suggestions rendered as `QuickHeroChip` tappable cards (44 dp portrait + hero name + badge label).

**PICK section:**
- Section header: "✅ PICK" in green.
- Turn indicator row showing "YOUR TURN" (green background) or "ENEMY TURN" (red background) with the current pick number (e.g., "Pick 3/10") from the `PickSequenceEngine`.
- Trading phase message: "Trading phase — tap heroes to swap" in amber.
- Top 3 pick recommendations rendered as `QuickHeroChip` cards.
- First enemy counter-pick warning shown in amber below the recommendations (truncated to 1 line with ellipsis).

**Slot overview:**
- Two rows of slot dots (Bans, Picks) with "E:" and "Y:" prefixes. Enemy slots in red, our slots in teal.

### 1.6 Complete Body
- Shown when `DraftPhase` is `COMPLETE`.
- "Draft complete ✅" in green.
- "Close overlay" button that calls `stopSelf()`.

### 1.7 Window Management Details
- Bubble uses `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS | FLAG_NOT_TOUCH_MODAL`.
- Widget uses `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_NO_LIMITS`.
- `FLAG_LAYOUT_NO_LIMITS` on both modes allows dragging to screen edges without the WindowManager clipping the position mid-gesture.
- `FLAG_NOT_TOUCH_MODAL` ensures touches outside the overlay window pass through to MLBB below.
- Window gravity is `Gravity.TOP or Gravity.START` with explicit x/y coordinates, enabling precise positioning anywhere on screen.

---

## 2. Autonomous Screen Capture Pipeline

### 2.1 MediaProjection Setup
- `ScreenCaptureManager` acquires a `MediaProjection` via `MediaProjectionManager.getMediaProjection()` using the result code and intent from the user's consent dialog.
- Creates an `ImageReader` in `RGBA_8888` format at full device resolution with a buffer depth of 2 (latest-frame semantics, no queue buildup).
- Creates a `VirtualDisplay` with `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR` backed by the `ImageReader`'s surface.
- `captureFrame()` calls `acquireLatestImage()` on the IO dispatcher, copies pixel data from the image plane buffer (accounting for row stride padding), and returns a clean `screenWidth × screenHeight` bitmap with the padded region cropped off.
- `stopCapture()` releases the VirtualDisplay, closes the ImageReader, and stops the MediaProjection, setting `isCapturing` to false.

### 2.2 Foreground Service Type Lifecycle
- `OverlayService.startFg()` starts with only `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+ (no MediaProjection token required at this point).
- On API 29–33, starts with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` (pre-API-34 behavior allowed this before consent).
- `upgradeFgToProjection()` is called from `onStartCommand` only after valid projection data is confirmed, adding `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` to the running foreground service via a second `startForeground()` call — satisfying Android 14's requirement that the token is present at declaration time.

### 2.3 Phase Detection (`PhaseDetector`)
- Samples the bottom-centre 33%–67% width × 80%–92% height band of each frame (the region where MLBB renders its action buttons).
- Every 4th pixel is sampled (step = 4) for performance.
- Red pixel scoring: R > 160, G < 80, B < 80 → BAN button present.
- Blue pixel scoring: B > 140, R < 100 → PICK button present.
- Thresholds: ratio > 0.06f triggers phase detection with confidence equal to the ratio.
- Returns a `DetectedPhase` enum (SETUP, BAN, PICK, TRADING, LOADING, UNKNOWN) with a float confidence score.
- `toDraftPhase()` maps the detected phase to the internal `DraftPhase` enum, preserving `BAN_ROUND_2` context when already in that phase.

### 2.4 Portrait Matching (`PortraitMatcher`)
- `PerceptualHash.compute()` implements difference hash (dHash): scales the bitmap to 9×8 pixels, computes 64 left-vs-right luminance comparisons, and packs results into a `Long`.
- `PerceptualHash.similarity()` = 1 − (Hamming distance / 64). Same image = similarity of 1.0; threshold for "same" is distance ≤ 10 (similarity ≥ 0.84).
- `PortraitMatcher` pre-loads hashes for all heroes via `preloadHashes()` using Coil's `ImageLoader` to fetch portrait bitmaps from URLs, caching up to 200 hero hashes in an `LruCache<Int, Long>`.
- `match()` computes the dHash of a cropped slot bitmap and finds the best-matching hero hash by iterating all cached hashes.
- Result confidence tiers: ≥ 0.80 → confirmed match (`requiresConfirmation = false`); 0.40–0.79 → possible match (`requiresConfirmation = true`); < 0.40 → no match.

### 2.5 Slot Regions (`SlotRegions`)
- All slot coordinates are expressed as fractions of screen dimensions (resolution-independent).
- Enemy ban slots: 5 slots across top 4%–14% of screen height, spanning 10%–93% of width.
- Our ban slots: 5 slots in the 15%–24% height band.
- Enemy pick slots: 5 stacked slots on the left panel (2%–20% width, 25%–83% height).
- Our pick slots: 5 stacked slots on the right panel (80%–98% width, 25%–83% height).
- Rank emblem region: top-left (2%–18% width, 2%–10% height).
- First-pick indicator: top-centre (40%–60% width, 1%–6% height).
- Action button: bottom-centre (33%–67% width, 80%–92% height).
- `cropSlot()` safely clamps crop dimensions to stay within frame bounds.

### 2.6 Frame Processor (`FrameProcessor`)
- Throttles processing to 500 ms between frames during active draft phases, 2000 ms during IDLE/COMPLETE.
- Tracks which slots were already filled in `Set<Int>` collections (one per slot type) and only emits newly-filled slot indices on each frame — preventing duplicate hero registrations.
- `isSlotFilled()` detects a filled slot by sampling mean luminance of the cropped slot: empty draft slots have a dark background (mean < 40), filled slots have a hero portrait (mean > 40).
- `isBanButtonVisible()` re-runs `PhaseDetector.detect()` on the cropped action button region with a confidence threshold of 0.05 to determine if it is currently the ban turn.
- Ban scanning runs only during `BAN_ROUND_1` and `BAN_ROUND_2` phases; pick scanning runs only during `PICK` phase.
- Emits `FrameAnalysis` objects via a `SharedFlow` with `extraBufferCapacity = 4` to absorb backpressure.
- `resetSlotTracking()` clears all filled-slot sets at the start of a new draft session.

### 2.7 Capture Loop in OverlayService
- A `captureJob` launches a coroutine that calls `screenCaptureManager.captureFrame()` in a loop with a 500 ms delay.
- Each frame is passed to `FrameProcessor.processFrame()` along with the current `DraftPhase`.
- `FrameAnalysis` results drive phase transitions (via `draftSessionManager`), ban recording, and pick recording.
- Portrait matching is invoked on newly-filled slots to identify which hero was placed.
- Rank inference: if the observed ban count exceeds the current rank's expected total, `draftSessionManager.upgradeRankFromObservedBans()` promotes the rank to the minimum that explains the count.

---

## 3. Advisory Engine

### 3.1 Scoring Model (`DraftScorer`)
- **Meta score** (weight: 40% default): composite of win rate (normalized over 0.48–0.56 range, 35% contribution), ban rate (normalized over 0–40%, 30% contribution), pick rate (normalized over 0–30%, 15% contribution), and tier (normalized over Tier.S_PLUS to Tier.UNKNOWN using `TIER_MAX_ORDER = 5`, 20% contribution). Tier.UNKNOWN correctly scores 0.0 (not negative, which was a fixed bug).
- **Synergy score** (weight: 30% default): fraction of ally heroes that have the candidate in their synergy list, plus the fraction of allied heroes in the candidate's synergy list, averaged and normalized to [0, 1].
- **Counter score** (weight: 30% default): fraction of enemy heroes that the candidate directly counters.
- **Role/lane score** (weight: 15%, not user-configurable): 1.0 if candidate fills a currently empty lane, 0.6 if a flex lane matches a missing lane, 0.0 otherwise.
- **First-pick flexibility bonus** (10% additive): applied only on the first pick turn; measures how counterable the hero is (1 − counteredBy.size / 10).
- **Last-pick safety bonus** (10% additive): applied only on the last pick turn; measures how many enemy heroes the candidate counters.
- Total score is clamped to [0, 1] by `coerceIn`.
- `ScoreWeights` enforces summation to 1.0 ± 0.01 at construction time via `require()`.

### 3.2 Score Weight Presets (`ScoreWeights`)
- `DEFAULT`: meta=0.40, synergy=0.30, counter=0.30
- `META_HEAVY`: meta=0.60, synergy=0.20, counter=0.20
- `COUNTER_HEAVY`: meta=0.30, synergy=0.20, counter=0.50
- `SYNERGY_HEAVY`: meta=0.30, synergy=0.50, counter=0.20
- `normalized(meta, synergy, counter)` auto-scales any three positive floats to sum to 1.0.

### 3.3 Badge Labels
- Each `HeroScore` carries a `badgeLabel` that identifies the dominant scoring dimension:
  - `"◆ META"` — meta score is the highest contributor
  - `"◈ SYNERGY"` — synergy score is highest
  - `"◉ COUNTER"` — counter score is highest
  - `"◎ BALANCED"` — all three are roughly equal

### 3.4 Reason String Generation
Priority order for the human-readable reason:
1. Synergizes with a specific ally AND counters a specific enemy → *"Synergizes with [ally] + counters [enemy]"*
2. Synergizes with an ally (no notable counter) → *"Strong combo with [ally]"*
3. Counters enemies (no synergy match) → *"Direct counter to [enemy1], [enemy2]"*
4. Fills a missing lane → *"Fills missing [lane] role"*
5. Hero is flagged as OP → *"Top meta pick this patch"*
6. Fallback → *"Solid meta choice — 57% win rate"*

### 3.5 Ban Recommender (`BanRecommender`)
- Computes a ban priority score per hero: `metaScore = max(0, winRate − 0.50) × 2 + banRate × 1.5`.
- Additive bonuses: isToxicMechanic (+0.30), isOP (+0.25), lane matches preferredLanes (+0.10).
- Score is clamped to [0, 1].
- Returns top 3 suggestions sorted by score descending, each with a `badgeLabel`:
  - `"Toxic"` if isToxicMechanic
  - `"OP Meta"` if isOP
  - `"High Ban"` if banRate > 0.25
  - `"Counter"` otherwise
- Ban reason is context-specific: toxic/OP/ban-rate/community-consensus messaging.

### 3.6 Composition Analyzer (`CompositionAnalyzer`)
- **Damage type balance**: magic roles (Mage, Support) vs. remaining (physical). Computes `physicalPct` and `magicPct`.
- **CC level**: counts heroes by name (hardcoded known CC heroes: Tigreal, Atlas, Khufra, Franco, Johnson, Chou, Jawhead, Kaja, Aurora, Selena) or role (Tank, Support). NONE / LOW / MEDIUM / HIGH enum.
- **Mobility level**: counts Assassin-role heroes. LOW / MEDIUM / HIGH enum.
- **Sustain level**: counts Support and Fighter-role heroes. LOW / MEDIUM / HIGH enum.
- **Composition warnings** (up to 5):
  - Full physical (≥ 80%): *"⚠️ Full physical — one Dominance Ice counters team"*
  - Full magic (≥ 80%): *"⚠️ Full magic — build Oracle to counter"*
  - No CC: *"⚠️ No CC — dive compositions will dominate"*
  - Low sustain: *"⚠️ Low sustain — avoid extended fights"*
  - High enemy mobility: *"⚠️ High enemy mobility — bring crowd control"*
- **Lane assignment** (`getLanesFilled`): assigns heroes to lanes greedily (primary lane first, then flex lanes) without duplication.
- **Missing lane detection** (`getMissingLanes`): returns lanes with no assigned hero — used as input to role scoring.
- **Strength generation**: CC chain, sustain, mobility, mixed damage.
- **Weakness generation**: lack of CC, squishy lineup, full physical/magic.
- **Counter-pick warnings** (`getCounterPickWarnings`): for each allied hero, checks if any enemy hero has the ally's ID in its `counters` list. Returns strings like *"⚠️ Layla is countered by Saber (enemy pick)"*.

### 3.7 Build Advisor (`BuildAdvisor`)
- **Battle spell recommendation**: context-aware spell pairs with explanations.
  - Jungle → Retribution (primary) / Flicker (alt)
  - Roam → Flicker / Vengeance
  - Carry vs. high-mobility enemy → Sprint / Flicker
  - Carry vs. physical-heavy enemy → Inspire / Flicker
  - Fighter vs. high-CC enemy → Purify / Vengeance
  - Default → hero's `recommendedSpells[0]` and `[1]`
- **Item adjustment**: modifies the hero's core item list based on enemy composition:
  - ≥ 70% physical enemy → prepend Antique Cuirass for Tank/Fighter (if no armour item present)
  - ≥ 70% magic enemy → append Athena's Shield for Tank/Fighter (if no magic resist present)
  - List capped at 3 items after adjustment.
- **Item reasons**: plain-language explanations of adjustments.
- **Macro tips**: role- and composition-specific gameplay advice (jungle invade, farm priority, rotation patterns, teamfight positioning, vision placement, sustain-burst interaction).

---

## 4. Draft Session Engine

### 4.1 Draft Session State (`DraftSession`)
- Immutable data class holding the complete draft snapshot: rank, ban structure, phase, first-picker side, pick sequence, ban slots (R1 + R2 for both teams), pick slots (both teams), undo stack, and recommendation follow metrics.
- Derived properties: `allBannedHeroes`, `allPickedHeroes`, `unavailableIds` (union of bans and picks), `ourPickedHeroes`, `enemyPickedHeroes`, `currentTurn`.
- Missed ban tracking: a hero with `id = DraftSession.MISSED_BAN_ID (−1)` represents a timed-out ban slot, kept separate from genuinely null (unfilled) slots.

### 4.2 Draft Session Manager (`DraftSessionManager`)
- Singleton (Hilt-injected) `StateFlow<DraftSession>` — reactive single source of truth for all draft state.
- Phase transition methods: `initSession()`, `startBanPhase()`, `startBanRound2()`, `startPickPhase()`, `startTradingPhase()`, `completeDraft()`, `reset()`.
- Ban recording: `recordEnemyBan(hero, round, slot)` and `recordOurBan(hero, round, slot)`, both supporting round 1 and round 2.
- Pick recording: `recordEnemyPick(hero, slot)` and `recordOurPick(hero, slot, followedRecommendation)`. Recording a pick increments `currentPickIndex`.
- Undo: `undo()` pops the last action from the `undoStack` and reverses its effect (null-back for bans, null-back for picks with decremented pick index, remove for hero swaps).
- Trading: `swapOurHeroes(fromSlot, toSlot)` swaps two entries in `ourPicks` with a `HeroSwap` action pushed to the undo stack.
- Rank inference: `upgradeRankFromObservedBans(banCount)` promotes the session rank if the observed ban count implies a higher tier than the current setting.

### 4.3 Rank Rule Engine (`RankRuleEngine`)
| Rank | Total Bans | R1 Per Team | R2 Per Team | Has Round 2 |
|---|---|---|---|---|
| EPIC | 6 | 3 | 0 | No |
| LEGEND | 8 | 3 | 1 | Yes |
| MYTHIC / MYTHICAL_HONOR / MYTHICAL_GLORY / IMMORTAL | 10 | 3 | 2 | Yes |
- `getBannerSlots(rank)` returns the player slot indices designated as banners at each rank.
- `inferFromBanCount(n)` returns the minimum rank that explains n observed bans (≥10 → MYTHIC, ≥8 → LEGEND, else EPIC).
- `fromString(raw)` parses rank strings case-insensitively.

### 4.4 Pick Sequence Engine (`PickSequenceEngine`)
- Encodes the MLBB 1-2-2-2-2-1 pick pattern: first team picks 1, second picks 2, first picks 2, second picks 2, first picks 2, second picks 1 (10 turns total).
- `buildSequence(firstPicker)` returns a `List<PickTurn>` of 10 entries, each with: 0-based index, TeamSide, isDoublePick (concurrent pair), isFirstPick (index 0), isLastPick (index 9), 1-based pickNumber.
- Both first-pick-first and second-pick-first starting configurations are fully supported.

### 4.5 Hero Domain Model (`Hero`)
Fields: id, name, role, secondaryRole, lane (Lane enum), tier (Tier enum), patchTrend, winRate, pickRate, banRate, imageUrl, counters (List<Int>), counteredBy (List<Int>), synergies (List<Int>), recommendedSpells (List<String>), coreItems (List<CoreItem>), flexLanes (List<Lane>), isToxicMechanic (Boolean), isOP (Boolean).

Enums:
- `Lane`: EXP, GOLD, JUNGLE, MID, ROAM — each with a display name and short label.
- `Tier`: S+, S, A+, A, B, UNKNOWN — each with a display string and order (used in scoring normalization). `fromString()` handles all case/trim variants.

---

## 5. In-App Screens

### 5.1 Home Screen
- Top app bar with MLBB gold branding and Settings shortcut icon.
- Meta banner card linking to MetaBoardScreen, with a fire icon and "Current Meta / Tap to see full tier list".
- Quick action grid: Hero Explorer, Meta Board, Draft History, Settings — responsive layout (2×2 on phones < 600 dp, 1×4 on tablets).
- Top Meta Heroes horizontal scroll row: each card shows hero portrait (52 dp), name, and formatted win rate (e.g., "57% win").
- Start Draft extended FAB (MLBB gold, bottom-end, SportsMartialArts icon) — triggers overlay launch flow.
- Loading spinner (28 dp, MLBB gold) shown while heroes are loading.

### 5.2 Hero Explorer (HeroListScreen)
- Full hero grid rendered by `HeroGrid` composable.
- Filtered heroes displayed if a filter is active, otherwise full list.
- Empty state with Person icon and instructions to refresh.
- Loading state with centered `CircularProgressIndicator`.
- Tapping a hero navigates to `HeroDetailScreen`.

### 5.3 Hero Detail Screen
- 160 dp hero splash art banner (Coil `AsyncImage`, `ContentScale.Crop`) with a vertical gradient overlay fading to dark at the bottom.
- Hero name (22 sp, bold), role / secondary role, tier label with tier-accurate color (S+ = amber, S = gold, A+ = purple, A = blue, B = grey).
- Stats row: Win Rate, Pick Rate, Ban Rate, Patch Trend — all formatted as percentages with color coding (green/red for positive/negative trend).
- M3 `TabRow` with three tabs: Overview, Counters, Synergies.
- **Overview tab**: lane, role/secondary role, tier, OP badge, Toxic Mechanic badge, flex lanes, recommended spells as chips, core build order as numbered list with priority indicators.
- **Counters tab**: two sections — "Heroes that BEAT [hero]" (counteredBy list) and "[hero] BEATS" (counters list), each showing up to 5 hero portraits with names.
- **Synergies tab**: "BEST PARTNERS" (synergies list) and "WORKS AGAINST" (counters list) with 48 dp portraits.
- Optional action buttons "Add to Enemy" and "Add to Yours" at the bottom (used when navigating from a draft context).

### 5.4 Meta Board Screen
- Three tabs: Tier List, Trending, By Role.
- **Tier List**: heroes grouped by tier (S+, S, A+, A, B, UNKNOWN) with colored tier labels (32 dp boxes) and horizontal hero portrait rows. Tapping navigates to Hero Detail.
- **Trending**: top 20 heroes sorted by `patchTrend` descending, each card showing hero portrait (44 dp), name, role, tier, and trend percentage with a TrendingUp icon (green = rising, red = falling).
- **By Role**: six role sections (Tank, Fighter, Mage, Marksman, Support, Assassin), each with a role-color header and a horizontal scroll of up to 8 heroes sorted by win rate. Role colors are unique per role.

### 5.5 Draft History Screen
- `LazyColumn` of completed draft sessions loaded from Room via `GetDraftHistoryUseCase`.
- Each card shows: draft number, formatted timestamp (e.g., "Jun 4, 2:30 PM"), composite draft score out of 100 with color coding (≥ 80 = green, ≥ 60 = amber, < 60 = red).
- Three `SuggestionChip` badges per card: Meta%, Counter%, Synergy% breakdown scores.
- Recommendation follow rate text: "X/Y recs followed".
- Empty state with ListAlt icon and guidance text.

### 5.6 Draft Screen (in-app view)
- Companion view of the active overlay session (not a replacement for the overlay).
- Shows YOUR TEAM portraits (up to 5, 48 dp with name), ENEMY TEAM portraits, and TOP SUGGESTIONS list (up to 5 `SuggestionCard` items).
- Each suggestion card: 40 dp portrait, hero name, reason (1 line, ellipsis), badge label, score percentage.
- Empty state with Groups icon when no active draft session exists.

### 5.7 Settings Screen
- **Overlay section**:
  - Opacity slider (30%–100%, with percentage label updated live).
  - Toggle: "Auto-show when MLBB detected" (voice-alerts-aware).
  - Toggle: "Voice alerts".
- **Scoring Weights section**:
  - Three sliders: Meta strength, Counter value, Synergy value (each 0–100%).
  - Live `AnimatedVisibility` warning banner if weights do not sum to 100%, showing the current sum and asking the user to adjust.
  - "Reset to defaults" button with a confirmation `AlertDialog` (restores 40/30/30%).
- **Draft Preferences section**: default rank display.
- **Data section**: Auto-sync toggle, last-synced label (formatted date/time or "Never"), "Sync now" button.
- **Permissions section**:
  - Overlay permission row: CheckCircle (green) or Cancel (red) icon + "Granted" or "Tap to enable" text. Tapping opens `ACTION_MANAGE_OVERLAY_PERMISSION`.
  - Accessibility permission row: same pattern, opens `ACTION_ACCESSIBILITY_SETTINGS`.

### 5.8 Permission Wizard Screen
- Six-step onboarding flow with step indicators.
- Steps in order:
  1. **Draw Over Other Apps** — opens `ACTION_MANAGE_OVERLAY_PERMISSION` deep link.
  2. **Open New Windows in Background** — opens OEM background running settings via `openBackgroundRunningSettings()`.
  3. **Disable Battery Optimisation** — uses `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with package URI; falls back to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` if the direct intent throws.
  4. **App Auto-Start** — opens OEM-specific auto-start settings via `openAutoStartSettings()`, which attempts known intent actions for Xiaomi, OPPO, Vivo, Huawei, and similar devices.
  5. **Restricted Settings** — opens `ACTION_APPLICATION_DETAILS_SETTINGS` with package URI for sideloaded app unblocking on Android 12+. Has "Installed from Play Store" skip option.
  6. **Accessibility Service** — opens `ACTION_ACCESSIBILITY_SETTINGS`. No skip option — this is required for auto-detection.
- Each step card shows: icon (emoji), title, description, "Why this matters" rationale, and action button. Optional skip button for non-critical steps.
- Step navigation: "Done / Grant" → next step; "Skip" → next step; final step → `onComplete()` callback.
- `onComplete()` writes `wizard_done = true` to DataStore and navigates to Home.

### 5.9 Log Screen
- In-app crash and error log viewer backed by `CrashLogStore`.
- Entry types: CRASH, ERROR, WARN, INFO, DEBUG — each with distinct border color and badge background.
- Each entry card: level badge, tag name, formatted timestamp (monospace), message text (up to 2 lines collapsed, full when expanded), expandable stack trace section with monospace rendering.
- Stack trace expand/collapse toggle with `ExpandMore`/`ExpandLess` icons.
- Copy button per entry: copies formatted log text to system clipboard.
- Toolbar actions: Refresh (reloads from file), Share (fires `ACTION_SEND` with full log as plain text, subject "MLBB Assistant Crash Log"), Clear (confirmation dialog before `CrashLogStore.clear()`).
- Entry count shown in toolbar subtitle.
- Empty state: "✅ No crashes or errors logged".

---

## 6. Data Layer

### 6.1 Remote Meta API
- Retrofit interface `MetaApi` with a single endpoint: `GET /v1/meta/snapshot` returning `MetaSnapshotDto`.
- `HeroRepositoryImpl` maps DTOs to domain `Hero` objects and persists them in Room.
- `SyncHeroesUseCase` triggers a network fetch and DB upsert.

### 6.2 Local Database (Room)
- `AppDatabase` with two entities: `HeroEntity` and `DraftSessionEntity`.
- `HeroEntity`: mirrors the `Hero` domain model with string-serialized enums (Lane, Tier) and JSON-serialized complex types (List<Int>, List<CoreItem>, etc.) via `Converters`.
- `HeroEntity.toDomain()`: maps string lane/tier back to enums using `Lane.entries.firstOrNull` and `Tier.fromString`.
- `HeroDao`: standard CRUD + flow-based `getAll()` for reactive UI updates.
- `DraftSessionDao`: insert and query operations for completed sessions.
- `DraftSessionEntity`: stores rank, timestamp, picks/bans as serialized lists, meta/counter/synergy scores, follow rate numerator/denominator, and a composite `draftScore`.

### 6.3 DataStore (Preferences)
- Single `AppDataStore.kt` file defines `val Context.appDataStore` as the one authoritative `preferencesDataStore` delegate for the entire app (file name: `"mlbb_preferences"`).
- `WizardPreference` reads/writes `wizard_done: Boolean` via `context.appDataStore`.
- `PreferencesDataStore` stores overlay settings (opacity, auto-show, voice alerts), scoring weights, default rank, and sync state.
- Hilt `AppModule` provides `DataStore<Preferences>` as a `@Singleton` using the same `appDataStore` extension.

### 6.4 Crash Log Store (`CrashLogStore`)
- Append-only log file (`mlbb_crash_log.txt`) in the app's private files directory.
- File format: `TIMESTAMP|LEVEL|TAG|MESSAGE` per line, with tab-indented stack trace continuation lines.
- Pipe character in messages and stack traces is escaped to `¦`; newlines to `↵`.
- Rotation: file is halved when it exceeds 512 KB (last 50% of lines kept).
- `readAll()` returns entries sorted newest-first, capped at 500 entries.
- `appendSync()` is available for crash handlers that cannot use coroutines.
- `AppLogTree` is a Timber `Tree` subclass that routes `Log.e` and `Log.wtf` events to `CrashLogStore`.

---

## 7. Architecture and Infrastructure

### 7.1 Dependency Injection (Hilt)
- `@HiltAndroidApp` on `MLBBApplication`.
- `AppModule` provides: `DataStore<Preferences>`, `DraftSessionManager`, `VoiceAlertService`.
- `DatabaseModule` provides: `AppDatabase` (singleton Room instance), `HeroDao`, `DraftSessionDao`.
- `NetworkModule` provides: `Retrofit` instance, `MetaApi`.
- `RepositoryModule` binds `HeroRepositoryImpl → HeroRepository`, `DraftSessionRepositoryImpl → DraftSessionRepository`.
- `OverlayModule` provides overlay-related dependencies.
- `@AndroidEntryPoint` on `OverlayService` and `MainActivity` for field injection.

### 7.2 Navigation
- Single `MainActivity` hosts a `NavHostController` and `AppNavGraph`.
- Routes: Wizard, Home, HeroList, HeroDetail (with Int argument `heroId`), MetaBoard, History, Settings, CrashLog.
- Start destination: Wizard if `WizardPreference.observe(context)` emits `false`; Home otherwise.
- Wizard route pops itself from the back stack on completion (inclusive `popUpTo`) so Back does not return to it.
- HeroDetail receives `heroId` as a typed `NavType.IntType` argument and resolves the `Hero` from the `HeroListViewModel` state.

### 7.3 Overlay Service Lifecycle
- `OverlayService` implements `LifecycleOwner` and `SavedStateRegistryOwner` (required for `ComposeView` to function inside a `Service`).
- `SavedStateRegistryController.performAttach()` and `performRestore(null)` are called before any Compose content is set.
- `LifecycleRegistry` advances from CREATED (onCreate) to RESUMED (onStartCommand) to DESTROYED (onDestroy).
- `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)` — `SupervisorJob` prevents a failing child coroutine from cancelling sibling coroutines.
- All coroutines are cancelled in `onDestroy()` via `serviceScope.cancel()`.

### 7.4 Accessibility Service
- Separate `AccessibilityService` implementation detects when the MLBB package is in the foreground.
- Triggers `OverlayService.start()` automatically when MLBB launches (if auto-show is enabled in settings).

### 7.5 Voice Alert Service (`VoiceAlertService`)
- `TextToSpeech`-backed service provided by Hilt as a singleton.
- Called from `OverlayService` to announce significant events (phase transitions, turn indicators) when voice alerts are enabled in settings.

### 7.6 Connectivity Banner
- `ConnectivityBanner` composable observes network connectivity via `ConnectivityManager`.
- Displays a non-dismissable amber banner when the device is offline.
- Used in the home screen and any screen that depends on remote data.

### 7.7 Image Loading (Coil 3)
- `AsyncImage` for hero splash arts in HeroDetailScreen and all portrait renderers.
- `HeroPortrait` composable: loads portrait by URL with Coil, supports configurable size, optional tier badge overlay, optional name label below, and an optional `onClick` handler.
- `ImageLoader` singleton is passed into `PortraitMatcher` for hash pre-loading.

### 7.8 Theming
- Full Material 3 dark theme (`MLBBAssistantTheme`).
- Custom color tokens: `MLBBGold`, `MLBBBlue`, `MLBBTeal`, `MLBBRed`, `SurfaceDark`, `SurfaceMid`, `SurfaceCard`, `SurfaceElevated`, `TextPrimary`, `TextSecondary`, `TextDisabled`, `ErrorRed`, `SuccessGreen`, `WarningAmber`, `InfoBlue`, `OverlayBackground`.
- Tier colors: `TierSPlus`, `TierS`, `TierAPlus`, `TierA`, `TierB`.
- Role colors: `RoleColorTank`, `RoleColorFighter`, `RoleColorMage`, `RoleColorMarksman`, `RoleColorSupport`, `RoleColorAssassin`.

### 7.9 Shared UI Components
- `BackButton`: 48 dp touch-target back arrow with content description, correct minimum touch target for accessibility.
- `HeroGrid`: `LazyVerticalGrid` of `HeroPortrait` cells with disabled-ID support (greyed out for already banned/picked heroes).
- `HeroPortrait`: configurable size, optional tier badge, optional name, optional click.
- `LoadingSpinner`: centered `CircularProgressIndicator` in MLBB gold.
- `MLBBButton`: styled primary button with MLBB gold accent.
- `MLBBTextField`: styled text field with MLBB theme.
- `RoleDashboard`: displays all 6 roles with hero counts.
- `ConnectivityBanner`: offline warning strip.

### 7.10 Logging
- Timber is the logging facade throughout. `Log.d/i/w/e` are never called directly in service/domain code.
- `AppLogTree` routes WARN and above to `CrashLogStore` in addition to logcat.
- Server-side code uses `req.log` (not `console.log`) per workspace convention.

### 7.11 Boot Receiver
- `RECEIVE_BOOT_COMPLETED` permission declared in manifest.
- `BootReceiver` restarts the overlay service if it was running before device reboot (when auto-show is enabled).

---

## 8. Permissions and System Integration

| Permission | Purpose |
|---|---|
| `INTERNET` | Remote meta data sync |
| `ACCESS_NETWORK_STATE` | Connectivity banner |
| `SYSTEM_ALERT_WINDOW` | WindowManager overlay |
| `FOREGROUND_SERVICE` | Overlay foreground service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | FGS type declaration (API 34+) |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | FGS type declaration for screen capture |
| `POST_NOTIFICATIONS` | Foreground service notification (API 33+) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Direct battery opt-out dialog |
| `RECEIVE_BOOT_COMPLETED` | Overlay restart after reboot |

- Manifest declares `OverlayService` with `foregroundServiceType="specialUse|mediaProjection"`.
- `OverlayPermissionActivity` handles the `MediaProjectionManager.createScreenCaptureIntent()` consent flow and forwards the result code and data to `OverlayService.startWithProjection()`.

---

## 9. Small but Noteworthy Details

- `@Stable` and `@Immutable` annotations on `Hero`, `HeroScore`, `BanSuggestion`, `BuildAdvice`, `CompositionProfile` — compiler hints that allow Compose to skip recomposition when object references are unchanged.
- `collectAsStateWithLifecycle()` used throughout (not `collectAsState()`) — cancels flow collection when the UI is backgrounded, preventing unnecessary computation.
- All `windowManager.updateViewLayout()` and `windowManager.removeView()` calls are wrapped in `runCatching` to handle `IllegalArgumentException` if the view is no longer attached (e.g., called after `onDestroy`).
- `MotionEvent.obtain(event)` used to create the synthetic `ACTION_CANCEL` event dispatched to Compose during drag interception — the obtained event must be recycled immediately after `v.onTouchEvent(cancel)` to avoid MotionEvent pool exhaustion.
- `DraftHistoryScreen` uses `remember(session.timestamp)` for date formatting to avoid re-running `DateTimeFormatter.format()` on every recomposition.
- `MetaBoardScreen` uses `remember(heroes, role)` for per-role hero filtering to avoid re-running the filter on every recomposition.
- `Tier.fromString()` handles `"D"` and other unexpected tier values gracefully via the `UNKNOWN` catch-all — no crash on unexpected server data.
- `ScoreWeights.normalized()` handles the zero-sum edge case (all weights zero) by returning `DEFAULT` rather than dividing by zero.
- `FrameProcessor` uses `System.currentTimeMillis()` throttle rather than a fixed `delay()` to account for variable frame processing time — the throttle measures wall time between emissions, not coroutine sleep time.
