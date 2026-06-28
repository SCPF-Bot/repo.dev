package com.mlbb.assistant.presentation.settings.components

import android.view.ViewGroup
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
 * Full-screen ban-phase mapping tool.
 *
 * ## Architecture decisions (backed by research)
 *
 * ### Why aspectRatio + FillBounds instead of weight + Fit?
 * MLBB screenshots are landscape (e.g. 2400×1080). `ContentScale.Fit` inside a
 * portrait-dialog container (`weight(1f)`) would scale the image down to a thin
 * horizontal strip (~177dp tall) inside a ~800dp box — producing 300dp of black
 * bars above and below. Fixing this: once the image intrinsic size is known, the
 * canvas `BoxWithConstraints` is sized with `Modifier.aspectRatio(w/h)` so it is
 * *exactly* as tall as the rendered image. `ContentScale.FillBounds` then fills
 * the container perfectly, eliminating all letterboxing.
 *
 * ### Why no letterbox-offset math?
 * Because the container and image aspect ratios match, there is zero offset.
 * Tap-to-normalised becomes: `normX = tap.x / containerPx`, no subtraction needed.
 *
 * ### Dialog window constraint fix (Google issue #275732345)
 * `Dialog` uses `Configuration.screenWidthDp/screenHeightDp` which can be
 * inaccurate (especially for edge-to-edge). Applying `setLayout(MATCH_PARENT,
 * MATCH_PARENT)` via `DialogWindowProvider` forces the window to truly fill the
 * screen before content is laid out.
 *
 * ### Auto team detection
 * MLBB always places ally bans on the left half of the screen and enemy bans on
 * the right half. Tapping left → ALLY, right → ENEMY, no manual toggle required.
 * A Swap button handles rare inverted layouts.
 *
 * ### Tap to remove
 * Tapping within [HIT_RADIUS_PX] of an existing marker removes it.
 *
 * ### Long-press drag
 * Long-pressing and dragging repositions an existing marker in real time.
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

    var teamsSwapped by remember { mutableStateOf(false) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }

    // Populated by AsyncImage's onSuccess callback once the bitmap is decoded.
    // Until then the canvas shows a placeholder skeleton.
    var imageIntrinsicSize by remember { mutableStateOf(Size.Unspecified) }

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
        // ── Google issue #275732345 fix ────────────────────────────────
        // Force the dialog Window to truly match the device screen,
        // bypassing the inaccurate Configuration.screenWidthDp calculation.
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(SurfaceDark)
        ) {

            // ── Header ─────────────────────────────────────────────────
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
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TeamDot(MLBBTeal)
                        Text("$allyCount Ally", color = MLBBTeal, fontSize = 11.sp)
                        Text("·", color = TextDisabled, fontSize = 11.sp)
                        TeamDot(MLBBRed)
                        Text("$enemyCount Enemy", color = MLBBRed, fontSize = 11.sp)
                        if (slots.isNotEmpty()) {
                            Text("· long-press to drag", color = TextDisabled, fontSize = 10.sp)
                        }
                    }
                }

                IconButton(
                    onClick  = { onSave(serializeMappedPoints(slots)) },
                    enabled  = slots.isNotEmpty()
                ) {
                    Icon(
                        Icons.Rounded.Done,
                        contentDescription = "Save",
                        tint = if (slots.isNotEmpty()) MLBBGold else TextDisabled
                    )
                }
            }

            // ── Template + tools bar ────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated.copy(alpha = 0.6f))
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
                TemplateChip("3+3") { slots.clear(); slots.addAll(TEMPLATE_3_PLUS_3) }
                TemplateChip("5+5") { slots.clear(); slots.addAll(TEMPLATE_5_PLUS_5) }

                Spacer(Modifier.weight(1f))

                // Swap-teams toggle
                PillButton(
                    label    = "Swap",
                    icon     = Icons.Rounded.SwapHoriz,
                    active   = teamsSwapped,
                    color    = MLBBGold,
                    onClick  = { teamsSwapped = !teamsSwapped }
                )

                // Clear
                if (slots.isNotEmpty()) {
                    PillButton(
                        label   = "Clear",
                        active  = false,
                        color   = ErrorRed,
                        onClick = { slots.clear() }
                    )
                }
            }

            // ── Auto-team hint ─────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                val allyColor  = if (!teamsSwapped) MLBBTeal else MLBBRed
                val enemyColor = if (!teamsSwapped) MLBBRed  else MLBBTeal
                TeamDot(allyColor)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (!teamsSwapped) "ALLY" else "ENEMY",
                    color = allyColor, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "  ←  tap left · tap right  →  ",
                    color = TextDisabled, fontSize = 10.sp
                )
                Text(
                    if (!teamsSwapped) "ENEMY" else "ALLY",
                    color = enemyColor, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                TeamDot(enemyColor)
            }

            // ── Screenshot canvas ──────────────────────────────────────
            // Outer Box fills remaining Column space (weight(1f)).
            // Once the image intrinsic size is known, the inner canvas is sized via
            // aspectRatio so it matches the image exactly — zero letterbox bars.
            // Before the image loads, the canvas fills the available space.
            // NOTE: verticalScroll is intentionally NOT used here: scroll conflicts
            // with tap/drag gesture detection. MLBB screenshots are landscape so the
            // canvas will be shorter than the available height in all normal cases.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                val hasSize = imageIntrinsicSize != Size.Unspecified &&
                        imageIntrinsicSize.width > 0f &&
                        imageIntrinsicSize.height > 0f

                val canvasMod = if (hasSize) {
                    // Aspect-ratio-matched container → FillBounds fills it perfectly
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(imageIntrinsicSize.width / imageIntrinsicSize.height)
                } else {
                    // Loading state — fill the bounded Box until size is known
                    Modifier.fillMaxSize()
                }

                ImageCanvas(
                    modifier          = canvasMod,
                    screenshotUri     = screenshotUri,
                    slots             = slots,
                    draggedIndex      = draggedIndex,
                    teamsSwapped      = teamsSwapped,
                    onImageLoaded     = { size -> imageIntrinsicSize = size },
                    onDragIndexChange = { draggedIndex = it }
                )
            }
        }
    }
}

