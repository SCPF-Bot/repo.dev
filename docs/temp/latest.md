# Latest Changes Log

> This file must always be overwritten with the latest session's changes only.

---

## Session changes (2026-07-01 — reference repository integration)

### Overview
Analysed 5 reference MLBB draft assistant repos and extracted best-practice algorithms, data assets,
and testing patterns. Implemented all improvements in repo.dev. See `docs/improvement_plan.md` for
the full structured summary.

---

### 1. New data assets added to `app/src/main/assets/`

| File | Source repo | Contents |
|---|---|---|
| `counter_lookup.json` | bipash25/mlbb-assistant | 1 000+ directional hero counter pairs with empirical confidence scores |
| `hero_archetypes.json` | AlanNobita/mlbb_drafter | 20+ sub-role archetypes, trait tags, matchup rules |
| `items.json` | bipash25/mlbb-assistant | 89 items with stats, passives, build paths |
| `emblems.json` | bipash25/mlbb-assistant | Full emblem system data |
| `spells.json` | bipash25/mlbb-assistant | All battle spells with role recommendations |

---

### 2. New domain classes

**`domain/advisor/HeroArchetypeService.kt`** (new — `@Singleton` Hilt)  
Reads `hero_archetypes.json` at startup; builds hero↔archetype + hero↔traits indices.  
Provides: `computeAllyStateGaps()`, `computeArchetypeMatchupScore()`, `isMagicDamageSource()`,
`isFrontlineHero()`, `uniqueArchetypes()`, `sharedArchetypes()`.

**`domain/advisor/TraitCounterEngine.kt`** (new)  
Kotlin port of AlanNobita `scoring.py` trait counter matrix.  
7-threat-trait × counter-trait cross-reference; +0.15 per match, capped at MAX_TRAIT_BONUS = 0.45.
`describeCounters()` produces human-readable overlay explanation strings.

**`domain/advisor/BanValueScorer.kt`** (new — roadmap A4)  
Context-free intrinsic ban value: win rate, ban rate, toxic/OP flags, lane priority bonus.
`isAbsoluteBan()` encapsulates the single-flag-sufficient logic (any of toxic/OP/banRate≥0.40).

**`domain/advisor/BanUrgencyScorer.kt`** (new — roadmap A4)  
Reactive contextual urgency: counter-threat to allied picks, enemy synergy amplifiers,
archetype enabler detection. `buildReason()` produces urgency-specific ban reason strings.

---

### 3. Modified domain classes

**`domain/advisor/BanRecommender.kt`**  
`rankSplit()` now delegates to `BanValueScorer.score()` + `BanUrgencyScorer.score()`.
`finalScore = value + urgency` (clamped to [0,1]). Roadmap item A4 marked complete.

**`domain/advisor/CompositionAnalyzer.kt`**  
`analyze()` emits two new gap warnings (ported from AlanNobita gap detection):
- "No magic damage — enemy will rush physical defense"
- "Squishy comp — add a tank or support to survive dives"

---

### 4. New data classes

- `data/local/database/CounterLookupEntity.kt` — Room entity (composite PK)
- `data/local/database/CounterLookupDao.kt` — DAO with 5 query methods
- `data/local/database/AppDatabase.kt` — version 3 → 4; CounterLookupEntity added
- `di/DatabaseModule.kt` — MIGRATION_3_4 + provideCounterLookupDao()

---

### 5. New unit tests (5 classes, 49 tests)

| Class | Tests |
|---|---|
| `WeightCalibratorTest` | 9 |
| `EnemyIntentAnalyzerTest` | 8 |
| `WinConditionGeneratorTest` | 9 |
| `TraitCounterEngineTest` | 14 |
| `BanValueScorerTest` | 8 |

---

### 6. Docs updated

- `docs/improvement_plan.md` — new; full structured record of what was done and why
- `docs/roadmap.md` — A4/A7/A8/A9 completed; deep intelligence section expanded; test items ticked
- `docs/features.md` — 7 new advisor feature rows (4.12–4.17)
- `docs/todo.md` — unit test items ticked; TD counter updated to TD-17
- `docs/overview.md` — advisor and data layers updated; DB version noted as v4
- `docs/temp/findings.md` — eighth-pass delta summary prepended
- `docs/temp/recommendations.md` — summary table updated with eighth-pass adoptions
