# Vision

## The End State

A player opens MLBB and enters a ranked queue. The moment the draft screen appears, the MLBB Draft Assistant bubble pulses gold at the corner of their screen. They tap it once. The MiniWidget expands, already tracking the phase. As the enemy team locks their first ban, the overlay registers it autonomously — no tapping required. Three ranked ban recommendations appear immediately. When it is the player's team's turn to pick, the engine has already computed every available hero's score against the partial enemy composition, weighted by the current patch meta, and surfaced the top three choices with a plain-language reason for each. The player picks. The overlay collapses. The game begins.

That is the vision: **complete, zero-friction draft intelligence delivered at the exact moment it is actionable, with no break in the player's mental focus on the game.**

---

## The Five Pillars of the Long-Term Vision

### Pillar 1 — Autonomous Awareness
The overlay should eventually know everything a spectator sitting next to the player would know, without the player reporting anything.

Today, the capture pipeline detects draft phase via color sampling of the action button region and matches hero portraits via 64-bit perceptual hash with an LRU-cached hash table. This is a correct foundation.

The long-term capability extension is:
- **Rank detection from the emblem region** (`SlotRegions.rankEmblem` is already defined but not yet consumed). Automatically inferring rank means the correct ban structure is applied without the player selecting it — no manual Epic/Mythic toggle.
- **First-pick side auto-detection** from the top-center indicator region (`SlotRegions.firstPickIndicator` defined). Eliminates the "WHO PICKS FIRST?" toggle in the idle widget.
- **Higher-confidence portrait matching** using a hybrid of perceptual hash and optional on-device ML inference (TensorFlow Lite), reducing the `requiresConfirmation` flag from triggering at 80% similarity and improving accuracy on portrait art that shares color palettes (e.g., similar-looking mage splash arts).
- **Phase transition detection precision** — expanding beyond the current red/blue color sampling of the action button to include OCR of the phase countdown timer region for disambiguation between BAN_ROUND_1 and BAN_ROUND_2.

### Pillar 2 — Deeper Intelligence
The scoring engine is currently a well-structured multi-factor linear model. The vision is to graduate it through successive layers of depth:

**Near term:**
- **Composition archetype recognition** — beyond raw damage type ratios, identify named draft archetypes (Dive, Poke, Turtle, Split-push, Wombo-combo) from the combination of picked roles and hero identities, and surface archetype-specific win conditions and counters.
- **Draft-phase-aware scoring adjustments** — the current first-pick flexibility bonus and last-pick safety bonus are binary. A continuous function over pick index (adjusting synergy weight upward as more ally picks are known and counter weight upward as more enemy picks are known) would produce more contextually accurate recommendations throughout the draft.
- **Enemy composition intent inference** — if the enemy has picked a Tank + Assassin + high-CC Support, the engine should recognize this as a Dive composition in progress and up-weight CC and burst-damage picks in the recommendation list, before the enemy's fourth and fifth picks make it obvious.

**Medium term:**
- **Personal hero pool integration** — a player can mark heroes they actually own and have a minimum proficiency with. The engine applies a pool-weight multiplier so it never recommends a theoretically optimal hero the player cannot play effectively.
- **Patch delta weighting** — `patchTrend` exists in the Hero model but currently influences ranking only implicitly through meta score. An explicit recency multiplier that up-weights heroes whose win rates are rising this patch (rather than just high at baseline) would surface sleeper picks before they become obvious.
- **Ban value vs. ban urgency separation** — today `BanRecommender` returns a static ranked list. The vision is a dynamic ban advisor that distinguishes between "ban this regardless of what the enemy shows" (absolute priority: toxic mechanic, OP in all compositions) and "ban this because the enemy's first pick suggests they are building toward it" (reactive priority based on detected enemy intent).

**Long term:**
- **Role-based macro tip customization** — `BuildAdvisor` already generates macro tips per hero role and enemy composition profile. This evolves into a full in-draft coaching panel: rotation timing recommendations, objective priority based on team comp, vision placement suggestions, and teamfight engagement rules.
- **Win-condition statement generation** — a one-sentence synthesis of the team's complete draft: *"Your team wins long teamfights. Force fights after Khufra's ultimate is up. Avoid split-push games."*

