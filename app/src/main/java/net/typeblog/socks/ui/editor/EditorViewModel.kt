package net.typeblog.socks.ui.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import net.typeblog.socks.R
import net.typeblog.socks.SocksApp
import net.typeblog.socks.ui.Routes
import net.typeblog.socks.ui.common.UiMessage
import net.typeblog.socks.vpn.VpnController
import net.typeblog.socks.vpn.VpnStateHolder

/**
 * Holds the in-progress edit of one profile. Shared with the app picker, which is a nested
 * destination of the editor and writes its selection straight into [form].
 */
class EditorViewModel(application: Application, private val savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val app: SocksApp = getApplication()
    private val repo = app.profiles

    val profileName: String = checkNotNull(savedStateHandle[Routes.ARG_NAME])

    private var saved by mutableStateOf(repo.get(profileName)?.let(EditorForm::from))

    /** The form being edited, or null if the profile no longer exists. Survives process death. */
    var form by mutableStateOf(
        saved?.let { s -> savedStateHandle.get<String>(KEY_FORM)?.let { EditorForm.fromJson(it) } ?: s },
    )
        private set

    val errors: EditorErrors get() = form?.validate() ?: EditorErrors()

    val isDirty: Boolean get() = form != saved

    fun update(transform: (EditorForm) -> EditorForm) {
        form = form?.let(transform)
        savedStateHandle[KEY_FORM] = form?.toJson()
    }

    fun toggleApp(packageName: String) = update {
        it.copy(apps = if (packageName in it.apps) it.apps - packageName else it.apps + packageName)
    }

    fun setApps(apps: Set<String>) = update { it.copy(apps = apps) }

    /**
     * Persists the form and returns the confirmation to show, or null if the form is invalid.
     * The confirmation is returned rather than posted because the editor closes right away.
     */
    fun save(): UiMessage? {
        val current = form ?: return null
        val profile = current.toProfile(profileName) ?: return null
        repo.save(profile)
        saved = current
        savedStateHandle.remove<String>(KEY_FORM)
        // The tunnel always runs the active profile (its state may still carry a pre-rename name).
        val running = VpnStateHolder.state.value.isActive && repo.activeName.value == profileName
        if (!running) return UiMessage.Text(R.string.editor_saved)
        VpnController.restartIfRunning(app)
        return UiMessage.Text(R.string.editor_saved_reconnecting)
    }

    private companion object {
        const val KEY_FORM = "editor_form"
    }
}
