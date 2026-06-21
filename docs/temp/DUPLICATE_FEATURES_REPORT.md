# DUPLICATE FEATURES REPORT
_Generated: 2026-06-21 | Phase 1 Step 2_

## Summary
Overall the codebase is well-structured with Clean Architecture layering. True duplicates are low. Findings below are ranked by severity.

---

## MEDIUM Severity

### M-01: Score Calculation Split Across Two Files
| Item | Details |
|---|---|
| **Files** | `domain/advisor/DraftScoreCalculator.kt`, `domain/scoring/DraftScorer.kt` |
| **Issue** | `DraftScoreCalculator` produces a `FinalDraftScore` (overall team score). `DraftScorer` produces per-hero `HeroScore` rankings. Names are confusingly similar ("Calculator" vs "Scorer"), leading developers to search in the wrong file. Logic is not duplicated but the naming boundary is unclear. |
| **Recommendation** | Rename `DraftScoreCalculator` → `TeamScoreEvaluator` to clearly distinguish team-level evaluation from per-hero scoring. |
| **Action** | `[RENAME_ONLY]` — no logic duplication to delete. |

### M-02: `DateFormatter` Uses Deprecated `SimpleDateFormat`
| Item | Details |
|---|---|
| **File** | `utils/DateFormatter.kt` |
| **Issue** | `SimpleDateFormat` is thread-unsafe and deprecated in favour of `java.time.format.DateTimeFormatter` (available from API 26; minSdk = 29 so no compat concern). |
| **Recommendation** | Replace with `java.time.Instant`, `LocalDateTime`, and `DateTimeFormatter`. |
| **Action** | `[REFACTOR]` |

### M-03: `HeroDto` Has Redundant `isOP` Field
| Item | Details |
|---|---|
| **Files** | `data/remote/dto/MetaSnapshotDto.kt`, `data/local/database/HeroEntity.kt`, `domain/model/Hero.kt` |
| **Issue** | `isOP` is propagated through all three layers but `tier: Tier.S_PLUS` already represents the same concept. `isToxicMechanic` is similarly overlapping with ban-rate signals. Both booleans are carried through the full stack but never appear in scoring logic or UI with independent branching. |
| **Recommendation** | Flag `[MANUAL_REVIEW_NEEDED]` — if confirmed redundant, remove both fields from DTO/Entity/Domain in a coordinated migration. Do NOT delete without test confirmation. |
| **Action** | `[MANUAL_REVIEW_NEEDED]` |

---

## LOW Severity

### L-01: Duplicate Vector Launcher Icons
| Item | Details |
|---|---|
| **Files** | `mipmap-anydpi/ic_launcher.xml`, `mipmap-anydpi-v26/ic_launcher.xml` (same for `_round`) |
| **Issue** | The `-v26` qualifier folder overrides the `anydpi` folder for API 26+; both exist but contain identical content. |
| **Recommendation** | Keep `mipmap-anydpi-v26/` only (minSdk=29 > 26), delete the `mipmap-anydpi/` directory. |
| **Action** | `[DELETE_DUPLICATE]` |

### L-02: Theme Colors Defined in Two Places
| Item | Details |
|---|---|
| **Files** | `res/values/colors.xml` (2 entries), `presentation/common/theme/Color.kt` (all brand colors) |
| **Issue** | `colors.xml` only retains `black` and `white` as placeholders. All actual theme colors live in the Kotlin `Color.kt`. The XML file is vestigial — these two entries are not referenced anywhere in Kotlin code or themes. |
| **Recommendation** | Delete or empty `colors.xml` (keep the file only if `themes.xml` references it). |
| **Action** | `[VERIFY_THEN_DELETE]` |

### L-03: `GetSuggestionsUseCase` Contains Inline Logic
| Item | Details |
|---|---|
| **File** | `domain/usecase/GetSuggestionsUseCase.kt` |
| **Issue** | This use case injects no repository and constructs `DraftScorer` output directly, duplicating the delegation pattern already in `DraftViewModel`. If the logic is non-trivial, it should delegate to `DraftScorer`; if trivial, it can be deleted and inlined into the ViewModel. |
| **Recommendation** | Audit and either delegate fully to `DraftScorer` or delete and inline. |
| **Action** | `[MANUAL_REVIEW_NEEDED]` |

---

## No Duplicates Found In:
- Activities/Fragments (2 total: `MainActivity`, `OverlayPermissionActivity` — distinct purposes)
- Use Cases (7 total — each has a distinct responsibility)
- Navigation routes (`AppRoute` sealed class — single source of truth)
- Strings (no duplicate `name` attributes found in `strings.xml`)
- Image assets (no PNG files exist — launcher uses vectors only)

