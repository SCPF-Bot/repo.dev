package com.example.mlbbdraftassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import me.zhanghai.compose.preference.*
import com.example.mlbbdraftassistant.util.CropRegions
import com.example.mlbbdraftassistant.util.PrefKeys

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                SettingsScreen(
                    onResetCalibration = {
                        CropRegions.saveToPrefs(
                            this@SettingsActivity,
                            CropRegions.DEFAULT_ALLY_SLOTS,
                            CropRegions.DEFAULT_ENEMY_SLOTS
                        )
                    }
                )
            }
        }
    }
}

/**
 * FIX: moved `showDisclaimer` remember state and the AlertDialog out of the
 * [LazyColumn] (LazyListScope) into a proper @Composable function.
 * Previously they were inside a `LazyListScope.item { }` lambda — a non-composable
 * scope that doesn't support `remember` or composable calls,
 * causing an "Composable invocations can only happen from the context of a
 * @Composable function" compile error.
 */
@Composable
fun SettingsScreen(onResetCalibration: () -> Unit) {
    // FIX: state lifted here, outside LazyListScope
    var showDisclaimer by remember { mutableStateOf(false) }

    ProvidePreferenceLocals {
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            preferenceCategory(
                key = "cat_general",
                title = { Text("General") }
            )

            sliderPreference(
                key = PrefKeys.OVERLAY_OPACITY,
                defaultValue = 0.85f,
                title = { Text("Overlay opacity: %.0f%%".format(it * 100)) },
                valueRange = 0.3f..1f
            )

            listPreference(
                key = PrefKeys.DETECTION_MODE,
                defaultValue = "ocr",
                values = listOf("ocr", "icon", "manual"),
                title = { value -> Text("Detection method: ${value.uppercase()}") },
                summary = { value ->
                    Text(
                        when (value) {
                            "ocr"    -> "Use Google ML Kit to read hero names"
                            "icon"   -> "Use TFLite model to recognise hero icons"
                            "manual" -> "Select heroes from dropdown lists"
                            else     -> ""
                        }
                    )
                },
                type = ListPreferenceType.ALERT_DIALOG
            )

            textFieldPreference(
                key = PrefKeys.API_ENDPOINT,
                defaultValue = "https://raw.githubusercontent.com/ridwaanhall/api-mobilelegends/main/hero.json",
                title = { Text("API endpoint") },
                textToValue = { it },
                valueToText = { it }
            )

            preferenceCategory(
                key = "cat_accessibility",
                title = { Text("Accessibility") }
            )

            switchPreference(
                key = PrefKeys.AUTO_START,
                defaultValue = false,
                title = { Text(if (it) "Auto‑start on draft" else "Auto‑start off") },
                summary = { Text("Automatically show the overlay when you enter a draft") }
            )

            switchPreference(
                key = PrefKeys.AUTO_CAPTURE,
                defaultValue = false,
                title = { Text(if (it) "Auto‑capture on" else "Auto‑capture off") },
                summary = { Text("Automatically run detection when a draft starts") }
            )

            preferenceCategory(
                key = "cat_calibration",
                title = { Text("Calibration") }
            )

            preference(
                key = "reset_calibration",
                title = { Text("Reset calibration") },
                summary = { Text("Restore default crop regions for hero names") },
                onClick = onResetCalibration
            )

            preferenceCategory(
                key = "cat_scoring",
                title = { Text("Scoring weights") }
            )

            sliderPreference(
                key = PrefKeys.WEIGHT_SYNERGY,
                defaultValue = 0.30f,
                title = { Text("Synergy weight: %.0f%%".format(it * 100)) },
                valueRange = 0f..1f
            )

            sliderPreference(
                key = PrefKeys.WEIGHT_COUNTER,
                defaultValue = 0.40f,
                title = { Text("Counter weight: %.0f%%".format(it * 100)) },
                valueRange = 0f..1f
            )

            sliderPreference(
                key = PrefKeys.WEIGHT_ROLE,
                defaultValue = 0.10f,
                title = { Text("Role balance weight: %.0f%%".format(it * 100)) },
                valueRange = 0f..1f
            )

            sliderPreference(
                key = PrefKeys.WEIGHT_META,
                defaultValue = 0.20f,
                title = { Text("Meta weight: %.0f%%".format(it * 100)) },
                valueRange = 0f..1f
            )

            preferenceCategory(
                key = "cat_about",
                title = { Text("About") }
            )

            preference(
                key = "disclaimer",
                title = { Text("Disclaimer") },
                summary = { Text("This app is not affiliated with or endorsed by Moonton.") },
                // FIX: onClick now just updates the hoisted state instead of being
                // a composable that called remember inside LazyListScope
                onClick = { showDisclaimer = true }
            )
        }
    }

    // FIX: dialog rendered outside LazyColumn, in a proper composable context
    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text("Disclaimer") },
            text = {
                Text(
                    "This app is not affiliated with or endorsed by Moonton. " +
                    "Mobile Legends: Bang Bang is a trademark of Moonton.\n\n" +
                    "All hero data comes from publicly available community APIs. " +
                    "This app does not modify or interact with the game in any way."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDisclaimer = false }) { Text("Close") }
            }
        )
    }
}
