package com.mlbb.assistant.presentation.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber

/**
 * Fixed header bar for the mini overlay widget.
 * Contains the drag handle, title, phase label, and control buttons.
 */
@Composable
internal fun WidgetHeader(
    phaseLabel:     String,
    isDraftActive:  Boolean,
    onMinimize:     () -> Unit,
    onClose:        () -> Unit,
    onRestartDraft: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Left: drag handle + title + phase
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            DragHandle()
            Text(
                "MLBB",
                color      = MLBBGold,
                fontWeight = FontWeight.Bold,
                fontSize   = 10.sp
            )
            Text(
                "DRAFT",
                color      = MLBBGold.copy(alpha = 0.6f),
                fontSize   = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (phaseLabel.isNotEmpty() && phaseLabel != "STANDBY") {
                Text(
                    "· $phaseLabel",
                    color    = TextSecondary,
                    fontSize = 8.sp
                )
            }
        }

        // Right: restart (when active) + minimize + close
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (isDraftActive) {
                WidgetIconBtn(
                    label   = "↺",
                    color   = WarningAmber,
                    onClick = onRestartDraft
                )
            }
            WidgetIconBtn(label = "—", color = TextSecondary, onClick = onMinimize)
            WidgetIconBtn(label = "✕", color = ErrorRed,      onClick = onClose)
        }
    }
}

@Composable
private fun DragHandle() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) {
                    Box(
                        Modifier
                            .size(2.5.dp)
                            .clip(CircleShape)
                            .background(TextDisabled)
                    )
                }
            }
        }
    }
}

/** 26×26 dp header icon button. */
@Composable
internal fun WidgetIconBtn(
    label:   String,
    color:   Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(26.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
