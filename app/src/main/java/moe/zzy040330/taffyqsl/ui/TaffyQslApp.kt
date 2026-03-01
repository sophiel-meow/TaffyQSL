package moe.zzy040330.taffyqsl.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import moe.zzy040330.taffyqsl.R
import moe.zzy040330.taffyqsl.ui.certificates.CertificatesScreen
import moe.zzy040330.taffyqsl.ui.logs.LogsScreen
import moe.zzy040330.taffyqsl.ui.logs.QsoEditScreen
import moe.zzy040330.taffyqsl.ui.logs.QsoListScreen
import moe.zzy040330.taffyqsl.ui.lotw.LotwScreen
import moe.zzy040330.taffyqsl.ui.settings.AboutScreen
import moe.zzy040330.taffyqsl.ui.settings.LicensesScreen
import moe.zzy040330.taffyqsl.ui.settings.SettingsScreen
import moe.zzy040330.taffyqsl.ui.stations.StationsScreen

sealed class Screen(val route: String, val titleResId: Int) {
    data object Certificates : Screen("certificates", R.string.nav_certificates)
    data object Stations : Screen("stations", R.string.nav_stations)
    data object Logs : Screen("logs", R.string.nav_logs)
    data object Lotw : Screen("lotw", R.string.nav_lotw)
    data object Settings : Screen("settings", R.string.nav_settings)
}

private val topLevelRoutes = setOf(
    Screen.Certificates.route,
    Screen.Stations.route,
    Screen.Logs.route,
    Screen.Lotw.route,
    Screen.Settings.route
)

@Composable
fun TaffyQslApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomNav = currentDestination?.route in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                val navItemColors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                    unselectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    NavigationBarItem(
                        colors = navItemColors,
                        icon = { Icon(Icons.Default.Description, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_logs)) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Logs.route } == true,
                        onClick = {
                            navController.navigate(Screen.Logs.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        colors = navItemColors,
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_stations)) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Stations.route } == true,
                        onClick = {
                            navController.navigate(Screen.Stations.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )

                    NavigationBarItem(
                        colors = navItemColors,
                        icon = { Icon(Icons.Default.Security, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_certificates)) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Certificates.route } == true,
                        onClick = {
                            navController.navigate(Screen.Certificates.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )

                    NavigationBarItem(
                        colors = navItemColors,
                        icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_lotw)) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Lotw.route } == true,
                        onClick = {
                            navController.navigate(Screen.Lotw.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        colors = navItemColors,
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Settings.route } == true,
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Logs.route
        ) {
            composable(Screen.Certificates.route) {
                CertificatesScreen(innerPadding)
            }
            composable(Screen.Stations.route) {
                StationsScreen(innerPadding)
            }
            composable(Screen.Logs.route) {
                LogsScreen(innerPadding, navController)
            }
            composable(Screen.Lotw.route) {
                LotwScreen(
                    innerPadding = innerPadding,
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(innerPadding, navController)
            }
            composable("about") {
                AboutScreen(navController)
            }
            composable("licenses") {
                LicensesScreen(navController)
            }
            composable(
                route = "qso_list/{fileName}",
                arguments = listOf(
                    navArgument("fileName") { type = NavType.StringType }
                )
            ) {
                QsoListScreen(navController = navController)
            }
            composable(
                route = "qso_edit/{fileName}/{qsoId}",
                arguments = listOf(
                    navArgument("fileName") { type = NavType.StringType },
                    navArgument("qsoId") { type = NavType.LongType }
                )
            ) {
                QsoEditScreen(navController = navController)
            }
        }
    }
}
