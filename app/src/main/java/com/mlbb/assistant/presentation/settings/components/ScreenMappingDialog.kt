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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
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
 * Key design decisions:
 * - **Auto team detection**: taps on the LEFT half are ALLY; taps on the RIGHT half are ENEMY.
 *   This matches the permanent MLBB layout (ally always left, enemy always right during bans).
 *   A [SwapHoriz] button flips the rule for inverted layouts.
 * - **Image-relative coordinates**: marker positions are stored as fractions of the *rendered
 *   image rect* (not the container rect). [ContentScale.Fit] letterboxes the image inside the
 *   container; we compute the actual image bounds and clamp/map all gestures accordingly, so
 *   markers never float outside the screenshot.
 * - **Tap-to-remove**: tapping within [HIT_RADIUS_PX] of an existing marker removes it.
 * - **Long-press drag**: repositions an existing marker.
 *
 * Data model: [MappedPoint] / serialisation: [MappingSlotModel]
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

    var teamsSwapped  by remember { mutableStateOf(false) }
    var draggedIndex  by remember { mutableStateOf<Int?>(null) }

    // Intrinsic image size — updated once the image loads via onSuccess callback.
    var imageSize by remember { mutableStateOf(Size.Unspecified) }

    val allyCount  = slots.count { it.team == SlotTeam.ALLY  }
    val enemyCount = slots.count { it.team == SlotTeam.ENEMY }
    val totalCount = slots.size

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
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = TextSecondary)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Map Ban Positions",
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        TeamDot(MLBBTeal)
                        Text("$allyCount Ally", color = MLBBTeal, fontSize = 11.sp)
                        Text("·", color = TextDisabled, fontSize = 11.sp)
                        TeamDot(MLBBRed)
                        Text("$enemyCount Enemy", color = MLBBRed, fontSize = 11.sp)
                        if (totalCount > 0) {
                            Text("· long-press to drag", color = TextDisabled, fontSize = 10.sp)
                        }
                    }
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

            // ── Template + swap bar ────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.GridOn,
                    contentDescription = null,
                    tint     = TextDisabled,
                    modifier = Modifier.size(13.dp)
                )
                Text("Templates:", color = TextSecondary, fontSize = 11.sp)
                TemplateChip("3+3") { slots.clear(); slots.addAll(TEMPLATE_3_PLUS_3) }
                TemplateChip("5+5") { slots.clear(); slots.addAll(TEMPLATE_5_PLUS_5) }
                Spacer(Modifier.weight(1f))

                // Swap-teams toggle
                Row(
                    Modifier
                        .background(
                            if (teamsSwapped) MLBBGold.copy(alpha = 0.15f) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            0.5.dp,
                            if (teamsSwapped) MLBBGold.copy(alpha = 0.4f) else SurfaceElevated,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { teamsSwapped = !teamsSwapped }
                        },
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.SwapHoriz,
                        contentDescription = "Swap teams",
                        tint     = if (teamsSwapped) MLBBGold else TextDisabled,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Swap",
                        color      = if (teamsSwapped) MLBBGold else TextDisabled,
                        fontSize   = 10.sp,
                        fontWeight = if (teamsSwapped) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Clear all
                if (slots.isNotEmpty()) {
                    Row(
                        Modifier
                            .background(ErrorRed.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                            .border(0.5.dp, ErrorRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { slots.clear() }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Clear", color = ErrorRed, fontSize = 10.sp)
                    }
                }
            }

            // ── Auto-team hint ─────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (!teamsSwapped) Color.Transparent
                        else MLBBGold.copy(alpha = 0.06f)
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TeamDot(if (!teamsSwapped) MLBBTeal else MLBBRed)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (!teamsSwapped) "ALLY" else "ENEMY",
                    color      = if (!teamsSwapped) MLBBTeal else MLBBRed,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "  ←  left side     right side  →  ",
                    color    = TextDisabled,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
                TeamDot(if (!teamsSwapped) MLBBRed else MLBBTeal)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (!teamsSwapped) "ENEMY" else "ALLY",
                    color      = if (!teamsSwapped) MLBBRed else MLBBTeal,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Screenshot canvas ──────────────────────────────────────────
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                val density = LocalDensity.current

                val containerWPx = with(density) { maxWidth.toPx() }
                val containerHPx = with(density) { maxHeight.toPx() }

                // Compute the actual rendered image rect inside ContentScale.Fit letterbox.
                // Falls back to the container rect until the image has loaded.
                val imgW = if (imageSize != Size.Unspecified && imageSize.width > 0f) imageSize.width else containerWPx
                val imgH = if (imageSize != Size.Unspecified && imageSize.height > 0f) imageSize.height else containerHPx
                val fitScale   = minOf(containerWPx / imgW, containerHPx / imgH)
                val renderedWPx = imgW * fitScale
                val renderedHPx = imgH * fitScale
                val imgOffXPx  = (containerWPx - renderedWPx) / 2f
                val imgOffYPx  = (containerHPx - renderedHPx) / 2f

                // Keep latest geometry available inside pointer-input lambdas without
                // re-creating the gesture detectors on every recomposition.
                val geom = rememberUpdatedState(
                    ImageGeometry(renderedWPx, renderedHPx, imgOffXPx, imgOffYPx)
                )
                val slotsState      = rememberUpdatedState(slots.toList())
                val teamsSwappedState = rememberUpdatedState(teamsSwapped)

                // Screenshot
                AsyncImage(
                    model              = screenshotUri,
                    contentDescription = "Ban phase screenshot",
                    contentScale       = ContentScale.Fit,
                    onSuccess          = { state: AsyncImagePainter.State.Success ->
                        imageSize = state.painter.intrinsicSize
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Centre divider — visual guide for the auto-team split
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .width(1.dp)
                        .size(width = 1.dp, height = with(density) { renderedHPx.toDp() })
                        .offset(
                            x = with(density) { imgOffXPx.toDp() },
                            y = with(density) { imgOffYPx.toDp() }
                        )
                        .background(Color.White.copy(alpha = 0.12f))
                )

                // Invisible overlay for all gesture handling
                Box(
                    Modifier
                        .fillMaxSize()
                        // Tap: add marker (auto-team by X) or remove existing
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                val g = geom.value
                                // Ignore taps in the letterbox bars
                                if (!g.contains(tap.x, tap.y)) return@detectTapGestures

                                val normX = g.normX(tap.x)
                                val normY = g.normY(tap.y)

                                val removeIdx = slotsState.value.indexOfFirst { p ->
                                    val px = g.screenX(p.x)
                                    val py = g.screenY(p.y)
                                    sqrt((tap.x - px) * (tap.x - px) + (tap.y - py) * (tap.y - py)) < HIT_RADIUS_PX
                                }

                                if (removeIdx >= 0) {
                                    slots.removeAt(removeIdx)
                                } else {
                                    val team = if (!teamsSwappedState.value) {
                                        if (normX < 0.5f) SlotTeam.ALLY else SlotTeam.ENEMY
                                    } else {
                                        if (normX < 0.5f) SlotTeam.ENEMY else SlotTeam.ALLY
                                    }
                                    slots.add(MappedPoint(normX, normY, team))
                                }
                            }
                        }
                        // Long-press drag: reposition existing marker
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { start ->
                                    val g = geom.value
                                    draggedIndex = slotsState.value.indices.minByOrNull { i ->
                                        val p  = slotsState.value[i]
                                        val px = g.screenX(p.x)
                                        val py = g.screenY(p.y)
                                        sqrt((start.x - px) * (start.x - px) + (start.y - py) * (start.y - py))
                                    }?.takeIf { i ->
                                        val p  = slotsState.value[i]
                                        val px = g.screenX(p.x)
                                        val py = g.screenY(p.y)
                                        sqrt((start.x - px) * (start.x - px) + (start.y - py) * (start.y - py)) < HIT_RADIUS_PX * 1.8f
                                    }
                                },
                                onDrag = { change, _ ->
                                    val idx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                    val g = geom.value
                                    if (idx in slots.indices) {
                                        val pos = change.position
                                        slots[idx] = slots[idx].copy(
                                            x = g.normX(pos.x).coerceIn(0f, 1f),
                                            y = g.normY(pos.y).coerceIn(0f, 1f)
                                        )
                                    }
                                },
                                onDragEnd    = { draggedIndex = null },
                                onDragCancel = { draggedIndex = null }
                            )
                        }
                )

                // Markers rendered on top of the screenshot, correctly placed inside image bounds
                slots.forEachIndexed { index, point ->
                    val g = ImageGeometry(renderedWPx, renderedHPx, imgOffXPx, imgOffYPx)
                    val markerColor = if (point.team == SlotTeam.ALLY) MLBBTeal else MLBBRed
                    val isDragging  = index == draggedIndex

                    val markerScale by animateFloatAsState(
                        targetValue   = if (isDragging) 1.45f else 1f,
                        animationSpec = tween(120),
                        label         = "ms$index"
                    )
                    val ringColor by animateColorAsState(
                        targetValue   = if (isDragging) MLBBGold else Color.White.copy(alpha = 0.65f),
                        animationSpec = tween(120),
                        label         = "mr$index"
                    )

                    val markerSizeDp = 34.dp
                    val halfDp       = markerSizeDp / 2

                    val screenXDp = with(density) { g.screenX(point.x).toDp() }
                    val screenYDp = with(density) { g.screenY(point.y).toDp() }
                    val label     = slots.slotLabel(point)

                    Box(
                        Modifier
                            .offset(x = screenXDp - halfDp, y = screenYDp - halfDp)
                            .size(markerSizeDp)
                            .scale(markerScale)
                            .border(2.dp, ringColor, CircleShape)
                            .background(markerColor.copy(alpha = 0.88f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color      = Color.White,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Empty state prompt
                if (slots.isEmpty()) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Tap on each hero portrait", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Left side = Ally  •  Right side = Enemy", color = TextSecondary, fontSize = 11.sp)
                        Text("Tap a marker again to remove it", color = TextDisabled, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ── Image geometry helper ──────────────────────────────────────────────────────

/**
 * Encapsulates the pixel-space geometry of the rendered image inside a Fit-scaled container.
 * All coordinates are in raw pixels, matching what gesture APIs report.
 */
private data class ImageGeometry(
    val renderedW: Float,
    val renderedH: Float,
    val offsetX:   Float,
    val offsetY:   Float
) {
    fun contains(tapX: Float, tapY: Float): Boolean =
        tapX in offsetX..(offsetX + renderedW) &&
        tapY in offsetY..(offsetY + renderedH)

    fun normX(tapX: Float): Float = ((tapX - offsetX) / renderedW).coerceIn(0f, 1f)
    fun normY(tapY: Float): Float = ((tapY - offsetY) / renderedH).coerceIn(0f, 1f)

    fun screenX(normX: Float): Float = normX * renderedW + offsetX
    fun screenY(normY: Float): Float = normY * renderedH + offsetY
}

// ── Small indicator dot ────────────────────────────────────────────────────────

@Composable
private fun TeamDot(color: Color) {
    Box(
        Modifier
            .size(7.dp)
            .background(color, CircleShape)
    )
}
