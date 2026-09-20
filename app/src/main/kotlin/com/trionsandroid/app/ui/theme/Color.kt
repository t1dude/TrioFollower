package com.trionsandroid.app.ui.theme

import androidx.compose.ui.graphics.Color

val TrioBackground = Color(0xFF0A0E1B)
val TrioSurface = Color(0xFF11162A)
val TrioSurfaceVariant = Color(0xFF1B2138)
val TrioOutline = Color(0xFF2B3350)

val TrioOnBackground = Color(0xFFF2F3F8)
val TrioOnSurfaceMuted = Color(0xFF9AA3C0)

// Accent colors
val TrioAccentPurple = Color(0xFF8B7CF6)
val TrioAccentBlue = Color(0xFF5FC7E8)

val TrioGlucoseInRange = Color(0xFF4FD88A)
val TrioGlucoseLow = Color(0xFFE0B84D)
val TrioGlucoseHigh = Color(0xFFE0954D)
val TrioGlucoseUrgent = Color(0xFFE85B5B)

// Trio's Insulin asset color, used for both basal and bolus.
val TrioInsulin = Color(0xFF1E96FC)
val TrioBasal = TrioInsulin
val TrioBolus = TrioInsulin
val TrioIob = Color(0xFF3355C9)

// HUD status colors: Trio's loopGreen, loopRed and systemOrange.
val TrioLoopGreen = Color(0xFF6FCF97)
val TrioLoopRed = Color(0xFFEB5757)
val TrioWarningOrange = Color(0xFFFF9500)
// Trio draws carb markers and the COB curve in this orange.
val TrioCob = TrioWarningOrange

// Carb entries in History (Trio's loopYellow).
val TrioCarb = Color(0xFFFFC145)

// Bubble ring gradient from Trio's CurrentGlucoseView; the trend arrow uses its blue end.
val TrioRingGradient = listOf(
    Color(0xFFB857FF),
    Color(0xFF9F6CFA),
    Color(0xFF7C8BF3),
    Color(0xFF57AAEC),
    Color(0xFF43BBE9),
    Color(0xFFB857FF),
)
val TrioTrendArrowColor = Color(0xFF43BBE9)
