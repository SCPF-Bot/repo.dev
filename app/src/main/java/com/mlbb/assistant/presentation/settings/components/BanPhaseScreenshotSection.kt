package com.mlbb.assistant.presentation.settings.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBGoldDark
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.WarningAmber

@Composable
internal fun BanPhaseScreenshotSection(
    currentUri:        String,
    screenMappingJson: String,
    onUriSelected:     (String) -> Unit,
    onClearUri:        () -> Unit,
    onOpenMapping:     () -> Unit,
    onClearMapping:    () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { onUriSelected(it.toString()) }
    }
    val mappedCount = remember(screenMappingJson) { parseMappedPoints(screenMappingJson).size }

    SettingsSection(
        icon     = Icons.Rounded.Image,
        title    = "BAN PHASE REFERENCE",
        subtitle = "Screenshot used for hero portrait detection"
    ) {
        if (currentUri.isBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .border(1.dp, SurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(32.dp))
                Text("No screenshot selected", color = TextDisabled, fontSize = 13.sp)
                Text(
                    "Select a screenshot of the ban phase to let the app detect portrait locations.",
                    color     = TextDisabled,
                    fontSize  = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SuccessGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Uri.parse(currentUri).lastPathSegment ?: "Screenshot",
                        color    = TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (mappedCount > 0) {
                        Text(
                            "$mappedCount portrait position${if (mappedCount > 1) "s" else ""} mapped",
                            color    = MLBBTeal,
                            fontSize = 11.sp
                        )
                    } else {
                        Text("No positions mapped yet", color = WarningAmber, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = onClearUri, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove screenshot", tint = TextDisabled, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick  = { launcher.launch(arrayOf("image/*")) },
                modifier = Modifier.weight(1f),
                border   = BorderStroke(1.dp, MLBBGold.copy(alpha = 0.4f)),
                shape    = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp), tint = MLBBGold)
                Spacer(Modifier.width(6.dp))
                Text(if (currentUri.isBlank()) "Select Screenshot" else "Replace", color = MLBBGold, fontSize = 13.sp)
            }
            Button(
                onClick  = onOpenMapping,
                enabled  = currentUri.isNotBlank(),
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = if (mappedCount > 0) MLBBTeal else MLBBGoldDark,
                    disabledContainerColor = SurfaceElevated
                )
            ) {
                Icon(
                    Icons.Rounded.GridOn,
                    contentDescription = null,
                    modifier           = Modifier.size(15.dp),
                    tint               = if (currentUri.isNotBlank()) SurfaceDark else TextDisabled
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (mappedCount > 0) "Remap ($mappedCount)" else "Map Screen",
                    color    = if (currentUri.isNotBlank()) SurfaceDark else TextDisabled,
                    fontSize = 13.sp
                )
            }
        }

        if (mappedCount > 0) {
            TextButton(onClick = onClearMapping, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(13.dp), tint = ErrorRed)
                Spacer(Modifier.width(4.dp))
                Text("Clear mapping", color = ErrorRed, fontSize = 12.sp)
            }
        }
    }
}
