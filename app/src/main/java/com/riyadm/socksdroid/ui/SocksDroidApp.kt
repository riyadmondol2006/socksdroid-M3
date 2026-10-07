package com.riyadm.socksdroid.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.data.Profile
import com.riyadm.socksdroid.ui.apps.AppPickerScreen
import com.riyadm.socksdroid.ui.common.AppSnackbar
import com.riyadm.socksdroid.ui.common.LocalAppSnackbar
import com.riyadm.socksdroid.ui.common.MessagesEffect
import com.riyadm.socksdroid.ui.connect.ConnectFlowHost
import com.riyadm.socksdroid.ui.editor.EditorViewModel
import com.riyadm.socksdroid.ui.editor.ProfileEditorScreen
import com.riyadm.socksdroid.ui.home.HomeScreen
import com.riyadm.socksdroid.ui.logs.LogsScreen
import com.riyadm.socksdroid.ui.profiles.ProfilesScreen
import com.riyadm.socksdroid.ui.settings.SettingsScreen

/** Below this width the top-level destinations live in a bottom bar, above it in a side rail. */
private val RailBreakpoint = 600.dp

@Composable
fun SocksDroidApp(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbar = remember(hostState, scope) { AppSnackbar(hostState, scope) }

    CompositionLocalProvider(LocalAppSnackbar provides snackbar) {
        ConnectFlowHost(requests = mainViewModel.connectRequests) {
            MessagesEffect(mainViewModel.messages)
            val backStackEntry by navController.currentBackStackEntryAsState()
            val destination = backStackEntry?.destination
            val currentTopLevel = TopLevelDestination.entries.firstOrNull { top ->
                destination?.hierarchy?.any { it.route == top.route } == true
            }
            val showNavigation = destination == null || currentTopLevel != null

            BoxWithConstraints {
                val useRail = maxWidth >= RailBreakpoint
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        if (showNavigation && !useRail) {
                            NavigationBar {
                                TopLevelDestination.entries.forEach { top ->
                                    val selected = top == currentTopLevel
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navController.navigateTopLevel(top.route) },
                                        icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null) },
                                        label = { Text(stringResource(top.label)) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    Row(
                        Modifier
                            .padding(padding)
                            .consumeWindowInsets(padding),
                    ) {
                        val railShown = showNavigation && useRail
                        if (railShown) {
                            NavigationRail {
                                Column(Modifier.padding(top = 8.dp)) {
                                    TopLevelDestination.entries.forEach { top ->
                                        val selected = top == currentTopLevel
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { navController.navigateTopLevel(top.route) },
                                            icon = {
                                                Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null)
                                            },
                                            label = { Text(stringResource(top.label)) },
                                        )
                                    }
                                }
                            }
                        }
                        AppNavHost(
                            navController = navController,
                            modifier = if (railShown) {
                                Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }

            val pendingImport by mainViewModel.pendingImport.collectAsStateWithLifecycle()
            pendingImport?.let { profile ->
                ImportLinkDialog(
                    profile = profile,
                    onConfirm = mainViewModel::confirmImport,
                    onDismiss = mainViewModel::dismissImport,
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        // Material "fade through" between top-level destinations.
        enterTransition = { fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), 0.94f) },
        exitTransition = { fadeOut(tween(90)) },
        popEnterTransition = { fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), 0.94f) },
        popExitTransition = { fadeOut(tween(90)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(onEditProfile = { navController.navigate(Routes.editor(it)) })
        }
        composable(Routes.PROFILES) {
            ProfilesScreen(onEditProfile = { navController.navigate(Routes.editor(it)) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onOpenLogs = { navController.navigate(Routes.LOGS) })
        }
        detail(Routes.EDITOR, arguments = listOf(navArgument(Routes.ARG_NAME) { type = NavType.StringType })) { entry ->
            val editor: EditorViewModel = viewModel()
            ProfileEditorScreen(
                viewModel = editor,
                onClose = { navController.popIfCurrent(entry) },
                onChooseApps = { navController.navigate(Routes.apps(editor.profileName)) },
            )
        }
        detail(Routes.APPS, arguments = listOf(navArgument(Routes.ARG_NAME) { type = NavType.StringType })) { entry ->
            // The picker edits the selection held by the editor below it in the back stack.
            val editorEntry = remember(entry) { navController.getBackStackEntry(Routes.EDITOR) }
            AppPickerScreen(
                editor = viewModel(editorEntry),
                onBack = { navController.popIfCurrent(entry) },
            )
        }
        detail(Routes.LOGS) { entry ->
            LogsScreen(onBack = { navController.popIfCurrent(entry) })
        }
    }
}

/** A pushed destination that slides in from the end edge (and back out on pop / predictive back). */
private fun NavGraphBuilder.detail(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = {
            slideIntoContainer(SlideDirection.Start, tween(300)) { it / 4 } + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(SlideDirection.End, tween(250)) { it / 4 } + fadeOut(tween(250))
        },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) },
        content = { content(it) },
    )
}

private fun NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Pops [entry] only if it is still on top, so a double tap on "back" can't pop twice. */
private fun NavController.popIfCurrent(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

@Composable
private fun ImportLinkDialog(profile: Profile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_message))
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(profile.endpoint, style = MaterialTheme.typography.bodyMedium)
                if (profile.useAuth) {
                    Text(
                        stringResource(R.string.import_user, profile.username),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_import)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
