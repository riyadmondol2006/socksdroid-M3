package net.typeblog.socks.ui.settings

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.typeblog.socks.BuildConfig
import net.typeblog.socks.R
import net.typeblog.socks.data.ThemeMode
import net.typeblog.socks.ui.common.ActionListItem
import net.typeblog.socks.ui.common.InfoListItem
import net.typeblog.socks.ui.common.LocalAppSnackbar
import net.typeblog.socks.ui.common.MessagesEffect
import net.typeblog.socks.ui.common.SectionHeader
import net.typeblog.socks.ui.common.SwitchListItem
import net.typeblog.socks.ui.common.readableWidth
import net.typeblog.socks.ui.common.startActivitySafely
import net.typeblog.socks.ui.theme.supportsDynamicColor

private const val SOURCE_URL = "https://github.com/riyadmondol2006/SocksDroid"
private const val PRIVACY_URL = "https://github.com/riyadmondol2006/SocksDroid/blob/master/PRIVACY.md"
private const val LICENSE_URL = "https://github.com/riyadmondol2006/SocksDroid/blob/master/LICENSE"
private const val UPSTREAM_URL = "https://github.com/PeterCxy/SocksDroid"
private const val TILE_SERVICE_CLASS = "net.typeblog.socks.vpn.ProxyTileService"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenLogs: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MessagesEffect(viewModel.messages)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    fun open(intent: Intent) {
        if (!context.startActivitySafely(intent)) viewModel.onSystemSettingsUnavailable()
    }
    fun openUrl(url: String) = open(Intent(Intent.ACTION_VIEW, url.toUri()))

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(LocalAppSnackbar.current.hostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(bottom = 16.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_appearance))
            ThemeModeItem(settings.themeMode, viewModel::setThemeMode)
            if (supportsDynamicColor) {
                SwitchListItem(
                    headline = stringResource(R.string.settings_dynamic_color),
                    supporting = stringResource(R.string.settings_dynamic_color_summary),
                    icon = Icons.Rounded.ColorLens,
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }
            SwitchListItem(
                headline = stringResource(R.string.settings_amoled),
                supporting = stringResource(R.string.settings_amoled_summary),
                icon = Icons.Rounded.Contrast,
                checked = settings.amoledBlack,
                onCheckedChange = viewModel::setAmoledBlack,
            )

            SectionHeader(stringResource(R.string.settings_section_connection))
            SwitchListItem(
                headline = stringResource(R.string.settings_connect_on_boot),
                supporting = stringResource(R.string.settings_connect_on_boot_summary),
                icon = Icons.Rounded.RestartAlt,
                checked = settings.connectOnBoot,
                onCheckedChange = viewModel::setConnectOnBoot,
            )
            SwitchListItem(
                headline = stringResource(R.string.settings_auto_reconnect),
                supporting = stringResource(R.string.settings_auto_reconnect_summary),
                icon = Icons.Rounded.Autorenew,
                checked = settings.autoReconnect,
                onCheckedChange = viewModel::setAutoReconnect,
            )
            ActionListItem(
                headline = stringResource(R.string.settings_always_on),
                supporting = stringResource(R.string.settings_always_on_summary),
                icon = Icons.Rounded.VpnLock,
                onClick = { open(Intent(Settings.ACTION_VPN_SETTINGS)) },
                trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                NotificationPermissionItem(onOpenSettings = ::open)
                ActionListItem(
                    headline = stringResource(R.string.settings_tile),
                    supporting = stringResource(R.string.settings_tile_summary),
                    icon = Icons.Rounded.Dashboard,
                    onClick = { requestAddTile(context, viewModel::onTileRequestResult) },
                )
            } else {
                InfoListItem(
                    headline = stringResource(R.string.settings_tile),
                    supporting = stringResource(R.string.settings_tile_hint),
                    icon = Icons.Rounded.Dashboard,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_troubleshooting))
            ActionListItem(
                headline = stringResource(R.string.settings_logs),
                supporting = stringResource(R.string.settings_logs_summary),
                icon = Icons.AutoMirrored.Rounded.Article,
                onClick = onOpenLogs,
                trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            )

            SectionHeader(stringResource(R.string.settings_section_about))
            InfoListItem(
                headline = stringResource(R.string.settings_version),
                supporting = BuildConfig.VERSION_NAME,
                icon = Icons.Rounded.Info,
            )
            LinkItem(R.string.settings_source, stringResource(R.string.settings_source_summary), Icons.Rounded.Code) {
                openUrl(SOURCE_URL)
            }
            LinkItem(R.string.settings_privacy, null, Icons.Rounded.PrivacyTip) { openUrl(PRIVACY_URL) }
            LinkItem(R.string.settings_license, stringResource(R.string.settings_license_summary), Icons.Rounded.Gavel) {
                openUrl(LICENSE_URL)
            }
            LinkItem(R.string.settings_credits, stringResource(R.string.settings_credits_summary), Icons.Rounded.Favorite) {
                openUrl(UPSTREAM_URL)
            }
        }
    }
}

@Composable
private fun ThemeModeItem(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
    val modes = ThemeMode.entries
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        leadingContent = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                modes.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = mode == item,
                        onClick = { onModeChange(item) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        label = {
                            Text(
                                stringResource(
                                    when (item) {
                                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                                        ThemeMode.LIGHT -> R.string.settings_theme_light
                                        ThemeMode.DARK -> R.string.settings_theme_dark
                                    },
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun LinkItem(
    @StringRes headline: Int,
    supporting: String?,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ActionListItem(
        headline = stringResource(headline),
        supporting = supporting,
        icon = icon,
        onClick = onClick,
        trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
    )
}

/** Shown only while notifications are blocked; re-checked whenever the screen resumes. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun NotificationPermissionItem(onOpenSettings: (Intent) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(isGranted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = isGranted() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        // Once the system stops showing the prompt, send the user to the app's notification settings.
        val promptBlocked = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false
        if (!result && promptBlocked) {
            onOpenSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    if (granted) return
    ActionListItem(
        headline = stringResource(R.string.settings_notifications),
        supporting = stringResource(R.string.settings_notifications_summary),
        icon = Icons.Rounded.Notifications,
        onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context, onResult: (Int) -> Unit) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, TILE_SERVICE_CLASS),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_vpn),
        context.mainExecutor,
    ) { onResult(it) }
}
