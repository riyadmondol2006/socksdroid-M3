package net.typeblog.socks

import android.app.Application
import net.typeblog.socks.data.ProfileRepository
import net.typeblog.socks.data.SettingsRepository

class SocksApp : Application() {
    lateinit var profiles: ProfileRepository
        private set
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        profiles = ProfileRepository(this)
        settings = SettingsRepository(this)
        // 1.x stored "connect on boot" per profile; carry the active profile's choice over.
        if (profiles.legacyAutoConnect) settings.update { it.copy(connectOnBoot = true) }
    }

    companion object {
        lateinit var instance: SocksApp
            private set
    }
}
