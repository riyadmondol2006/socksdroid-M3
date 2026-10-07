package com.riyadm.socksdroid.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.riyadm.socksdroid.SocksApp

/** Connects the active profile after boot when "connect on boot" is enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SocksApp.instance.settings.settings.value.connectOnBoot) return
        if (VpnController.needsUserSetup(context)) {
            LogBuffer.append(LOG_TAG, "Connect on boot skipped: open the app and connect once first")
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
