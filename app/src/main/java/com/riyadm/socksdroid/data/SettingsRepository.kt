package com.riyadm.socksdroid.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You wallpaper-based colors (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Pure black surfaces in dark theme. */
    val amoledBlack: Boolean = false,
    /** Start the active profile when the device boots (requires VPN consent already granted). */
    val connectOnBoot: Boolean = false,
    /** Reconnect automatically if tun2socks exits unexpectedly. */
    val autoReconnect: Boolean = true,
)

/** App-wide preferences, read synchronously at startup so the first frame uses the right theme. */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val new = transform(_settings.value)
        prefs.edit {
            putString("theme_mode", new.themeMode.name)
            putBoolean("dynamic_color", new.dynamicColor)
            putBoolean("amoled_black", new.amoledBlack)
            putBoolean("connect_on_boot", new.connectOnBoot)
            putBoolean("auto_reconnect", new.autoReconnect)
        }
        _settings.value = new
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null)!!) }.getOrDefault(d.themeMode),
            dynamicColor = prefs.getBoolean("dynamic_color", d.dynamicColor),
            amoledBlack = prefs.getBoolean("amoled_black", d.amoledBlack),
            connectOnBoot = prefs.getBoolean("connect_on_boot", d.connectOnBoot),
            autoReconnect = prefs.getBoolean("auto_reconnect", d.autoReconnect),
        )
    }
}
