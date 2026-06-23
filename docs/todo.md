# TODO — MLBB Draft Assistant

> **Status:** Living, actionable backlog. Every item is concrete and verifiable.
> This is the working checklist; strategic phasing lives in
> [`roadmap.md`](./roadmap.md), shipped capabilities in
> [`features.md`](./features.md), and structure in [`overview.md`](./overview.md).
>
> **Priority:** P0 critical · P1 high · P2 normal · P3 nice-to-have.
> **Effort:** S small · M medium · L large.

---

## 1. Technical-debt register (TD-xx)

The codebase uses inline `TD-xx` tags to mark debt resolved at the fix site.
The table below is the canonical register. Items marked done are annotated in
source; open items are new follow-ups discovered during review.

| ID | Item | State | Where |
| --- | --- | --- | --- |
| TD-01 | `hasCCUlt` field replaces hardcoded CC name list | done | `Hero.kt`, `HeroEntity.kt`, `CompositionAnalyzer.kt`, migration v2→v3 |
| TD-02 | Personal hero-pool proficiency multiplier + 6-item builds | done | `DraftScorer.kt`, `Proficiency.kt`, `BuildAdvisor.kt` |
| TD-03 | CV thresholds centralised in config | done | `PhaseDetectionConfig.kt`, `PhaseDetector.kt` |
| TD-04 | Normalised luminance slot-fill threshold | done | `FrameProcessor.kt` |
| TD-05 | Dataset-derived dynamic scoring bounds | done | `DraftScorer.kt` (`computeBounds`) |
| TD-06 | Explicit `Dispatchers.IO` in repository suspend fns | done | `HeroRepositoryImpl.kt` |
| TD-07 | SavedStateHandle-backed search/filter | done | `HeroPoolViewModel.kt` |
| TD-08 | Parallel lazy portrait-hash preload + hybrid match | done | `PortraitMatcher.kt` |
| TD-10 | Paging 3 hero grid | done | `HeroRepositoryImpl.kt`, `HeroDao.kt`, `GetPagedHeroesUseCase.kt` |
| TD-11 | Mutex-guarded crash-log writes | done | `CrashLogStore.kt` |
| TD-12 | Bubble position persistence | done | `OverlayService.kt` |
| TD-09 | *(reserved / verify)* confirm whether a TD-09 item exists or renumber | open | grep `TD-09` returns no hits — **reconcile numbering** |

- [ ] **P2/S** Confirm TD-09 status; either document it or renumber the register so the sequence is gapless.
- [ ] **P3/S** Add a short "TD-xx" convention note to `overview.md` §8 cross-reference (done) and ensure new debt is logged here on creation.

---

## 2. Correctness & robustness

- [ ] **P1/M** Verify `/schemas` JSON files are actually committed (build exports them); if missing, generate and commit to enable safe future migrations.
- [ ] **P1/M** Add migration test that opens v1 → migrates → asserts v3 schema (Room `MigrationTestHelper`).
- [ ] **P1/M** Validate `SlotRegions` / `draft_ui_map.json` against multiple aspect ratios (18:9, 19.5:9, 20:9, tablets); document supported set.
- [ ] **P2/M** Handle `MediaProjection` revocation / `onStop` callback gracefully (stop capture, surface status in overlay).
- [ ] **P2/S** Guard `scoreSafety` against `enemies.size == 0` already coerced — add unit test to lock behaviour.
- [ ] **P2/S** Confirm `adaptiveWeights` never violates the `ScoreWeights` sum-to-1 invariant for all `pickIndex` (add property test).
- [ ] **P2/M** Define behaviour when `MetaApi` returns partial/garbage data (DTO validation + reject-and-keep-existing).
- [ ] **P3/S** Make `inferFromBanCount` thresholds named constants instead of inline literals.

---

## 3. Architecture & maintainability

