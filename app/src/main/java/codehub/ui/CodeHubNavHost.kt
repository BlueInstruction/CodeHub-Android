package codehub.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.codehub.R
import codehub.ui.screens.AiScreen
import codehub.ui.screens.BuildScreen
import codehub.ui.screens.DevicesScreen
import codehub.ui.screens.newproject.NewProjectScreen
import codehub.ui.screens.EditorScreen
import codehub.ui.screens.GitScreen
import codehub.ui.screens.TerminalScreen
import codehub.ui.screens.WorkspaceScreen

private sealed class Dest(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    data object Workspace : Dest("workspace", R.string.nav_files, Icons.Filled.Source)
    data object Editor : Dest("editor", R.string.nav_editor, Icons.Filled.Code)
    data object Terminal : Dest("terminal", R.string.nav_terminal, Icons.Filled.Terminal)
    data object Git : Dest("git", R.string.nav_git, Icons.Filled.Code)
    data object Build : Dest("build", R.string.nav_run, Icons.Filled.PlayArrow)
    data object NewProject : Dest("newproject", R.string.nav_run, Icons.Filled.AddCircle)
    data object Ai : Dest("ai", R.string.nav_ai, Icons.Filled.Psychology)
    data object Devices : Dest("devices", R.string.nav_devices, Icons.Filled.Devices)
}

@Composable
fun CodeHubNavHost() {
    val navController = rememberNavController()
    val items = listOf(
        Dest.Workspace,
        Dest.Editor,
        Dest.Terminal,
        Dest.Git,
        Dest.Build,
        Dest.NewProject,
        Dest.Ai,
        Dest.Devices
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                val entry by navController.currentBackStackEntryAsState()
                val current = entry?.destination
                items.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = stringResource(dest.labelRes),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHostContainer(padding, navController)
    }
}

@Composable
private fun NavHostContainer(
    padding: PaddingValues,
    navController: androidx.navigation.NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Dest.Workspace.route,
        modifier = Modifier.fillMaxSize().padding(padding)
    ) {
        composable(Dest.Workspace.route) { WorkspaceScreen() }
        composable(Dest.Editor.route) { EditorScreen() }
        composable(Dest.Terminal.route) { TerminalScreen() }
        composable(Dest.Git.route) { GitScreen() }
        composable(Dest.Build.route) { BuildScreen() }
        composable(Dest.NewProject.route) { NewProjectScreen() }
        composable(Dest.Ai.route) { AiScreen() }
        composable(Dest.Devices.route) { DevicesScreen() }
    }
}
