package com.mlbb.assistant.presentation.logviewer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.crashlog.CrashLogStore
import com.mlbb.assistant.data.local.crashlog.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@Immutable
data class LogViewerState(
    val entries:   List<LogEntry> = emptyList(),
    val isLoading: Boolean        = true
)

@HiltViewModel
class LogViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(LogViewerState())
    val state: StateFlow<LogViewerState> = _state.asStateFlow()

    init {
        load()
        startAutoRefresh()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val entries = CrashLogStore.readAll(getApplication())
            _state.value = LogViewerState(entries = entries, isLoading = false)
        }
    }

    fun clear() {
        viewModelScope.launch {
            CrashLogStore.clear(getApplication())
            _state.value = LogViewerState(entries = emptyList(), isLoading = false)
        }
    }

    /**
     * todo.md §6 — "Share logs" via the app's existing [FileProvider] (already
     * declared in AndroidManifest.xml under `${applicationId}.provider`, with
     * `cache-path` entries in `res/xml/file_paths.xml`).
     *
     * Writes the full log as a plain-text file under `cacheDir/logs/` and returns
     * a content:// [Uri] the caller can attach to an `ACTION_SEND` intent —
     * this replaces the previous text-only [android.content.Intent.EXTRA_TEXT]
     * share, which silently truncated very long logs at the Binder transaction
     * size limit.
     */
    suspend fun shareLogs(): Uri = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val entries = _state.value.entries
        val text = entries.joinToString("\n\n") { e ->
            "[${e.formattedTime}] ${e.level.label}/${e.tag}\n${e.message}" +
                if (e.stackTrace.isNotBlank()) "\n${e.stackTrace}" else ""
        }

        val logsDir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(logsDir, "mlbb_assistant_log.txt")
        file.writeText(text)

        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_MS)
                val entries = CrashLogStore.readAll(getApplication())
                _state.value = _state.value.copy(entries = entries, isLoading = false)
            }
        }
    }

    companion object {
        private const val AUTO_REFRESH_MS = 3_000L
    }
}
