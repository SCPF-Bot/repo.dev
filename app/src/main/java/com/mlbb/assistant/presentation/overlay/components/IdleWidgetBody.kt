package com.mlbb.assistant.presentation.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextSecondary

// ── Idle body (draft not started) ─────────────────────────────────────────────

@Composable
internal fun IdleBody(session: DraftSession, onStartDraft: (Boolean) -> Unit) {
    var ourTeamFirst by remember { mutableStateOf(true) }

    Column(
        modifier            = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMid, RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Waiting for draft to begin…", color = TextSecondary, fontSize = 9.sp)
        }
        Text(
            "WHO PICKS FIRST?",
            color    = MLBBGold,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TeamToggleBtn("🔵", "ALLY",  ourTeamFirst,  MLBBTeal, Modifier.weight(1f)) { ourTeamFirst = true  }
            TeamToggleBtn("🔴", "ENEMY", !ourTeamFirst, ErrorRed,  Modifier.weight(1f)) { ourTeamFirst = false }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MLBBGold.copy(alpha = 0.15f), RoundedCornerShape(7.dp))
                .border(1.5.dp, MLBBGold.copy(alpha = 0.70f), RoundedCornerShape(7.dp))
                .clickable { onStartDraft(ourTeamFirst) }
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("▶  START DRAFT", color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TeamToggleBtn(
    emoji:         String,
    label:         String,
    isSelected:    Boolean,
    selectedColor: Color,
    modifier:      Modifier,
    onClick:       () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) selectedColor.copy(alpha = 0.22f) else SurfaceMid,
                RoundedCornerShape(7.dp)
            )
            .border(
                if (isSelected) 1.5.dp else 0.5.dp,
                if (isSelected) selectedColor else TextSecondary.copy(alpha = 0.4f),
                RoundedCornerShape(7.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(emoji, fontSize = 13.sp)
            Text(
                label,
                color      = if (isSelected) selectedColor else TextSecondary,
                fontSize   = 8.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign  = TextAlign.Center
            )
        }
    }
}

// ── Complete body (draft finished) ────────────────────────────────────────────

@Composable
internal fun CompleteBody(onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("Draft complete ✅", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Box(
                Modifier
                    .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.40f), RoundedCornerShape(5.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text("Close overlay", color = SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
