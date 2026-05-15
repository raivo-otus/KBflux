package com.kbminisplit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MonoLightColors = lightColorScheme(
    primary = Black,
    onPrimary = White,
    secondary = Grey800,
    onSecondary = White,
    tertiary = Grey600,
    onTertiary = White,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey800,
    outline = Grey400,
    outlineVariant = Grey200,
)

private val MonoDarkColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = Grey200,
    onSecondary = Black,
    tertiary = Grey400,
    onTertiary = Black,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Grey900,
    onSurfaceVariant = Grey200,
    outline = Grey600,
    outlineVariant = Grey800,
)

@Composable
fun KBMiniSplitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) MonoDarkColors else MonoLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = KBTypography,
        content = content,
    )
}
