package com.kbminisplit.ui.theme

import androidx.compose.ui.graphics.Color

// Monochrome palette — the entire app, minus feedback dots, draws from this.
val Black = Color(0xFF000000)
val Grey900 = Color(0xFF1A1A1A)
val Grey800 = Color(0xFF2E2E2E)
val Grey700 = Color(0xFF4A4A4A)
val Grey600 = Color(0xFF6E6E6E)
val Grey500 = Color(0xFF8E8E8E)
val Grey400 = Color(0xFFB0B0B0)
val Grey300 = Color(0xFFCFCFCF)
val Grey200 = Color(0xFFE5E5E5)
val Grey100 = Color(0xFFF2F2F2)
val White = Color(0xFFFFFFFF)

// Feedback tokens. Per spec §1.4, these are the *only* non-monochrome colors in the UI
// and must only appear on the R/Y/G feedback dot and the Log calendar cells.
object FeedbackColors {
    val Red = Color(0xFFD64545)
    val Yellow = Color(0xFFE0B547)
    val Green = Color(0xFF4CAF50)
}
