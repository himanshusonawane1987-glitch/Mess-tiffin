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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.domain.AppLanguage
import com.example.ui.discovery.FindTiffinNearMeScreen
import com.example.ui.discovery.SellerDetailScreen
import com.example.ui.theme.GlassBackdrop
import com.example.ui.theme.GlassBottomNavBar
import com.example.ui.theme.NavTabItem
import com.example.ui.theme.lStr
import kotlinx.serialization.Serializable

@Serializable data object CustomerHomeRoute
@Serializable data object CustomerHistoryRoute
@Serializable data object CustomerMenuRoute
@Serializable data object CustomerSubscriptionRoute
@Serializable data object CustomerPaymentsRoute
@Serializable data object CustomerProfileRoute
@Serializable data object CustomerDiscoveryRoute
@Serializable data object CustomerSellerDetailRoute

@Composable
fun CustomerShell(
    viewModel: CustomerViewModel,
    currentLanguage: AppLanguage = AppLanguage.DEFAULT,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onSignOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = backStackEntry?.destination?.route ?: ""
    val context = LocalContext.current

    val tabs = listOf(
        NavTabItem("home", lStr(R.string.nav_home), Icons.Outlined.Home, "customer_tab_home"),
        NavTabItem("history", lStr(R.string.nav_history), Icons.Outlined.History, "customer_tab_history"),
        NavTabItem("menu", lStr(R.string.nav_menu), Icons.Outlined.RestaurantMenu, "customer_tab_menu"),
        NavTabItem("payments", lStr(R.string.nav_payments), Icons.Outlined.ReceiptLong, "customer_tab_payments"),
        NavTabItem("profile", lStr(R.string.nav_profile), Icons.Outlined.Person, "customer_tab_profile")
    )

    val selectedTab = when {
        currentRouteName.contains("CustomerHistoryRoute") -> "history"
        currentRouteName.contains("CustomerMenuRoute") -> "menu"
        currentRouteName.contains("CustomerPaymentsRoute") -> "payments"
        currentRouteName.contains("CustomerProfileRoute") -> "profile"
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
                        onNavigateToProfile = { navigateTab("profile") },
                        onNavigateToDiscovery = {
                            viewModel.loadDiscoveryMesses()
                            navController.navigate(CustomerDiscoveryRoute)
                        },
                        onNavigateToRateMess = {
                            viewModel.openOwnMessForReview()
                            navController.navigate(CustomerSellerDetailRoute)
                        },
                        onDismissLocationBanner = viewModel::dismissLocationBanner,
                        onMarkTodayTiffin = viewModel::markTodayTiffin,
                        onCreateLeave = viewModel::createLeave,
                        onCancelLeave = viewModel::cancelLeave
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
                    CustomerPaymentsScreen(
                        state = state,
                        onSubmitPaymentProof = { bill, amountInput, screenshotUri ->
                            viewModel.submitPaymentProof(context, bill, amountInput, screenshotUri)
                        }
                    )
                }
                composable<CustomerProfileRoute> {
                    CustomerProfileScreen(
                        state = state,
                        currentLanguage = currentLanguage,
                        onSelectLanguage = onSelectLanguage,
                        onUpdateProfile = viewModel::updateProfile,
                        onSignOut = onSignOut
                    )
                }
                composable<CustomerDiscoveryRoute> {
                    FindTiffinNearMeScreen(
                        messes = state.discoveryMesses,
                        areaFilter = state.discoveryAreaFilter,
                        cityFilter = state.discoveryCityFilter,
                        isLoading = state.discoveryLoading,
                        isLocating = state.isLocating,
                        errorMessage = state.errorMessage,
                        infoMessage = state.discoveryInfoMessage,
                        onLocationFilterChange = viewModel::updateDiscoveryLocationFilter,
                        onUseCurrentLocation = { viewModel.useCurrentLocationForDiscovery(context) },
                        onRefresh = viewModel::loadDiscoveryMesses,
                        onSelectMess = { item ->
                            viewModel.openSellerDetail(item)
                            navController.navigate(CustomerSellerDetailRoute)
                        },
                        onBack = { navController.popBackStack() },
                        bottomPaddingDp = 104
                    )
                }
                composable<CustomerSellerDetailRoute> {
                    val selected = state.selectedDiscoveryMess
                    if (selected != null) {
                        SellerDetailScreen(
                            item = selected,
                            reviews = state.selectedMessReviews,
                            reviewsLoading = state.reviewsLoading,
                            canJoinFromDiscovery = false,
                            isCurrentActiveMember = selected.mess.id == state.mess.id,
                            currentUserId = state.profile.id,
                            isActionBusy = state.isActionBusy,
                            errorMessage = state.errorMessage,
                            successMessage = state.successMessage,
                            onBack = { navController.popBackStack() },
                            onSubmitReview = { stars, comment ->
                                viewModel.submitMessReview(selected.mess.id, stars, comment)
                            },
                            bottomPaddingDp = 104
                        )
                    }
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
