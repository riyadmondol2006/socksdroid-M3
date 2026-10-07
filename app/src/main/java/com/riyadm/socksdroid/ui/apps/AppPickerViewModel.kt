package com.riyadm.socksdroid.ui.apps

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

@Immutable
data class AppEntry(val packageName: String, val label: String)

class AppPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val pm: PackageManager = application.packageManager
    private val ownPackage = application.packageName
    private val iconCache = LruCache<String, ImageBitmap>(ICON_CACHE_SIZE)

    /** All installed apps, or null while loading. */
    private var allApps by mutableStateOf<List<AppEntry>?>(null)

    /** Apps selected when the picker opened; they stay pinned to the top so rows don't jump on toggle. */
    private var pinned by mutableStateOf(emptySet<String>())
    private var pinnedInitialized = false

    var query by mutableStateOf("")

    val loading: Boolean get() = allApps == null

    val visibleApps: List<AppEntry> by derivedStateOf {
        val q = query.trim()
        allApps.orEmpty()
            .filter { app ->
                q.isEmpty() || app.label.contains(q, ignoreCase = true) || app.packageName.contains(q, ignoreCase = true)
            }
            // Stable sort: keeps the alphabetical order from loadApps() within each group.
            .sortedBy { it.packageName !in pinned }
    }

    init {
        viewModelScope.launch { allApps = withContext(Dispatchers.IO) { loadApps() } }
    }

    fun pinSelection(selected: Set<String>) {
        if (pinnedInitialized) return
        pinnedInitialized = true
        pinned = selected
    }

    fun cachedIcon(packageName: String): ImageBitmap? = iconCache.get(packageName)

    suspend fun loadIcon(packageName: String, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
                .also { iconCache.put(packageName, it) }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Apps with a launcher icon. Only these are visible to the app: the manifest declares a
     * launcher-intent <queries> element instead of the restricted QUERY_ALL_PACKAGES permission.
     */
    private fun loadApps(): List<AppEntry> {
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent(), PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent(), 0)
        }
        val collator = Collator.getInstance()
        return activities
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ownPackage }
            .map { AppEntry(packageName = it.packageName, label = it.loadLabel(pm).toString()) }
            .sortedWith(compareBy(collator) { it.label })
    }

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private companion object {
        const val ICON_CACHE_SIZE = 128
    }
}
