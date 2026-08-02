package com.kbminisplit.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbminisplit.ui.main.MainShell

/**
 * There is no onboarding wizard: a fresh install is seeded with a default program
 * and opens on the Program tab, so the first thing you see is the thing you can
 * change. Every later launch opens on the Tracker.
 */
@Composable
fun RootApp(viewModel: RootViewModel = hiltViewModel()) {
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsStateWithLifecycle(initialValue = null)
    when (val first = isFirstLaunch) {
        null -> LoadingScaffold()
        else -> MainShell(startOnProgram = first, rootViewModel = viewModel)
    }
}

@Composable
private fun LoadingScaffold() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
