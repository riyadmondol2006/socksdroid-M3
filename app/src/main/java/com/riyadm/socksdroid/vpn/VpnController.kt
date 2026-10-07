package com.riyadm.socksdroid.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.riyadm.socksdroid.SocksApp

/** Entry points for starting / stopping the proxy from the UI, the tile and the boot receiver. */
object VpnController {
    /**
     * Action of the [com.riyadm.socksdroid.ui.MainActivity] intent sent by the Quick Settings tile when
     * VPN consent is missing: the activity should request consent and then call [start].
     */
    const val ACTION_REQUEST_CONNECT = "com.riyadm.socksdroid.action.REQUEST_CONNECT"

    /** Returns the system consent Intent to launch, or null if VPN permission is already granted. */
    fun prepare(context: Context): Intent? = VpnService.prepare(context)

    /**
     * True if connecting requires the app's UI first: the VPN notice hasn't been accepted, Android's
     * VPN consent is missing, or the active profile needs local network access that isn't granted.
     */
    fun needsUserSetup(context: Context): Boolean {
        val app = SocksApp.instance
        return !app.settings.settings.value.vpnDisclosureAccepted ||
            prepare(context) != null ||
            LocalNetwork.isMissingFor(context, app.profiles.active)
    }

    /**
     * Starts the VPN with the active profile (or [profileName] if given, which also becomes active).
     * May throw [IllegalStateException] if the app is not allowed to start a foreground service.
     */
    fun start(context: Context, profileName: String? = null) {
        profileName?.let(SocksApp.instance.profiles::setActive)
        val intent = SocksVpnService.intent(context, SocksVpnService.ACTION_START)
            .putExtra(SocksVpnService.EXTRA_PROFILE, profileName)
        ContextCompat.startForegroundService(context, intent)
    }

    /** Stops the VPN if it is running. */
    fun stop(context: Context) {
        if (VpnStateHolder.state.value.isActive) send(context, SocksVpnService.ACTION_STOP)
    }

    /** Restarts the VPN so profile edits take effect. No-op when disconnected. */
    fun restartIfRunning(context: Context) {
        if (VpnStateHolder.state.value.isActive) send(context, SocksVpnService.ACTION_RESTART)
    }

    /** Delivers [action] to the already running (hence foreground) service. */
    private fun send(context: Context, action: String) {
        try {
            context.startService(SocksVpnService.intent(context, action))
        } catch (e: IllegalStateException) {
            LogBuffer.append(LOG_TAG, "Could not deliver $action: ${e.message}")
        }
    }
}
