package net.typeblog.socks.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import net.typeblog.socks.R
import net.typeblog.socks.data.ProfileRepository

enum class NameError(@StringRes val message: Int) {
    EMPTY(R.string.name_error_empty),
    TAKEN(R.string.name_error_taken),
}

/**
 * Collapses whitespace (including line breaks, which the profile list uses as a separator) in a
 * name that came from outside the app, such as a shared link.
 */
fun sanitizeProfileName(name: String): String = ProfileRepository.sanitizeName(name)

/** Validates a new profile name; [current] is the name being renamed, which may be kept. */
fun validateProfileName(name: String, existing: Collection<String>, current: String? = null): NameError? {
    val trimmed = sanitizeProfileName(name)
    return when {
        trimmed.isEmpty() -> NameError.EMPTY
        trimmed != current && trimmed in existing -> NameError.TAKEN
        else -> null
    }
}

/** Dialog asking for a profile name, used for both creating and renaming profiles. */
@Composable
fun ProfileNameDialog(
    title: String,
    confirmLabel: String,
    existingNames: Collection<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialName: String = "",
) {
    val current = initialName.takeIf { it.isNotEmpty() }
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length)))
    }
    val error = validateProfileName(value.text, existingNames, current)
    val canConfirm = error == null && sanitizeProfileName(value.text) != current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.copy(text = it.text.replace("\n", "")) },
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true,
                isError = error == NameError.TAKEN,
                supportingText = { if (error == NameError.TAKEN) Text(stringResource(error.message)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(sanitizeProfileName(value.text)) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sanitizeProfileName(value.text)) }, enabled = canConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
