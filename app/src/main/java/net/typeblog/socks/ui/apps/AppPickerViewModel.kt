package net.typeblog.socks.ui.apps

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
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
data class AppEntry(val packageName: String, val label: String, val system: Boolean)

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
    var showSystem by mutableStateOf(false)

    val loading: Boolean get() = allApps == null

    val visibleApps: List<AppEntry> by derivedStateOf {
        val q = query.trim()
        allApps.orEmpty()
            .filter { app ->
                (showSystem || !app.system || app.packageName in pinned) &&
                    (q.isEmpty() || app.label.contains(q, ignoreCase = true) || app.packageName.contains(q, ignoreCase = true))
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

    private fun loadApps(): List<AppEntry> {
        val launchable = launcherIntent().let { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        }.mapTo(HashSet()) { it.activityInfo.packageName }

        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        val collator = Collator.getInstance()
        return installed
            .filter { it.packageName != ownPackage }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    // Preinstalled apps with a launcher icon (browsers, stores...) are treated as user apps.
                    system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && info.packageName !in launchable,
                )
            }
            .sortedWith(compareBy(collator) { it.label })
    }

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private companion object {
        const val ICON_CACHE_SIZE = 128
    }
}
