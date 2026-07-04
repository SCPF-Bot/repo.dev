package com.mlbb.assistant.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable  // Pass 1: was missing; clickable is a Modifier extension, not callable as a free function
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.advisor.BuildAdvice
import com.mlbb.assistant.domain.advisor.FinalDraftScore
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun FinalReportContent(
    draftScore: FinalDraftScore,
    buildAdvice: BuildAdvice?,
    onNewDraft: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OverlayBackground, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("📊 FINAL DRAFT REPORT", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ScoreBadge(score = draftScore.overall)
        }

        // Overall scores
        ScoreRow("Meta Adherence",    draftScore.metaAdherence)
        ScoreRow("Counter Efficiency", draftScore.counterEfficiency)
        ScoreRow("Synergy Strength",   draftScore.synergyStrength)

        Divider()

        // Team strengths
        if (draftScore.teamStrengths.isNotEmpty()) {
            SectionTitle("YOUR TEAM STRENGTHS")
            draftScore.teamStrengths.forEach { Text(it, color = SuccessGreen, fontSize = 11.sp) }
        }

        // Team weaknesses
        if (draftScore.teamWeaknesses.isNotEmpty()) {
            SectionTitle("YOUR TEAM WEAKNESSES")
            draftScore.teamWeaknesses.forEach { Text(it, color = WarningAmber, fontSize = 11.sp) }
        }

        // Damage split
        SectionTitle("DAMAGE DISTRIBUTION")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Phys ${draftScore.damagePhysicalPct}%", color = MLBBRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { draftScore.damagePhysicalPct / 100f },
                modifier = Modifier.weight(1f).height(6.dp),
                color = MLBBRed, trackColor = SurfaceElevated
            )
            Text("Magic ${draftScore.damageMagicPct}%", color = RoleColorMage, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        // CC / Sustain / Mobility
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatPill("CC",       draftScore.ccRating)
            StatPill("Sustain",  draftScore.sustainRating)
            StatPill("Mobility", draftScore.mobilityRating)
        }

        Divider()

        // Enemy threats
        if (draftScore.enemyThreats.isNotEmpty()) {
            SectionTitle("ENEMY THREAT ASSESSMENT")
            draftScore.enemyThreats.forEach { Text(it, color = TextPrimary, fontSize = 11.sp) }
        }

        // Build advice
        buildAdvice?.let { advice ->
            Divider()
            SectionTitle("BATTLE SPELL")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpellPill(spell = advice.battleSpell, primary = true)
                Text("or", color = TextDisabled, fontSize = 10.sp)
                SpellPill(spell = advice.altSpell, primary = false)
            }
            Text(advice.spellReason, color = TextSecondary, fontSize = 10.sp)

            SectionTitle("CORE ITEMS — BUILD ORDER")
            advice.coreItems.forEachIndexed { i, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(MLBBGold.copy(alpha = 0.20f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("${i + 1}", color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    Text(item.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                if (i < advice.itemReasons.size) {
                    Text("   ${advice.itemReasons.getOrElse(i) { "" }}", color = TextSecondary, fontSize = 10.sp)
                }
            }

            SectionTitle("MACRO STRATEGY")
            advice.macroTips.take(3).forEach { tip ->
                Text("• $tip", color = TextPrimary, fontSize = 11.sp)
            }
        }

        // Actions
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Start New Draft", MLBBGold,   Modifier.weight(1f)) { onNewDraft() }
            ActionButton("Close",           TextDisabled, Modifier.weight(1f)) { onClose()    }
        }
    }
}

@Composable private fun Divider() =
    Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceElevated))

@Composable private fun SectionTitle(title: String) =
    Text(title, color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)

@Composable
private fun ScoreBadge(score: Int) {
    val color = when {
        score >= 80 -> SuccessGreen
        score >= 60 -> WarningAmber
        else        -> ErrorRed
    }
    Box(
        Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("$score / 100", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun ScoreRow(label: String, score: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.width(130.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.weight(1f).height(5.dp),
            color = MLBBGold, trackColor = SurfaceElevated
        )
        Text("$score%", color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Box(
        Modifier
            .background(SurfaceElevated, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextDisabled, fontSize = 8.sp)
            Text(value,  color = TextPrimary,  fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpellPill(spell: String, primary: Boolean) {
    Box(
        Modifier
            .background(if (primary) MLBBGold.copy(alpha = 0.20f) else SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, if (primary) MLBBGold.copy(alpha = 0.50f) else SurfaceElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(spell, color = if (primary) MLBBGold else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionButton(label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
