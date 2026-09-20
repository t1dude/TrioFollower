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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trionsandroid.app.ui.history.HistoryScreen
import com.trionsandroid.app.ui.onboarding.ConnectNightscoutDialog
import com.trionsandroid.app.ui.onboarding.OnboardingViewModel
import com.trionsandroid.app.ui.onboarding.WelcomeDialog
import com.trionsandroid.app.ui.home.HomeScreen
import com.trionsandroid.app.ui.settings.SettingsScreen

private enum class OnboardingStep { WELCOME, CONNECT }

@Composable
fun TrioNavHost(navController: NavHostController = rememberNavController()) {
    val destinations = TrioDestination.entries
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // First run: welcome (the README) -> "connect to Nightscout" prompt -> Settings with Basic
    // Settings already expanded, so the URL and token fields are right there.
    val onboarding: OnboardingViewModel = hiltViewModel()
    val showWelcome by onboarding.showWelcome.collectAsStateWithLifecycle()
    var onboardingStep by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var expandBasicSettings by remember { mutableStateOf(false) }
    if (showWelcome) {
        when (onboardingStep) {
            OnboardingStep.WELCOME -> WelcomeDialog(onOk = { onboardingStep = OnboardingStep.CONNECT })
            OnboardingStep.CONNECT -> ConnectNightscoutDialog(onOk = {
                expandBasicSettings = true
                onboarding.completeWelcome()
                navController.navigate(TrioDestination.Settings.route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                }
            })
        }
    }

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
            composable(TrioDestination.Settings.route) { SettingsScreen(expandBasicSettings = expandBasicSettings) }
        }
    }
}
