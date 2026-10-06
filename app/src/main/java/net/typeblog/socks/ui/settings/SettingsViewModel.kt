package net.typeblog.socks.ui.settings

import android.app.Application
import android.app.StatusBarManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.StateFlow
import net.typeblog.socks.R
import net.typeblog.socks.data.AppSettings
import net.typeblog.socks.data.ThemeMode
import net.typeblog.socks.ui.common.MessagingViewModel
import net.typeblog.socks.ui.common.UiMessage

class SettingsViewModel(application: Application) : MessagingViewModel(application) {
    private val repo = app.settings

    val settings: StateFlow<AppSettings> = repo.settings

    fun setThemeMode(mode: ThemeMode) = repo.update { it.copy(themeMode = mode) }

    fun setDynamicColor(enabled: Boolean) = repo.update { it.copy(dynamicColor = enabled) }

    fun setAmoledBlack(enabled: Boolean) = repo.update { it.copy(amoledBlack = enabled) }

    fun setConnectOnBoot(enabled: Boolean) = repo.update { it.copy(connectOnBoot = enabled) }

    fun setAutoReconnect(enabled: Boolean) = repo.update { it.copy(autoReconnect = enabled) }

    fun onSystemSettingsUnavailable() = post(UiMessage.Text(R.string.settings_unavailable))

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onTileRequestResult(result: Int) {
        val message = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.settings_tile_added
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.settings_tile_already_added
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> return
            else -> R.string.settings_tile_failed
        }
        post(UiMessage.Text(message))
    }
}
