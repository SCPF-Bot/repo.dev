package com.example.mlbbdraftassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.mlbbdraftassistant.util.CropRegions
import com.example.mlbbdraftassistant.util.PrefKeys
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.sliderPreference
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ProvidePreferenceLocals {
                    var showDisclaimer by remember { mutableStateOf(false) }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {

                        // ── General ──
                        preferenceCategory(key = "cat_general") {
                            title = { Text("General") }
                        }

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
                                        "ocr" -> "Use Google ML Kit to read hero names"
                                        "icon" -> "Use TFLite model to recognise hero icons"
                                        "manual" -> "Select heroes from dropdown lists"
                                        else -> ""
                                    }
                                )
                            },
                            type = me.zhanghai.compose.preference.ListPreferenceType.ALERT_DIALOG
                        )

                        textFieldPreference(
                            key = PrefKeys.API_ENDPOINT,
                            defaultValue = "https://raw.githubusercontent.com/ridwaanhall/api-mobilelegends/main/hero.json",
                            title = { Text("API endpoint") },
                            textToValue = { it },
                            valueToText = { it }
                        )

                        // ── Accessibility ──
                        preferenceCategory(key = "cat_accessibility") {
                            title = { Text("Accessibility") }
                        }

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

                        // ── Calibration ──
                        preferenceCategory(key = "cat_calibration") {
                            title = { Text("Calibration") }
                        }

                        preference(
                            key = "reset_calibration",
                            title = { Text("Reset calibration") },
                            summary = { Text("Restore default crop regions for hero names") },
                            onClick = {
                                CropRegions.saveToPrefs(
                                    this@SettingsActivity,
                                    CropRegions.DEFAULT_ALLY_SLOTS,
                                    CropRegions.DEFAULT_ENEMY_SLOTS
                                )
                            }
                        )

                        // ── Scoring weights ──
                        preferenceCategory(key = "cat_scoring") {
                            title = { Text("Scoring weights") }
                        }

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

                        // ── About ──
                        preferenceCategory(key = "cat_about") {
                            title = { Text("About") }
                        }

                        // Tappable disclaimer that shows a dialog
                        preference(
                            key = "disclaimer",
                            title = { Text("Disclaimer") },
                            summary = { Text("This app is not affiliated with or endorsed by Moonton.") },
                            onClick = { showDisclaimer = true }
                        )
                    }

                    // Full disclaimer dialog
                    if (showDisclaimer) {
                        AlertDialog(
                            onDismissRequest = { showDisclaimer = false },
                            title = { Text("Disclaimer", fontWeight = FontWeight.Bold) },
                            text = {
                                Text(
                                    "This app is not affiliated with or endorsed by Moonton. " +
                                    "Mobile Legends: Bang Bang is a trademark of Moonton.\n\n" +
                                    "All hero data comes from publicly available community APIs. " +
                                    "This app does not modify or interact with the game in any way."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { showDisclaimer = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}