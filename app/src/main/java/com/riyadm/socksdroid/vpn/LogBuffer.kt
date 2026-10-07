package com.riyadm.socksdroid.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory ring buffer of service / native daemon output, shown on the Logs screen. */
object LogBuffer {
    private const val MAX_LINES = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow(emptyList<String>())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun append(tag: String, message: String) {
        val line = "${synchronized(timeFormat) { timeFormat.format(Date()) }} [$tag] $message"
        _lines.update { (it + line).takeLast(MAX_LINES) }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
