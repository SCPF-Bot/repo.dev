package com.mlbb.assistant.presentation.log

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.data.local.crashlog.LogEntry
import com.mlbb.assistant.data.local.crashlog.LogLevel
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun LogScreen(
    onBack: () -> Unit,
    vm: LogViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipScope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title   = { Text("Clear logs?") },
            text    = { Text("All crash and error logs will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { vm.clear(); showClearDialog = false }) {
                    Text("Clear", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Column {
                        Text("App Log", fontWeight = FontWeight.Bold)
                        Text(
                            "${state.entries.size} entries",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh logs")
                    }
                    IconButton(onClick = {
                        // Share full log as plain text
                        val text = state.entries.joinToString("\n\n") { e ->
                            "[${e.formattedTime}] ${e.level.label}/${e.tag}\n${e.message}" +
                                if (e.stackTrace.isNotBlank()) "\n${e.stackTrace}" else ""
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_SUBJECT, "MLBB Assistant Crash Log")
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "Share log")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share logs")
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Rounded.DeleteForever,
                            contentDescription = "Clear logs",
                            tint = ErrorRed
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.entries.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✅", fontSize = 48.sp)
                        Text(
                            "No crashes or errors logged",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                        Text(
                            "Errors and crashes will appear here automatically",
                            color = TextDisabled,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier            = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding      = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries, key = { it.timestamp }) { entry ->
                        LogEntryCard(
                            entry     = entry,
                            onCopy    = {
                                val text = "[${entry.formattedTime}] ${entry.level.label}/${entry.tag}\n" +
                                    entry.message +
                                    if (entry.stackTrace.isNotBlank()) "\n${entry.stackTrace}" else ""
                                clipScope.launch { clipboard.setClipEntry(AnnotatedString(text).toClipEntry()) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(entry.level == LogLevel.CRASH) }

    val (borderColor, badgeBg, badgeText) = when (entry.level) {
        LogLevel.CRASH -> Triple(ErrorRed,      ErrorRed.copy(alpha = 0.20f),   Color.White)
        LogLevel.ERROR -> Triple(MLBBRed,       MLBBRed.copy(alpha = 0.15f),    Color.White)
        LogLevel.WARN  -> Triple(WarningAmber,  WarningAmber.copy(alpha = 0.15f), SurfaceDark)
        LogLevel.INFO  -> Triple(InfoBlue,      InfoBlue.copy(alpha = 0.10f),   Color.White)
        LogLevel.DEBUG -> Triple(SurfaceElevated, SurfaceElevated, TextSecondary)
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.50f), RoundedCornerShape(10.dp)),
        colors    = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // ── Header row ──────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Level badge
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            entry.level.label,
                            color      = if (entry.level == LogLevel.WARN) SurfaceDark else badgeText,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        entry.tag,
                        color      = TextSecondary,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    entry.formattedTime,
                    color    = TextDisabled,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // ── Message ─────────────────────────────────────────────────────────
            Text(
                entry.message,
                color    = TextPrimary,
                fontSize = 13.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2
            )

            // ── Stack trace (expandable) ─────────────────────────────────────
            if (entry.stackTrace.isNotBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "Hide stacktrace" else "Show stacktrace",
                        color    = InfoBlue,
                        fontSize = 11.sp
                    )
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint   = InfoBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (expanded) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(SurfaceMid, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            entry.stackTrace,
                            color      = TextSecondary,
                            fontSize   = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // ── Copy button ──────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick      = onCopy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy entry",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", fontSize = 11.sp)
                }
            }
        }
    }
}
