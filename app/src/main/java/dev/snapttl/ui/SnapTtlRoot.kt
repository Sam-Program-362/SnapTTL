package dev.snapttl.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.snapttl.di.Services
import dev.snapttl.ui.allScreenshots.AllScreenshotsScreen
import dev.snapttl.ui.easteregg.EasterEggScreen
import dev.snapttl.ui.home.HomeScreen
import dev.snapttl.ui.navigation.Routes
import dev.snapttl.ui.navigation.Tabs
import dev.snapttl.ui.scheduled.ScheduledScreen
import dev.snapttl.ui.search.SearchScreen
import dev.snapttl.ui.settings.SettingsScreen

@Composable
fun SnapTtlRoot(services: Services) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tabs.entries.forEach { route ->
                    val selected = currentRoute == route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tabIcon(route),
                                contentDescription = tabLabel(route)
                            )
                        },
                        label = { Text(tabLabel(route)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenEasterEgg = { navController.navigate(Routes.EASTER_EGG) }
                )
            }
            composable(Routes.SCHEDULED) { ScheduledScreen() }
            composable(Routes.SCREENSHOTS) { AllScreenshotsScreen() }
            composable(Routes.SEARCH) { SearchScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.EASTER_EGG) {
                EasterEggScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun tabIcon(route: String) = when (route) {
    Routes.HOME -> Icons.Default.Home
    Routes.SCHEDULED -> Icons.Default.Schedule
    Routes.SCREENSHOTS -> Icons.Default.Image
    Routes.SEARCH -> Icons.Default.Search
    else -> Icons.Default.Home
}

@Composable
private fun tabLabel(route: String): String = when (route) {
    Routes.HOME -> stringResource(dev.snapttl.R.string.nav_home)
    Routes.SCHEDULED -> stringResource(dev.snapttl.R.string.nav_scheduled)
    Routes.SCREENSHOTS -> stringResource(dev.snapttl.R.string.nav_screenshots)
    Routes.SEARCH -> stringResource(dev.snapttl.R.string.nav_search)
    else -> stringResource(dev.snapttl.R.string.nav_home)
}
