package com.riyadm.socksdroid.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** Starts [intent], returning false instead of crashing when no activity can handle it. */
fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Opens the Android sharesheet for plain [text]. */
fun Context.shareText(text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    startActivitySafely(Intent.createChooser(send, chooserTitle))
}
