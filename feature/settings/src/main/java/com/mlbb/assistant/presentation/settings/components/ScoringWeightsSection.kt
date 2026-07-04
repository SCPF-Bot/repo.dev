package com.mlbb.assistant.presentation.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.settings.SettingsState
import kotlin.math.abs

@Composable
internal fun ScoringWeightsSection(
    state:            SettingsState,
    onShowReset:      () -> Unit,
    onMetaChanged:    (Float) -> Unit,
    onCounterChanged: (Float) -> Unit,
    onSynergyChanged: (Float) -> Unit
) {
    SettingsSection(icon = Icons.Rounded.GridOn, title = "SCORING WEIGHTS", subtitle = "Weights must sum to 100%") {
        val weightSum = state.metaWeight + state.counterWeight + state.synergyWeight
        val balanced  = abs(weightSum - 1f) < 0.01f

        SliderRow("Meta strength", state.metaWeight,    0f..1f, onMetaChanged)
        SectionDivider()
        SliderRow("Counter value", state.counterWeight, 0f..1f, onCounterChanged)
        SectionDivider()
        SliderRow("Synergy value", state.synergyWeight, 0f..1f, onSynergyChanged)

        AnimatedVisibility(visible = !balanced) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(ErrorRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(15.dp))
                Text(
                    "Weights sum to ${"%.0f".format(weightSum * 100)}% — adjust to reach 100%",
                    color    = ErrorRed,
                    fontSize = 12.sp
                )
            }
        }

        TextButton(onClick = onShowReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Reset to defaults", color = TextDisabled, fontSize = 12.sp)
        }
    }
}
