# OFFICIAL TEMPLATES — Canonical Patterns Reference
_Generated: 2026-06-21 | Phase 2_
_Source constraint: developer.android.com, kotlinlang.org (2024–2026)_

---

## T-01: java.time DateTimeFormatter (replaces SimpleDateFormat)
**Source:** https://kotlinlang.org/api/latest/jvm/stdlib/ + Android API 26+ (minSdk=29 ✅)
**Replaces:** `DateFormatter.kt` usage of `SimpleDateFormat`

```kotlin
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Thread-safe; create once as a constant
private val ABSOLUTE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val FULL_FORMATTER      = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault())
private val TIME_FORMATTER      = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun formatAbsolute(timestampMs: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault())
        .format(ABSOLUTE_FORMATTER)
```

---

## T-02: Composable Extraction Pattern (large file splitting)
**Source:** https://developer.android.com/jetpack/compose/composables-best-practices (2025)

Rule: Each Composable should do one thing. If a file exceeds ~300 lines, extract private composable
functions into their own files in a `components/` sub-package.

```kotlin
// Before (monolithic):
@Composable
fun SettingsScreen(...) {
    // 1000 lines of mixed concerns
}

// After (extracted):
// presentation/settings/components/PermissionSection.kt
@Composable
internal fun PermissionSection(state: SettingsState, onAction: (SettingsAction) -> Unit) { ... }

// presentation/settings/components/CalibrationSection.kt
@Composable
internal fun CalibrationSection(state: SettingsState, onAction: (SettingsAction) -> Unit) { ... }

// presentation/settings/SettingsScreen.kt (~100 lines)
@Composable
fun SettingsScreen(...) {
    Column {
        PermissionSection(state, onAction)
        CalibrationSection(state, onAction)
        // ...
    }
}
```

---

## T-03: JSON Minification (data asset optimization)
**Source:** https://developer.android.com/topic/performance/reduce-apk-size (2025)

Minified JSON has no whitespace or newlines. Valid syntax is preserved.
Tool: `python3 -c "import json,sys; print(json.dumps(json.load(sys.stdin), separators=(',', ':')))" < input.json > output.json`

For JSON files with comment keys (`_comment`), strip before minifying:
```python
import json
with open('input.json') as f:
    data = json.load(f)
# Remove comment keys at top level
data = {k: v for k, v in data.items() if not k.startswith('_')}
print(json.dumps(data, separators=(',', ':')))
```

---

## T-04: Notification Channel ID Constant
**Source:** https://developer.android.com/develop/ui/views/notifications/channels (2024)

```kotlin
// In a constants file (e.g., AppConstants.kt)
object AppConstants {
    const val OVERLAY_NOTIFICATION_CHANNEL_ID = "draft_overlay_channel"
    const val OVERLAY_NOTIFICATION_ID = 1001
}
```

---

## T-05: Kotlin collectAsStateWithLifecycle (already used — document for consistency)
**Source:** https://developer.android.com/jetpack/compose/libraries#lifecycle-runtime-ktx (2025)

```kotlin
// Correct pattern (already used in project — confirming compliance)
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

---

## T-06: Room Paging (already used — confirming compliance)
**Source:** https://developer.android.com/topic/libraries/architecture/paging/v3-paged-data (2025)

The project correctly uses `room-paging` + `paging-compose` for hero grid. No changes needed.

