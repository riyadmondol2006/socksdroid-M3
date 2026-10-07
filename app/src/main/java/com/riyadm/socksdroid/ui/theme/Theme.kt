package com.riyadm.socksdroid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.riyadm.socksdroid.data.AppSettings
import com.riyadm.socksdroid.data.ThemeMode

val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SocksTheme(
    settings: AppSettings,
    darkTheme: Boolean = settings.themeMode.isDark(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = settings.dynamicColor && supportsDynamicColor
    val colorScheme = remember(context, darkTheme, useDynamic, settings.amoledBlack) {
        val base = when {
            useDynamic && darkTheme -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            darkTheme -> IndigoDarkColorScheme
            else -> IndigoLightColorScheme
        }
        if (darkTheme && settings.amoledBlack) base.toAmoled() else base
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
