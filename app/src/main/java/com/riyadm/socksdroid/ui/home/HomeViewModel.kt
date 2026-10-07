package com.riyadm.socksdroid.ui.home

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.data.Profile
import com.riyadm.socksdroid.ui.common.MessagingViewModel
import com.riyadm.socksdroid.ui.common.UiMessage
import com.riyadm.socksdroid.ui.common.startVpn
import com.riyadm.socksdroid.vpn.ProxyTestResult
import com.riyadm.socksdroid.vpn.ProxyTester
import com.riyadm.socksdroid.vpn.VpnController
import com.riyadm.socksdroid.vpn.VpnState
import com.riyadm.socksdroid.vpn.VpnStateHolder

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Done(val result: ProxyTestResult) : TestState
}

data class HomeUiState(
    val vpnState: VpnState,
    val profiles: List<Profile>,
    val activeProfile: Profile,
    val test: TestState,
)

class HomeViewModel(application: Application) : MessagingViewModel(application) {
    private val repo = app.profiles
    private val uiPrefs = application.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)

    /** Test result for the profile it was run against; cleared whenever that profile changes. */
    private val testState = MutableStateFlow<Pair<Profile, TestState>?>(null)
    private var testJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        VpnStateHolder.state,
        repo.profiles,
        repo.activeName,
        testState,
    ) { vpn, profiles, activeName, test ->
        val active = profiles.firstOrNull { it.name == activeName } ?: profiles.first()
        HomeUiState(
            vpnState = vpn,
            profiles = profiles,
            activeProfile = active,
            test = test?.takeIf { it.first == active }?.second ?: TestState.Idle,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(VpnStateHolder.state.value, repo.profiles.value, repo.active, TestState.Idle),
    )

    /** Whether the one-time notification permission prompt has been shown before connecting. */
    val notificationPromptShown: Boolean
        get() = uiPrefs.getBoolean(KEY_NOTIFICATION_PROMPT, false)

    fun markNotificationPromptShown() {
        uiPrefs.edit { putBoolean(KEY_NOTIFICATION_PROMPT, true) }
    }

    /** The system VPN consent intent, or null if consent was already granted. */
    fun consentIntent(): Intent? = VpnController.prepare(app)

    fun connect() {
        startVpn(app)?.let(::post)
    }

    fun disconnect() = VpnController.stop(app)

    fun onConsentDenied() = post(UiMessage.Text(R.string.home_consent_denied))

    fun onConsentUnavailable() = post(UiMessage.Text(R.string.home_consent_unavailable))

    fun selectProfile(name: String) {
        if (name == repo.activeName.value) return
        repo.setActive(name)
        if (VpnStateHolder.state.value.isActive) {
            VpnController.restartIfRunning(app)
            post(UiMessage.Text(R.string.home_switched_reconnecting, name))
        }
    }

    /** Creates a profile, makes it active and returns its name, or null if [name] was rejected. */
    fun createProfile(name: String): String? {
        val profile = repo.create(name) ?: return null
        selectProfile(profile.name)
        return profile.name
    }

    fun testConnection() {
        val profile = uiState.value.activeProfile
        testJob?.cancel()
        testState.value = profile to TestState.Running
        testJob = viewModelScope.launch {
            val result = try {
                ProxyTester.test(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProxyTestResult.Failure(e.message ?: e.javaClass.simpleName)
            }
            testState.value = profile to TestState.Done(result)
        }
    }

    private companion object {
        const val UI_PREFS = "ui"
        const val KEY_NOTIFICATION_PROMPT = "notification_prompt_shown"
    }
}
