package com.riyadm.socksdroid.ui.common

import android.content.Context
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.vpn.VpnController

/** Starts the VPN with the active profile; returns a message if Android refused to start the service. */
fun startVpn(context: Context): UiMessage? = try {
    VpnController.start(context)
    null
} catch (_: IllegalStateException) {
    UiMessage.Text(R.string.home_start_blocked)
}