### Pillar 3 — Historical Intelligence
The `DraftHistoryScreen` and `DraftSessionEntity` today record completed sessions with aggregate scores (meta, counter, synergy percentages) and recommendation follow rate. The vision is for this historical data to become a feedback loop:

- **Personal meta calibration** — the app tracks which recommended picks the player chose, which they overrode, and correlates this with session outcomes (once win/loss reporting is added). Over time, the scoring weights self-adjust toward what actually works for this player's specific playstyle and hero pool.
- **Draft pattern recognition** — identifying the player's tendencies (do they consistently over-ban safe picks and leave OP heroes open? do they under-value roam heroes?), and surfacing coaching observations.
- **Match timeline view** — replaying the draft decision tree for a completed session: what was recommended at each step, what was actually picked, and how the composition score evolved as each hero was added.

### Pillar 4 — Frictionless Deployment
The overlay works today through a six-step permission wizard. Every step is necessary given Android's current security model. The vision is to reduce the subjective friction of that process to near zero:

- **Deep link integration** — the permission wizard emits deep links to exact settings pages rather than top-level settings screens, where possible, reducing the number of taps the user must perform inside system UI.
- **Accessibility service auto-detection** — the service currently reports enabled/not-enabled. A richer detection checks whether the service is actually running and healthy (not just installed), and recovers or prompts gracefully if it dies unexpectedly.
- **Quick Re-setup** — if the app detects that the overlay permission was revoked (e.g., after an OS update), it re-enters the wizard at the specific revoked step rather than forcing full re-onboarding.
- **One-tap overlay relaunch** — a persistent notification action button that relaunches the overlay without returning to the app, for players who minimize the bubble and then need to restart it mid-game.

### Pillar 5 — Platform Resilience
The overlay runs as a foreground service on a platform (Android) that aggressively kills background processes, imposes evolving foreground service type restrictions, and varies significantly across OEM skins (Xiaomi MIUI, OPPO ColorOS, Vivo FuntouchOS, Huawei EMUI). The vision is a service that is effectively indestructible during a draft:

- **Accessibility service watchdog** — detect service death and attempt auto-restart using a coroutine that polls the AccessibilityManager, with a user notification if restart is not possible.
- **Overlay state persistence** — serialize the current `DraftSession` to DataStore on every state change, so if the OS kills and restarts the service mid-draft, it resumes from exactly where it was.
- **OEM-specific workarounds** — the auto-start wizard step already links to OEM-specific settings screens via `openAutoStartSettings()`. The long-term goal is a maintained table of OEM intent actions (Xiaomi, OPPO, Vivo, Huawei, Samsung, OnePlus), continuously verified against current firmware versions.
- **No-capture mode parity** — the manual mode fallback (tapping heroes yourself) should reach complete feature parity with the autonomous mode, including phase transition buttons, so players on locked-down corporate devices or devices without screen capture can use every feature.

---

## What "Done" Looks Like

The vision is complete when three things are true simultaneously:

1. **A new user who has never heard of the app** can install it, complete the wizard, and have the overlay providing useful ban recommendations in their first ranked draft — without reading any documentation, watching any tutorial, or asking anyone for help.

2. **An experienced user** at Mythical Glory rank who has completed one hundred drafts with the app has their scoring weights automatically tuned to their playstyle, sees the overlay recommend heroes they have actually proven to play well, and receives composition feedback specific to the archetypes they face at their rank.

3. **The overlay never fails silently.** If screen capture is unavailable, the overlay says so and offers manual mode. If the meta data is stale, the overlay says so and offers the last cached snapshot. If the accessibility service dies, the user is informed. The system is always honest about its own state.

---

## What This Project Will Never Be

- A tool that interacts with the game process, reads game memory, or intercepts network traffic. The project observes only what is visually on screen — the same information available to any spectator — via the standard Android MediaProjection API with explicit user consent.
- A subscription service or freemium gate. The advisory engine, the overlay, and the draft history are core features that must work completely offline from cached meta data. Remote sync is an enhancement, not a prerequisite.
- An opaque system. Every score, every recommendation, and every warning surfaces a reason the player can read and evaluate. If the player disagrees with the advice, that is a legitimate outcome. The tool is a co-pilot, not an autopilot.
