package com.mlbb.assistant.presentation.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mlbb.assistant.presentation.common.theme.InfoBlue
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

/** Normalised (0..1) coordinate of a mapped portrait point. */
internal data class MappedPoint(val x: Float, val y: Float)

internal fun parseMappedPoints(json: String): List<MappedPoint> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            MappedPoint(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
        }
    }.getOrDefault(emptyList())
}

internal fun serializeMappedPoints(points: List<MappedPoint>): String {
    val arr = JSONArray()
    points.forEach { p ->
        arr.put(JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble()))
    }
    return arr.toString()
}

/**
 * Full-screen dialog for tapping hero portrait positions on a ban-phase screenshot.
 * Markers are saved as normalised (0..1) coordinates so they work on any screen size.
 */
@Composable
internal fun ScreenMappingDialog(
    screenshotUri:  String,
    initialMapping: String,
    onDismiss:      () -> Unit,
    onSave:         (String) -> Unit
) {
    val initialPoints = remember(initialMapping) { parseMappedPoints(initialMapping) }
    val points = remember { mutableStateListOf<MappedPoint>().also { it.addAll(initialPoints) } }

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
            // ── Header ────────────────────────────────────────────────────────
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
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                    Text(
                        "Tap each hero portrait location on the screenshot",
                        color = TextSecondary, fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick  = { onSave(serializeMappedPoints(points)) },
                    enabled  = points.isNotEmpty()
                ) {
                    Icon(
                        Icons.Rounded.Done, contentDescription = "Save",
                        tint = if (points.isNotEmpty()) MLBBGold else TextDisabled
                    )
                }
            }

            // ── Instructions chip ─────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(InfoBlue.copy(alpha = 0.10f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(14.dp))
                Text(
                    "Tap to add a marker. Tap an existing marker to remove it.",
                    color    = InfoBlue,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Screenshot + tap surface ──────────────────────────────────────
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .pointerInput(points.size) {
                        detectTapGestures { tapOffset ->
                            val xRatio   = (tapOffset.x / size.width).coerceIn(0f, 1f)
                            val yRatio   = (tapOffset.y / size.height).coerceIn(0f, 1f)
                            val removeIdx = points.indexOfFirst { p ->
                                val px = p.x * size.width
                                val py = p.y * size.height
                                sqrt(
                                    (tapOffset.x - px) * (tapOffset.x - px) +
                                    (tapOffset.y - py) * (tapOffset.y - py)
                                ) < 40f
                            }
                            if (removeIdx >= 0) points.removeAt(removeIdx)
                            else points.add(MappedPoint(xRatio, yRatio))
                        }
                    }
            ) {
                AsyncImage(
                    model              = screenshotUri,
                    contentDescription = "Ban phase screenshot",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
                points.forEachIndexed { index, point ->
                    val markerSize = 28.dp
                    val halfMarker = markerSize / 2
                    Box(
                        Modifier
                            .offset(x = maxWidth * point.x - halfMarker, y = maxHeight * point.y - halfMarker)
                            .size(markerSize)
                            .border(2.dp, SurfaceDark, CircleShape)
                            .background(MLBBGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = SurfaceDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Footer ────────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceMid)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    if (points.isEmpty()) "No positions marked"
                    else "${points.size} position${if (points.size > 1) "s" else ""} marked",
                    color      = if (points.isEmpty()) TextDisabled else MLBBTeal,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { points.clear() },
                        enabled = points.isNotEmpty(),
                        shape   = RoundedCornerShape(8.dp),
                        border  = androidx.compose.foundation.BorderStroke(
                            1.dp, if (points.isNotEmpty()) ErrorRed.copy(0.6f) else SurfaceElevated
                        )
                    ) {
                        Text(
                            "Clear All",
                            color    = if (points.isNotEmpty()) ErrorRed else TextDisabled,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = { onSave(serializeMappedPoints(points)) },
                        enabled = points.isNotEmpty(),
                        shape   = RoundedCornerShape(8.dp),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor         = MLBBGold,
                            disabledContainerColor = SurfaceElevated
                        )
                    ) {
                        Text(
                            "Save Mapping",
                            color    = if (points.isNotEmpty()) SurfaceDark else TextDisabled,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
