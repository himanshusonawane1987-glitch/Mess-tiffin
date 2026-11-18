package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.AuthRouteState
import com.example.domain.OnboardingStep
import com.example.ui.admin.AdminShell
import com.example.ui.admin.AdminViewModel
import com.example.ui.auth.AuthLoadingScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.AwaitingApprovalScreen
import com.example.ui.auth.CustomerRegistrationScreen
import com.example.ui.auth.RejectedRegistrationScreen
import com.example.ui.auth.RoleChoiceScreen
import com.example.ui.auth.SellerRegistrationScreen
import com.example.ui.auth.SellerRegistrationSuccessScreen
import com.example.ui.auth.SuspendedAccountScreen
import com.example.ui.auth.WelcomeScreen
import com.example.ui.customer.CustomerShell
import com.example.ui.customer.CustomerViewModel
import com.example.ui.discovery.FindTiffinNearMeScreen
import com.example.ui.discovery.SellerDetailScreen
import com.example.ui.theme.TiffinMessTheme

class MainActivity : ComponentActivity() {

    private val appContainer by lazy {
        (application as TiffinMessApplication).container
    }

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(appContainer.authClient, appContainer.repository, appContainer.appStrings)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.data?.let { uri -> authViewModel.handleOAuthCallback(uri) }

