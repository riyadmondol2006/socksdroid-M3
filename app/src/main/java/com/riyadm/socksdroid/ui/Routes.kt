package com.riyadm.socksdroid.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.riyadm.socksdroid.R

object Routes {
    const val ARG_NAME = "name"

    const val HOME = "home"
    const val PROFILES = "profiles"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
    const val EDITOR = "editor/{$ARG_NAME}"
    const val APPS = "editor/{$ARG_NAME}/apps"

    fun editor(profileName: String) = "editor/${Uri.encode(profileName)}"

    fun apps(profileName: String) = "editor/${Uri.encode(profileName)}/apps"
}

enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    PROFILES(Routes.PROFILES, R.string.nav_profiles, Icons.Rounded.Dns, Icons.Outlined.Dns),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}
