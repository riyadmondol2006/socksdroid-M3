package net.typeblog.socks.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.typeblog.socks.R
import net.typeblog.socks.data.RouteMode
import net.typeblog.socks.ui.common.ActionListItem
import net.typeblog.socks.ui.common.BackButton
import net.typeblog.socks.ui.common.LocalAppSnackbar
import net.typeblog.socks.ui.common.SectionHeader
import net.typeblog.socks.ui.common.SwitchListItem
import net.typeblog.socks.ui.common.readableWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    onChooseApps: () -> Unit,
) {
    val form = viewModel.form
    if (form == null) {
        // The profile was deleted or renamed while this screen was in the back stack.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val errors = viewModel.errors
    val snackbar = LocalAppSnackbar.current
    val resources = LocalResources.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (viewModel.isDirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = viewModel.isDirty) { confirmDiscard = true }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            viewModel.profileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { BackButton(onClick = requestClose) },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save()?.let {
                                snackbar.show(it.resolve(resources))
                                onClose()
                            }
                        },
                        enabled = viewModel.isDirty && !errors.any,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(R.string.action_save)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar.hostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(bottom = 24.dp),
        ) {
            ServerSection(form, errors, viewModel::update)
            AuthSection(form, errors, viewModel::update)
            RoutingSection(form, viewModel::update)
            DnsSection(form, errors, viewModel::update)
            AdvancedSection(form, errors, viewModel::update)
            PerAppSection(form, viewModel::update, onChooseApps)
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.editor_keep_editing)) }
            },
        )
    }
}

private typealias FormUpdate = ((EditorForm) -> EditorForm) -> Unit

private val FieldPadding = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)

@Composable
private fun ServerSection(form: EditorForm, errors: EditorErrors, update: FormUpdate) {
    SectionHeader(stringResource(R.string.editor_section_server))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = FieldPadding.fillMaxWidth()) {
        FormField(
            value = form.server,
            onValueChange = { v -> update { it.copy(server = v) } },
            label = stringResource(R.string.editor_server),
            error = stringResource(R.string.editor_error_server).takeIf { errors.server },
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.weight(1f),
        )
        FormField(
            value = form.port,
            onValueChange = { v -> update { it.copy(port = v.filter(Char::isDigit).take(5)) } },
            label = stringResource(R.string.editor_port),
            error = stringResource(R.string.editor_error_port).takeIf { errors.port },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.width(112.dp),
        )
    }
}

@Composable
private fun AuthSection(form: EditorForm, errors: EditorErrors, update: FormUpdate) {
    SectionHeader(stringResource(R.string.editor_section_auth))
    SwitchListItem(
        headline = stringResource(R.string.editor_use_auth),
        supporting = stringResource(R.string.editor_use_auth_summary),
        icon = Icons.Rounded.Key,
        checked = form.useAuth,
        onCheckedChange = { v -> update { it.copy(useAuth = v) } },
    )
    Reveal(form.useAuth) {
        FormField(
            value = form.username,
            onValueChange = { v -> update { it.copy(username = v) } },
            label = stringResource(R.string.editor_username),
            error = stringResource(R.string.editor_error_username).takeIf { errors.username },
            modifier = FieldPadding.fillMaxWidth(),
        )
        var visible by rememberSaveable { mutableStateOf(false) }
        FormField(
            value = form.password,
            onValueChange = { v -> update { it.copy(password = v) } },
            label = stringResource(R.string.editor_password),
            keyboardType = KeyboardType.Password,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.editor_hide_password else R.string.editor_show_password,
                        ),
                    )
                }
            },
            modifier = FieldPadding.fillMaxWidth(),
        )
    }
}

