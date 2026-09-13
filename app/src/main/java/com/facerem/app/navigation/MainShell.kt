package com.facerem.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.facerem.feature.dashboard.presentation.DashboardRoute
import com.facerem.feature.employees.presentation.EmployeesRoute
import com.facerem.feature.history.presentation.HistoryRoute
import com.facerem.feature.settings.presentation.SettingsRoute

/**
 * Bottom-navigation container. Tab switching lives in a nested graph so the root back stack keeps
 * modal flows (scan, enrollment, employee detail) above the shell.
 */
@Composable
fun MainShell(
    onSignedOut: () -> Unit,
    onOpenDestination: (String) -> Unit,
) {
    val tabController = rememberNavController()
    val backStackEntry by tabController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                MainTab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            tabController.navigate(tab.route) {
                                popUpTo(tabController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenDestination(AppRoute.Scan) },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                text = { Text("Scan") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { padding ->
        NavHost(
            navController = tabController,
            startDestination = AppRoute.Dashboard,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
        ) {
            composable(AppRoute.Dashboard) {
                DashboardRoute(
                    onOpenScan = { onOpenDestination(AppRoute.Scan) },
                    onOpenEmployees = { tabController.navigateToTab(AppRoute.Employees) },
                    onOpenHistory = { tabController.navigateToTab(AppRoute.History) },
                    onOpenReports = { onOpenDestination(AppRoute.Reports) },
                    onOpenNotifications = { onOpenDestination(AppRoute.Notifications) },
                    onOpenProfile = { onOpenDestination(AppRoute.Profile) },
                )
            }
            composable(AppRoute.Employees) {
                EmployeesRoute(
                    onOpenEmployee = { employeeId -> onOpenDestination(AppRoute.employeeDetail(employeeId)) },
                    onAddEmployee = { onOpenDestination(AppRoute.AddEmployee) },
                )
            }
            composable(AppRoute.History) {
                HistoryRoute(onOpenReports = { onOpenDestination(AppRoute.Reports) })
            }
            composable(AppRoute.Settings) {
                SettingsRoute(
                    onSignedOut = onSignedOut,
                    onOpenProfile = { onOpenDestination(AppRoute.Profile) },
                    onOpenReports = { onOpenDestination(AppRoute.Reports) },
                    onOpenNotifications = { onOpenDestination(AppRoute.Notifications) },
                    onOpenPrivacy = { onOpenDestination(AppRoute.Privacy) },
                )
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
