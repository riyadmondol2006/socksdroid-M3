package com.riyadm.socksdroid.ui.connect

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.SocksApp
import com.riyadm.socksdroid.data.Profile
import com.riyadm.socksdroid.ui.common.LocalAppSnackbar
import com.riyadm.socksdroid.ui.common.startVpn
import com.riyadm.socksdroid.vpn.LocalNetwork
import com.riyadm.socksdroid.vpn.VpnController
import kotlinx.coroutines.flow.Flow

/**
 * Connects the VPN after the steps that must come first, each only when needed:
 * 1. the in-app VPN disclosure (Google Play VpnService policy), until accepted;
 * 2. the notification permission (Android 13+), asked once;
 * 3. local network access (Android 17+), when the server is on the LAN;
 * 4. the system VPN consent dialog.
 */
@Stable
class ConnectFlow internal constructor(
    private val onConnect: () -> Unit,
    private val onLocalNetwork: (Profile, () -> Unit) -> Unit,
) {
    fun connect() = onConnect()

    /** Asks for local network access if [profile] needs it, then runs [then] whatever the answer. */
    fun withLocalNetworkAccess(profile: Profile, then: () -> Unit) = onLocalNetwork(profile, then)
}

val LocalConnectFlow = staticCompositionLocalOf<ConnectFlow> { error("ConnectFlow not provided") }

/** Provides [LocalConnectFlow] to [content]; [requests] triggers a connection (e.g. from the tile). */
@Composable
fun ConnectFlowHost(requests: Flow<Unit>, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalAppSnackbar.current
    val app = SocksApp.instance
    fun show(id: Int) = snackbar.show(resources.getString(id))

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) startVpn(context)?.let { m -> snackbar.show(m.resolve(resources)) }
        else show(R.string.home_consent_denied)
    }
    fun startNow() {
        startVpn(context)?.let { snackbar.show(it.resolve(resources)) }
    }
    fun requestConsentAndConnect() {
        val consent = VpnController.prepare(context) ?: return startNow()
        try {
            consentLauncher.launch(consent)
        } catch (_: ActivityNotFoundException) {
            show(R.string.home_consent_unavailable)
        }
    }

    var afterLocalNetwork by remember { mutableStateOf<(() -> Unit)?>(null) }
    val localNetworkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) show(R.string.connect_local_network_denied)
        afterLocalNetwork?.invoke()
        afterLocalNetwork = null
    }
    fun withLocalNetworkAccess(profile: Profile, then: () -> Unit) {
        if (!LocalNetwork.isMissingFor(context, profile)) return then()
        afterLocalNetwork = then
        localNetworkLauncher.launch(LocalNetwork.PERMISSION)
    }
    fun localNetworkThenConnect() = withLocalNetworkAccess(app.profiles.active, ::requestConsentAndConnect)

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        localNetworkThenConnect()
    }
    fun notificationsThenContinue() {
        val ask = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !app.settings.settings.value.notificationPromptShown &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (!ask) return localNetworkThenConnect()
        app.settings.update { it.copy(notificationPromptShown = true) }
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    fun connect() {
        if (app.settings.settings.value.vpnDisclosureAccepted) notificationsThenContinue() else showDisclosure = true
    }

    val flow = remember { ConnectFlow(onConnect = ::connect, onLocalNetwork = ::withLocalNetworkAccess) }
    LaunchedEffect(requests) { requests.collect { flow.connect() } }

    if (showDisclosure) {
        VpnDisclosureDialog(
            onAccept = {
                showDisclosure = false
                app.settings.update { it.copy(vpnDisclosureAccepted = true) }
                notificationsThenContinue()
            },
            onDecline = {
                showDisclosure = false
                show(R.string.disclosure_declined)
            },
        )
    }
    CompositionLocalProvider(LocalConnectFlow provides flow, content = content)
}

/** Prominent disclosure of what the VPN does, shown before the system VPN consent dialog. */
@Composable
fun VpnDisclosureDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        icon = { Icon(Icons.Rounded.VpnKey, contentDescription = null) },
        title = { Text(stringResource(R.string.disclosure_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.disclosure_intro), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.disclosure_data), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.disclosure_encryption), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.disclosure_control), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.disclosure_accept)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.disclosure_decline)) } },
    )
}
