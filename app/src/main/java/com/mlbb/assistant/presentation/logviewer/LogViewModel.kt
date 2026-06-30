package com.mlbb.assistant.presentation.logviewer

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.crashlog.CrashLogStore
import com.mlbb.assistant.data.local.crashlog.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
