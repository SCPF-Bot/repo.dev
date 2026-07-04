# Latest Changes Log

> This file must always be overwritten with the latest session's changes only.

---

## Session changes (2026-07-03 — merged `docs/temp/recommendations.md` into permanent docs)

### Overview
`docs/temp/recommendations.md` (the CV/hero-detection "pure perceptual matching" proposal,
v2.2.0) was audited against the current codebase. Its core proposals — `SlotType`-aware
normalization, `SlotAwareHasher` triple-hash fusion, per-hero/per-slot calibrated
thresholds, and temporal consensus — were found **already implemented** (`TD-16`, `TD-17`,
`TD-18` in `todo.md`; `misc.md` §9/§13; `roadmap.md` RA-04/RA-05). The remaining
not-yet-implemented recommendations were extracted and merged into permanent docs; the
source file was then deleted.

### What was merged

| Recommendation | Destination | Status |
|---|---|---|
| `CvFeatureFlags` remote-config kill-switch for the CV matching cascade | `todo.md` §5 | new backlog item (not implemented) |
| `CV_MIGRATION`-tagged structured telemetry for match confidence/threshold | `todo.md` §6 (existing item, annotated) | not implemented |
| `test_corpus/` collection protocol (per-hero ban/pick crops + manifest) for `scripts/calibrate_thresholds.py` | `todo.md` §9 | new backlog item (not implemented) |
| Perpetual CV pipeline maintenance cycle (patch/new-hero/FP-spike/quarterly triggers) | `misc.md` new §9a | documented as standing process |

Everything else in the source file (slot-aware normalization, triple-hash fusion,
per-hero thresholds, consensus manager, APK-size rationale for dropping TFLite) was
already accurately reflected in existing docs and required no further changes.

### Result
`docs/temp/recommendations.md` deleted — fully absorbed into `todo.md` and `misc.md`.

---

### Open items (unchanged)

| Item | Source |
|------|--------|
| JImageHash FP benchmark / promotion in `PortraitMatcher` | `misc.md` §9, `todo.md` §10 |
| CV feature-flag rollback gate (`USE_SLOT_AWARE_HASH` etc.) | `todo.md` §5 |
| Test corpus collection for threshold calibration | `todo.md` §9 |
| `DraftExporter` round-trip serialization test | `todo.md` §4 |
| Gson removal after full kotlinx.serialization migration | `todo.md` §5 / `misc.md` §10 |
| Overlay self-status banners (capture unavailable / meta stale / accessibility off) | `todo.md` §7 |
