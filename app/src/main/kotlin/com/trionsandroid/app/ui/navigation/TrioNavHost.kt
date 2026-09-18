package com.trionsandroid.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import com.trionsandroid.app.ui.history.HistoryScreen
import com.trionsandroid.app.ui.home.HomeScreen
import com.trionsandroid.app.ui.settings.SettingsScreen

@Composable
fun TrioNavHost(navController: NavHostController = rememberNavController()) {
    val destinations = TrioDestination.entries
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            TrioBottomBar(
                destinations = destinations,
                currentRoute = currentRoute,
                onDestinationSelected = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TrioDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TrioDestination.Home.route) { HomeScreen() }
            composable(TrioDestination.History.route) { HistoryScreen() }
            composable(TrioDestination.Settings.route) { SettingsScreen() }
        }
    }
}
