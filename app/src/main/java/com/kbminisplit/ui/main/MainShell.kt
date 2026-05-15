package com.kbminisplit.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kbminisplit.ui.log.LogScreen
import com.kbminisplit.ui.tracker.TrackerScreen

/**
 * Post-onboarding entry point.
 *
 * Phase 5 scaffolding: a two-tab top strip hosts Tracker + Log so the Log
 * screen is reachable for manual verification. Phase 7 replaces this with the
 * three-tab bottom navigation bar (Tracker, Log, Progression).
 */
@Composable
fun MainShell() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().testTag("main_shell")) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Tracker") },
                modifier = Modifier.testTag("tab_tracker"),
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Log") },
                modifier = Modifier.testTag("tab_log"),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> TrackerScreen()
                1 -> LogScreen()
            }
        }
    }
}
