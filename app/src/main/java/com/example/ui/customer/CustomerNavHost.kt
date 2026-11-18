package com.example.ui.customer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.GlassBackdrop
import com.example.ui.theme.GlassBottomNavBar
import com.example.ui.theme.NavTabItem
import kotlinx.serialization.Serializable

@Serializable data object CustomerHomeRoute
@Serializable data object CustomerHistoryRoute
@Serializable data object CustomerMenuRoute
@Serializable data object CustomerSubscriptionRoute
@Serializable data object CustomerPaymentsRoute
@Serializable data object CustomerProfileRoute

@Composable
fun CustomerShell(
    viewModel: CustomerViewModel,
    onSignOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = backStackEntry?.destination?.route ?: ""

    val tabs = listOf(
        NavTabItem("home", "Home", Icons.Outlined.Home, "customer_tab_home"),
        NavTabItem("history", "History", Icons.Outlined.History, "customer_tab_history"),
        NavTabItem("menu", "Menu", Icons.Outlined.RestaurantMenu, "customer_tab_menu"),
        NavTabItem("payments", "Payments", Icons.Outlined.ReceiptLong, "customer_tab_payments"),
        NavTabItem("profile", "Profile", Icons.Outlined.Person, "customer_tab_profile")
    )

    val selectedTab = when {
        currentRouteName.contains("CustomerHistoryRoute") -> "history"
        currentRouteName.contains("CustomerMenuRoute") -> "menu"
        currentRouteName.contains("CustomerPaymentsRoute") -> "payments"
        currentRouteName.contains("CustomerProfileRoute") -> "profile"
        currentRouteName.contains("CustomerSubscriptionRoute") -> "home"
        else -> "home"
    }

    fun navigateTab(key: String) {
        val target: Any = when (key) {
            "history" -> CustomerHistoryRoute
            "menu" -> CustomerMenuRoute
            "payments" -> CustomerPaymentsRoute
            "profile" -> CustomerProfileRoute
            else -> CustomerHomeRoute
        }
        navController.navigate(target) {
            popUpTo(CustomerHomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    GlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            NavHost(
                navController = navController,
                startDestination = CustomerHomeRoute,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<CustomerHomeRoute> {
                    CustomerDashboardScreen(
                        state = state,
                        onRefresh = { viewModel.refreshAll() },
                        onNavigateToHistory = { navigateTab("history") },
                        onNavigateToMenu = { navigateTab("menu") },
                        onNavigateToSubscription = { navController.navigate(CustomerSubscriptionRoute) },
                        onNavigateToPayments = { navigateTab("payments") },
                        onNavigateToProfile = { navigateTab("profile") }
                    )
                }
                composable<CustomerHistoryRoute> {
                    CustomerHistoryScreen(
                        state = state,
                        onSelectMonth = viewModel::selectHistoryMonth
                    )
                }
                composable<CustomerMenuRoute> {
                    CustomerMenuScreen(state = state)
                }
                composable<CustomerSubscriptionRoute> {
                    CustomerSubscriptionScreen(
                        state = state,
                        onRequestRenewal = viewModel::submitRenewalRequest
                    )
                }
                composable<CustomerPaymentsRoute> {
                    CustomerPaymentsScreen(state = state)
                }
                composable<CustomerProfileRoute> {
                    CustomerProfileScreen(
                        state = state,
                        onUpdateProfile = viewModel::updateProfile,
                        onSignOut = onSignOut
                    )
                }
            }

            GlassBottomNavBar(
                items = tabs,
                selectedRoute = selectedTab,
                onSelect = ::navigateTab,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
