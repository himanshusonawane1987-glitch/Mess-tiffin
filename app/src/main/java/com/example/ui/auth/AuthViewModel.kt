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
import com.example.R
import com.example.data.LocationHelper
import com.example.data.SupabaseAuthClient
import com.example.data.SupabaseConfig
import com.example.data.TiffinRepository
import com.example.domain.AppLanguage
import com.example.domain.AuthRouteState
import com.example.domain.DiscoverableMess
import com.example.domain.DiscoveryJoinPrefill
import com.example.domain.Mess
import com.example.domain.MessReview
import com.example.domain.OnboardingRoleChoice
import com.example.domain.OnboardingStep
import com.example.domain.SellerRegistrationResult
import com.example.domain.TiffinBusinessLogic
import com.example.domain.VegType
import com.example.ui.theme.AppStrings
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val routeState: AuthRouteState = AuthRouteState.Loading,
    val currentLanguage: AppLanguage = AppLanguage.DEFAULT,
    val hasChosenLanguage: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val reapplyingAfterReject: Boolean = false,
    val onboardingStep: OnboardingStep = OnboardingStep.ROLE_CHOICE,
    val sellerRegistrationResult: SellerRegistrationResult? = null,
    val discoveryPrefill: DiscoveryJoinPrefill? = null,
    val discoveryMesses: List<DiscoverableMess> = emptyList(),
    val discoveryAreaFilter: String = "",
    val discoveryCityFilter: String = "",
    val discoveryLoading: Boolean = false,
    val isLocating: Boolean = false,
    val selectedDiscoveryMess: DiscoverableMess? = null,
    val selectedMessReviews: List<MessReview> = emptyList(),
    val reviewsLoading: Boolean = false
)

