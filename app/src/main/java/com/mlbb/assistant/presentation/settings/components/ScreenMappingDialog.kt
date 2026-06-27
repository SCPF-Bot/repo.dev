package com.mlbb.assistant.presentation.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import kotlin.math.sqrt

/**
 * Full-screen mapping tool for tapping hero portrait positions onto a ban-phase screenshot.
 *
 * Capabilities:
 * - **Templates** — pre-populate standard 3+3 or 5+5 ban layouts in one tap.
 * - **Guided tapping** — instruction bar directs the user to the next slot; A/E toggle
 *   switches active team at any time.
 * - **Drag to reposition** — long-press any marker to drag it to the exact pixel.
 * - **Color-coded labels** — ally markers are teal (A1, A2…), enemy are red (E1, E2…).
 * - **Backward-compatible JSON** — round-trips correctly with older JSON lacking "team".
 *
 * Data model: [MappedPoint] / serialisation: [MappingSlotModel]
 * Small UI chips: [MappingDialogComponents]
 */
@Composable
internal fun ScreenMappingDialog(
    screenshotUri:  String,
    initialMapping: String,
    onDismiss:      () -> Unit,
    onSave:         (String) -> Unit
) {
    val initialPoints = remember(initialMapping) { parseMappedPoints(initialMapping) }
    val slots = remember { mutableStateListOf<MappedPoint>().also { it.addAll(initialPoints) } }

    var activeTeam   by remember { mutableStateOf(SlotTeam.ALLY) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }

    val allyCount  = slots.count { it.team == SlotTeam.ALLY  }
    val enemyCount = slots.count { it.team == SlotTeam.ENEMY }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = false
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(SurfaceDark)
        ) {

            // ── Header ─────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceMid)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Map Portrait Positions",
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp
                    )
                    Text(
                        "${allyCount}A + ${enemyCount}E mapped  •  long-press to drag",
                        color    = if (slots.isEmpty()) TextDisabled else MLBBTeal,
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = { onSave(serializeMappedPoints(slots)) },
                    enabled = slots.isNotEmpty()
                ) {
                    Icon(
                        Icons.Rounded.Done,
                        contentDescription = "Save",
                        tint = if (slots.isNotEmpty()) MLBBGold else TextDisabled
                    )
                }
            }

            // ── Template row ───────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.GridOn,
                    contentDescription = null,
                    tint     = TextDisabled,
                    modifier = Modifier.size(14.dp)
                )
                Text("Templates:", color = TextSecondary, fontSize = 11.sp)
                TemplateChip("3+3 Bans") { slots.clear(); slots.addAll(TEMPLATE_3_PLUS_3) }
                TemplateChip("5+5 Bans") { slots.clear(); slots.addAll(TEMPLATE_5_PLUS_5) }
                Spacer(Modifier.weight(1f))
                Text(
                    "Fine-tune by dragging",
                    color     = TextDisabled,
                    fontSize  = 9.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }

            // ── Guided instruction bar ─────────────────────────────────────
            val activeColor = if (activeTeam == SlotTeam.ALLY) MLBBTeal else MLBBRed
            val guideText   = if (activeTeam == SlotTeam.ALLY)
                "Tap to place ALLY ban slot A${allyCount + 1}"
            else
                "Tap to place ENEMY ban slot E${enemyCount + 1}"

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(activeColor.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint     = activeColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(guideText, color = activeColor, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Row(
                    Modifier
                        .background(SurfaceMid, RoundedCornerShape(20.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TeamToggle("A", activeTeam == SlotTeam.ALLY,  MLBBTeal) { activeTeam = SlotTeam.ALLY  }
                    TeamToggle("E", activeTeam == SlotTeam.ENEMY, MLBBRed)  { activeTeam = SlotTeam.ENEMY }
                }
            }

            // ── Screenshot canvas ──────────────────────────────────────────
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    // Tap: add or remove marker
                    .pointerInput(slots.size, activeTeam) {
                        detectTapGestures { tap ->
                            val removeIdx = slots.indexOfFirst { p ->
                                val px = p.x * size.width
                                val py = p.y * size.height
                                sqrt(
                                    (tap.x - px) * (tap.x - px) +
                                    (tap.y - py) * (tap.y - py)
                                ) < HIT_RADIUS_PX
                            }
                            if (removeIdx >= 0) {
                                slots.removeAt(removeIdx)
                            } else {
                                slots.add(
                                    MappedPoint(
                                        x    = (tap.x / size.width).coerceIn(0f, 1f),
                                        y    = (tap.y / size.height).coerceIn(0f, 1f),
                                        team = activeTeam
                                    )
                                )
                            }
                        }
                    }
                    // Long-press + drag: reposition existing marker
                    .pointerInput(slots.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startOffset ->
                                draggedIndex = slots.indices.minByOrNull { i ->
                                    val p  = slots[i]
                                    val px = p.x * size.width
                                    val py = p.y * size.height
                                    sqrt(
                                        (startOffset.x - px) * (startOffset.x - px) +
                                        (startOffset.y - py) * (startOffset.y - py)
                                    )
                                }?.takeIf { i ->
                                    val p  = slots[i]
                                    val px = p.x * size.width
                                    val py = p.y * size.height
                                    sqrt(
                                        (startOffset.x - px) * (startOffset.x - px) +
                                        (startOffset.y - py) * (startOffset.y - py)
                                    ) < HIT_RADIUS_PX * 1.5f
                                }
                            },
                            onDrag = { change, _ ->
                                val idx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                if (idx in slots.indices) {
                                    slots[idx] = slots[idx].copy(
                                        x = (change.position.x / size.width).coerceIn(0f, 1f),
                                        y = (change.position.y / size.height).coerceIn(0f, 1f)
                                    )
                                }
                            },
                            onDragEnd    = { draggedIndex = null },
                            onDragCancel = { draggedIndex = null }
                        )
                    }
            ) {
                AsyncImage(
                    model              = screenshotUri,
                    contentDescription = "Ban phase screenshot",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )

                slots.forEachIndexed { index, point ->
                    val markerSizeDp = 30.dp
                    val half         = markerSizeDp / 2
                    val markerColor  = if (point.team == SlotTeam.ALLY) MLBBTeal else MLBBRed
                    val isDragging   = index == draggedIndex

                    val scale by animateFloatAsState(
                        targetValue   = if (isDragging) 1.35f else 1.0f,
                        animationSpec = tween(120),
                        label         = "marker_scale_$index"
                    )
                    val borderColor by animateColorAsState(
                        targetValue   = if (isDragging) MLBBGold else SurfaceDark,
                        animationSpec = tween(120),
                        label         = "marker_border_$index"
                    )

                    Box(
                        Modifier
                            .offset(
                                x = maxWidth  * point.x - half,
                                y = maxHeight * point.y - half
                            )
                            .size(markerSizeDp)
                            .scale(scale)
                            .border(2.dp, borderColor, CircleShape)
                            .background(markerColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            slots.slotLabel(point),
                            color      = SurfaceDark,
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // ── Footer ─────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceMid)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SlotCountBadge("$allyCount Ally",   MLBBTeal, allyCount  > 0)
                        SlotCountBadge("$enemyCount Enemy", MLBBRed,  enemyCount > 0)
                    }
                    if (slots.isEmpty()) {
                        Text("No positions marked", color = TextDisabled, fontSize = 11.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { slots.clear() },
                        enabled = slots.isNotEmpty(),
                        shape   = RoundedCornerShape(8.dp),
                        border  = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (slots.isNotEmpty()) ErrorRed.copy(0.6f) else SurfaceElevated
                        )
                    ) {
                        Text(
                            "Clear All",
                            color    = if (slots.isNotEmpty()) ErrorRed else TextDisabled,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = { onSave(serializeMappedPoints(slots)) },
                        enabled = slots.isNotEmpty(),
                        shape   = RoundedCornerShape(8.dp),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor         = MLBBGold,
                            disabledContainerColor = SurfaceElevated
                        )
                    ) {
                        Text(
                            "Save Mapping",
                            color    = if (slots.isNotEmpty()) SurfaceDark else TextDisabled,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
