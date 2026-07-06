package com.mlbb.assistant.presentation.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.TextDisabled

/** Merges R1 and R2 ban lists into a single display list. */
internal fun buildSlotList(r1: List<Hero?>, r2: List<Hero?>): List<Hero?> =
    if (r2.isEmpty()) r1 else r1 + r2

@Composable
internal fun BanSlotRow(allySlots: List<Hero?>, enemySlots: List<Hero?>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        SlotGroup(label = "Ally",  slots = allySlots,  filledColor = MLBBTeal)
        Box(Modifier.width(1.dp).height(16.dp).background(TextDisabled.copy(alpha = 0.3f)))
        SlotGroup(label = "Enemy", slots = enemySlots, filledColor = MLBBRed)
    }
}

@Composable
internal fun PickSlotRow(allySlots: List<Hero?>, enemySlots: List<Hero?>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        SlotGroup(label = "Ally",  slots = allySlots,  filledColor = MLBBTeal)
        Box(Modifier.width(1.dp).height(16.dp).background(TextDisabled.copy(alpha = 0.3f)))
        SlotGroup(label = "Enemy", slots = enemySlots, filledColor = MLBBRed)
    }
}

@Composable
private fun SlotGroup(label: String, slots: List<Hero?>, filledColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = TextDisabled, fontSize = 6.5.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            slots.take(5).forEach { hero -> SlotDot(hero = hero, filledColor = filledColor) }
            repeat((5 - slots.size).coerceAtLeast(0)) { SlotDot(hero = null, filledColor = filledColor) }
        }
    }
}

@Composable
private fun SlotDot(hero: Hero?, filledColor: Color) {
    val isFilled = hero != null && hero.id != -1
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (isFilled) filledColor.copy(alpha = 0.25f) else SurfaceElevated)
            .border(
                width = if (isFilled) 1.dp else 0.5.dp,
                color = if (isFilled) filledColor.copy(alpha = 0.80f) else TextDisabled.copy(0.35f),
                shape = RoundedCornerShape(2.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isFilled && hero != null) {
            Text(hero.name.take(1), color = filledColor, fontSize = 5.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}
