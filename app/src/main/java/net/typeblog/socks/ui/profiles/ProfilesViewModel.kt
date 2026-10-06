package net.typeblog.socks.ui.profiles

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.typeblog.socks.R
import net.typeblog.socks.data.Profile
import net.typeblog.socks.ui.common.MessagingViewModel
import net.typeblog.socks.ui.common.UiMessage
import net.typeblog.socks.ui.common.sanitizeProfileName
import net.typeblog.socks.vpn.VpnController
import net.typeblog.socks.vpn.VpnStateHolder
import org.json.JSONException
import java.io.IOException

data class ProfilesUiState(val profiles: List<Profile>, val activeName: String)

class ProfilesViewModel(application: Application) : MessagingViewModel(application) {
    private val repo = app.profiles

    val uiState: StateFlow<ProfilesUiState> = combine(repo.profiles, repo.activeName, ::ProfilesUiState)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProfilesUiState(repo.profiles.value, repo.activeName.value),
        )

    fun setActive(name: String) {
        if (name == repo.activeName.value) return
        repo.setActive(name)
        if (VpnStateHolder.state.value.isActive) {
            VpnController.restartIfRunning(app)
            post(UiMessage.Text(R.string.home_switched_reconnecting, name))
        } else {
            post(UiMessage.Text(R.string.profiles_now_active, name))
        }
    }

    /** Returns the new profile's name, or null if [name] was rejected. */
    fun create(name: String): String? = repo.create(name)?.name

    fun duplicate(name: String) {
        repo.duplicate(name)?.let { post(UiMessage.Text(R.string.profiles_duplicated, it.name)) }
    }

    fun rename(oldName: String, newName: String) {
        if (!repo.rename(oldName, newName)) post(UiMessage.Text(R.string.profiles_rename_failed))
    }

    fun delete(name: String) {
        val wasActive = name == repo.activeName.value
        if (!repo.delete(name)) return
        post(UiMessage.Text(R.string.profiles_deleted, name))
        // The running tunnel used the deleted profile; move it to the new active profile.
        if (wasActive && VpnStateHolder.state.value.isActive) VpnController.restartIfRunning(app)
    }

    fun importLink(text: String?) {
        val profile = text?.let { Profile.fromUri(it, app.getString(R.string.profiles_imported_name)) }
        if (profile == null) {
            post(UiMessage.Text(R.string.profiles_clipboard_invalid))
            return
        }
        val saved = profile.copy(name = repo.uniqueName(sanitizeProfileName(profile.name)))
        repo.save(saved)
        post(UiMessage.Text(R.string.profiles_imported_one, saved.name))
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val count = withContext(Dispatchers.IO) {
                    val json = app.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                        ?: throw IOException()
                    repo.importJson(json)
                }
                UiMessage.Plural(R.plurals.profiles_imported_count, count)
            } catch (_: JSONException) {
                UiMessage.Text(R.string.profiles_import_invalid)
            } catch (_: IllegalArgumentException) {
                UiMessage.Text(R.string.profiles_import_invalid)
            } catch (_: IOException) {
                UiMessage.Text(R.string.profiles_import_failed)
            } catch (_: SecurityException) {
                UiMessage.Text(R.string.profiles_import_failed)
            }
            post(message)
        }
    }

    fun exportFile(uri: Uri) {
        viewModelScope.launch {
            val json = repo.exportJson()
            val message = try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                        ?: throw IOException()
                }
                UiMessage.Plural(R.plurals.profiles_exported_count, repo.profiles.value.size)
            } catch (_: IOException) {
                UiMessage.Text(R.string.profiles_export_failed)
            } catch (_: SecurityException) {
                UiMessage.Text(R.string.profiles_export_failed)
            }
            post(message)
        }
    }
}
