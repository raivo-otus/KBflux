package com.kbminisplit.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kbminisplit.domain.model.Split
import com.kbminisplit.ui.info.InfoScreen
import com.kbminisplit.ui.log.LogScreen
import com.kbminisplit.ui.nav.MainDestination
import com.kbminisplit.ui.progression.ProgressionScreen
import com.kbminisplit.ui.root.RootViewModel
import com.kbminisplit.ui.tracker.TrackerScreen
import com.kbminisplit.ui.tracker.TrackerViewModel

/**
 * Post-onboarding entry point.
 *
 * Phase 7: hosts a bottom navigation bar for Tracker, Log, and Progression tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val darkModeOverride by rootViewModel.isDarkMode.collectAsStateWithLifecycle(initialValue = null)

    var showResetDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showInfoPage by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val trackerViewModel: TrackerViewModel = hiltViewModel()

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Progress?") },
            text = { Text("This will wipe all history and reset the app to its initial state. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    rootViewModel.wipeAllData()
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSplitDialog) {
        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            title = { Text("Choose Next Split") },
            text = {
                Column {
                    Split.entries.forEach { split ->
                        TextButton(
                            onClick = {
                                trackerViewModel.forceSplit(split)
                                showSplitDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Split ${split.name}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSplitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("main_shell"),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { showInfoPage = !showInfoPage }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Toggle Info Page"
                        )
                    }
                },
                title = {
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 500) {
                                    tapCount++
                                } else {
                                    tapCount = 1
                                }
                                lastTapTime = now
                                if (tapCount >= 5) {
                                    if (currentDestination?.route == MainDestination.Log.route) {
                                        showResetDialog = true
                                    } else if (currentDestination?.route == MainDestination.Tracker.route) {
                                        showSplitDialog = true
                                    }
                                    tapCount = 0
                                }
                            }
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "KB MiniSplit")
                    }
                },
                actions = {
                    IconButton(onClick = { rootViewModel.toggleDarkMode() }) {
                        val isDark = darkModeOverride ?: true
                        val icon = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode
                        Icon(icon, contentDescription = "Toggle Dark Mode")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                MainDestination.all.forEach { destination ->
                    NavigationBarItem(
                        selected = !showInfoPage && (currentDestination?.hierarchy?.any { it.route == destination.route } == true),
                        onClick = {
                            showInfoPage = false
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
        if (showInfoPage) {
            InfoScreen(modifier = Modifier.padding(innerPadding))
        } else {
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
}
