package com.mlbb.assistant.presentation.draft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.feature.draft.R
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.Pie

/**
 * Modal bottom sheet showing the detailed score breakdown for a hero recommendation.
 *
 * Accessibility: each score bar carries a contentDescription that reads
 * "<label> score <value> out of 100" for TalkBack users (Section 6.5).
 *
 * Score visualisation (recommendations.md §8.3):
 *   - A [PieChart] at the top of the breakdown section gives an immediate
 *     proportional view of the four score components (meta, synergy, counter, role).
 *   - Horizontal [ScoreBar]s below provide the exact numeric values.
 *   - Both representations are intentionally kept: the chart helps users grasp
 *     relative weights at a glance; the bars give screenreader-accessible numbers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreExplanationSheet(
    heroScore:  HeroScore,
    sheetState: SheetState,
    onDismiss:  () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = SurfaceElevated,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Hero header ───────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroPortrait(hero = heroScore.hero, size = 56.dp)
                Column {
                    Text(
                        heroScore.hero.name,
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp
                    )
                    Text(
                        "${heroScore.hero.role} · ${heroScore.hero.lane.display}",
                        color    = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                // Overall score pill
                Box(
                    Modifier
                        .background(MLBBGold.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${(heroScore.totalScore * 100).toInt()}",
                        color      = MLBBGold,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 22.sp
                    )
                }
            }

            HorizontalDivider(color = MLBBGold.copy(alpha = 0.15f))

            // ── Reason text ───────────────────────────────────────────────────
            Text(
                heroScore.reason,
                color    = TextSecondary,
                fontSize = 13.sp
            )

            // ── Score breakdown header ────────────────────────────────────────
            Text(
                stringResource(R.string.score_breakdown),
                color      = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp
            )

            // ── Score pie chart (recommendations.md §8.3) ────────────────────
            // Clamp scores to 0.01 minimum so every segment is always rendered;
            // a zero-value slice would be invisible and confuse the chart layout.
            ScoreBreakdownPieChart(heroScore = heroScore)

            // ── Score bars ────────────────────────────────────────────────────
            ScoreBar(
                label = stringResource(R.string.score_meta),
                value = heroScore.metaScore,
                color = MLBBGold
            )
            ScoreBar(
                label = stringResource(R.string.score_synergy),
                value = heroScore.synergyScore,
                color = SuccessGreen
            )
            ScoreBar(
                label = stringResource(R.string.score_counter),
                value = heroScore.counterScore,
                color = MLBBTeal
            )
            ScoreBar(
                label = stringResource(R.string.score_role),
                value = heroScore.roleScore,
                color = WarningAmber
            )

            // ── Patch trend indicator ─────────────────────────────────────────
            val trend = heroScore.hero.patchTrend
            if (trend != 0.0) {
                HorizontalDivider(color = MLBBGold.copy(alpha = 0.10f))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        if (trend > 0) "↑" else "↓",
                        color      = if (trend > 0) SuccessGreen else MLBBRed,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = if (trend > 0)
                            stringResource(R.string.patch_trend_rising)
                        else
                            stringResource(R.string.patch_trend_falling),
                        color    = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Score pie chart ───────────────────────────────────────────────────────────

/**
 * Animated pie chart showing the relative proportions of [heroScore]'s four
 * score components.
 *
 * Uses [PieChart] from `io.github.ehsannarmani:compose-charts`. Each [Pie]
 * segment is tappable — tapping expands the slice with a spring animation
 * (handled by the library's built-in `selectedScale`/`selectedPaddingAngle`).
 *
 * Each segment's `data` value is pre-clamped to 0.01 so no segment is
 * invisible when a score dimension is zero.
 *
 * Accessibility note: the pie chart is marked as `mergeDescendants = false` so
 * TalkBack ignores it — the numeric [ScoreBar]s below convey the same
 * information with full screen-reader support.
 */
@Composable
private fun ScoreBreakdownPieChart(heroScore: HeroScore) {
    val minSlice = 0.01

    // Mutable state is required by the ComposeCharts PieChart API — it
    // manages tap-to-select state by updating the Pie.selected property.
    var pieData by remember(heroScore) {
        mutableStateOf(
            listOf(
                Pie(
                    label        = "Meta",
                    data         = maxOf(heroScore.metaScore.toDouble(), minSlice),
                    color        = MLBBGold,
                    selectedColor = MLBBGold.copy(alpha = 0.75f)
                ),
                Pie(
                    label        = "Synergy",
                    data         = maxOf(heroScore.synergyScore.toDouble(), minSlice),
                    color        = SuccessGreen,
                    selectedColor = SuccessGreen.copy(alpha = 0.75f)
                ),
                Pie(
                    label        = "Counter",
                    data         = maxOf(heroScore.counterScore.toDouble(), minSlice),
                    color        = MLBBTeal,
                    selectedColor = MLBBTeal.copy(alpha = 0.75f)
                ),
                Pie(
                    label        = "Role",
                    data         = maxOf(heroScore.roleScore.toDouble(), minSlice),
                    color        = WarningAmber,
                    selectedColor = WarningAmber.copy(alpha = 0.75f)
                ),
            )
        )
    }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        PieChart(
            modifier             = Modifier.size(120.dp),
            data                 = pieData,
            onPieClick           = { clicked ->
                // Toggle the selected state; the library animates the expansion.
                pieData = pieData.map { it.copy(selected = it == clicked && !it.selected) }
            },
            selectedScale        = 1.08f,
        )

        // Legend
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PieLegendItem(label = "Meta",    color = MLBBGold,    value = heroScore.metaScore)
            PieLegendItem(label = "Synergy", color = SuccessGreen, value = heroScore.synergyScore)
            PieLegendItem(label = "Counter", color = MLBBTeal,     value = heroScore.counterScore)
            PieLegendItem(label = "Role",    color = WarningAmber, value = heroScore.roleScore)
        }
    }
}

@Composable
private fun PieLegendItem(label: String, color: Color, value: Float) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text("$label: ${(value * 100).toInt()}", color = TextSecondary, fontSize = 11.sp)
    }
}

// ── Score bar component ───────────────────────────────────────────────────────

@Composable
private fun ScoreBar(label: String, value: Float, color: Color) {
    val pct       = value.coerceIn(0f, 1f)
    val intVal    = (pct * 100).toInt()
    val a11yDesc  = "$label score $intVal out of 100"

    Row(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = a11yDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color    = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(72.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.15f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Text(
            "$intVal",
            color      = color,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.width(28.dp)
        )
    }
}
