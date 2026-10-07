package com.riyadm.socksdroid.ui.home

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.ui.connect.LocalConnectFlow
import com.riyadm.socksdroid.ui.common.LocalAppSnackbar
import com.riyadm.socksdroid.ui.common.MessagesEffect
import com.riyadm.socksdroid.ui.common.ProfileNameDialog
import com.riyadm.socksdroid.ui.common.readableWidth
import com.riyadm.socksdroid.vpn.VpnState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onEditProfile: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MessagesEffect(viewModel.messages)

    val connectFlow = LocalConnectFlow.current
    val onToggle: () -> Unit = {
        if (state.vpnState.isActive) viewModel.disconnect() else connectFlow.connect()
    }

    var showSwitcher by rememberSaveable { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Medium) },
            )
        },
        snackbarHost = { SnackbarHost(LocalAppSnackbar.current.hostState) },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            val minHeight = maxHeight
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .readableWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val statusText = statusText(state.vpnState)
                ConnectButton(
                    state = state.vpnState,
                    statusDescription = statusText,
                    onClick = onToggle,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                StatusBlock(state.vpnState, statusText)
                ActiveProfileCard(
                    profile = state.activeProfile,
                    test = state.test,
                    onSwitch = { showSwitcher = true },
                    onEdit = { onEditProfile(state.activeProfile.name) },
                    onTest = { connectFlow.withLocalNetworkAccess(state.activeProfile, viewModel::testConnection) },
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }
        }
    }

    if (showSwitcher) {
        ProfileSwitcherSheet(
            profiles = state.profiles,
            activeName = state.activeProfile.name,
            onSelect = viewModel::selectProfile,
            onNewProfile = { showCreate = true },
            onDismiss = { showSwitcher = false },
        )
    }
    if (showCreate) {
        ProfileNameDialog(
            title = stringResource(R.string.profiles_new),
            confirmLabel = stringResource(R.string.action_create),
            existingNames = state.profiles.map { it.name },
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                viewModel.createProfile(name)?.let(onEditProfile)
            },
        )
    }
}

@Composable
private fun statusText(state: VpnState): String = when (state) {
    VpnState.Disconnected -> stringResource(R.string.home_status_disconnected)
    is VpnState.Connecting -> stringResource(R.string.home_status_connecting)
    is VpnState.Connected -> stringResource(R.string.home_status_connected)
    VpnState.Disconnecting -> stringResource(R.string.home_status_disconnecting)
    is VpnState.Error -> stringResource(R.string.home_status_error)
}

@Composable
private fun StatusBlock(state: VpnState, statusText: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = statusText,
            transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() },
            label = "status",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) { text ->
            Text(text, style = MaterialTheme.typography.headlineSmall)
        }
        when (state) {
            is VpnState.Connected -> {
                ElapsedTime(since = state.since)
                Text(
                    text = stringResource(R.string.home_via_profile, state.profileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is VpnState.Connecting -> Text(
                text = state.profileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is VpnState.Error -> Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            else -> Unit
        }
    }
}

/** Connection duration that ticks once per second, aligned to whole seconds since [since]. */
@Composable
private fun ElapsedTime(since: Long) {
    val elapsedMs by produceState(SystemClock.elapsedRealtime() - since, since) {
        while (true) {
            value = SystemClock.elapsedRealtime() - since
            delay(1_000 - value.mod(1_000L))
        }
    }
    Text(
        text = DateUtils.formatElapsedTime(elapsedMs.coerceAtLeast(0) / 1_000),
        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
