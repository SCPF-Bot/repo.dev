# MLBB Draft Assistant — Codebase Overhaul Plan

_Generated: 2026-07-01. Tracks every improvement item discovered during the
exhaustive docs/ audit and maps them to implementation status._

---

## Scope

All changes are confined to the single Gradle module `:app`.
Package root: `com.mlbb.assistant`. AppDatabase version: 4.

---

## Status legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Implemented in this overhaul |
| 🔲 | Identified, not yet implemented |
| ➡️ | Existing code already satisfies the requirement |

---

## Group A — Correctness & Safety

| ID | Item | File(s) | Status |
|----|------|---------|--------|
| A1 | `scoreSafety` divide-by-zero guard + unit test | `DraftScorer.kt`, `DraftScorerTest.kt` | ✅ guard existed; test added |
| A2 | `adaptiveWeights` sum-to-1 property test | `DraftScorerTest.kt` | ✅ |
| A3 | MetaApi DTO validation — reject partial snapshots (< 20 heroes) and keep existing DB | `HeroRepositoryImpl.kt` | ✅ |
| A4 | `inferFromBanCount` named constants (`MYTHIC_BAN_THRESHOLD`, `LEGEND_BAN_THRESHOLD`) | `RankRuleEngine.kt` | ➡️ already present |
| A5 | Slot-set `ConcurrentHashMap.newKeySet()` (P0-04/P0-05) | `OverlayStateHolder.kt` | ➡️ already correct |

---

## Group B — CV Pipeline

| ID | Item | File(s) | Status |
|----|------|---------|--------|
| B1 | TD-16: Wire `AspectRatioPreset` from DataStore into `OverlayCaptureCoordinator` — compute horizontal pillarbox inset and adjust all slot regions | `OverlayCaptureCoordinator.kt`, `ScreenCaptureManager.kt` | ✅ |
| B2 | Separate ban / pick confidence thresholds (`BAN_PHASE_CONFIDENCE_MIN`, `PICK_PHASE_CONFIDENCE_MIN`) | `PhaseDetectionConfig.kt` | ✅ |
| B3 | Occupied / empty slot pre-classifier constant (`SLOT_OCCUPIED_LUMINANCE_MIN`) | `PhaseDetectionConfig.kt` | ✅ |
| B4 | JImageHash integration into `PortraitMatcher` (Gradle dep already present) | `PortraitMatcher.kt` | 🔲 |
| B5 | Document that `HeroPortraitObjectDetector` does region detection only (not classification) | architecture comment | ➡️ existing KDoc clear |
| B6 | **Screenshot review** — PhaseOcrDetector enrichment: add `isBanRound2`, `isAllyPickTurn`, `isPickAnimation` fields; detect "SECOND BAN PHASE", "ALLY/ENEMY TEAM PICK", "YOUR TURN TO PICK", "STARTING/PROCEED TO/BATTLE SETUP"; fix double-crop bug | `PhaseOcrDetector.kt` | ✅ |
| B7 | **Screenshot review** — Ban round 2 OCR auto-advance: when "Second Ban Phase" detected by OCR, call `advanceToBanRound2()` without waiting for slot-fill counts | `OverlayCaptureCoordinator.kt`, `OverlayStateHolder.kt` | ✅ |
| B8 | **Screenshot review** — "Selecting hero" double-pick animation guard: skip slot scanning when `OcrResult.isPickAnimation = true` (screenshot 7: both players selecting simultaneously, no hero grid visible) | `OverlayCaptureCoordinator.kt` | ✅ |
| B9 | **Screenshot review** — OCR single atomic: replace two separate `AtomicReference` for `lastOcrPhase`/`lastOcrConfidence` with one `AtomicReference<OcrResult>` to avoid torn reads | `OverlayCaptureCoordinator.kt` | ✅ |
| B10 | **Screenshot review** — Add `ocrTextRegion` to `SlotRegions` (20–80 % x, 0–14 % y) — wider/taller than `phaseBanner`; coordinator uses this for OCR crops | `SlotRegions.kt` | ✅ |
| B11 | **Screenshot review** — Fix `phaseBanner.top` 0.007 → 0.000 (phase label text starts flush with top of landscape frame) | `SlotRegions.kt` | ✅ |
| B12 | **Screenshot review** — Add `selectingHeroCenter` region for future interstitial state detection | `SlotRegions.kt` | ✅ |
| B13 | **Screenshot review** — `detect()` no longer does internal crop — accepts pre-cropped bitmap from coordinator to eliminate double-crop; KDoc updated | `PhaseOcrDetector.kt` | ✅ |
| B14 | **Screenshot review** — End-of-draft detection: "STARTING" / "PROCEED TO" / "BATTLE SETUP" → `DetectedPhase.LOADING` → `completeDraft()` via `autoTransitionPhase` | `PhaseOcrDetector.kt` | ✅ |

