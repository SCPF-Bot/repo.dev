package com.mlbb.assistant.presentation.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.utils.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Lightweight ViewModel that exposes network connectivity state to [AppShell].
 *
 * Provides [isOffline] as a [StateFlow] backed by [NetworkMonitor.isConnected],
 * making it lifecycle-aware and preventing the callback from leaking after the
 * Activity is destroyed.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    networkMonitor: NetworkMonitor
) : ViewModel() {

    /**
     * Emits `true` when the device has no active internet connection.
     * Starts as `false` (optimistic — avoids a flash of the offline banner on launch).
     */
    val isOffline: StateFlow<Boolean> = networkMonitor.isConnected
        .map { connected -> !connected }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false
        )
}
