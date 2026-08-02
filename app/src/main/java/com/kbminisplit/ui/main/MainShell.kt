package com.kbminisplit.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.kbminisplit.ui.info.InfoScreen
import com.kbminisplit.ui.log.LogScreen
import com.kbminisplit.ui.log.LogViewModel
import com.kbminisplit.ui.nav.MainDestination
import com.kbminisplit.ui.program.ProgramScreen
import com.kbminisplit.ui.root.RootUiEvent
import com.kbminisplit.ui.root.RootViewModel
import com.kbminisplit.ui.tracker.TrackerEvent
import com.kbminisplit.ui.tracker.TrackerScreen
import com.kbminisplit.ui.tracker.TrackerViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Post-launch entry point: bottom navigation over Tracker, Log and Program.
 *
 * [startOnProgram] opens on the Program tab, which is what a fresh install does —
 * there is no onboarding wizard, so the editor is the introduction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    startOnProgram: Boolean = false,
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val darkModeOverride by rootViewModel.isDarkMode.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    var showResetDialog by remember { mutableStateOf(false) }
    var showDayDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showInfoPage by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(rootViewModel) {
        rootViewModel.uiEvents.collect { event ->
            when (event) {
                is RootUiEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { rootViewModel.exportData(context.contentResolver, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { rootViewModel.importData(context.contentResolver, it) }
    }

    val trackerViewModel: TrackerViewModel = hiltViewModel()
    val logViewModel: LogViewModel = hiltViewModel()

    // Seeing the Program tab is what retires the first-launch state, so the next
    // launch opens straight on the Tracker.
    LaunchedEffect(currentDestination?.route) {
        if (currentDestination?.route == MainDestination.Program.route) {
            rootViewModel.markProgramSeen()
        }
    }

    LaunchedEffect(trackerViewModel) {
        trackerViewModel.events.collect { event ->
            when (event) {
                is TrackerEvent.SessionCommitted -> {
                    logViewModel.markJustCommitted(event.date)
                    navController.navigate(MainDestination.Log.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }

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

    if (showDayDialog) {
        val days by trackerViewModel.days.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showDayDialog = false },
            title = { Text("Choose Next Day") },
            text = {
                Column {
                    days.forEach { day ->
                        TextButton(
                            onClick = {
                                trackerViewModel.forceDay(day.key)
                                showDayDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(day.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDayDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsMenu) {
        val currentLevel by rootViewModel.hapticLevel.collectAsStateWithLifecycle(initialValue = 1)
        AlertDialog(
            onDismissRequest = { showSettingsMenu = false },
            title = { Text("Settings") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Haptic Intensity",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = when (currentLevel) {
                            0 -> "Low"
                            1 -> "Medium"
                            else -> "High"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = currentLevel.toFloat(),
                        onValueChange = { rootViewModel.setHapticLevel(it.roundToInt()) },
                        valueRange = 0f..2f,
                        steps = 1
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    TextButton(
                        onClick = {
                            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                            exportLauncher.launch("kbminisplit_backup_$timestamp.json")
                            showSettingsMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Data (JSON)")
                    }
                    TextButton(
                        onClick = {
                            importLauncher.launch("application/json")
                            showSettingsMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import Data (JSON)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsMenu = false }) {
                    Text("Done")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("main_shell"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                    when (currentDestination?.route) {
                                        MainDestination.Log.route -> showResetDialog = true
                                        MainDestination.Tracker.route -> showDayDialog = true
                                        MainDestination.Program.route -> showSettingsMenu = true
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
                startDestination = if (startOnProgram) {
                    MainDestination.Program.route
                } else {
                    MainDestination.Tracker.route
                },
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(MainDestination.Tracker.route) {
                    TrackerScreen(viewModel = trackerViewModel)
                }
                composable(MainDestination.Log.route) {
                    LogScreen(viewModel = logViewModel)
                }
                composable(MainDestination.Program.route) { ProgramScreen() }
            }
        }
    }
}
