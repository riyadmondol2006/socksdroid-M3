package net.typeblog.socks.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.typeblog.socks.ui.theme.SocksTheme
import net.typeblog.socks.ui.theme.isDark
import net.typeblog.socks.vpn.VpnController

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) viewModel.connect() else viewModel.onConsentDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val relaunchedFromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !relaunchedFromHistory) handleIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = settings.themeMode.isDark()
            // Keep status / navigation bar icon contrast in sync with the in-app theme choice.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            SocksTheme(settings = settings, darkTheme = darkTheme) {
                SocksDroidApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            VpnController.ACTION_REQUEST_CONNECT -> requestConnect()
            Intent.ACTION_VIEW -> viewModel.onImportLink(intent.dataString)
        }
    }

    /** Sent by the Quick Settings tile when VPN consent is still missing. */
    private fun requestConnect() {
        val consent = viewModel.consentIntent() ?: return viewModel.connect()
        try {
            consentLauncher.launch(consent)
        } catch (_: ActivityNotFoundException) {
            viewModel.onConsentUnavailable()
        }
    }
}
