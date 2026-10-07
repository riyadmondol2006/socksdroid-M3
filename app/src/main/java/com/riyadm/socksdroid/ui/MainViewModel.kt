package com.riyadm.socksdroid.ui

import android.app.Application
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.data.AppSettings
import com.riyadm.socksdroid.data.Profile
import com.riyadm.socksdroid.ui.common.MessagingViewModel
import com.riyadm.socksdroid.ui.common.UiMessage
import com.riyadm.socksdroid.ui.common.sanitizeProfileName

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

    private val _connectRequests = Channel<Unit>(Channel.CONFLATED)

    /** Connection requests from the Quick Settings tile, handled by the connect flow. */
    val connectRequests: Flow<Unit> = _connectRequests.receiveAsFlow()

    fun requestConnect() {
        _connectRequests.trySend(Unit)
    }
}
