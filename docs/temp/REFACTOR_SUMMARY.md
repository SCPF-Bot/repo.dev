# Refactor Summary — MLBB Draft Assistant

_Generated after Phase 3 (MODIFY) of the REVIEW → PLAN → MODIFY → VALIDATE overhaul._

---

## Scope

All changes are **in-place refinements** of the existing Kotlin/Compose codebase.
No new features were added and no primary business logic was rewritten.

---

## Changes by Category

### 1. Data Asset Minification (APK Size)

| File | Before | After | Reduction |
|---|---|---|---|
| `res/raw/default_heroes.json` | 116 KB (pretty-printed) | 74 KB (minified) | **−36%** |
| `app/src/main/assets/draft_ui_map.json` | 9.9 KB (had `_comment` noise) | 7.3 KB (comments stripped, minified) | **−26%** |

**Method:** Node.js one-liner to parse → re-serialise without whitespace.
`draft_ui_map.json` additionally had all `_comment` keys removed before minification.

---

### 2. Dead Resource Elimination

| Artefact | Action | Reason |
|---|---|---|
| `mipmap-anydpi/` (duplicate launcher icon set) | **Deleted** | Exact copy of `mipmap-anydpi-v26/`. `minSdk=29 ≥ 26`, so the `-v26` variant is always selected. Retaining the unqualified copy caused duplicate-resource lint warnings. |

---

### 3. Thread-safety Fix — `DateFormatter.kt`

**Before:** `SimpleDateFormat` stored as a `companion object` field.
`SimpleDateFormat` is not thread-safe; concurrent calls from multiple coroutines / ViewModels could produce garbled output or throw `NumberFormatException`.

**After:** Replaced with `java.time.DateTimeFormatter` (immutable, thread-safe, API 26+ which is below `minSdk=29`).
The object now exposes `formatTimestamp(epochMillis: Long): String` and
`formatDayLabel(epochMillis: Long): String` via `DateTimeFormatter.ofPattern(…, Locale.getDefault())`.

---

### 4. Constant Centralisation — `AppConstants.kt`

Created `utils/AppConstants.kt` as the single source of truth for magic values that were scattered inline:

```kotlin
object AppConstants {
    const val OVERLAY_NOTIFICATION_CHANNEL_ID = "draft_overlay_channel"
    const val OVERLAY_NOTIFICATION_ID         = 1001
}
```

Any future `OverlayService` notification calls should reference these constants instead of string/int literals.

---

### 5. Large-file Splitting (Maintainability)

Both files were over 1 000 lines and contained multiple unrelated concerns.
All new component files use `internal` visibility, keeping them accessible within the same package without leaking into unrelated modules.

#### 5a. `MiniWidget.kt` (1 203 → 672 lines)

| New File | Package | Lines | Contents |
|---|---|---|---|
| `overlay/components/WidgetHeaderBar.kt` | `…overlay.components` | 127 | `WidgetHeader`, `DragHandle`, `WidgetIconBtn` |
| `overlay/components/WidgetScorePanel.kt` | `…overlay.components` | 239 | `WidgetScorePanel`, `ScoreSection`, `ScoreLine`, `ScoreLinePair`, colour helpers |
| `overlay/MiniWidget.kt` _(trimmed)_ | `…overlay` | 672 | Orchestrator + `ActiveDraftBody`, `PhasePanel`, slot rows, ban/pick chips, `IdleBody`, `CompleteBody` |

#### 5b. `SettingsScreen.kt` (1 061 → 492 lines)

| New File | Package | Lines | Contents |
|---|---|---|---|
| `settings/components/ScreenMappingDialog.kt` | `…settings.components` | 251 | `ScreenMappingDialog`, `MappedPoint`, `parseMappedPoints`, `serializeMappedPoints` |
| `settings/components/SettingsPrimitives.kt` | `…settings.components` | 209 | `SettingsSection`, `SectionDivider`, `SliderRow`, `ToggleRow`, `InfoRow`, `PermissionRow` |
| `settings/SettingsScreen.kt` _(trimmed)_ | `…settings` | 492 | Orchestrator + `ScoringWeightsSection`, `CalibrationSection`, `BanCountRow`, `BanPhaseScreenshotSection` |

---

## Files Modified / Created / Deleted

| Status | Path |
|---|---|
| **Modified** | `app/src/main/res/raw/default_heroes.json` |
| **Modified** | `app/src/main/assets/draft_ui_map.json` |
| **Modified** | `app/src/main/java/…/utils/DateFormatter.kt` |
| **Modified** | `app/src/main/java/…/presentation/overlay/MiniWidget.kt` |
| **Modified** | `app/src/main/java/…/presentation/settings/SettingsScreen.kt` |
| **Created** | `app/src/main/java/…/utils/AppConstants.kt` |
| **Created** | `app/src/main/java/…/presentation/overlay/components/WidgetHeaderBar.kt` |
| **Created** | `app/src/main/java/…/presentation/overlay/components/WidgetScorePanel.kt` |
| **Created** | `app/src/main/java/…/presentation/settings/components/ScreenMappingDialog.kt` |
| **Created** | `app/src/main/java/…/presentation/settings/components/SettingsPrimitives.kt` |
| **Deleted** | `app/src/main/res/mipmap-anydpi/` (duplicate launcher icon set) |

---

## Non-Functional Invariants Preserved

- Zero changes to domain layer (`domain/`) — it remains Android-free.
- All DI wiring (`AppModule`, Hilt) untouched.
- `VoiceAlertService` is live code (confirmed injected) — not removed.
- All StateFlow / ViewModel patterns preserved.
- Navigation graph and public API surfaces (`MiniWidget`, `SettingsScreen`) unchanged.
- `minSdk=29`, `compileSdk=36`, AGP 8.10.1, Kotlin 2.1.0, Compose BOM 2025.05.01 — no dependency changes.

---

## Recommended Follow-Up (Out of Scope for this Pass)

1. **Update `OverlayService`** to import `AppConstants.OVERLAY_NOTIFICATION_CHANNEL_ID` / `OVERLAY_NOTIFICATION_ID` wherever notification channel strings/IDs are hardcoded.
2. **WebP conversion** — no PNG assets are currently >100 KB, but run `cwebp` on any future icon additions.
3. **ProtoBuffer migration** for `default_heroes.json` if the hero roster grows beyond 200 entries (would cut size a further ~30–40% and eliminate JSON parsing overhead at runtime).
4. **Extract `BanPhaseScreenshotSection`** into its own file if `SettingsScreen.kt` creeps back over 500 lines.
