# CV Calibration Workflow

> Companion to [`todo.md`](./todo.md) §9 and §10. Read [`overview.md`](./overview.md)
> first for the overall CV pipeline architecture (`PhaseDetector` → `PortraitMatcher`
> → `HeroClassifier` / `SlotAwareHasher` / legacy pHash cascade).

This document covers two related tasks:

1. **Remapping `SlotRegions` for a new device** — when the ban/pick slot
   coordinates drift because of a different screen aspect ratio, notch/cutout,
   or a game-client UI update.
2. **The `test_corpus/` collection protocol** for `scripts/calibrate_thresholds.py`
   — building a representative, ground-truthed dataset so future
   `hero_thresholds.json` recalibrations (after an MLBB patch changes hero
   roster or portrait art) aren't done against an ad-hoc sample.

---

## 1. Remapping `SlotRegions` for a new device

`SlotRegions` (`app/src/main/java/com/mlbb/assistant/capture/SlotRegions.kt`)
defines normalised `(left, top, right, bottom)` fractions — in the `0..1` range,
relative to the *full captured frame* — for every ban and pick slot, plus the
action-button and OCR text regions used by `PhaseDetector` / `PhaseOcrDetector`.

These coordinates are calibrated against MLBB's draft UI at a **20:9 landscape
reference ratio** (`OverlayCaptureCoordinator.REF_GAME_RATIO`). On other aspect
ratios the game is pillarboxed (ultra-wide, e.g. 21:9) or the UI shifts
slightly (older/unusual devices) — `OverlayCaptureCoordinator.adjusted()`
already compensates for pillarbox insets driven by the user's
`AspectRatioPreset` setting, but a genuinely new UI layout (e.g. after an MLBB
client update, or a device whose game renders draft UI at different absolute
positions) requires re-deriving the regions themselves.

### Steps

1. **Capture reference screenshots.** Using `adb shell screencap` (or the
   in-app screen-capture path via `ScreenCaptureManager`), grab one full-frame
   screenshot per draft phase: ban round 1, ban round 2 (if applicable at the
   target rank), and pick phase. Note the device's reported resolution and
   aspect ratio.

2. **Measure slot bounding boxes in pixels.** Open each screenshot in an image
   editor (or `PIL`/`cv2` via a throwaway script) and record the pixel
   `(left, top, right, bottom)` for every ban slot, pick slot, the action
   button, and the OCR text band, using the same slot ordering as
   `SlotRegions` (`enemyBanSlots[0..N]`, `ourBanSlots[0..N]`,
   `enemyPickSlots[0..4]`, `ourPickSlots[0..4]`).

3. **Normalise to fractions.** Divide each pixel coordinate by the
   screenshot's width (for `left`/`right`) or height (for `top`/`bottom`) to
   get the `0..1` fraction `SlotRegions` expects.

4. **Update or extend `SlotRegions` / `BanSlotTemplates`.**
   - If the new device just needs a **global offset** across all slots (e.g.
     a status-bar inset), that's usually already captured by the
     `AspectRatioPreset` inset math in `OverlayCaptureCoordinator` — prefer
     adding/adjusting a preset there over hand-editing `SlotRegions`.
   - If the **draft UI itself changed** (MLBB client update moved elements),
     update the base fractions in `SlotRegions`/`BanSlotTemplates` directly,
     and bump the reference-ratio comment if the assumed layout ratio changed.

5. **Validate on-device.** Run the app in debug (Pluto floating bubble gives a
   live log view), start a real or custom-game draft, and confirm
   `isSlotFilled` triggers only on genuine portrait reveals — false positives
   usually mean a region is slightly mis-bounded and picking up adjacent UI
   chrome.

6. **Record the supported aspect-ratio set.** `todo.md` §2 tracks validating
   `SlotRegions` against 18:9 / 19.5:9 / 20:9 / tablet ratios as a P1 item —
   this requires physical or emulator devices at each ratio and is not
   achievable without that hardware access. When validated, list the
   confirmed ratios here:

   | Aspect ratio | Status | Notes |
   |---|---|---|
   | 20:9 | ✅ Reference ratio | `SlotRegions` calibrated natively against this. |
   | 21:9 (ultra-wide) | ✅ Supported | Pillarbox inset handled via `AspectRatioPreset.ULTRAWIDE_21_9`. |
   | 16:9 | ✅ Supported | `AspectRatioPreset.STANDARD_16_9`. |
   | 18:9 / 19.5:9 | ⏳ Not yet validated on real hardware | Needs a physical device or emulator profile at this ratio. |
   | Tablets (4:3 / 16:10) | ⏳ Not yet validated | Landscape/tablet overlay layout is also unimplemented (`todo.md` §7). |

