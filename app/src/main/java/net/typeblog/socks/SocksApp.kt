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
    }

    companion object {
        lateinit var instance: SocksApp
            private set
    }
}
