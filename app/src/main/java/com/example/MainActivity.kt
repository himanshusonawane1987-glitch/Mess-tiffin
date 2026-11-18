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
import com.example.ui.admin.AdminShell
import com.example.ui.admin.AdminViewModel
import com.example.ui.auth.AuthLoadingScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.AwaitingApprovalScreen
import com.example.ui.auth.CustomerRegistrationScreen
import com.example.ui.auth.RejectedRegistrationScreen
import com.example.ui.auth.SuspendedAccountScreen
import com.example.ui.auth.WelcomeScreen
import com.example.ui.customer.CustomerShell
import com.example.ui.customer.CustomerViewModel
import com.example.ui.theme.TiffinMessTheme

class MainActivity : ComponentActivity() {

    private val appContainer by lazy {
        (application as TiffinMessApplication).container
    }

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(appContainer.authClient, appContainer.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.data?.let { uri -> authViewModel.handleOAuthCallback(uri) }

        setContent {
            TiffinMessTheme {
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()
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
                            onContinueWithGoogle = { authViewModel.startGoogleSignIn(context) },
                            onRetryRestore = { authViewModel.restoreSession() }
                        )
                    }
                    AuthRouteState.SignedOut -> {
                        WelcomeScreen(
                            isSupabaseConfigured = true,
                            isBusy = authState.isBusy,
                            errorMessage = authState.errorMessage,
                            onContinueWithGoogle = { authViewModel.startGoogleSignIn(context) },
                            onRetryRestore = { authViewModel.restoreSession() }
                        )
                    }
                    is AuthRouteState.NeedsCustomerRegistration -> {
                        CustomerRegistrationScreen(
                            profile = route.profile,
                            isBusy = authState.isBusy,
                            errorMessage = authState.errorMessage,
                            onSubmit = authViewModel::submitRegistration,
                            onSignOut = authViewModel::signOut
                        )
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
                            CustomerRegistrationScreen(
                                profile = route.profile,
                                isBusy = authState.isBusy,
                                errorMessage = authState.errorMessage,
                                onSubmit = authViewModel::submitRegistration,
                                onSignOut = authViewModel::signOut
                            )
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
                                customer = route.customer
                            )
                        )
                        CustomerShell(
                            viewModel = customerVm,
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
                                isOwner = route.isOwner
                            )
                        )
                        AdminShell(
                            viewModel = adminVm,
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
