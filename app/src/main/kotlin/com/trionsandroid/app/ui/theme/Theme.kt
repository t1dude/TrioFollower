package com.trionsandroid.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TrioColorScheme = darkColorScheme(
    primary = TrioAccentPurple,
    secondary = TrioAccentBlue,
    tertiary = TrioGlucoseInRange,
    background = TrioBackground,
    surface = TrioSurface,
    surfaceVariant = TrioSurfaceVariant,
    onBackground = TrioOnBackground,
    onSurface = TrioOnBackground,
    onSurfaceVariant = TrioOnSurfaceMuted,
    outline = TrioOutline,
    error = TrioGlucoseUrgent,
)

@Composable
fun TrioNSTheme(
    // Dark only for now; the parameter allows a light theme later.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TrioColorScheme,
        typography = TrioTypography,
        content = content,
    )
}
