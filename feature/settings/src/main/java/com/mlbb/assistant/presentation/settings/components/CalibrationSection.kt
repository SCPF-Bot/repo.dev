package com.mlbb.assistant.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.R
import com.mlbb.assistant.domain.engine.WeightCalibrator
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary

@Composable
internal fun CalibrationSection(
    result:        WeightCalibrator.CalibrationResult?,
    isCalibrating: Boolean,
    onRefresh:     () -> Unit,
    onApply:       () -> Unit
) {
    SettingsSection(icon = Icons.Rounded.Info, title = stringResource(R.string.calibration_title)) {
        when {
            isCalibrating -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = MLBBGold, modifier = Modifier.size(22.dp))
                }
            }
            result == null -> {
                Text(stringResource(R.string.calibration_need_more), color = TextSecondary, fontSize = 12.sp)
            }
            else -> {
                Text(stringResource(R.string.calibration_rationale_label), color = MLBBGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(result.rationale, color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                SectionDivider()
                Spacer(Modifier.height(6.dp))
                val confPct  = (result.confidence * 100).toInt()
                val confDesc = "${stringResource(R.string.calibration_confidence_label)}: $confPct%"
                Text(confDesc, color = TextSecondary, fontSize = 11.sp)
                LinearProgressIndicator(
                    progress   = { result.confidence },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = confDesc },
                    color      = MLBBTeal,
                    trackColor = SurfaceElevated
                )
                val sw = result.suggestedWeights
                Text(
                    "Suggested — Meta ${"%.0f".format(sw.meta * 100)}%  Counter ${"%.0f".format(sw.counter * 100)}%  Synergy ${"%.0f".format(sw.synergy * 100)}%",
                    color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onRefresh) { Text("Refresh", color = TextDisabled, fontSize = 12.sp) }
                    TextButton(onClick = onApply) { Text(stringResource(R.string.calibration_apply), color = MLBBGold, fontSize = 12.sp) }
                }
            }
        }
    }
}
