package net.typeblog.socks.ui.common

import android.app.Application
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.typeblog.socks.SocksApp

/** A user-facing message produced by a ViewModel and resolved against resources by the UI. */
sealed interface UiMessage {
    fun resolve(res: Resources): String

    class Text(@StringRes private val id: Int, private vararg val args: Any) : UiMessage {
        override fun resolve(res: Resources): String = res.getString(id, *args)
    }

    class Plural(@PluralsRes private val id: Int, private val count: Int) : UiMessage {
        override fun resolve(res: Resources): String = res.getQuantityString(id, count, count)
    }
}

/** Base for ViewModels that report one-off results as snackbar messages. */
abstract class MessagingViewModel(application: Application) : AndroidViewModel(application) {
    protected val app: SocksApp get() = getApplication()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    protected fun post(message: UiMessage) {
        _messages.trySend(message)
    }
}

/**
 * App-wide snackbar. Messages are shown from a scope that outlives individual screens, so a
 * confirmation posted right before navigating away still appears on the next screen.
 */
@Stable
class AppSnackbar(val hostState: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(message)
        }
    }
}

val LocalAppSnackbar = staticCompositionLocalOf<AppSnackbar> { error("AppSnackbar not provided") }

/** Shows every message emitted by [messages] while this composable is in the composition. */
@Composable
fun MessagesEffect(messages: Flow<UiMessage>) {
    val snackbar = LocalAppSnackbar.current
    val resources = LocalResources.current
    LaunchedEffect(messages, resources) {
        messages.collect { snackbar.show(it.resolve(resources)) }
    }
}
