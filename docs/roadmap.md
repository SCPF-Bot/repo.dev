# Roadmap — MLBB Draft Assistant

> **Status:** Living roadmap. Tracks what has shipped, what is in progress, and
> what is planned, organised into phases. Checkboxes reflect implementation
> state in source. Pair this with [`features.md`](./features.md) (what exists),
> [`todo.md`](./todo.md) (granular backlog), and [`overview.md`](./overview.md)
> (architecture).
>
> **Conventions:** `[x]` shipped · `[~]` partially done / in progress · `[ ]` planned.
> The five long-term pillars below come from [`MISSION.md`](./MISSION.md).

---

## Release history

| Version | Code | Theme |
| --- | --- | --- |
| 2.0.0 | 2 | Clean-architecture rewrite ("MLBB Assistant 2.0"): overlay-first product, CV pipeline, scoring engine, technical-debt (TD-xx) remediation pass. **Current.** |
| 1.x | 1 | Initial release (superseded). |

---

## Phase 0 — Foundation (shipped)

- [x] Clean Architecture + MVI/UDF layering (`domain` is Android-free)
- [x] Hilt DI with single-source modules (`AppModule`, `DatabaseModule`, `NetworkModule`, `RepositoryModule`, `OverlayModule`)
- [x] Room v3 with exported schema + migrations 1→2→3
- [x] Single DataStore delegate (no duplicate-delegate crash)
- [x] Retrofit + OkHttp meta sync with local JSON seed fallback
- [x] Version catalog (`libs.versions.toml`) as dependency source of truth
- [x] Timber + file-backed `CrashLogStore`
- [x] Localization scaffold (EN/FIL/ID/MS/TH/VI)
- [x] Unit-test suite for domain (scoring, engine, advisor, hashing)

## Phase 1 — Autonomous awareness (shipped / hardening)

- [x] MediaProjection screen capture + foreground service (Android 14+ compliant)
- [x] Phase detection from banner colours (`PhaseDetector`)
- [x] Perceptual-hash portrait matching (`PortraitMatcher` + `PerceptualHash`)
- [x] Hybrid dHash + histogram matching (TD-08)
- [x] Normalised luminance slot-fill detection (TD-04)
- [x] Config-driven CV thresholds (TD-03, `PhaseDetectionConfig`)
- [x] Rank detection (`RankDetector`) and first-pick detection (`FirstPickDetector`)
- [x] OCR-assisted ban round 1/2 disambiguation (`PhaseOcrDetector`)
- [~] Per-aspect-ratio slot-region calibration (works; needs broader device validation)
- [ ] Higher-confidence matching via TFLite hybrid model
- [ ] Auto-recalibration of `SlotRegions` from a one-time guided capture

## Phase 2 — Deeper intelligence (shipped / expanding)

- [x] Multi-factor scoring with adaptive weights
- [x] Dataset-derived dynamic bounds (TD-05)
- [x] Patch-velocity multiplier
- [x] Composition archetype recognition
- [x] Enemy intent inference (`EnemyIntentAnalyzer`)
- [x] Win-condition generation (`WinConditionGenerator`)
- [x] Personal hero-pool proficiency weighting (TD-02)
- [x] Build/item advice with situational items
- [~] Ban value vs. ban urgency separation (single combined recommender today)
- [ ] Patch-delta weighting from historical snapshots
- [ ] Draft-phase-aware "power spike timing" advice

## Phase 3 — Historical feedback loop (in progress)

- [x] Draft history persistence (`draft_sessions`)
- [x] Match-outcome recording + simulation exclusion
- [x] Draft replay viewer (`DraftReplayScreen`)
- [x] Weight self-calibration (`WeightCalibrator`)
- [x] Draft pattern analysis (`DraftPatternAnalyzer`)
- [~] Personal meta calibration from win/loss correlation (calibrator exists; needs UI + larger sample tuning)
- [ ] Tendency feedback surfaced in-app ("you over-ban", "you under-roam")
- [ ] Match timeline replay with frame thumbnails

## Phase 4 — Frictionless deployment (in progress)

- [x] Guided permission wizard (ordered least→most intrusive)
- [x] Wizard progress persistence
- [x] One-tap overlay start from app
- [x] Bubble position persistence (TD-12)
- [~] OEM-specific auto-start guidance (generic flow exists; per-OEM deep links pending)
- [ ] Deep links into exact system settings pages per manufacturer
- [ ] Accessibility-service health watchdog with re-setup at the revoked step
- [ ] One-tap overlay relaunch from the notification after an OS kill

## Phase 5 — Platform resilience (planned)

- [~] Overlay state survives configuration changes (bubble position persisted)
- [ ] Full overlay-session serialization to DataStore on every emission (survive mid-draft OS kill)
- [ ] Verified no-capture mode at full feature parity with autonomous mode
- [ ] Maintained OEM workaround matrix (Xiaomi/OPPO/Vivo/Huawei/Samsung/OnePlus)
- [ ] Honest self-status surfacing everywhere (stale meta, dead service, capture unavailable)

---

## Quality & engineering roadmap

### Shipped
- [x] TD-01..TD-12 technical-debt remediation pass (see [`todo.md`](./todo.md) register)
- [x] `java.time`-only date handling
- [x] Explicit IO dispatch in repositories (TD-06)
- [x] Mutex-guarded crash log writes (TD-11)
- [x] Paging 3 for hero grid (TD-10)
- [x] SavedStateHandle-backed search/filter survival (TD-07)

### Planned
- [ ] Decompose `OverlayService` (~1,100 LOC) into window-manager, lifecycle, and UI-host collaborators
- [ ] Instrumentation tests for overlay flows + capture orchestration
- [ ] Compose UI tests for primary screens
- [ ] CI pipeline: lint + unit tests + assemble on every PR
- [ ] Baseline profile / startup performance pass
- [ ] Dependency freshness automation (catalog updates)
- [ ] Crashlytics or equivalent remote crash reporting (currently local only)

---

## Product roadmap (forward-looking ideas)

- [ ] Cloud sync of hero pool + settings across devices
- [ ] Shareable post-draft report cards
- [ ] Community-sourced counter/synergy data with confidence weighting
- [ ] In-overlay quick notes / callouts for team comms
- [ ] Tablet / landscape-optimised overlay layouts
- [ ] Theming options (light theme, accent presets)
- [ ] Tutorial / interactive first-run draft simulation

---

## How to use this file

1. When a planned item ships, flip `[ ]`/`[~]` to `[x]` here **and** add the row
   to [`features.md`](./features.md).
2. Break any `[ ]`/`[~]` item into concrete steps in [`todo.md`](./todo.md)
   before starting work.
3. Keep phase groupings aligned with the five MISSION pillars; add new phases
   rather than overloading existing ones.