class AuthViewModel(
    private val authClient: SupabaseAuthClient,
    private val repository: TiffinRepository,
    private val strings: AppStrings? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var rawDiscoveryMesses: List<Mess> = emptyList()
    private var rawTodayMenus: Map<String, com.example.domain.DailyMenu> = emptyMap()

    init {
        observeLanguagePrefs()
        restoreSession()
    }

    private fun str(resId: Int, vararg args: Any): String {
        return strings?.get(resId, *args) ?: ""
    }

    private fun observeLanguagePrefs() {
        viewModelScope.launch {
            repository.observeLocalLanguage().collect { lang ->
                _uiState.update { it.copy(currentLanguage = lang) }
            }
        }
        viewModelScope.launch {
            repository.observeHasChosenLanguage().collect { chosen ->
                _uiState.update { it.copy(hasChosenLanguage = chosen) }
            }
        }
    }

    fun selectLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.saveLocalLanguage(language, markChosen = true)
            _uiState.update { it.copy(currentLanguage = language, hasChosenLanguage = true) }
            if (authClient.getCurrentSession() != null) {
                repository.updateMyLanguage(language)
            }
        }
    }

    fun openWelcomeLanguagePicker() {
        _uiState.update { it.copy(hasChosenLanguage = false) }
    }

    fun restoreSession() {
        viewModelScope.launch {
            val localLang = repository.getLocalLanguage()
            val localChosen = repository.hasChosenLanguage()
            _uiState.update {
                it.copy(currentLanguage = localLang, hasChosenLanguage = localChosen)
            }

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
                val resolved = TiffinBusinessLogic.resolveAuthRouteState(cached)
                _uiState.update {
                    it.copy(
                        routeState = resolved,
                        discoveryAreaFilter = it.discoveryAreaFilter.ifBlank { cached.profile?.area.orEmpty() },
                        discoveryCityFilter = it.discoveryCityFilter.ifBlank { cached.profile?.city.orEmpty() }
                    )
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
                    val serverProfile = payload.profile
                    if (serverProfile != null) {
                        val localChosen = repository.hasChosenLanguage()
                        val localLang = repository.getLocalLanguage()
                        if (serverProfile.languageSetByUser) {
                            val serverLang = AppLanguage.fromDb(serverProfile.language)
                            repository.saveLocalLanguage(serverLang, markChosen = true)
                            _uiState.update { it.copy(currentLanguage = serverLang, hasChosenLanguage = true) }
                        } else if (localChosen) {
                            repository.updateMyLanguage(localLang)
                        }
                    }

                    val resolved = TiffinBusinessLogic.resolveAuthRouteState(payload)
                    _uiState.update {
                        it.copy(
                            routeState = resolved,
                            isBusy = false,
                            reapplyingAfterReject = false,
                            discoveryAreaFilter = it.discoveryAreaFilter.ifBlank { payload.profile?.area.orEmpty() },
                            discoveryCityFilter = it.discoveryCityFilter.ifBlank { payload.profile?.city.orEmpty() }
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
                            errorMessage = err.message ?: str(R.string.err_reach_supabase).ifBlank { "Unable to reach Supabase." }
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
                    it.copy(
                        errorMessage = str(R.string.err_supabase_secrets)
                            .ifBlank { "Configure SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets first." }
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = null) }

            if (SupabaseConfig.hasGoogleWebClientId) {
                val nativeSuccess = tryNativeGoogleCredentialSignIn(activityContext)
                if (nativeSuccess) return@launch
            }

            runCatching {
                val oauthUri = authClient.buildGoogleOAuthAuthorizeUrl()
                launchCustomTabOrBrowser(activityContext, oauthUri)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = err.message ?: str(R.string.err_open_google).ifBlank { "Could not open Google Sign-In." }
                    )
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
                            errorMessage = err.message ?: str(R.string.err_oauth_failed).ifBlank { "Google OAuth callback failed." }
                        )
                    }
                }
            )
        }
    }

    // ==================== ONBOARDING ROLE CHOICE & SELLER REGISTRATION ====================

    fun selectOnboardingRole(choice: OnboardingRoleChoice) {
        val nextStep = TiffinBusinessLogic.resolveOnboardingStepForChoice(choice)
        _uiState.update {
            it.copy(
                onboardingStep = nextStep,
                errorMessage = null,
                infoMessage = null
            )
        }
        if (nextStep == OnboardingStep.DISCOVERY) {
            loadDiscoveryMesses()
        }
    }

    fun openDiscoveryFromCustomerRegistration() {
        _uiState.update {
            it.copy(
                onboardingStep = OnboardingStep.DISCOVERY,
                errorMessage = null,
                infoMessage = null
            )
        }
        loadDiscoveryMesses()
    }

    fun navigateBackInOnboarding() {
        val current = _uiState.value.onboardingStep
        val target = TiffinBusinessLogic.resolveOnboardingBackStep(current)
        _uiState.update {
            it.copy(
                onboardingStep = target,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun submitSellerRegistration(
        messName: String,
        area: String,
        city: String,
        phone: String,
        vegType: VegType?,
        monthlyPriceInput: String,
        lunchTime: String,
        dinnerTime: String,
        deliveryAreas: String,
        description: String
    ) {
        val validationRes = TiffinBusinessLogic.validateSellerRegistrationRes(
            messName = messName,
            area = area,
            city = city,
            phone = phone,
            vegType = vegType,
            monthlyPriceInput = monthlyPriceInput
        )
        if (validationRes != null) {
            val fallback = TiffinBusinessLogic.validateSellerRegistration(
                messName, area, city, phone, vegType, monthlyPriceInput
            ) ?: ""
            _uiState.update { it.copy(errorMessage = str(validationRes).ifBlank { fallback }) }
            return
        }

        val price = monthlyPriceInput.trim().toDoubleOrNull() ?: return
        val vegDb = vegType?.dbValue ?: VegType.BOTH.dbValue

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.registerMyMess(
                messName = messName,
                area = area,
                city = city,
                phone = phone,
                vegTypeDb = vegDb,
                monthlyPrice = price,
                lunchTime = lunchTime,
                dinnerTime = dinnerTime,
                deliveryAreas = deliveryAreas,
                description = description
            ).fold(
                onSuccess = { res ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            sellerRegistrationResult = res,
                            onboardingStep = OnboardingStep.SELLER_SUCCESS,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.message ?: str(R.string.err_register_mess_failed)
                                .ifBlank { "Failed to register your mess. Please try again." }
                        )
                    }
                }
            )
        }
    }

    fun goToAdminDashboardAfterRegistration() {
        _uiState.update { it.copy(sellerRegistrationResult = null) }
        refreshAuthContext()
    }

    // ==================== CUSTOMER REGISTRATION & DISCOVERY ====================

    fun loadDiscoveryMesses() {
        viewModelScope.launch {
            _uiState.update { it.copy(discoveryLoading = true, errorMessage = null) }
            val todayIso = TiffinBusinessLogic.messToday("Asia/Kolkata").toString()
            repository.getDiscoverableActiveMesses(todayIso).fold(
                onSuccess = { (messes, menusByMess) ->
                    rawDiscoveryMesses = messes
                    rawTodayMenus = menusByMess
                    val sorted = TiffinBusinessLogic.sortMessesByProximity(
                        messes = messes,
                        todayMenusByMessId = menusByMess,
                        customerArea = _uiState.value.discoveryAreaFilter,
                        customerCity = _uiState.value.discoveryCityFilter
                    )
                    _uiState.update {
                        it.copy(
                            discoveryLoading = false,
                            discoveryMesses = sorted
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            discoveryLoading = false,
                            errorMessage = err.message ?: str(R.string.err_load_nearby_failed)
                                .ifBlank { "Unable to load nearby messes." }
                        )
                    }
                }
            )
        }
    }

    fun updateDiscoveryLocationFilter(area: String, city: String) {
        val sorted = TiffinBusinessLogic.sortMessesByProximity(
            messes = rawDiscoveryMesses,
            todayMenusByMessId = rawTodayMenus,
            customerArea = area,
            customerCity = city
        )
        _uiState.update {
            it.copy(
                discoveryAreaFilter = area,
                discoveryCityFilter = city,
                discoveryMesses = sorted
            )
        }
    }

    fun useCurrentLocationForDiscovery(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, errorMessage = null, infoMessage = null) }
            LocationHelper.detectAreaAndCity(context).fold(
                onSuccess = { detected ->
                    val sorted = TiffinBusinessLogic.sortMessesByProximity(
                        messes = rawDiscoveryMesses,
                        todayMenusByMessId = rawTodayMenus,
                        customerArea = detected.area,
                        customerCity = detected.city
                    )
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            discoveryAreaFilter = detected.area,
                            discoveryCityFilter = detected.city,
                            discoveryMesses = sorted,
                            infoMessage = str(R.string.info_location_detected, detected.area, detected.city)
                                .ifBlank { "Location detected: ${detected.area}, ${detected.city}" }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            errorMessage = err.message ?: str(R.string.err_location_detect_failed)
                                .ifBlank { "Unable to detect location. Enter area & city manually." }
                        )
                    }
                }
            )
        }
    }

    fun openSellerDetail(item: DiscoverableMess) {
        _uiState.update {
            it.copy(
                selectedDiscoveryMess = item,
                selectedMessReviews = emptyList(),
                reviewsLoading = true,
                onboardingStep = OnboardingStep.SELLER_DETAIL,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            repository.getMessReviews(item.mess.id).fold(
                onSuccess = { reviews ->
                    _uiState.update { it.copy(selectedMessReviews = reviews, reviewsLoading = false) }
                },
                onFailure = {
                    _uiState.update { it.copy(reviewsLoading = false) }
                }
            )
        }
    }

    fun joinMessFromDiscovery(mess: Mess) {
        val prefill = TiffinBusinessLogic.buildDiscoveryJoinPrefill(mess)
        _uiState.update {
            it.copy(
                discoveryPrefill = prefill,
                onboardingStep = OnboardingStep.CUSTOMER_REGISTRATION,
                errorMessage = null,
                infoMessage = str(R.string.info_selected_mess, mess.name)
                    .ifBlank { "Selected ${mess.name}. Complete your details below to send your join request." }
            )
        }
    }

    fun clearDiscoveryPrefill() {
        _uiState.update { it.copy(discoveryPrefill = null, infoMessage = null) }
    }

    fun submitRegistration(
        registrationCode: String,
        fullName: String,
        phone: String,
        addressOrRoom: String,
        area: String,
        city: String
    ) {
        val prefill = _uiState.value.discoveryPrefill
        val validationRes = TiffinBusinessLogic.validateCustomerRegistrationInputRes(
            hasSelectedMess = prefill != null,
            registrationCode = registrationCode,
            fullName = fullName,
            phone = phone,
            area = area,
            city = city
        )
        if (validationRes != null) {
            val fallback = TiffinBusinessLogic.validateCustomerRegistrationInput(
                prefill != null, registrationCode, fullName, phone, area, city
            ) ?: ""
            _uiState.update { it.copy(errorMessage = str(validationRes).ifBlank { fallback }) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = if (prefill != null) {
                repository.submitCustomerRegistrationByMess(
                    messId = prefill.messId,
                    fullName = fullName,
                    phone = phone,
                    addressOrRoom = addressOrRoom,
                    area = area,
                    city = city
                )
            } else {
                repository.submitCustomerRegistration(
                    registrationCode = registrationCode,
                    fullName = fullName,
                    phone = phone,
                    addressOrRoom = addressOrRoom,
                    area = area,
                    city = city
                )
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            reapplyingAfterReject = false,
                            discoveryPrefill = null
                        )
                    }
                    refreshAuthContext()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.message ?: str(R.string.err_registration_failed)
                                .ifBlank { "Registration failed. Check your mess code." }
                        )
                    }
                }
            )
        }
    }

    fun startReapplyFromRejected() {
        _uiState.update {
            it.copy(
                reapplyingAfterReject = true,
                onboardingStep = OnboardingStep.CUSTOMER_REGISTRATION,
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            authClient.signOut()
            val lang = repository.getLocalLanguage()
            val chosen = repository.hasChosenLanguage()
            _uiState.update {
                AuthUiState(
                    routeState = if (SupabaseConfig.isConfigured) {
                        AuthRouteState.SignedOut
                    } else {
                        AuthRouteState.SupabaseConfigMissing
                    },
                    currentLanguage = lang,
                    hasChosenLanguage = chosen,
                    isBusy = false
                )
            }
        }
    }

    class Factory(
        private val authClient: SupabaseAuthClient,
        private val repository: TiffinRepository,
        private val strings: AppStrings? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authClient, repository, strings) as T
        }
    }
}
