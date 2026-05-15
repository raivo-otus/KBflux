package com.kbminisplit.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kbminisplit.ui.log.LogScreen
import com.kbminisplit.ui.nav.MainDestination
import com.kbminisplit.ui.progression.ProgressionScreen
import com.kbminisplit.ui.tracker.TrackerScreen

/**
 * Post-onboarding entry point.
 *
 * Phase 7: hosts a bottom navigation bar for Tracker, Log, and Progression tabs.
 */
@Composable
fun MainShell() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("main_shell"),
        bottomBar = {
            NavigationBar {
                MainDestination.all.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                        modifier = Modifier.testTag("tab_${destination.route}"),
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.Tracker.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainDestination.Tracker.route) { TrackerScreen() }
            composable(MainDestination.Log.route) { LogScreen() }
            composable(MainDestination.Progression.route) { ProgressionScreen() }
        }
    }
}
