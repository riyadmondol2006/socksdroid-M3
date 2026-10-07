package com.riyadm.socksdroid

import android.app.Application
import com.riyadm.socksdroid.data.ProfileRepository
import com.riyadm.socksdroid.data.SettingsRepository

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