---

## 2. `test_corpus/` collection protocol

`scripts/calibrate_thresholds.py` derives `hero_thresholds.json` (per-hero,
per-slot-type accept/reject distance thresholds consumed by
`capture/HeroThresholds.kt`) from a labelled corpus of portrait crops. A
representative corpus matters more than the calibration math itself — a small
or biased sample produces thresholds that look fine in testing but drift badly
against real gameplay video.

### Target composition

- **Positive crops:** 20 crops × top-40 meta heroes (by `MetaBoard` pick rate),
  covering both **ban** and **pick** slot types separately (a hero's ban-slot
  portrait crop is a different crop geometry/scale than its pick-slot crop —
  see `SlotType` and `PortraitNormalizer.normalizeForSlot`). That's
  `40 heroes × 20 crops × 2 slot types = 1,600` positive crops at full target
  size; a partial corpus (fewer heroes or crops) is still useful, but note the
  coverage gap in `manifest.json` (see below).
- **Negative set:** a `negative/` subfolder of crops that are *not* a
  confirmed hero pick — empty slot placeholders, the hero-reveal fly-in
  animation mid-transition, and UI chrome that occasionally lands inside a
  slot's bounding box (portrait borders, rank badges). These calibrate the
  reject side of the threshold, preventing a low threshold from matching
  noise as a low-confidence hero.

### Directory layout

```
test_corpus/
  manifest.json
  <heroId>/
    ban/
      001.png
      002.png
      ...
    pick/
      001.png
      ...
  negative/
    empty_slot_001.png
    flyin_anim_001.png
    ui_chrome_001.png
    ...
```

### `manifest.json` schema

Each crop entry must record enough provenance that a future recalibration can
tell whether the corpus is still representative of the current MLBB client:

```json
{
  "crops": [
    {
      "path": "104/pick/001.png",
      "heroId": 104,
      "slotType": "PICK",
      "device": "Pixel 7 Pro",
      "aspectRatio": "20:9",
      "brightness": "normal",
      "groundTruthHeroId": 104
    },
    {
      "path": "negative/empty_slot_001.png",
      "heroId": null,
      "slotType": "BAN",
      "device": "Pixel 7 Pro",
      "aspectRatio": "20:9",
      "brightness": "dim",
      "groundTruthHeroId": null
    }
  ]
}
```

- `device` / `aspectRatio`: lets a future maintainer tell whether the corpus
  needs refreshing for a new device class (see §1 above).
- `brightness`: coarse bucket (`dim` / `normal` / `bright`) — MLBB's in-game
  brightness setting and time-of-day lighting on a captured device both shift
  mean luminance, which affects `isSlotFilled`'s saturation/luminance gates
  and can shift hash-fusion distances slightly.
- `groundTruthHeroId`: `null` for the negative set; otherwise the confirmed
  hero ID, independent of `heroId` (which is really "which folder this lives
  under" — kept as a redundant cross-check against manifest corruption).

### Recalibration checklist

When an MLBB patch changes hero portraits, adds new heroes, or the meta top-40
shifts significantly:

1. Add crops for any new top-40 entrants; retire crops for heroes that fell
   out of the top-40 (or keep them — a larger corpus is never wrong, only
   slower to calibrate against).
2. Re-run `scripts/calibrate_thresholds.py` against the updated `test_corpus/`.
3. Diff the new `hero_thresholds.json` against the previous version — large
   swings for a hero with an unchanged portrait usually indicate a corpus
   labelling error rather than a genuine threshold shift.
4. Roll out gradually: use `CvFeatureFlags` (`capture/CvFeatureFlags.kt`) to
   disable `useSlotAwareHash` for a cohort if the new calibration regresses
   match accuracy, and watch the `CV_MIGRATION`-tagged log lines emitted by
   `PortraitMatcher.match` (todo.md §6) to compare `confidence` /
   `matchedDistance` against the new `threshold` in production before rolling
   the recalibration out further.

### Current status

No `test_corpus/` directory or `manifest.json` exists in this repository yet —
building the actual corpus requires capturing real gameplay footage from a
running MLBB client, which is not possible in this environment. This document
defines the protocol so the corpus can be built out incrementally as real
device access becomes available; `scripts/calibrate_thresholds.py` should be
pointed at `test_corpus/` once crops exist.
