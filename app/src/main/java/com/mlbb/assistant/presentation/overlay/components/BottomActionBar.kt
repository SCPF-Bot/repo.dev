package com.mlbb.assistant.presentation.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber

/**
 * Fixed bottom bar shown when a draft session is active.
 *
 * Contains Minimize, Undo, Score toggle, and an optional manual Next Phase
 * button whose label is supplied by the caller (e.g. "→ PICK", "→ BAN R2").
 * The next-phase button is hidden when [nextPhaseLabel] is null.
 */
@Composable
internal fun BottomActionBar(
    canUndo:        Boolean,
    scoreActive:    Boolean,
    nextPhaseLabel: String?,
    onMinimize:     () -> Unit,
    onUndo:         () -> Unit,
    onScore:        () -> Unit,
    onNextPhase:    () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ActionBarBtn("⏹ Min",  TextSecondary,                                    Modifier.weight(1f), onMinimize)
        ActionBarBtn("↩ Undo", if (canUndo) WarningAmber else TextDisabled,      Modifier.weight(1f)) { if (canUndo) onUndo() }
        ActionBarBtn(
            label    = if (scoreActive) "📊 Hide" else "📊 Score",
            color    = if (scoreActive) MLBBGold  else MLBBTeal,
            modifier = Modifier.weight(1f),
            onClick  = onScore
        )
        if (nextPhaseLabel != null) {
            ActionBarBtn(
                label    = nextPhaseLabel,
                color    = WarningAmber,
                modifier = Modifier.weight(1f),
                onClick  = onNextPhase
            )
        }
    }
}

@Composable
internal fun ActionBarBtn(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color    = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis
        )
    }
}
