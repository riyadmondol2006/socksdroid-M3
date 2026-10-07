package com.riyadm.socksdroid.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.SocksApp
import com.riyadm.socksdroid.ui.MainActivity

/** Quick Settings toggle for the active profile. */
class ProxyTileService : TileService() {
    private var listening: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = MainScope().apply {
            launch {
                combine(VpnStateHolder.state, SocksApp.instance.profiles.activeName, ::render).collect {}
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when {
            VpnStateHolder.state.value.isActive -> VpnController.stop(this)
            // The app shows the VPN notice and permission prompts before the first connection.
            VpnController.needsUserSetup(this) -> openApp()
            else -> try {
                VpnController.start(this)
            } catch (e: IllegalStateException) {
                LogBuffer.append(LOG_TAG, "Tile could not start the VPN: ${e.message}")
                openApp()
            }
        }
    }

    private fun render(state: VpnState, activeProfile: String) {
        val tile = qsTile ?: return
        tile.state = if (state.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                is VpnState.Connecting -> getString(R.string.vpn_tile_connecting)
                is VpnState.Connected -> state.profileName
                VpnState.Disconnecting -> getString(R.string.vpn_tile_disconnecting)
                is VpnState.Error -> getString(R.string.vpn_tile_error)
                VpnState.Disconnected -> activeProfile
            }
        }
        tile.updateTile()
    }

    /** Opens the app so the user can grant VPN consent; it then connects. */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(VpnController.ACTION_REQUEST_CONNECT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (isLocked) unlockAndRun { launchAndCollapse(intent) } else launchAndCollapse(intent)
    }

    // The Intent overload is the only option below API 34 and is only used there.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchAndCollapse(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
