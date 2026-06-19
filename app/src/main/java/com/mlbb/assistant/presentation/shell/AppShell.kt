package com.mlbb.assistant.presentation.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.data.local.preferences.WizardPreference
import com.mlbb.assistant.presentation.navigation.AppNavGraph

/**
 * Root Composable. Reads the wizard-completion flag from [WizardPreference]
 * (DataStore-backed) and hands off to [AppNavGraph].
 *
 * Removed: the previous implementation read `wizard_done` from
 * [android.content.SharedPreferences] inside a `remember {}` block — a
 * synchronous disk read on the main thread that mixed two persistence layers.
 * Now sourced via [produceState] so the read is async and the UI never blocks.
 */
@Composable
fun AppShell() {
    val context = LocalContext.current

    val wizardDone by produceState(initialValue = false) {
        WizardPreference.observe(context).collect { value = it }
    }

    AppNavGraph(wizardCompleted = wizardDone)
}
