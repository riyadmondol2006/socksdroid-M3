package net.typeblog.socks.vpn

import android.content.Context
import android.content.Intent

/** Entry points for starting / stopping the proxy from the UI, the tile and the boot receiver. */
object VpnController {
    /** Returns the system consent Intent to launch, or null if VPN permission is already granted. */
    fun prepare(context: Context): Intent? = TODO("implemented by the service layer")

    /** Starts the VPN with the active profile (or [profileName] if given, which also becomes active). */
    fun start(context: Context, profileName: String? = null): Unit = TODO("implemented by the service layer")

    /** Stops the VPN if it is running. */
    fun stop(context: Context): Unit = TODO("implemented by the service layer")

    /** Restarts the VPN so profile edits take effect. No-op when disconnected. */
    fun restartIfRunning(context: Context): Unit = TODO("implemented by the service layer")
}
