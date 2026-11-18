package com.example.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.TiffinRepository
import com.example.domain.CustomerProfile
import com.example.domain.DailyMenu
import com.example.domain.Mess
import com.example.domain.MonthlyBill
import com.example.domain.Payment
import com.example.domain.Profile
import com.example.domain.RenewalRequest
import com.example.domain.Subscription
import com.example.domain.SubscriptionPlan
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinMonthSummary
import com.example.domain.TiffinRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class CustomerUiState(
    val profile: Profile,
    val mess: Mess,
    val customer: CustomerProfile,
    val isLoading: Boolean = true,
    val isActionBusy: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedMonth: YearMonth,
    val todayTiffins: List<TiffinRecord> = emptyList(),
    val monthTiffins: List<TiffinRecord> = emptyList(),
    val monthSummary: TiffinMonthSummary = TiffinMonthSummary(0, 0, 0, 0, null, 0),
    val publishedMenus: List<DailyMenu> = emptyList(),
    val todayMenu: DailyMenu? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val activeSubscription: Subscription? = null,
    val availablePlans: List<SubscriptionPlan> = emptyList(),
    val renewalRequests: List<RenewalRequest> = emptyList(),
    val bills: List<MonthlyBill> = emptyList(),
    val currentMonthBill: MonthlyBill? = null,
    val payments: List<Payment> = emptyList()
)

class CustomerViewModel(
    private val repository: TiffinRepository,
    initialProfile: Profile,
    initialMess: Mess,
    initialCustomer: CustomerProfile
) : ViewModel() {

    private val initialMonth = TiffinBusinessLogic.messYearMonth(initialMess.timezone)

    private val _uiState = MutableStateFlow(
        CustomerUiState(
            profile = initialProfile,
            mess = initialMess,
            customer = initialCustomer,
            selectedMonth = initialMonth
        )
    )
    val uiState: StateFlow<CustomerUiState> = _uiState.asStateFlow()

    init {
        hydrateCachedMenus()
        refreshAll()
        observeRealtime()
    }

    private fun hydrateCachedMenus() {
        viewModelScope.launch {
            val cached = repository.getCachedMenus()
            if (cached.isNotEmpty()) {
                val todayIso = TiffinBusinessLogic.messToday(_uiState.value.mess.timezone).toString()
                _uiState.update {
                    it.copy(
                        publishedMenus = cached,
                        todayMenu = cached.firstOrNull { m -> m.menuDate == todayIso }
                    )
                }
            }
        }
    }

    private fun observeRealtime() {
        viewModelScope.launch {
            val messId = _uiState.value.mess.id
            repository.observeMessRealtime(
                messId = messId,
                tables = listOf("daily_menus", "tiffin_records", "monthly_bills")
            ).collect {
                refreshAll(silent = true)
            }
        }
    }

    fun refreshAll(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val state = _uiState.value
            val todayIso = TiffinBusinessLogic.messToday(state.mess.timezone).toString()
            val monthStart = TiffinBusinessLogic.monthStartIso(state.selectedMonth)
            val monthEnd = TiffinBusinessLogic.monthEndIso(state.selectedMonth)
            val currentMonthStart = TiffinBusinessLogic.monthStartIso(
                TiffinBusinessLogic.messYearMonth(state.mess.timezone)
            )

            val tiffinsRes = repository.getCustomerTiffinsForRange(state.customer.id, monthStart, monthEnd)
            val todayRes = if (monthStart <= todayIso && todayIso <= monthEnd) {
                tiffinsRes.map { list -> list.filter { it.serviceDate == todayIso } }
            } else {
                repository.getCustomerTiffinsForRange(state.customer.id, todayIso, todayIso)
            }
            val menusRes = repository.getPublishedMenus(state.mess.id)
            val subsRes = repository.getCustomerSubscriptions(state.customer.id)
            val plansRes = repository.getMessPlans(state.mess.id)
            val renewalsRes = repository.getCustomerRenewalRequests(state.customer.id)
            val billsRes = repository.getCustomerBills(state.customer.id)
            val paymentsRes = repository.getCustomerPayments(state.customer.id)

            val firstError = listOf(tiffinsRes, menusRes, subsRes, billsRes, paymentsRes)
                .firstOrNull { it.isFailure }?.exceptionOrNull()?.message

            val subs = subsRes.getOrDefault(state.subscriptions)
            val activeSub = subs.firstOrNull {
                it.status.name == "ACTIVE" || it.status.name == "EXPIRING_SOON"
            } ?: subs.firstOrNull()

            val monthRecords = tiffinsRes.getOrDefault(state.monthTiffins)
            val summary = TiffinBusinessLogic.calculateMonthlyTiffinSummary(monthRecords, activeSub)
            val menus = menusRes.getOrDefault(state.publishedMenus)
            val bills = billsRes.getOrDefault(state.bills)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = firstError,
                    todayTiffins = todayRes.getOrDefault(state.todayTiffins),
                    monthTiffins = monthRecords,
                    monthSummary = summary,
                    publishedMenus = menus,
                    todayMenu = menus.firstOrNull { m -> m.menuDate == todayIso },
                    subscriptions = subs,
                    activeSubscription = activeSub,
                    availablePlans = plansRes.getOrDefault(state.availablePlans),
                    renewalRequests = renewalsRes.getOrDefault(state.renewalRequests),
                    bills = bills,
                    currentMonthBill = bills.firstOrNull { b -> b.billingMonth == currentMonthStart } ?: bills.firstOrNull(),
                    payments = paymentsRes.getOrDefault(state.payments)
                )
            }
        }
    }

    fun selectHistoryMonth(month: YearMonth) {
        _uiState.update { it.copy(selectedMonth = month) }
        refreshAll()
    }

    fun submitRenewalRequest(planId: String?, note: String?) {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            repository.submitRenewalRequest(state.mess.id, state.customer.id, planId, note).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            successMessage = "Renewal request sent to ${state.mess.name} admin."
                        )
                    }
                    refreshAll(silent = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: "Failed to submit renewal request."
                        )
                    }
                }
            )
        }
    }

    fun updateProfile(fullName: String, phone: String, addressOrRoom: String) {
        viewModelScope.launch {
            if (fullName.isBlank() || phone.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Name and mobile number cannot be empty.") }
                return@launch
            }
            val state = _uiState.value
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            repository.updateOwnCustomerProfile(
                userId = state.profile.id,
                customerId = state.customer.id,
                fullName = fullName,
                phone = phone,
                addressOrRoom = addressOrRoom
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            profile = it.profile.copy(fullName = fullName.trim(), phone = phone.trim()),
                            customer = it.customer.copy(
                                fullName = fullName.trim(),
                                phone = phone.trim(),
                                addressOrRoom = addressOrRoom.trim()
                            ),
                            successMessage = "Profile updated."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: "Unable to update profile."
                        )
                    }
                }
            )
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    class Factory(
        private val repository: TiffinRepository,
        private val profile: Profile,
        private val mess: Mess,
        private val customer: CustomerProfile
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CustomerViewModel(repository, profile, mess, customer) as T
        }
    }
}
