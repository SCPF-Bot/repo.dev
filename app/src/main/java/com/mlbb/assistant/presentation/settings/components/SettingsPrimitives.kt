package com.mlbb.assistant.presentation.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import kotlin.math.roundToInt

/**
 * Shared primitive UI components for the Settings screen.
 *
 * Extracted from [SettingsScreen] to keep each file under 300 lines
 * and to allow reuse across settings sub-sections.
 */

@Composable
internal fun SettingsSection(
    icon:     ImageVector,
    title:    String,
    subtitle: String?      = null,
    content:  @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceElevated, RoundedCornerShape(14.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(MLBBGold.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MLBBGold, modifier = Modifier.size(16.dp))
            Column {
                Text(title, color = MLBBGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, color = TextDisabled, fontSize = 10.sp)
                }
            }
        }
        HorizontalDivider(color = SurfaceElevated, thickness = 0.5.dp)
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
internal fun SectionDivider() {
    HorizontalDivider(
        color     = SurfaceElevated,
        thickness = 0.5.dp,
        modifier  = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
internal fun SliderRow(
    label:     String,
    value:     Float,
    range:     ClosedFloatingPointRange<Float>,
    onChanged: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextPrimary, fontSize = 13.sp)
            Text(
                "%.0f%%".format(value * 100),
                color      = MLBBGold,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value         = value,
            onValueChange = onChanged,
            valueRange    = range,
            colors        = SliderDefaults.colors(
                thumbColor         = MLBBGold,
                activeTrackColor   = MLBBGold,
                inactiveTrackColor = SurfaceElevated
            ),
            modifier = Modifier.semantics {
                contentDescription = "$label, ${value.times(100).roundToInt()} percent"
            }
        )
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = MLBBGold,
                checkedTrackColor   = MLBBGold.copy(alpha = 0.30f),
                uncheckedThumbColor = TextDisabled,
                uncheckedTrackColor = SurfaceElevated
            )
        )
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary,   fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    val iconTint by animateColorAsState(
        targetValue   = if (granted) SuccessGreen else ErrorRed,
        animationSpec = tween(300),
        label         = "perm_icon"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics {
                contentDescription =
                    "$label permission, ${if (granted) "granted" else "not granted"}, tap to open settings"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector        = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(16.dp)
            )
            Text(
                if (granted) "Granted" else "Tap to enable",
                color      = iconTint,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