        setContent {
            val authState by authViewModel.uiState.collectAsStateWithLifecycle()
            TiffinMessTheme(language = authState.currentLanguage) {
                val context = LocalContext.current

                when (val route = authState.routeState) {
                    AuthRouteState.Loading -> {
                        AuthLoadingScreen()
                    }
                    AuthRouteState.SupabaseConfigMissing -> {
                        WelcomeScreen(
                            isSupabaseConfigured = false,
                            isBusy = authState.isBusy,
                            errorMessage = authState.errorMessage,
                            selectedLanguage = authState.currentLanguage,
                            hasChosenLanguage = authState.hasChosenLanguage,
                            onSelectLanguage = authViewModel::selectLanguage,
                            onChangeLanguageClick = authViewModel::openWelcomeLanguagePicker,
                            onContinueWithGoogle = { authViewModel.startGoogleSignIn(context) },
                            onRetryRestore = { authViewModel.restoreSession() }
                        )
                    }
                    AuthRouteState.SignedOut -> {
                        WelcomeScreen(
                            isSupabaseConfigured = true,
                            isBusy = authState.isBusy,
                            errorMessage = authState.errorMessage,
                            selectedLanguage = authState.currentLanguage,
                            hasChosenLanguage = authState.hasChosenLanguage,
                            onSelectLanguage = authViewModel::selectLanguage,
                            onChangeLanguageClick = authViewModel::openWelcomeLanguagePicker,
                            onContinueWithGoogle = { authViewModel.startGoogleSignIn(context) },
                            onRetryRestore = { authViewModel.restoreSession() }
                        )
                    }
                    is AuthRouteState.NeedsCustomerRegistration -> {
                        when (authState.onboardingStep) {
                            OnboardingStep.ROLE_CHOICE -> {
                                RoleChoiceScreen(
                                    profile = route.profile,
                                    onSelectRole = authViewModel::selectOnboardingRole,
                                    onSignOut = authViewModel::signOut
                                )
                            }
                            OnboardingStep.CUSTOMER_REGISTRATION -> {
                                CustomerRegistrationScreen(
                                    profile = route.profile,
                                    prefill = authState.discoveryPrefill,
                                    isBusy = authState.isBusy,
                                    errorMessage = authState.errorMessage,
                                    infoMessage = authState.infoMessage,
                                    onBrowseNearby = authViewModel::openDiscoveryFromCustomerRegistration,
                                    onClearPrefill = authViewModel::clearDiscoveryPrefill,
                                    onBack = authViewModel::navigateBackInOnboarding,
                                    onSubmit = authViewModel::submitRegistration,
                                    onSignOut = authViewModel::signOut
                                )
                            }
                            OnboardingStep.SELLER_REGISTRATION -> {
                                SellerRegistrationScreen(
                                    profile = route.profile,
                                    isBusy = authState.isBusy,
                                    errorMessage = authState.errorMessage,
                                    onBack = authViewModel::navigateBackInOnboarding,
                                    onSubmit = authViewModel::submitSellerRegistration
                                )
                            }
                            OnboardingStep.SELLER_SUCCESS -> {
                                val res = authState.sellerRegistrationResult
                                if (res != null) {
                                    SellerRegistrationSuccessScreen(
                                        result = res,
                                        isBusy = authState.isBusy,
                                        onGoToDashboard = authViewModel::goToAdminDashboardAfterRegistration
                                    )
                                } else {
                                    RoleChoiceScreen(
                                        profile = route.profile,
                                        onSelectRole = authViewModel::selectOnboardingRole,
                                        onSignOut = authViewModel::signOut
                                    )
                                }
                            }
                            OnboardingStep.DISCOVERY -> {
                                FindTiffinNearMeScreen(
                                    messes = authState.discoveryMesses,
                                    areaFilter = authState.discoveryAreaFilter,
                                    cityFilter = authState.discoveryCityFilter,
                                    isLoading = authState.discoveryLoading,
                                    isLocating = authState.isLocating,
                                    errorMessage = authState.errorMessage,
                                    infoMessage = authState.infoMessage,
                                    onLocationFilterChange = authViewModel::updateDiscoveryLocationFilter,
                                    onUseCurrentLocation = { authViewModel.useCurrentLocationForDiscovery(context) },
                                    onRefresh = authViewModel::loadDiscoveryMesses,
                                    onSelectMess = authViewModel::openSellerDetail,
                                    onBack = authViewModel::navigateBackInOnboarding
                                )
                            }
                            OnboardingStep.SELLER_DETAIL -> {
                                val selected = authState.selectedDiscoveryMess
                                if (selected != null) {
                                    SellerDetailScreen(
                                        item = selected,
                                        reviews = authState.selectedMessReviews,
                                        reviewsLoading = authState.reviewsLoading,
                                        canJoinFromDiscovery = true,
                                        isCurrentActiveMember = false,
                                        currentUserId = route.profile.id,
                                        isActionBusy = authState.isBusy,
                                        errorMessage = authState.errorMessage,
                                        successMessage = authState.infoMessage,
                                        onBack = authViewModel::navigateBackInOnboarding,
                                        onJoinMess = authViewModel::joinMessFromDiscovery
                                    )
                                } else {
                                    FindTiffinNearMeScreen(
                                        messes = authState.discoveryMesses,
                                        areaFilter = authState.discoveryAreaFilter,
                                        cityFilter = authState.discoveryCityFilter,
                                        isLoading = authState.discoveryLoading,
                                        isLocating = authState.isLocating,
                                        errorMessage = authState.errorMessage,
                                        infoMessage = authState.infoMessage,
                                        onLocationFilterChange = authViewModel::updateDiscoveryLocationFilter,
                                        onUseCurrentLocation = { authViewModel.useCurrentLocationForDiscovery(context) },
                                        onRefresh = authViewModel::loadDiscoveryMesses,
                                        onSelectMess = authViewModel::openSellerDetail,
                                        onBack = authViewModel::navigateBackInOnboarding
                                    )
                                }
                            }
                        }
                    }
                    is AuthRouteState.AwaitingApproval -> {
                        AwaitingApprovalScreen(
                            profile = route.profile,
                            messName = route.messName,
                            request = route.request,
                            isBusy = authState.isBusy,
                            errorMessage = authState.errorMessage,
                            onRefresh = authViewModel::refreshAuthContext,
                            onSignOut = authViewModel::signOut
                        )
                    }
                    is AuthRouteState.Rejected -> {
                        if (authState.reapplyingAfterReject) {
                            when (authState.onboardingStep) {
                                OnboardingStep.DISCOVERY -> FindTiffinNearMeScreen(
                                    messes = authState.discoveryMesses,
                                    areaFilter = authState.discoveryAreaFilter,
                                    cityFilter = authState.discoveryCityFilter,
                                    isLoading = authState.discoveryLoading,
                                    isLocating = authState.isLocating,
                                    errorMessage = authState.errorMessage,
                                    infoMessage = authState.infoMessage,
                                    onLocationFilterChange = authViewModel::updateDiscoveryLocationFilter,
                                    onUseCurrentLocation = { authViewModel.useCurrentLocationForDiscovery(context) },
                                    onRefresh = authViewModel::loadDiscoveryMesses,
                                    onSelectMess = authViewModel::openSellerDetail,
                                    onBack = authViewModel::startReapplyFromRejected
                                )
                                OnboardingStep.SELLER_DETAIL -> {
                                    val selected = authState.selectedDiscoveryMess
                                    if (selected != null) {
                                        SellerDetailScreen(
                                            item = selected,
                                            reviews = authState.selectedMessReviews,
                                            reviewsLoading = authState.reviewsLoading,
                                            canJoinFromDiscovery = true,
                                            isCurrentActiveMember = false,
                                            currentUserId = route.profile.id,
                                            isActionBusy = authState.isBusy,
                                            errorMessage = authState.errorMessage,
                                            successMessage = authState.infoMessage,
                                            onBack = authViewModel::navigateBackInOnboarding,
                                            onJoinMess = authViewModel::joinMessFromDiscovery
                                        )
                                    }
                                }
                                else -> CustomerRegistrationScreen(
                                    profile = route.profile,
                                    prefill = authState.discoveryPrefill,
                                    isBusy = authState.isBusy,
                                    errorMessage = authState.errorMessage,
                                    infoMessage = authState.infoMessage,
                                    onBrowseNearby = authViewModel::openDiscoveryFromCustomerRegistration,
                                    onClearPrefill = authViewModel::clearDiscoveryPrefill,
                                    onSubmit = authViewModel::submitRegistration,
                                    onSignOut = authViewModel::signOut
                                )
                            }
                        } else {
                            RejectedRegistrationScreen(
                                messName = route.messName,
                                isBusy = authState.isBusy,
                                errorMessage = authState.errorMessage,
                                onTryAnotherCode = authViewModel::startReapplyFromRejected,
                                onSignOut = authViewModel::signOut
                            )
                        }
                    }
                    is AuthRouteState.Suspended -> {
                        SuspendedAccountScreen(
                            profile = route.profile,
                            onRefresh = authViewModel::refreshAuthContext,
                            onSignOut = authViewModel::signOut
                        )
                    }
                    is AuthRouteState.CustomerActive -> {
                        val customerVm: CustomerViewModel = viewModel(
                            key = "customer_${route.customer.id}",
                            factory = CustomerViewModel.Factory(
                                repository = appContainer.repository,
                                profile = route.profile,
                                mess = route.mess,
                                customer = route.customer,
                                strings = appContainer.appStrings
                            )
                        )
                        CustomerShell(
                            viewModel = customerVm,
                            currentLanguage = authState.currentLanguage,
                            onSelectLanguage = authViewModel::selectLanguage,
                            onSignOut = authViewModel::signOut
                        )
                    }
                    is AuthRouteState.AdminActive -> {
                        val adminVm: AdminViewModel = viewModel(
                            key = "admin_${route.mess.id}_${route.profile.id}",
                            factory = AdminViewModel.Factory(
                                repository = appContainer.repository,
                                profile = route.profile,
                                mess = route.mess,
                                isOwner = route.isOwner,
                                strings = appContainer.appStrings
                            )
                        )
                        AdminShell(
                            viewModel = adminVm,
                            currentLanguage = authState.currentLanguage,
                            onSelectLanguage = authViewModel::selectLanguage,
                            onSignOut = authViewModel::signOut
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri -> authViewModel.handleOAuthCallback(uri) }
    }
}
