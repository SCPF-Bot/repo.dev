# Latest Changes Log

> This file have to be always overwritten with the latest changes. This markdown file will always have a description of the latest changes. This file should be always fresh and should only contain any changes made in current session. Freely edit and overwrite the contents below.

---

## Session changes

### 1. Crash fix — overlay service crash on open (Problem 1)

**Root cause:** `OverlayService.onCreate()` called `JetOverlay.show()` unconditionally. `show()` calls `WindowManager.addView()`, which throws `WindowManager.BadTokenException` when `SYSTEM_ALERT_WINDOW` is not granted. Because `onCreate()` runs before `onStartCommand()`, the permission guard in `onStartCommand()` never had a chance to stop the service, causing a fatal crash on app open for new installs or after permission revocation.

**Fixes applied:**
- `OverlayService.kt`: Added `Settings.canDrawOverlays(this)` guard before `JetOverlay.show()` in `onCreate()`. The service still starts (to run the permission watchdog), but the overlay window is only inflated when permission is present.
- `JetOverlay.kt`: Wrapped `wm.addView(view, params)` in `runCatching` with full state reset on failure (`composeView`, `windowManager`, `lifecycleOwner` all set to `null`). This ensures a subsequent `show()` call after permission is granted can succeed cleanly.

**Files changed:** `OverlayService.kt`, `JetOverlay.kt`

---

### 2. Developer toggle in Settings › Logs (Problems 2, 3 & 4)

**New feature:** A **LOGS** section has been added to the Settings screen containing a **Developer** toggle.

**Behaviour:**
- **Toggle ON**: persists the preference and immediately opens the Play Store listing for **Logcat Reader** (`com.dp.logcatapp`) if the app is not already installed.
  - If already installed, an "Open logger app" button replaces "Install logger app".
- **Toggle OFF**: disables the developer mode; the companion logger section collapses.
- **Fresh install default**: `developerModeEnabled` defaults to `true` in both `SettingsState` and the DataStore read path. Combined with the `MainActivity.onCreate()` install check, the companion logger install is prompted automatically on first launch without the user needing to open Settings.

**Files changed / created:**
- `utils/DevLoggerManager.kt` *(new)* — singleton managing install-check, Play Store redirect, launch, and the SharedPreferences "already prompted" flag.
- `SettingsState.kt` — added `developerModeEnabled: Boolean = true`.
- `SettingsViewModel.kt` — added `KEY_DEVELOPER_MODE`, DataStore read (default `true`), and `setDeveloperMode(Boolean)`.
- `SettingsScreen.kt` — added **LOGS** section with Developer toggle, logger status row, and install / open button. Calls `DevLoggerManager.promptInstallIfNeeded(context, force = true)` when toggled ON.
- `MainActivity.kt` — calls `DevLoggerManager.promptInstallIfNeeded(context)` in `onCreate()` to trigger the auto-install on a fresh install where developer mode is ON by default.
