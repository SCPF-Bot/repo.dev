package com.mlbb.assistant.presentation.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary

internal val BAN_COUNT_OPTIONS = listOf(
    "6 bans (Epic)",
    "8 bans (Legend)",
    "10 bans (Mythic and higher)"
)

@Composable
internal fun BanCountRow(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ban count", color = TextSecondary, fontSize = 12.sp)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, MLBBGold.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Ban count: $current. Tap to change." },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(current, color = MLBBGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(
                    imageVector        = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint               = TextSecondary,
                    modifier           = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BAN_COUNT_OPTIONS.forEach { option ->
                    val selected = option == current
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                color      = if (selected) MLBBGold else TextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize   = 13.sp
                            )
                        },
                        trailingIcon = if (selected) ({
                            Icon(Icons.Rounded.Done, contentDescription = null, tint = MLBBGold, modifier = Modifier.size(16.dp))
                        }) else null,
                        onClick = { onSelected(option); expanded = false }
                    )
                }
            }
        }
        Text(
            "Sets how many hero portrait positions the overlay tracks during the ban phase.",
            color = TextDisabled, fontSize = 11.sp
        )
    }
}
