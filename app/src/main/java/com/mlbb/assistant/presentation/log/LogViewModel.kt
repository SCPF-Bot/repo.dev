package com.mlbb.assistant.presentation.log

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.crashlog.CrashLogStore
import com.mlbb.assistant.data.local.crashlog.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LogScreenState(
    val entries:   List<LogEntry> = emptyList(),
    val isLoading: Boolean        = true
)

@HiltViewModel
class LogViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(LogScreenState())
    val state: StateFlow<LogScreenState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val entries = CrashLogStore.readAll(getApplication())
            _state.value = LogScreenState(entries = entries, isLoading = false)
        }
    }

    fun clear() {
        viewModelScope.launch {
            CrashLogStore.clear(getApplication())
            _state.value = LogScreenState(entries = emptyList(), isLoading = false)
        }
    }
}
