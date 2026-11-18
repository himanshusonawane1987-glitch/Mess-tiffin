package com.example.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
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
import com.example.ui.theme.GlassBackdrop
import com.example.ui.theme.GlassBottomNavBar
import com.example.ui.theme.NavTabItem
import com.example.ui.theme.lStr
import kotlinx.serialization.Serializable

@Serializable data object AdminDashboardRoute
@Serializable data object AdminCustomersRoute
@Serializable data object AdminTiffinsRoute
@Serializable data object AdminBillingRoute
@Serializable data object AdminProfileRoute
@Serializable data object AdminMenuRoute
@Serializable data object AdminSubscriptionsRoute
@Serializable data object AdminReportsRoute

@Composable
fun AdminShell(
    viewModel: AdminViewModel,
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
        NavTabItem("dashboard", lStr(R.string.admin_nav_dashboard), Icons.Outlined.Dashboard, "admin_tab_dashboard"),
        NavTabItem("customers", lStr(R.string.admin_nav_customers), Icons.Outlined.Groups, "admin_tab_customers"),
        NavTabItem("tiffins", lStr(R.string.admin_nav_tiffins), Icons.Outlined.LunchDining, "admin_tab_tiffins"),
        NavTabItem("billing", lStr(R.string.admin_nav_billing), Icons.Outlined.Payments, "admin_tab_billing"),
        NavTabItem("profile", lStr(R.string.admin_nav_profile), Icons.Outlined.Person, "admin_tab_profile")
    )

    val selectedTab = when {
        currentRouteName.contains("AdminCustomersRoute") -> "customers"
        currentRouteName.contains("AdminTiffinsRoute") -> "tiffins"
        currentRouteName.contains("AdminBillingRoute") -> "billing"
        currentRouteName.contains("AdminProfileRoute") -> "profile"
        else -> "dashboard"
    }

    fun navigateTab(key: String) {
        val target: Any = when (key) {
            "customers" -> AdminCustomersRoute
            "tiffins" -> AdminTiffinsRoute
            "billing" -> AdminBillingRoute
            "profile" -> AdminProfileRoute
            else -> AdminDashboardRoute
        }
        navController.navigate(target) {
            popUpTo(AdminDashboardRoute) { saveState = true }
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
                startDestination = AdminDashboardRoute,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<AdminDashboardRoute> {
                    AdminDashboardScreen(
                        state = state,
                        onRefresh = { viewModel.refreshAll() },
                        onOpenTiffins = { navigateTab("tiffins") },
                        onOpenMenu = { navController.navigate(AdminMenuRoute) },
                        onOpenCustomers = { navigateTab("customers") },
                        onOpenBilling = { navigateTab("billing") },
                        onOpenSubscriptions = { navController.navigate(AdminSubscriptionsRoute) },
                        onOpenReports = { navController.navigate(AdminReportsRoute) },
                        onOpenProfile = { navigateTab("profile") }
                    )
                }
                composable<AdminCustomersRoute> {
                    AdminCustomersScreen(
                        state = state,
                        onCreateCustomer = viewModel::createCustomer,
                        onReviewRequest = viewModel::reviewRegistrationRequest,
                        onUpdateCustomerDetails = viewModel::updateCustomerDetails,
                        onUpdateCustomerStatus = viewModel::updateCustomerStatus,
                        onOpenBillingForCustomer = { navigateTab("billing") },
                        onOpenSubscriptionsForCustomer = { navController.navigate(AdminSubscriptionsRoute) },
                        onCreateLeave = viewModel::createLeaveForCustomer,
                        onCancelLeave = viewModel::cancelCustomerLeave
                    )
                }
                composable<AdminTiffinsRoute> {
                    AdminTiffinsScreen(
                        state = state,
                        onSelectDate = viewModel::selectDate,
                        onMarkTiffin = viewModel::markTiffin,
                        onBulkMarkDelivered = viewModel::bulkMarkDelivered,
                        onCreateLeave = viewModel::createLeaveForCustomer,
                        onCancelLeave = viewModel::cancelCustomerLeave
                    )
                }
                composable<AdminBillingRoute> {
                    AdminBillingScreen(
                        state = state,
                        onSelectMonth = viewModel::selectMonth,
                        onGenerateBill = viewModel::generateMonthlyBill,
                        onRecordPayment = viewModel::recordPayment,
                        onReversePayment = viewModel::reversePayment,
                        onConfirmPaymentProof = viewModel::confirmPaymentProof,
                        onRejectPaymentProof = viewModel::rejectPaymentProof
                    )
                }
                composable<AdminProfileRoute> {
                    AdminProfileSettingsScreen(
                        state = state,
                        currentLanguage = currentLanguage,
                        onSelectLanguage = onSelectLanguage,
                        onUpdateMessSettings = viewModel::updateMessSettings,
                        onRotateCode = viewModel::rotateRegistrationCode,
                        onInviteAdmin = viewModel::inviteAdmin,
                        onSaveUpiSettings = { upiId, newQrUri ->
                            viewModel.saveSellerUpiSettings(context, upiId, newQrUri)
                        },
                        onSignOut = onSignOut
                    )
                }
                composable<AdminMenuRoute> {
                    AdminMenuScreen(
                        state = state,
                        onBack = { navController.navigateUp() },
                        onSaveMenu = viewModel::saveMenu
                    )
                }
                composable<AdminSubscriptionsRoute> {
                    AdminSubscriptionsScreen(
                        state = state,
                        onBack = { navController.navigateUp() },
                        onCreatePlan = viewModel::createPlan,
                        onAssignSubscription = viewModel::assignSubscription,
                        onUpdateSubscriptionStatus = viewModel::updateSubscriptionStatus,
                        onReviewRenewalRequest = viewModel::reviewRenewalRequest
                    )
                }
                composable<AdminReportsRoute> {
                    AdminReportsScreen(
                        state = state,
                        onBack = { navController.navigateUp() },
                        onSelectMonth = viewModel::selectMonth
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
