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
import com.kbminisplit.ui.onboarding.OnboardingScreen

@Composable
fun RootApp(viewModel: RootViewModel = hiltViewModel()) {
    val route by viewModel.route.collectAsStateWithLifecycle(initialValue = RootRoute.Loading)
    when (route) {
        RootRoute.Loading -> LoadingScaffold()
        RootRoute.Onboarding -> OnboardingScreen()
        RootRoute.Main -> MainShell()
    }
}

@Composable
private fun LoadingScaffold() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
