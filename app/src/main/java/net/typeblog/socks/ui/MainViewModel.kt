package net.typeblog.socks.ui

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.typeblog.socks.R
import net.typeblog.socks.data.AppSettings
import net.typeblog.socks.data.Profile
import net.typeblog.socks.ui.common.MessagingViewModel
import net.typeblog.socks.ui.common.UiMessage
import net.typeblog.socks.ui.common.sanitizeProfileName
import net.typeblog.socks.ui.common.startVpn
import net.typeblog.socks.vpn.VpnController

/** Activity-scoped state: theme settings, `socks5://` link imports and connect requests from the tile. */
class MainViewModel(application: Application) : MessagingViewModel(application) {
    val settings: StateFlow<AppSettings> = app.settings.settings

    private val _pendingImport = MutableStateFlow<Profile?>(null)

    /** A profile parsed from an incoming link, waiting for the user to confirm the import. */
    val pendingImport: StateFlow<Profile?> = _pendingImport.asStateFlow()

    fun onImportLink(link: String?) {
        val profile = link?.let { Profile.fromUri(it, app.getString(R.string.profiles_imported_name)) }
        if (profile == null) {
            post(UiMessage.Text(R.string.profiles_link_invalid))
            return
        }
        _pendingImport.value = profile.copy(name = sanitizeProfileName(profile.name))
    }

    fun confirmImport() {
        val profile = _pendingImport.value ?: return
        _pendingImport.value = null
        val saved = profile.copy(name = app.profiles.uniqueName(profile.name))
        app.profiles.save(saved)
        post(UiMessage.Text(R.string.profiles_imported_one, saved.name))
    }

    fun dismissImport() {
        _pendingImport.value = null
    }

    /** The system VPN consent intent, or null if consent was already granted. */
    fun consentIntent(): Intent? = VpnController.prepare(app)

    fun connect() {
        startVpn(app)?.let(::post)
    }

    fun onConsentDenied() = post(UiMessage.Text(R.string.home_consent_denied))

    fun onConsentUnavailable() = post(UiMessage.Text(R.string.home_consent_unavailable))
}
