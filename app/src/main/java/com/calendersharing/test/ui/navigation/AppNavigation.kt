package com.calendersharing.test.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.calendersharing.test.ui.screen.calendar.CalendarScreen
import com.calendersharing.test.ui.screen.login.LoginScreen
import com.calendersharing.test.ui.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Calendar : Screen("calendar")
}

@Composable
fun AppNavigation(
    deepLinkInviteCode: String? = null,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val navController = rememberNavController()

    val startDestination = if (currentUser != null) Screen.Calendar.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onSignInSuccess = {
                    navController.navigate(Screen.Calendar.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(
                deepLinkInviteCode = deepLinkInviteCode,
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Calendar.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
