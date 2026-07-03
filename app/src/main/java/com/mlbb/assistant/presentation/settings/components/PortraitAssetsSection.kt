package com.mlbb.assistant.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.settings.SettingsState

/**
 * Hero portrait asset pipeline controls (`hero.main.png` / `hero.pick.png` / `hero.ban.png`).
 *
 * - **Download** — fetches (or redownloads any missing) full-size CDN originals.
 * - **Optimize** — converts already-downloaded originals into the two smaller,
 *   slot-fidelity-matched variants used by [com.mlbb.assistant.capture.PortraitMatcher]
 *   for PICK/BAN slot detection.
 * - **Refresh** — deletes every cached asset, then redownloads + reoptimizes from scratch.
 */
@Composable
internal fun PortraitAssetsSection(
    state:       SettingsState,
    onDownload:  () -> Unit,
    onOptimize:  () -> Unit,
    onRefresh:   () -> Unit
) {
    InfoRow("Downloaded", "${state.portraitDownloadedCount} / ${state.portraitTotalHeroes}")
    InfoRow("Optimized (pick + ban)", "${state.portraitOptimizedCount} / ${state.portraitTotalHeroes}")

    if (state.portraitTaskRunning) {
        SectionDivider()
        Text(state.portraitTaskLabel, color = MLBBGold, fontSize = 12.sp)
        LinearProgressIndicator(
            progress = { state.portraitTaskProgress },
            color    = MLBBGold,
            modifier = Modifier.fillMaxWidth()
        )
    }

    SectionDivider()

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextButton(onClick = onDownload, enabled = !state.portraitTaskRunning) {
            Text("Download", color = if (state.portraitTaskRunning) TextDisabled else MLBBGold, fontSize = 12.sp)
        }
        TextButton(onClick = onOptimize, enabled = !state.portraitTaskRunning) {
            Text("Optimize", color = if (state.portraitTaskRunning) TextDisabled else MLBBGold, fontSize = 12.sp)
        }
        TextButton(onClick = onRefresh, enabled = !state.portraitTaskRunning) {
            Text("Refresh", color = if (state.portraitTaskRunning) TextDisabled else MLBBGold, fontSize = 12.sp)
        }
    }
}
