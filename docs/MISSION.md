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
MLBB drafts differ meaningfully by rank. Epic lobbies have six total bans (three per team, one round). Legend adds a second ban round for eight total. Mythic and above run ten bans across two rounds. The RankRuleEngine encodes these exact structures. The PickSequenceEngine models the 1-2-2-2-2-1 pick turn order correctly, including which side picks first, double-pick turns, and the strategically significant first and last pick positions. Advice accounts for these positional asymmetries: first-pick recommendations favor flexible, hard-to-counter heroes; last-pick recommendations favor direct counters.

### 6. Every design decision is an engineering decision too
The codebase treats code quality as a product feature. Incorrect floating-point arithmetic between Kotlin `Double` and `Float` is called out explicitly with `// Kotlin has no Double ± Float operator — explicit .toFloat() required`. A previous scoring bug where `Tier.UNKNOWN` produced a negative contribution to scores is documented and fixed with a named constant (`TIER_MAX_ORDER`). Service lifecycle correctness on Android 14+ (FGS `mediaProjection` type requiring an authorized token before `startForeground`) is handled at the architectural level, not papered over. These are not incidental details — they reflect a belief that silent bugs in invisible systems erode trust in recommendations, and eroded trust makes the product useless.

### 7. Permission onboarding is a product, not an afterthought
Getting an overlay app through Android's layered permission system — Draw Over Other Apps, Accessibility Service, Battery Optimisation exemption, App Auto-Start, Restricted Settings unlock, Background Running — requires the user to navigate up to six separate system dialogs. The PermissionWizardScreen walks through each step in a deliberate order (from least intrusive to most intrusive, with the accessibility service last because it is the most sensitive), explains exactly *why* each permission is needed in plain language, and provides skip paths for permissions that are device-specific or optional.

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

## Technical Mission

At the engineering level, the mission is to build a **correct, maintainable, and resilient overlay service** for Android. This means:

- A single `ComposeView` that transitions between bubble and widget modes without recreation, avoiding the flicker and state loss that would come from removing and re-adding the view.
- A touch listener that correctly mediates between View-level drag detection and Compose-level click handling — with separate code paths for bubble mode (View owns the entire sequence) and widget mode (Compose owns button clicks, View steals the sequence only on confirmed drag).
- A foreground service that starts with the minimum required type (`SPECIAL_USE`) and upgrades to include `MEDIA_PROJECTION` only after the user has authorized screen capture — satisfying Android 14's strict token-at-startForeground requirement without crashing on devices that have not gone through the capture consent flow.
- A single authoritative DataStore delegate (`AppDataStore.kt`) shared across all callers, preventing the `IllegalStateException: There are multiple DataStores active for the same file` crash that occurs when two `preferencesDataStore` delegates target the same file name.
- A structured in-app log viewer backed by a rotation-capped append-only file store, so crashes and errors are visible to the user without requiring developer tools.
