package com.trionsandroid.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.trionsandroid.app.R

// Icons mirror Trio's tab bar.
enum class TrioDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Home(route = "home", labelRes = R.string.nav_home, icon = Icons.Filled.ShowChart),
    History(route = "history", labelRes = R.string.nav_history, icon = Icons.AutoMirrored.Filled.MenuBook),
    Settings(route = "settings", labelRes = R.string.nav_settings, icon = Icons.Filled.Settings),
}
