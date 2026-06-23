# Mission

## What This Project Does

MLBB Draft Assistant is an Android application that provides real-time, intelligent drafting guidance to Mobile Legends: Bang Bang players during the hero selection phase. It runs as a persistent floating overlay on top of the game itself, eliminating the need to alt-tab, consult spreadsheets, or rely on memory during the high-pressure seconds of a ranked draft.

---

## Why It Exists

The MLBB draft phase is the most decisive two minutes of any ranked match. A team that drafts a superior composition before the game begins wins more often than a team that out-plays but out-drafts poorly. Yet the information required to draft well — current win rates, ban rates, patch trends, hero counters, team synergies, composition balance — is spread across external tier lists, Discord communities, and meta write-ups that cannot be consulted mid-draft without losing focus.

This project closes that gap. It brings every piece of draft intelligence directly into the player's field of view, precisely when and where it is needed, with zero friction.

---

## Core Beliefs

### 1. Overlay-first, not app-first
The floating bubble and the MiniWidget are the primary product. The in-app screens (meta board, hero explorer, settings) exist to support the overlay, not the other way around. Every design decision is evaluated against the question: *does this help a player during the thirty seconds they have to make a pick or ban?*

### 2. Autonomous detection is the ideal; manual is the dependable fallback
The screen-capture pipeline — MediaProjection → ImageReader → PerceptualHash portrait matching → PhaseDetector — is designed to read the MLBB draft screen without the player touching anything. When a hero is locked, the overlay already knows. When the ban phase transitions to picks, the overlay already advanced.

However, screen capture requires explicit user consent and depends on a stable device/ROM environment. The system is therefore designed so that every piece of autonomous detection has a manual equivalent. A player who cannot or does not want to grant screen capture can tap each hero themselves. The advisory engine is identical in both paths.

### 3. Advice must be explainable, not just scored
Every hero recommendation surfaces a human-readable reason: *"Synergizes with Tigreal + counters Layla"*, *"Fills missing Roam role"*, *"Top meta pick this patch — 57% win rate"*. Numbers alone are insufficient. Players need to understand *why* a recommendation was made so they can override it with context the engine cannot know (their personal hero pool, team communication, opponent tendencies).

### 4. The scoring model is transparent and user-configurable
The multi-factor scoring formula — meta weight (default 40%), synergy weight (default 30%), counter weight (default 30%) — is not a black box. The weights sum to 1.0 by construction, are validated at compile time, and are exposed as sliders in Settings. Named presets (META_HEAVY, COUNTER_HEAVY, SYNERGY_HEAVY) let players align the model with their playstyle without understanding the math.

### 5. Rank-aware rules, not one-size-fits-all
MLBB drafts differ meaningfully by rank. Epic lobbies have six total bans. Legend adds a second ban round for eight total. Mythic and above run ten bans across two rounds. The `RankRuleEngine` encodes these exact structures. The `PickSequenceEngine` models the 1-2-2-2-2-1 pick turn order correctly, including which side picks first, double-pick turns, and the strategically significant first and last pick positions.

### 6. Every design decision is an engineering decision too
The codebase treats code quality as a product feature. Tier scoring formulas that produced negative scores for `Tier.UNKNOWN` are fixed and documented. Service lifecycle correctness on Android 14+ is handled at the architectural level. A single authoritative DataStore delegate prevents the `IllegalStateException` that occurs when two `preferencesDataStore` delegates target the same file. These are not incidental details — silent bugs in invisible systems erode trust in recommendations.

### 7. Permission onboarding is a product, not an afterthought
Getting an overlay app through Android's layered permission system — Draw Over Other Apps, Accessibility Service, Battery Optimisation exemption, App Auto-Start, Restricted Settings unlock, Background Running — requires the user to navigate up to six separate system dialogs. The `PermissionWizardScreen` walks through each step in deliberate order (least intrusive to most intrusive), explains exactly *why* each permission is needed in plain language, and provides skip paths for optional permissions.

---

## Who It Is For

**Primary users:** MLBB players in Epic rank and above who draft competitively and want data-backed guidance without breaking focus during a match.

**Secondary users:** Players who want to study the meta outside of a draft — browsing the tier list, learning counter relationships, understanding why certain heroes are banned frequently this patch.

---

## What It Is Not

- It is not a bot or an automation tool. It observes and advises; it never taps, inputs, or controls anything in the game.
- It is not a guaranteed win condition. It provides probabilistic, meta-level recommendations based on aggregate data. Player execution, team coordination, and opponent reads remain decisive.
- It is not permanently coupled to any specific backend. The `MetaApi` interface defines a single contract (`GET /v1/meta/snapshot`). The hero data model, the scoring engine, and the overlay UI are all independent of where the data originates.

---

## Long-term Vision

The vision is complete when a new user can install the app, complete the wizard, and have the overlay providing useful ban recommendations in their first ranked draft — without reading any documentation — and an experienced user at Mythical Glory rank has their scoring weights automatically tuned to their playstyle and receives feedback specific to the archetypes they face.

Five pillars drive the long-term roadmap:

1. **Autonomous awareness** — rank detection from the emblem region, first-pick side auto-detection, higher-confidence portrait matching via hybrid perceptual hash + TFLite, phase OCR for BAN_ROUND_1/2 disambiguation.
2. **Deeper intelligence** — composition archetype recognition, draft-phase-aware scoring ramps, enemy intent inference, personal hero pool integration, patch delta weighting, ban value vs. ban urgency separation.
3. **Historical feedback loop** — personal meta calibration from win/loss correlation, draft pattern recognition (over-ban tendencies, under-roam rate), match timeline replay.
4. **Frictionless deployment** — deep links into exact settings pages, accessibility service health watchdog, quick re-setup at the specific revoked step, one-tap overlay relaunch from notification.
5. **Platform resilience** — overlay state serialized to DataStore on every emission (survive OS kills mid-draft), OEM-specific auto-start workarounds maintained for Xiaomi/OPPO/Vivo/Huawei/Samsung/OnePlus, no-capture mode at full feature parity with autonomous mode.

The system is always honest about its own state: if screen capture is unavailable, the overlay says so. If meta data is stale, the overlay says so. If the accessibility service dies, the user is informed.