---

## Group C — Data Layer

| ID | Item | File(s) | Status |
|----|------|---------|--------|
| C1 | `lastUpdated` + `patchVersion` fields in `MetaSnapshotDto` | `MetaSnapshotDto.kt` | ✅ |
| C2 | Display patch version + last-updated timestamp in MetaBoard header | `MetaBoardScreen.kt` | ✅ |
| C3 | Gson removal after full kotlinx.serialization migration | multiple | 🔲 |

---

## Group D — UI / UX

| ID | Item | File(s) | Status |
|----|------|---------|--------|
| D1 | MetaBoard empty/error state | `MetaBoardScreen.kt` | ✅ |
| D2 | HeroList empty/error state | `HeroListScreen.kt` | ➡️ already has "No heroes loaded" state |
| D3 | DraftHistory empty/error state | `DraftHistoryScreen.kt` | ➡️ already has "No drafts saved yet" state |
| D4 | Overlay status banners ("capture unavailable", "meta stale", "accessibility off") | `OverlayService` / overlay components | 🔲 |
| D5 | WeightCalibrator Settings UI with minimum-sample gate | `SettingsScreen.kt`, `CalibrationSection.kt` | ➡️ CalibrationSection already gated on MIN_SESSIONS |
| D6 | Log sharing via `Intent.ACTION_SEND` from LogScreen | `LogViewerActivity.kt` | ➡️ already implemented |

---

## Group E — Documentation / Comments

| ID | Item | File(s) | Status |
|----|------|---------|--------|
| E1 | Clarify `MLBBAccessibilityService` does NOT do portrait detection | `MLBBAccessibilityService.kt` | ✅ |
| E2 | Android 15+ MediaProjection consent re-prompt note | `ScreenCaptureManager.kt` | ✅ |
| E3 | `DraftScorer.computeScore` KDoc marking it `@VisibleForTesting` | `DraftScorer.kt` | ➡️ already annotated in P2-03 fix |
| E4 | Write `docs/overhaul_plan.md` | this file | ✅ |
| E5 | Update `docs/temp/latest.md` | `docs/temp/latest.md` | ✅ |

---

## Group F — Tests

| ID | Item | File(s) | Status |
|----|------|---------|--------|
| F1 | `DraftScorer` — scoreSafety guard test | `DraftScorerTest.kt` | ✅ |
| F2 | `DraftScorer` — adaptiveWeights sum-to-1 property test | `DraftScorerTest.kt` | ✅ |
| F3 | `DraftPatternAnalyzer` unit tests | 🔲 |
| F4 | `BuildAdvisor` unit tests | 🔲 |
| F5 | `DraftScoreCalculator` unit tests | 🔲 |
| F6 | `DraftExporter` round-trip serialization test | 🔲 |

---

## Architecture decisions recorded in this overhaul

1. **TD-16 inset formula**: pillarbox inset = `(1 − REF_GAME_RATIO / effectiveRatio) / 2`
   where `REF_GAME_RATIO = 20/9`. Applied only when `effectiveRatio > REF_GAME_RATIO`
   (ultra-wide screens). Standard 16:9 and most auto-detected phones get `horizInset = 0`
   (no-op fast path).

2. **Partial snapshot rejection**: Snapshots with < 20 heroes are treated as malformed and
   rejected without touching the DB. The retry loop still exhausts `MAX_SYNC_RETRIES` so
   the error is logged; existing DB remains intact.

3. **Separate phase confidence thresholds**: `BAN_PHASE_CONFIDENCE_MIN = 0.12f` vs
   `PICK_PHASE_CONFIDENCE_MIN = 0.08f`. Ban detection can afford a stricter gate because
   the red gradient is more saturated; pick detection uses a softer gate to avoid missing
   the teal transition on slow devices.

4. **Slot occupied pre-classifier**: `SLOT_OCCUPIED_LUMINANCE_MIN = 45f` is a fast-exit
   luminance gate applied before the saturation criterion and before portrait matching.
   Empty MLBB slots have mean luminance ≈ 20–40; hero portraits are always brighter.
