# Latest Changes Log

> This file must always be overwritten with the latest session's changes only.

---

## Session changes (2026-07-01 — screenshot-based CV pipeline review)

### Overview
Reviewed 20 MLBB draft-screen screenshots (2026-06-21 and 2026-07-01) and implemented
all gaps found in the CV pipeline.  See `docs/overhaul_plan.md` items B6–B14 for the
full tracking table.

---

### 1. PhaseOcrDetector.kt — enriched OcrResult + label coverage

**`OcrResult` new fields:**
- `isBanRound2: Boolean` — true when OCR reads "Second Ban Phase".
- `isAllyPickTurn: Boolean?` — true = "Ally Team Pick" / "Your Turn To Pick";
  false = "Enemy Team Pick"; null = non-pick frame.
- `isPickAnimation: Boolean` — true when "Player N Selecting Hero" is detected
  (double-pick animation, screenshot 7: both players selecting simultaneously with
  no hero grid visible).

**`classifyText()` new label patterns (priority order):**

| Detected text                                          | Result phase | Notes |
|--------------------------------------------------------|--------------|-------|
| "STARTING" / "PROCEED TO" / "BATTLE SETUP"            | LOADING 0.92 | screenshots 5, 18–20: post-draft / match starting |
| "TRADING"                                              | TRADING 0.85 | unchanged |
| "LOADING"                                              | LOADING 0.85 | generic fallback |
| "BAN" + "SECOND"                                       | BAN 0.90     | Second Ban Phase, isBanRound2=true |
| "BAN"                                                  | BAN 0.90     | First Ban Phase or generic ban |
| "PICK" / "YOUR TURN"  + "ALLY"/"ENEMY"               | PICK 0.90    | ally/enemy pick turn attribution |
| "SELECTING"                                            | PICK 0.80    | isPickAnimation=true, skip slot scan |

**Double-crop bug fixed:**  
`detect()` previously held an internal `TEXT_REGION` and re-cropped the bitmap passed
by the coordinator — which was already cropped to `ocrTextRegion`.  `detect()` now
accepts the pre-cropped bitmap directly and passes it straight to ML Kit.

---

### 2. SlotRegions.kt — ocrTextRegion, selectingHeroCenter, phaseBanner fix

**`ocrTextRegion`** (new): `SlotRegionF(0.200f, 0.000f, 0.800f, 0.140f)`.  
Wider/taller than `phaseBanner` — captures full multi-line phase labels observed in
screenshots ("Second Ban Phase\n·1st·\n13", "The match is starting soon.\nProceed to:
Roam", etc.).  The coordinator uses this region for all OCR crops.

**`phaseBanner.top`** fixed: `0.007f → 0.000f`.  
Screenshots confirmed the "F" of "First Ban Phase" starts flush with the very top of the
landscape frame.  The previous 0.007 offset clipped the first pixel row.

**`selectingHeroCenter`** (new): `SlotRegionF(0.220f, 0.100f, 0.780f, 0.900f)`.  
Reference region for the "Selecting hero" double-pick animation and single pick lock-in
splash arts (screenshots 7, 12–13).  Not used for portrait matching — defined for
future interstitial state detection.

---

### 3. OverlayCaptureCoordinator.kt — single OcrResult atomic + ban round 2 advance + animation guard

**Single `AtomicReference<OcrResult>`:**  
Replaced two separate `AtomicReference`s (`lastOcrPhase`, `lastOcrConfidence`) with one
`AtomicReference<PhaseOcrDetector.OcrResult>` (`lastOcrResult`).  Prevents torn reads
where `phase` and `confidence` could belong to different OCR passes.

**Ban round 2 OCR auto-advance:**  
After `autoTransitionPhase()`, if `ocrResult.isBanRound2 = true` and the session is
still in `BAN_ROUND_1`, calls `stateHolder.advanceToBanRound2()`.  Guarded by
`anyBanRecorded` (at least one R1 ban must exist) to prevent a stale OCR result from
triggering a premature advance on a fresh session.

**"Selecting hero" animation guard:**  
If `ocrResult.isPickAnimation = true` the entire slot-scanning block is `return`ed.
Prevents the hero splash art in the double-pick centre region from producing false
portrait matches in the pick/ban slots.

**OCR crop fixed:**  
Coordinator now crops to `SlotRegions.ocrTextRegion` before calling `PhaseOcrDetector.detect()`,
consistent with the new contract (see §1 above).

---

### 4. OverlayStateHolder.kt — advanceToBanRound2()

New public function `advanceToBanRound2()`:  
Calls `draftSessionManager.startBanRound2()` only when the current phase is still
`BAN_ROUND_1` and the ban structure has a round 2.  Full KDoc explains why OCR-driven
advance is more reliable than slot-count gating.

---

### Open items (unchanged)

| Item | Source |
|------|--------|
| JImageHash integration into PortraitMatcher | misc.md §9 |
| DraftPatternAnalyzer / BuildAdvisor / DraftScoreCalculator unit tests | todo §4 |
| DraftExporter round-trip serialization test | todo §4 |
| Gson removal after full kotlinx.serialization migration | todo §5 / misc §10 |
| Overlay self-status banners (capture unavailable / meta stale / accessibility off) | todo §7 |
