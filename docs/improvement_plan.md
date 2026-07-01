# Improvement Plan — Reference Repository Integration

> **Generated:** 2026-07-01 · **Status:** Executed (all items below are implemented)
> **Source analysis:** 5 reference repositories catalogued in `docs/temp/findings.md`
> and `docs/temp/recommendations.md`.

---

## 1. Reference repositories analysed

| Repo | Author | Key contribution |
|---|---|---|
| `bipash25-mlbb-assistant` | bipash25 | Rich data assets: `counter_lookup.json`, `items.json`, `emblems.json`, `spells.json` |
| `techvibedz-mlbb-draft-assistant` | techvibedz | Trait-tag vocabulary; ban phase UI patterns |
| `polymathicneo-mlbb-draft-assistant` | polymathicneo | Pick sequencing patterns; composition balance heuristics |
| `AlanNobita-mlbb_drafter` | AlanNobita | `scoring.py` trait counter matrix; `hero_archetypes.json`; ally-state gap detection |
| `MomenAlfaqeh-MLBB-Draft-Assistant` | MomenAlfaqeh | Composition evaluation UI ideas; role-gap warnings |

---

## 2. Improvements implemented

### A — Algorithm improvements

#### A1 · Trait-based counter scoring (`TraitCounterEngine.kt`)
**Source:** AlanNobita `scoring.py` (trait counter matrix)  
**What:** Ported the 7-threat-trait × counter-trait cross-reference system to Kotlin.  
**Effect:** Candidate heroes with counter traits (anti-heal, armor-shred, crowd-control, …)
receive up to +0.45 bonus score against enemy threat traits (high-sustain, high-armor,
high-mobility, …), making counter-pick recommendations more precise.

#### A2 · Archetype gap detection (`CompositionAnalyzer.kt` + `HeroArchetypeService.kt`)
**Source:** AlanNobita `scoring.py` ally-state gap detection  
**What:** `CompositionAnalyzer.analyze()` now emits two additional warnings:
- "⚠️ No magic damage — enemy will rush physical defense" when no Mage/Support hero is present.
- "⚠️ Squishy comp — add a tank or support to survive dives" when ≥2 glass-cannon roles and no frontline.
`HeroArchetypeService` reads `hero_archetypes.json` to classify heroes into 20+ fine-grained
sub-role archetypes and provide archetype matchup scores.

#### A3 · Archetype service with trait extraction (`HeroArchetypeService.kt`)
**Source:** AlanNobita `hero_archetypes.json`  
**What:** Injectable `@Singleton` Hilt service that parses `hero_archetypes.json` at startup,
builds hero → archetype, archetype → hero, and hero → trait-set indices.  Provides:
`computeAllyStateGaps()`, `computeArchetypeMatchupScore()`, `isMagicDamageSource()`,
`isFrontlineHero()`, `uniqueArchetypes()`, `sharedArchetypes()`.

#### A4 · Ban value vs ban urgency separation (`BanValueScorer.kt`, `BanUrgencyScorer.kt`)
**Source:** Roadmap item A4; pattern observed across multiple reference repos  
**What:** Split `BanRecommender.computeBaseScore()` into two orthogonal objects:
- `BanValueScorer` — context-free intrinsic meta value (win rate, ban rate, toxic flag, OP flag).
- `BanUrgencyScorer` — reactive contextual urgency (counter threat to allies, enemy synergy
  amplifiers, archetype enablers).
`BanRecommender.rankSplit()` now combines both: `finalScore = value + urgency`.

---

### B — Data assets added

| File | Source | Contents |
|---|---|---|
| `counter_lookup.json` | bipash25 | 1 000+ directional counter pairs with empirical confidence scores (0–1) |
| `hero_archetypes.json` | AlanNobita | 20+ sub-role archetypes with hero lists, trait tags, and matchup rules |
| `items.json` | bipash25 | 89 items with stats, passives, build paths |
| `emblems.json` | bipash25 | Emblem system data for all roles |
| `spells.json` | bipash25 | All battle spells with descriptions and recommended role pairings |

---

### C — Database schema upgrade

| Change | Detail |
|---|---|
| New table `counter_lookup` | `(hero_name, counters_hero, confidence, direction)` — composite primary key for safe upserts |
| New DAO `CounterLookupDao` | `getCounterConfidence()`, `getCountersFor()`, `getWeakAgainst()`, `insertAll()`, `count()` |
| Migration 3→4 | SQL-based migration in `DatabaseModule.MIGRATION_3_4` |
| `AppDatabase` version | Bumped from 3 → 4 |

---

### D — Unit tests added

| Test class | Coverage |
|---|---|
| `WeightCalibratorTest` | 10 tests — null guards, weight normalization, delta clamping, confidence growth, min-floor |
| `EnemyIntentAnalyzerTest` | 8 tests — minimum-pick guard, WOMBO_COMBO, DIVE, POKE detection, intent summary, counter advice, BALANCED fallback |
| `WinConditionGeneratorTest` | 9 tests — empty draft, DIVE/POKE/WOMBO_COMBO/SPLIT_PUSH/TURTLE win conditions, BALANCED, null archetype |
| `TraitCounterEngineTest` | 14 tests — zero-input guards, each of 5 threat-trait mappings, multi-enemy accumulation, MAX_TRAIT_BONUS cap, no-match zero, `describeCounters`, flat overload |
| `BanValueScorerTest` | 8 tests — `isAbsoluteBan` three-trigger paths, score range, toxic/ban-rate/lane ordering, score clamping |

---

## 3. Files created

```
domain/advisor/HeroArchetypeService.kt      — archetype gap detection, trait extraction
domain/advisor/TraitCounterEngine.kt        — trait counter bonus scoring
domain/advisor/BanValueScorer.kt            — context-free ban value scoring
domain/advisor/BanUrgencyScorer.kt          — context-sensitive ban urgency scoring
data/local/database/CounterLookupEntity.kt  — Room entity for counter confidence table
data/local/database/CounterLookupDao.kt     — DAO for counter_lookup table
test/.../WeightCalibratorTest.kt
test/.../EnemyIntentAnalyzerTest.kt
test/.../WinConditionGeneratorTest.kt
test/.../TraitCounterEngineTest.kt
test/.../BanValueScorerTest.kt
```

## 4. Files modified

```
domain/advisor/BanRecommender.kt    — delegates to BanValueScorer + BanUrgencyScorer
domain/advisor/CompositionAnalyzer.kt — adds magic-gap + frontline-gap warnings
data/local/database/AppDatabase.kt  — version 3→4, adds CounterLookupEntity + DAO
di/DatabaseModule.kt                 — MIGRATION_3_4 + provideCounterLookupDao
```

## 5. Attribution

All data assets are sourced from open-source repositories under permissive licences
(MIT or no licence stated / implied open-source).  Community credit embedded in each
file's KDoc header.