// ── Canvas composable (image + gesture layer + markers) ───────────────────────

/**
 * Draws the screenshot, overlays placement markers, and handles all gestures.
 * Extracted so the coordinate system is self-contained and easy to reason about.
 *
 * Because the parent [BoxWithConstraints] is sized with `aspectRatio(w/h)` matching
 * the image, `ContentScale.FillBounds` fills the canvas exactly — there is zero
 * letterbox offset.  Normalised coordinates are therefore:
 *   normX = tap.x / canvasWidthPx
 *   normY = tap.y / canvasHeightPx
 */
@Composable
private fun ImageCanvas(
    modifier:          Modifier,
    screenshotUri:     String,
    slots:             MutableList<MappedPoint>,
    draggedIndex:      Int?,
    teamsSwapped:      Boolean,
    onImageLoaded:     (Size) -> Unit,
    onDragIndexChange: (Int?) -> Unit
) {
    BoxWithConstraints(modifier.background(Color.Black)) {
        val density = LocalDensity.current
        val canvasWPx = with(density) { maxWidth.toPx() }
        val canvasHPx = with(density) { maxHeight.toPx() }

        // Keep latest values available in long-lived pointer coroutines.
        val wState          = rememberUpdatedState(canvasWPx)
        val hState          = rememberUpdatedState(canvasHPx)
        val swappedState    = rememberUpdatedState(teamsSwapped)

        // ── Image ──────────────────────────────────────────────────────
        // FillBounds: container already has the correct aspect ratio, so
        // stretching to fill is correct (zero distortion).
        AsyncImage(
            model              = screenshotUri,
            contentDescription = "Ban phase screenshot",
            contentScale       = ContentScale.FillBounds,
            onSuccess          = { state: AsyncImagePainter.State.Success ->
                onImageLoaded(state.painter.intrinsicSize)
            },
            modifier           = Modifier.fillMaxSize()
        )

        // Centre-divider: visual guide for the auto-team split line
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 1.dp, height = maxHeight)
                .background(Color.White.copy(alpha = 0.10f))
        )

        // ── Gesture overlay ────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxSize()
                // Tap: add (auto-team by X) or remove an existing marker
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val W = wState.value
                        val H = hState.value
                        if (W <= 0f || H <= 0f) return@detectTapGestures

                        val normX = (tap.x / W).coerceIn(0f, 1f)
                        val normY = (tap.y / H).coerceIn(0f, 1f)

                        // Remove if hitting an existing marker
                        val removeIdx = slots.indexOfFirst { p ->
                            val px = p.x * W
                            val py = p.y * H
                            sqrt((tap.x - px) * (tap.x - px) + (tap.y - py) * (tap.y - py)) < HIT_RADIUS_PX
                        }

                        if (removeIdx >= 0) {
                            slots.removeAt(removeIdx)
                        } else {
                            val team = if (!swappedState.value) {
                                if (normX < 0.5f) SlotTeam.ALLY else SlotTeam.ENEMY
                            } else {
                                if (normX < 0.5f) SlotTeam.ENEMY else SlotTeam.ALLY
                            }
                            slots.add(MappedPoint(normX, normY, team))
                        }
                    }
                }
                // Long-press drag: reposition a marker
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            val W = wState.value
                            val H = hState.value
                            onDragIndexChange(
                                slots.indices.minByOrNull { i ->
                                    val p  = slots[i]
                                    val px = p.x * W
                                    val py = p.y * H
                                    sqrt((start.x - px) * (start.x - px) + (start.y - py) * (start.y - py))
                                }?.takeIf { i ->
                                    val p  = slots[i]
                                    val px = p.x * W
                                    val py = p.y * H
                                    sqrt((start.x - px) * (start.x - px) + (start.y - py) * (start.y - py)) < HIT_RADIUS_PX * 2f
                                }
                            )
                        },
                        onDrag = { change, _ ->
                            val idx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                            val W = wState.value
                            val H = hState.value
                            if (idx in slots.indices) {
                                val pos = change.position
                                slots[idx] = slots[idx].copy(
                                    x = (pos.x / W).coerceIn(0f, 1f),
                                    y = (pos.y / H).coerceIn(0f, 1f)
                                )
                            }
                        },
                        onDragEnd    = { onDragIndexChange(null) },
                        onDragCancel = { onDragIndexChange(null) }
                    )
                }
        )

        // ── Markers ────────────────────────────────────────────────────
        val markerSizeDp = 34.dp
        val halfDp       = markerSizeDp / 2

        slots.forEachIndexed { index, point ->
            val markerColor = if (point.team == SlotTeam.ALLY) MLBBTeal else MLBBRed
            val isDragging  = index == draggedIndex

            val markerScale by animateFloatAsState(
                targetValue   = if (isDragging) 1.5f else 1f,
                animationSpec = tween(120),
                label         = "ms$index"
            )
            val ringColor by animateColorAsState(
                targetValue   = if (isDragging) MLBBGold else Color.White.copy(alpha = 0.70f),
                animationSpec = tween(120),
                label         = "mr$index"
            )

            val screenXDp = with(density) { (point.x * canvasWPx).toDp() }
            val screenYDp = with(density) { (point.y * canvasHPx).toDp() }

            Box(
                Modifier
                    .offset(x = screenXDp - halfDp, y = screenYDp - halfDp)
                    .size(markerSizeDp)
                    .scale(markerScale)
                    .border(2.dp, ringColor, CircleShape)
                    .background(markerColor.copy(alpha = 0.90f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    slots.slotLabel(point),
                    color      = Color.White,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // ── Empty state ────────────────────────────────────────────────
        if (slots.isEmpty()) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "Tap each hero portrait",
                        color      = TextPrimary,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Left = Ally  ·  Right = Enemy",
                        color    = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        "Tap a marker again to remove",
                        color    = TextDisabled,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ── Small reusable components ─────────────────────────────────────────────────

@Composable
private fun TeamDot(color: Color) {
    Box(
        Modifier
            .size(7.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun PillButton(
    label:   String,
    active:  Boolean,
    color:   Color,
    onClick: () -> Unit,
    icon:    androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        Modifier
            .background(
                if (active) color.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .border(
                0.5.dp,
                if (active) color.copy(alpha = 0.45f) else SurfaceElevated,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint     = if (active) color else TextDisabled,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            label,
            color      = if (active) color else TextDisabled,
            fontSize   = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}
