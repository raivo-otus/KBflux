package com.kbminisplit.ui.main

import androidx.compose.runtime.Composable
import com.kbminisplit.ui.tracker.TrackerScreen

/**
 * Post-onboarding entry point. Phase 4 hosts only the Tracker; the bottom-nav
 * shell with Log + Progression tabs lands in Phase 7.
 */
@Composable
fun MainShell() {
    TrackerScreen()
}
