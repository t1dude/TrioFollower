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
    // Trio's UI is dark-only in the reference design; the param exists so a light
    // theme can be added later without changing every call site.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TrioColorScheme,
        typography = TrioTypography,
        content = content,
    )
}
