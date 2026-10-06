package net.typeblog.socks.ui.logs

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.typeblog.socks.vpn.LogBuffer

class LogsViewModel : ViewModel() {
    val lines: StateFlow<List<String>> = LogBuffer.lines

    /** All lines joined for copying or sharing. */
    fun text(): String = lines.value.joinToString("\n")

    fun clear() = LogBuffer.clear()
}
