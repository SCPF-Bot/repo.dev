# ASSET AUDIT — Images, Vectors, Fonts, Layouts
_Generated: 2026-06-21 | Phase 1 Step 3_

## Images (PNG / WebP)
**Result:** No PNG or WebP image assets found in `res/drawable/` or `res/mipmap/`.
- Launcher icons are vector-based (`ic_launcher_background.xml`, `ic_launcher_foreground.xml`).
- Hero portraits are loaded at runtime via URL using Coil 3 (`coil-network-okhttp`).
- **No PNG → WebP conversion needed.**

## Vector Drawables
Only launcher icon vectors exist:
- `ic_launcher_background.xml` — viewportWidth/Height = 108 (standard adaptive icon size, acceptable)
- `ic_launcher_foreground.xml` — viewportWidth/Height = 108 (standard adaptive icon size, acceptable)

No oversized viewports flagged.

## Font Files
No custom font files found in `res/font/`. Typography uses system defaults via Material3 `Typography` in `Type.kt`. **No unused font overhead.**

## Raw Resource Files
- `default_heroes.json` — 116 KB (see `DATA_ASSET_AUDIT.md` for details)

## Layout Files
Project is **100% Jetpack Compose** — no XML layouts exist in `res/layout/`. No nesting inefficiencies, no `LinearLayout` or `ConstraintLayout` XML to audit.

## Hardcoded Strings
All user-visible strings are externalized in `strings.xml` (confirmed ~80 string resources). All Compose screens use `stringResource(R.string.*)`.

**Minor finding:** `OverlayService.kt` uses a hardcoded notification channel ID string `"draft_overlay_channel"`. Recommend extracting to `strings.xml` or a constants file.

## Duplicate Resources

### ⚠️ Duplicate Launcher Icon Directories (L-01)
- `mipmap-anydpi/ic_launcher.xml` — identical to `mipmap-anydpi-v26/ic_launcher.xml`
- `mipmap-anydpi/ic_launcher_round.xml` — identical to `mipmap-anydpi-v26/ic_launcher_round.xml`
- **minSdk = 29 > 26**, so `mipmap-anydpi/` (non-v26) is never selected at runtime.
- **Action:** Delete `mipmap-anydpi/` directory (keep `mipmap-anydpi-v26/` only).

### ⚠️ Vestigial `colors.xml` (L-02)
- Contains only `black` (`#FF000000`) and `white` (`#FFFFFFFF`).
- Not referenced by any Kotlin code or `themes.xml`. All brand colors live in `Color.kt`.
- **Action:** Verify `themes.xml` references, then delete or keep empty.

## Summary of APK Size Wins
| Item | Estimated Saving |
|---|---|
| Minify `default_heroes.json` | ~35-40 KB |
| Minify `draft_ui_map.json` | ~1-2 KB |
| Delete duplicate `mipmap-anydpi/` | ~2 KB |
| **Total estimated** | **~38-44 KB** |

