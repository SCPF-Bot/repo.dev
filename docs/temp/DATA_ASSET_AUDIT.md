# DATA ASSET AUDIT
_Generated: 2026-06-21 | Phase 1 Step 4_

## Discovered Data Files

| File | Location | Size | Lines | Minified? | Referenced? |
|---|---|---|---|---|---|
| `default_heroes.json` | `res/raw/` | 116 KB | 6,377 | ❌ No | ✅ Yes (`R.raw.default_heroes` in `JsonParser.kt`) |
| `draft_ui_map.json` | `assets/` | 9.9 KB | ~200 | ❌ No | ✅ Yes (referenced by package name in `FrameProcessor` / capture layer) |

---

## Finding 1: `default_heroes.json` — Pretty-Printed (HIGH IMPACT)

**Current state:** 116 KB, 6,377 lines, fully human-formatted with indentation, newlines.

**Sample structure:**
```json
{
  "id": 1,
  "name": "Miya",
  "role": "Marksman",
  "secondaryRole": null,
  "lane": "GOLD",
  "tier": "A",
  ...
}
```

**Redundancy check:**
- `isOP` field overlaps with `tier: S+` semantics → candidate for removal (see `DUPLICATE_FEATURES_REPORT.md` M-03)
- `isToxicMechanic` overlaps with `banRate` signal → candidate for removal

**Recommendation:**
1. Minify the file (remove all whitespace/newlines) → estimated savings: **~35-40 KB** (30-35% reduction).
2. After `[MANUAL_REVIEW_NEEDED]` confirmation on M-03, remove `isOP` and `isToxicMechanic` keys from all hero objects.
3. **Do NOT convert to ProtoBuf** — the file is parsed with Gson via `JsonParser`. The parse speed is acceptable for a one-time seed load.

**Action:** `[MINIFY]`

---

## Finding 2: `draft_ui_map.json` — Has Comment Keys (MEDIUM)

**Current state:** 9.9 KB, pretty-printed, contains non-standard `_comment` and `_calibrated_from` keys used as human-readable documentation.

**Issue:** Comment keys (`_comment`, `_calibrated_from`) are parsed but likely ignored at runtime. They add ~200 bytes of waste per load.

**Recommendation:**
1. Move `_comment` and `_calibrated_from` values to a separate `docs/` file.
2. Minify the JSON file after removing comment keys.

**Action:** `[MINIFY + STRIP_COMMENT_KEYS]`

---

## Format Suitability Assessment

| Scenario | Verdict |
|---|---|
| Convert `default_heroes.json` to ProtoBuf | ❌ Not recommended — single load at install, Gson already in dependency tree, proto toolchain adds complexity |
| Convert `draft_ui_map.json` to ProtoBuf | ❌ Not recommended — small file, structure changes frequently |
| Minify both JSON files | ✅ Recommended — easy win, zero logic changes |

---

## Unused Data Files
None found. Both files have confirmed Kotlin references.

