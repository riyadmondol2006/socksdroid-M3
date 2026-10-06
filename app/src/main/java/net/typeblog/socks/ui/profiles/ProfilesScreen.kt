package net.typeblog.socks.ui.profiles

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.typeblog.socks.R
import net.typeblog.socks.data.Profile
import net.typeblog.socks.ui.common.LocalAppSnackbar
import net.typeblog.socks.ui.common.MessagesEffect
import net.typeblog.socks.ui.common.ProfileNameDialog
import net.typeblog.socks.ui.common.readableWidth
import net.typeblog.socks.ui.common.shareText

private const val EXPORT_FILE_NAME = "socksdroid-profiles.json"
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onEditProfile: (String) -> Unit,
    viewModel: ProfilesViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    MessagesEffect(viewModel.messages)

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFile)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportFile) }
    val noFilePicker = stringResource(R.string.profiles_no_file_picker)
    fun launchSafely(launch: () -> Unit) = try {
        launch()
    } catch (_: ActivityNotFoundException) {
        snackbar.show(noFilePicker)
    }

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val shareTitle = stringResource(R.string.profiles_share_title)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_profiles)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profiles_import_clipboard)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val clip = clipboard.getClipEntry()?.clipData
                                        val text = clip?.takeIf { it.itemCount > 0 }
                                            ?.getItemAt(0)?.coerceToText(context)?.toString()
                                        viewModel.importLink(text)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profiles_import_file)) },
                                leadingIcon = { Icon(Icons.Rounded.FileOpen, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    launchSafely { importLauncher.launch(IMPORT_MIME_TYPES) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profiles_export_file)) },
                                leadingIcon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    launchSafely { exportLauncher.launch(EXPORT_FILE_NAME) }
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.profiles_new)) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                expanded = fabExpanded,
                onClick = { showCreate = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbar.hostState) },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .readableWidth(),
        ) {
            items(state.profiles, key = { it.name }) { profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.name == state.activeName,
                    canDelete = state.profiles.size > 1,
                    onEdit = { onEditProfile(profile.name) },
                    onSetActive = { viewModel.setActive(profile.name) },
                    onDuplicate = { viewModel.duplicate(profile.name) },
                    onRename = { renameTarget = profile.name },
                    onShare = { withCredentials -> context.shareText(profile.toUri(withCredentials), shareTitle) },
                    onDelete = { deleteTarget = profile.name },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    val existingNames = state.profiles.map { it.name }
    if (showCreate) {
        ProfileNameDialog(
            title = stringResource(R.string.profiles_new),
            confirmLabel = stringResource(R.string.action_create),
            existingNames = existingNames,
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                viewModel.create(name)?.let(onEditProfile)
            },
        )
    }
    renameTarget?.let { target ->
        ProfileNameDialog(
            title = stringResource(R.string.profiles_rename),
            confirmLabel = stringResource(R.string.action_rename),
            existingNames = existingNames,
            initialName = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.rename(target, newName)
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.profiles_delete_title)) },
            text = { Text(stringResource(R.string.profiles_delete_message, target)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.delete(target)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    active: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onShare: (withCredentials: Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val activeDescription = stringResource(if (active) R.string.profiles_active else R.string.profiles_inactive)
    ListItem(
        headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(profile.endpoint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            RadioButton(
                selected = active,
                onClick = onSetActive,
                modifier = Modifier.semantics { stateDescription = activeDescription },
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.profiles_actions_for, profile.name),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    fun dismissThen(action: () -> Unit): () -> Unit = {
                        menuOpen = false
                        action()
                    }
                    MenuItem(R.string.action_edit, Icons.Rounded.Edit, onClick = dismissThen(onEdit))
                    if (!active) {
                        MenuItem(R.string.profiles_set_active, Icons.Rounded.CheckCircle, onClick = dismissThen(onSetActive))
                    }
                    MenuItem(R.string.profiles_duplicate, Icons.Rounded.ContentCopy, onClick = dismissThen(onDuplicate))
                    MenuItem(R.string.profiles_rename, Icons.Rounded.DriveFileRenameOutline, onClick = dismissThen(onRename))
                    MenuItem(R.string.profiles_share, Icons.Rounded.Share, onClick = dismissThen { onShare(true) })
                    if (profile.useAuth) {
                        MenuItem(
                            R.string.profiles_share_no_credentials,
                            Icons.Rounded.LinkOff,
                            onClick = dismissThen { onShare(false) },
                        )
                    }
                    MenuItem(R.string.action_delete, Icons.Rounded.Delete, enabled = canDelete, onClick = dismissThen(onDelete))
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onEdit),
    )
}

@Composable
private fun MenuItem(
    @StringRes text: Int,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}
