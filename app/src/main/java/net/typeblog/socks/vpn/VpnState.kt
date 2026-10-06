package net.typeblog.socks.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VpnState {
    data object Disconnected : VpnState
    data class Connecting(val profileName: String) : VpnState

    /** [since] is a [android.os.SystemClock.elapsedRealtime] timestamp. */
    data class Connected(val profileName: String, val since: Long) : VpnState
    data object Disconnecting : VpnState
    data class Error(val message: String) : VpnState

    val isActive: Boolean get() = this is Connecting || this is Connected
}

/**
 * Process-wide VPN state. The service runs in the app's main process, so the UI, the
 * Quick Settings tile and the notification all observe the same flow.
 */
object VpnStateHolder {
    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    internal fun set(state: VpnState) {
        _state.value = state
    }
}
