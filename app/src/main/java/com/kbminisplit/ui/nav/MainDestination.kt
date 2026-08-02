package com.kbminisplit.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class MainDestination(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object Tracker : MainDestination("tracker", Icons.Default.TrackChanges, "Tracker")
    object Log : MainDestination("log", Icons.Default.History, "Log")
    object Program : MainDestination("program", Icons.Default.Tune, "Program")

    companion object {
        val all = listOf(Tracker, Log, Program)
    }
}
