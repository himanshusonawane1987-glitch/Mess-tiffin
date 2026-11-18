package com.example.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SupabaseAuthClient
import com.example.data.SupabaseConfig
import com.example.data.TiffinRepository
import com.example.domain.AuthRouteState
import com.example.domain.TiffinBusinessLogic
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val routeState: AuthRouteState = AuthRouteState.Loading,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val reapplyingAfterReject: Boolean = false
)

class AuthViewModel(
    private val authClient: SupabaseAuthClient,
    private val repository: TiffinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        restoreSession()
    }

    fun restoreSession() {
        viewModelScope.launch {
            if (!SupabaseConfig.isConfigured) {
                _uiState.update {
                    it.copy(
                        routeState = AuthRouteState.SupabaseConfigMissing,
                        isBusy = false
                    )
                }
                return@launch
            }

            val stored = authClient.getCurrentSession()
            if (stored == null) {
                _uiState.update { it.copy(routeState = AuthRouteState.SignedOut, isBusy = false) }
                return@launch
            }

            // Fast initial hydration from local cache while server context syncs
            val cached = repository.getCachedAuthContext()
            if (cached != null) {
                _uiState.update {
                    it.copy(routeState = TiffinBusinessLogic.resolveAuthRouteState(cached))
                }
            }

            refreshAuthContext()
        }
    }

    fun refreshAuthContext() {
        viewModelScope.launch {
            if (!SupabaseConfig.isConfigured) {
                _uiState.update { it.copy(routeState = AuthRouteState.SupabaseConfigMissing, isBusy = false) }
                return@launch
            }
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = repository.fetchAuthContext()
            result.fold(
                onSuccess = { payload ->
                    val resolved = TiffinBusinessLogic.resolveAuthRouteState(payload)
                    _uiState.update {
                        it.copy(
                            routeState = resolved,
                            isBusy = false,
                            reapplyingAfterReject = false
                        )
                    }
                },
                onFailure = { err ->
                    val stillHasSession = authClient.getCurrentSession() != null
                    _uiState.update {
                        it.copy(
                            routeState = if (stillHasSession && it.routeState !is AuthRouteState.Loading) {
                                it.routeState
                            } else {
                                AuthRouteState.SignedOut
                            },
                            isBusy = false,
                            errorMessage = err.message ?: "Unable to reach Supabase."
                        )
                    }
                }
            )
        }
    }

    fun startGoogleSignIn(activityContext: Context) {
        viewModelScope.launch {
            if (!SupabaseConfig.isConfigured) {
                _uiState.update {
                    it.copy(errorMessage = "Configure SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets first.")
                }
                return@launch
            }
            _uiState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = null) }

            // Try native Credential Manager first when GOOGLE_WEB_CLIENT_ID is configured
            if (SupabaseConfig.hasGoogleWebClientId) {
                val nativeSuccess = tryNativeGoogleCredentialSignIn(activityContext)
                if (nativeSuccess) return@launch
            }

            // Standard Supabase Google OAuth PKCE via Chrome Custom Tabs -> tiffinmess://auth/callback
            runCatching {
                val oauthUri = authClient.buildGoogleOAuthAuthorizeUrl()
                launchCustomTabOrBrowser(activityContext, oauthUri)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = err.message ?: "Could not open Google Sign-In.")
                }
            }
        }
    }

    private suspend fun tryNativeGoogleCredentialSignIn(activityContext: Context): Boolean {
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(SupabaseConfig.googleWebClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = credentialManager.getCredential(activityContext, request)
            val googleCred = GoogleIdTokenCredential.createFrom(response.credential.data)
            val authRes = authClient.signInWithGoogleIdToken(googleCred.idToken)
            if (authRes.isSuccess) {
                refreshAuthContext()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun launchCustomTabOrBrowser(context: Context, uri: Uri) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, uri)
            _uiState.update { it.copy(isBusy = false) }
        } catch (_: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun handleOAuthCallback(uri: Uri) {
        if (uri.scheme != "tiffinmess") return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val sessionRes = authClient.handleOAuthCallback(uri)
            sessionRes.fold(
                onSuccess = { refreshAuthContext() },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.message ?: "Google OAuth callback failed."
                        )
                    }
                }
            )
        }
    }

    fun submitRegistration(
        registrationCode: String,
        fullName: String,
        phone: String,
        addressOrRoom: String
    ) {
        viewModelScope.launch {
            if (registrationCode.isBlank() || fullName.isBlank() || phone.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Please fill in your name, mobile number, and mess code.") }
                return@launch
            }
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.submitCustomerRegistration(registrationCode, fullName, phone, addressOrRoom)
                .fold(
                    onSuccess = {
                        _uiState.update { it.copy(reapplyingAfterReject = false) }
                        refreshAuthContext()
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                errorMessage = err.message ?: "Registration failed. Check your mess code."
                            )
                        }
                    }
                )
        }
    }

    fun startReapplyFromRejected() {
        _uiState.update { it.copy(reapplyingAfterReject = true, errorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            authClient.signOut()
            _uiState.update {
                AuthUiState(
                    routeState = if (SupabaseConfig.isConfigured) {
                        AuthRouteState.SignedOut
                    } else {
                        AuthRouteState.SupabaseConfigMissing
                    },
                    isBusy = false
                )
            }
        }
    }

    class Factory(
        private val authClient: SupabaseAuthClient,
        private val repository: TiffinRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authClient, repository) as T
        }
    }
}
