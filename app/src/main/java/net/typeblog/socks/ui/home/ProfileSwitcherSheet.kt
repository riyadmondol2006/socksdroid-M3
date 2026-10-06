package net.typeblog.socks.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.typeblog.socks.R
import net.typeblog.socks.data.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherSheet(
    profiles: List<Profile>,
    activeName: String,
    onSelect: (String) -> Unit,
    onNewProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = profiles.size < 6)
    val scope = rememberCoroutineScope()
    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            action()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(R.string.home_choose_profile),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        LazyColumn(Modifier.selectableGroup()) {
            items(profiles, key = { it.name }) { profile ->
                val selected = profile.name == activeName
                ListItem(
                    headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(profile.endpoint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { hideThen { onSelect(profile.name) } },
                        ),
                )
            }
            item(key = "new") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profiles_new)) },
                    leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                        .clickable(role = Role.Button, onClick = { hideThen(onNewProfile) }),
                )
            }
        }
    }
}