- [ ] **P1/L** Decompose `OverlayService.kt` (~1,100 LOC) into: window/layout manager, capture-loop coordinator, and Compose UI host. Keep the service as a thin lifecycle shell.
- [ ] **P2/M** Extract overlay state into a dedicated `OverlayStateHolder`/ViewModel-like object observed by all phase composables.
- [ ] **P2/S** Audit that no ViewModel touches a DAO directly (enforce `SaveDraftSessionUseCase` as the only write path) — add a lint/architecture test.
- [ ] **P2/M** Introduce a `:domain` and `:data` Gradle module split (currently one `:app` module) to enforce the dependency rule at compile time.
- [ ] **P3/S** Consolidate the two scoring entry points (`score`/`rankAll` vs `computeScore`) — document which is canonical and mark the other test-only.

---

## 4. Testing

- [ ] **P1/M** Compose UI tests for Draft, HeroList, Settings, and Permission Wizard.
- [ ] **P1/M** Instrumentation test for the overlay foreground-service start/stop lifecycle.
- [ ] **P2/M** Unit tests for `WeightCalibrator`, `DraftPatternAnalyzer`, `EnemyIntentAnalyzer`, `WinConditionGenerator`, `BuildAdvisor`, `DraftScoreCalculator` (advisor layer is under-tested).
- [ ] **P2/M** `FrameProcessor` slot-dedupe and throttle tests with synthetic bitmaps (Robolectric).
- [ ] **P2/S** `DraftExporter` round-trip serialization test.
- [ ] **P3/S** Snapshot tests for key Compose components.

---

## 5. CI / tooling / release

- [ ] **P1/M** Add CI workflow: `./gradlew lint testDebugUnitTest assembleDebug` on every PR.
- [ ] **P2/S** Add `ktlint`/`detekt` with a baseline.
- [ ] **P2/S** Add dependency-update automation against the version catalog.
- [ ] **P2/M** Add a signed-release workflow + R8 mapping upload.
- [ ] **P2/S** Verify ProGuard keep rules cover Gson DTOs, Room, and Hilt-generated classes; add tests on a minified build.
- [ ] **P3/S** Generate a baseline profile for startup performance.

---

## 6. Observability

- [ ] **P2/M** Optional remote crash reporting (Crashlytics/Sentry) gated behind a settings toggle; keep local `CrashLogStore` as fallback.
- [ ] **P2/S** Add a "share logs" action from `LogScreen` via the existing FileProvider.
- [ ] **P3/S** Structured event logging for detection accuracy (phase-detect confidence, match confidence) to inform tuning.

---

## 7. UX & product polish

- [ ] **P2/M** Surface self-status in the overlay: "capture unavailable", "meta data stale (n days)", "accessibility service off".
- [ ] **P2/M** No-capture (manual) mode parity audit — verify every autonomous path has a manual equivalent and document it.
- [ ] **P2/S** Empty/error states for HeroList, MetaBoard, and History.
- [ ] **P3/M** Light theme + accent presets.
- [ ] **P3/M** Landscape/tablet overlay layout.
- [ ] **P3/S** First-run interactive draft simulation tied to `isSimulation`.

---

## 8. Data & content

- [ ] **P1/M** Confirm `META_API_BASE_URL` (`https://api.mlbb-assistant.com/`) is live or document the bundled-seed-only mode clearly.
- [ ] **P2/M** Establish an update cadence/source for `default_heroes.json` (counters/synergies/tiers drift each patch).
- [ ] **P2/S** Add `lastUpdated`/patch-version metadata to the meta snapshot and show it in MetaBoard.
- [ ] **P3/M** Community-sourced counter/synergy contributions with confidence weighting.

---

## 9. Documentation upkeep

- [ ] **P1/S** Add a root `README.md` (currently none) linking to these four docs and `MISSION.md`, with build/run instructions.
- [ ] **P2/S** Keep `replit.md` "Pointers" section accurate — it references `docs/AUDIT.md` and `docs/temp/REFACTOR_PLAN.md` which were deleted; update links to point at these docs.
- [ ] **P2/S** Document the CV calibration workflow (how to remap `SlotRegions` for a new device) in `overview.md` or a dedicated `docs/cv-calibration.md`.
- [ ] **P3/S** Add ADRs (architecture decision records) for the big calls listed in `overview.md` §8.

---

## How to use this file

- Pull an item here before coding; check it off when merged and reflect any new
  capability in [`features.md`](./features.md).
- When you create new technical debt, add a `TD-xx` tag in source **and** a row
  in §1 the same commit.
- Keep priorities honest: promote/demote as the product situation changes.
