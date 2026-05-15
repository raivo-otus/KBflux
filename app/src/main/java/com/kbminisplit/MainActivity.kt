package com.kbminisplit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbminisplit.ui.root.RootApp
import com.kbminisplit.ui.root.RootViewModel
import com.kbminisplit.ui.theme.KBMiniSplitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val rootViewModel: RootViewModel = hiltViewModel()
            val darkModeOverride by rootViewModel.isDarkMode.collectAsStateWithLifecycle(initialValue = null)

            KBMiniSplitTheme(
                darkTheme = darkModeOverride ?: true
            ) {
                RootApp(viewModel = rootViewModel)
            }
        }
    }
}
