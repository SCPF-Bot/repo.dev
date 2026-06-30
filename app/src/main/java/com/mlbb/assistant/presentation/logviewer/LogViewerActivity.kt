package com.mlbb.assistant.presentation.logviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mlbb.assistant.data.local.crashlog.LogEntry
import com.mlbb.assistant.data.local.crashlog.LogLevel
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.InfoBlue
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber
import dagger.hilt.android.AndroidEntryPoint

/**
 * Standalone Activity that displays the in-app log file written by [AppLogTree].
 * Launched from the second launcher icon (controlled by the DevLogAlias activity-alias)
 * or from Settings > Logs > Open Log Viewer.
 *
 * Features:
 *  - Live log list with per-entry copy, expandable stack traces
 *  - Share full log as plain text
 *  - Clear all logs (with confirmation)
 *  - Auto-refreshes every 3 seconds via [LogViewModel]
 */
@AndroidEntryPoint
class LogViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MLBBAssistantTheme {
                LogViewerScreen(onBack = { finish() })
            }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerScreen(
    onBack: () -> Unit,
    vm: LogViewModel = hiltViewModel()
) {
    val state   by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor   = SurfaceCard,
            title  = { Text("Clear logs?", color = TextPrimary) },
            text   = { Text("All crash and error logs will be permanently deleted.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.clear(); showClearDialog = false }) {
                    Text("Clear", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = SurfaceDark,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceMid,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("App Log", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "${state.entries.size} entries • auto-refreshes",
                            fontSize = 11.sp,
                            color    = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        val text = state.entries.joinToString("\n\n") { e ->
                            "[${e.formattedTime}] ${e.level.label}/${e.tag}\n${e.message}" +
                                if (e.stackTrace.isNotBlank()) "\n${e.stackTrace}" else ""
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_SUBJECT, "MLBB Assistant Log")
                        }
                        context.startActivity(
                            Intent.createChooser(intent, "Share log")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Export log")
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
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = MLBBGold) }
            }

            state.entries.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✅", fontSize = 48.sp)
                        Text("No crashes or errors logged", color = TextSecondary, fontSize = 15.sp)
                        Text(
                            "Warnings and errors will appear here automatically",
                            color    = TextDisabled,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            else -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    state               = listState,
                    modifier            = Modifier.fillMaxSize().padding(padding),
                    contentPadding      = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries, key = { it.timestamp }) { entry ->
                        LogEntryCard(
                            entry  = entry,
                            onCopy = {
                                val text = "[${entry.formattedTime}] ${entry.level.label}/${entry.tag}\n" +
                                    entry.message +
                                    if (entry.stackTrace.isNotBlank()) "\n${entry.stackTrace}" else ""
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText("log_entry", text))
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Log Entry Card ────────────────────────────────────────────────────────────

@Composable
private fun LogEntryCard(entry: LogEntry, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(entry.level == LogLevel.CRASH) }

    val (borderColor, badgeBg, badgeText) = when (entry.level) {
        LogLevel.CRASH -> Triple(ErrorRed,       ErrorRed.copy(alpha = 0.20f),     Color.White)
        LogLevel.ERROR -> Triple(MLBBRed,        MLBBRed.copy(alpha = 0.15f),      Color.White)
        LogLevel.WARN  -> Triple(WarningAmber,   WarningAmber.copy(alpha = 0.15f), SurfaceDark)
        LogLevel.INFO  -> Triple(InfoBlue,       InfoBlue.copy(alpha = 0.10f),     Color.White)
        LogLevel.DEBUG -> Triple(SurfaceElevated, SurfaceElevated,                 TextSecondary)
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

            // Header row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
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
                    Text(entry.tag, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Text(entry.formattedTime, color = TextDisabled, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }

            // Message
            Text(
                entry.message,
                color    = TextPrimary,
                fontSize = 13.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2
            )

            // Expandable stack trace
            if (entry.stackTrace.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "Hide stacktrace" else "Show stacktrace",
                        color = InfoBlue, fontSize = 11.sp
                    )
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint     = InfoBlue,
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

            // Copy button
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick        = onCopy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", fontSize = 11.sp)
                }
            }
        }
    }
}
