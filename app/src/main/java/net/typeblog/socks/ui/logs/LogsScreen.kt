package net.typeblog.socks.ui.logs

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.typeblog.socks.R
import net.typeblog.socks.ui.common.BackButton
import net.typeblog.socks.ui.common.LocalAppSnackbar
import net.typeblog.socks.ui.common.readableWidth
import net.typeblog.socks.ui.common.shareText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = viewModel(),
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val copiedMessage = stringResource(R.string.logs_copied)
    val shareTitle = stringResource(R.string.logs_share_title)

    // Follow new output.
    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    val hasLines = lines.isNotEmpty()
                    IconButton(
                        enabled = hasLines,
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(shareTitle, viewModel.text())))
                                snackbar.show(copiedMessage)
                            }
                        },
                    ) { Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.logs_copy)) }
                    IconButton(enabled = hasLines, onClick = { context.shareText(viewModel.text(), shareTitle) }) {
                        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.logs_share))
                    }
                    IconButton(enabled = hasLines, onClick = viewModel::clear) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.logs_clear))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar.hostState) },
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .consumeWindowInsets(padding)
        if (lines.isEmpty()) {
            Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.logs_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = modifier.readableWidth(),
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