@Composable
private fun RoutingSection(form: EditorForm, update: FormUpdate) {
    SectionHeader(stringResource(R.string.editor_section_routing))
    Text(
        text = stringResource(R.string.editor_route),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
    )
    val routes = RouteMode.entries
    SingleChoiceSegmentedButtonRow(FieldPadding.padding(vertical = 4.dp).fillMaxWidth()) {
        routes.forEachIndexed { index, route ->
            SegmentedButton(
                selected = form.route == route,
                onClick = { update { it.copy(route = route) } },
                shape = SegmentedButtonDefaults.itemShape(index, routes.size),
                label = {
                    Text(
                        stringResource(
                            when (route) {
                                RouteMode.ALL -> R.string.route_all
                                RouteMode.NON_CHINA -> R.string.route_non_china
                            },
                        ),
                    )
                },
            )
        }
    }
    Text(
        text = stringResource(
            when (form.route) {
                RouteMode.ALL -> R.string.editor_route_all_summary
                RouteMode.NON_CHINA -> R.string.editor_route_non_china_summary
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    SwitchListItem(
        headline = stringResource(R.string.editor_bypass_lan),
        supporting = stringResource(R.string.editor_bypass_lan_summary),
        icon = Icons.Rounded.Lan,
        checked = form.bypassLan,
        onCheckedChange = { v -> update { it.copy(bypassLan = v) } },
    )
}

@Composable
private fun DnsSection(form: EditorForm, errors: EditorErrors, update: FormUpdate) {
    SectionHeader(stringResource(R.string.editor_section_dns))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = FieldPadding.fillMaxWidth()) {
        FormField(
            value = form.dns,
            onValueChange = { v -> update { it.copy(dns = v) } },
            label = stringResource(R.string.editor_dns_server),
            error = stringResource(R.string.editor_error_server).takeIf { errors.dns },
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.weight(1f),
        )
        FormField(
            value = form.dnsPort,
            onValueChange = { v -> update { it.copy(dnsPort = v.filter(Char::isDigit).take(5)) } },
            label = stringResource(R.string.editor_port),
            error = stringResource(R.string.editor_error_port).takeIf { errors.dnsPort },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.width(112.dp),
        )
    }
    Text(
        text = stringResource(R.string.editor_dns_summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun AdvancedSection(form: EditorForm, errors: EditorErrors, update: FormUpdate) {
    SectionHeader(stringResource(R.string.editor_section_advanced))
    SwitchListItem(
        headline = stringResource(R.string.editor_ipv6),
        supporting = stringResource(R.string.editor_ipv6_summary),
        icon = Icons.Rounded.Language,
        checked = form.ipv6,
        onCheckedChange = { v -> update { it.copy(ipv6 = v) } },
    )
    SwitchListItem(
        headline = stringResource(R.string.editor_udp),
        supporting = stringResource(R.string.editor_udp_summary),
        icon = Icons.Rounded.SwapVert,
        checked = form.udp,
        onCheckedChange = { v -> update { it.copy(udp = v) } },
    )
    Reveal(form.udp) {
        FormField(
            value = form.udpGateway,
            onValueChange = { v -> update { it.copy(udpGateway = v) } },
            label = stringResource(R.string.editor_udp_gateway),
            error = stringResource(R.string.editor_error_udp_gateway).takeIf { errors.udpGateway },
            supporting = stringResource(R.string.editor_udp_gateway_summary),
            keyboardType = KeyboardType.Uri,
            modifier = FieldPadding.fillMaxWidth(),
        )
    }
}

@Composable
private fun PerAppSection(form: EditorForm, update: FormUpdate, onChooseApps: () -> Unit) {
    SectionHeader(stringResource(R.string.editor_section_per_app))
    SwitchListItem(
        headline = stringResource(R.string.editor_per_app),
        supporting = stringResource(R.string.editor_per_app_summary),
        icon = Icons.Rounded.Apps,
        checked = form.perApp,
        onCheckedChange = { v -> update { it.copy(perApp = v) } },
    )
    Reveal(form.perApp) {
        val modes = listOf(false to R.string.editor_mode_proxy, true to R.string.editor_mode_bypass)
        SingleChoiceSegmentedButtonRow(FieldPadding.padding(vertical = 4.dp).fillMaxWidth()) {
            modes.forEachIndexed { index, (bypass, label) ->
                SegmentedButton(
                    selected = form.bypassApps == bypass,
                    onClick = { update { it.copy(bypassApps = bypass) } },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        ActionListItem(
            headline = stringResource(R.string.editor_choose_apps),
            supporting = pluralStringResource(R.plurals.editor_apps_selected, form.apps.size, form.apps.size),
            onClick = onChooseApps,
            trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        )
    }
}

@Composable
private fun Reveal(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false,
        ),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        modifier = modifier,
    )
}
