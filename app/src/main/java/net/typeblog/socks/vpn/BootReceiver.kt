package net.typeblog.socks.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.typeblog.socks.SocksApp

/** Connects the active profile after boot when "connect on boot" is enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SocksApp.instance.settings.settings.value.connectOnBoot) return
        if (VpnController.prepare(context) != null) {
            LogBuffer.append(LOG_TAG, "Connect on boot skipped: VPN permission not granted")
            return
        }
        try {
            VpnController.start(context)
        } catch (e: IllegalStateException) {
            // Includes ForegroundServiceStartNotAllowedException (API 31+).
            LogBuffer.append(LOG_TAG, "Connect on boot failed: ${e.message}")
        }
    }
}
