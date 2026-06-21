# [REVIEW] RESOURCE_AUDIT.md

---

## `res/values/strings.xml`

**Status: GOOD with minor gaps**

All user-facing navigation, home, score, hero pool, draft replay, settings, overlay notification, outcome, general, insights, calibration, badge, export, and accessibility strings are properly externalized.

### Hardcoded Strings Found in UI Code (NOT in strings.xml)
These literals appear directly in Kotlin/Composable source instead of `stringResource()`:

| Location | Hardcoded String |
|---|---|
| `HomeScreen.kt` | `"MLBB ASSISTANT"` |
| `HomeScreen.kt` | `"Start Draft"` |
| `HomeScreen.kt` | `"CURRENT META"` |
| `HomeScreen.kt` | `"Tap to see full tier list"` |
| `HomeScreen.kt` | `"QUICK ACTIONS"` |
| `HomeScreen.kt` | `"Hero Explorer"`, `"Meta Board"`, `"Draft History"`, `"Settings"` |
| `HomeScreen.kt` | `"TOP META HEROES"` |
| `HomeScreen.kt` | `"%.0f%% win"` |
| `HomeScreen.kt` | `"Win Rate"`, `"Sessions"`, `"Followed Recs"` |
| `DraftScreen.kt` | `"DRAFT PLANNER"` |
| `DraftScreen.kt` | `"Use the floating overlay during MLBB draft..."` |
| `DraftScreen.kt` | `"No active draft session"` |
| `DraftScreen.kt` | `"Start a draft from the Home screen..."` |
| `DraftScreen.kt` | `"YOUR TEAM"`, `"ENEMY TEAM"`, `"TOP SUGGESTIONS"` |
| `ConnectivityBanner.kt` | `"No internet connection — showing cached data"` |
| `OverlayService.kt` | `"MLBB Draft Assistant"`, `"Draft assistant is running"` |
| `AppShell.kt` | Nav item labels: `"Home"`, `"Heroes"`, `"Meta"`, `"History"`, `"Settings"`, `"Log"` |

**Recommendation:** Move all UI-visible strings to `strings.xml` for localization completeness. Priority: medium (app already has 5 locale translations).

---

## `res/values/colors.xml`

**Status: LEGACY — needs cleanup**

```xml
<color name="purple_200">#FFBB86FC</color>   <!-- UNUSED — MDC default, not referenced -->
<color name="purple_500">#FF6200EE</color>   <!-- UNUSED -->
<color name="purple_700">#FF3700B3</color>   <!-- UNUSED -->
<color name="teal_200">#FF03DAC5</color>     <!-- UNUSED -->
<color name="teal_700">#FF018786</color>     <!-- UNUSED -->
<color name="black">#FF000000</color>        <!-- Only referenced in ic_launcher_foreground -->
<color name="white">#FFFFFFFF</color>        <!-- May be used by system themes -->
```

**Recommendation:** Remove `purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700`. Keep `black` and `white` as they may be referenced by launcher drawables or the system. All app colors are defined as Compose `Color` objects in `presentation/common/theme/Color.kt` — the XML colors are dead weight from the Android Studio new-project template.

---

## `res/values/themes.xml`

**Status: GOOD — minimal and correct**

- Uses `android:Theme.Material.Light.NoActionBar` (SDK-native, no MDC dependency needed).
- Sets window background, status bar, nav bar to `#0D0D14` (matches `SurfaceDark`) to prevent white flash before Compose renders.
- `windowLightStatusBar = false` — correct for dark theme.
- Comment explains why `Theme.Material3.DayNight.NoActionBar` is NOT used (MDC not in deps). Well-documented.

**No changes needed.**

---

## Theme Consistency — `Theme.kt`

**Issue: Dynamic color (Material You) is enabled by default on API 31+**

```kotlin
fun MLBBAssistantTheme(
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    ...
)
```

On Android 12+ devices this overrides the entire custom MLBB brand color palette (gold, teal, red) with the user's wallpaper-derived colors. For a branded gaming companion app, this breaks visual identity.

**Recommendation:** Default `dynamicColor = false`. If the team wants to support Material You in the future, make it an explicit user opt-in in Settings.

---

## No `dimens.xml` Found

**Status: ACCEPTABLE**

The app uses Compose `Dp` literals throughout rather than XML dimensions. This is idiomatic for Compose-only apps. No dimens.xml is required.

---

## Summary of Issues

| Severity | Issue |
|---|---|
| Medium | ~17 hardcoded UI strings not in `strings.xml` |
| Low | 5 unused legacy XML colors from new-project template |
| Medium | Dynamic color on by default overrides brand identity |
