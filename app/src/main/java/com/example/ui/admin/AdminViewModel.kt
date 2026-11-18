package com.example.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.TiffinRepository
import com.example.domain.AdminInvitation
import com.example.domain.BillStatus
import com.example.domain.CustomerProfile
import com.example.domain.DailyMenu
import com.example.domain.Mess
import com.example.domain.MonthlyBill
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.Profile
import com.example.domain.RegistrationRequest
import com.example.domain.RenewalRequest
import com.example.domain.Subscription
import com.example.domain.SubscriptionPlan
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinRecord
import com.example.domain.TiffinStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class AdminUiState(
    val profile: Profile,
    val mess: Mess,
    val isOwner: Boolean,
    val isLoading: Boolean = true,
    val isActionBusy: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedDate: LocalDate,
    val selectedMonth: YearMonth,
    val customers: List<CustomerProfile> = emptyList(),
    val pendingRequests: List<RegistrationRequest> = emptyList(),
    val plans: List<SubscriptionPlan> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val renewalRequests: List<RenewalRequest> = emptyList(),
    val dailyTiffins: List<TiffinRecord> = emptyList(),
    val monthTiffins: List<TiffinRecord> = emptyList(),
    val menus: List<DailyMenu> = emptyList(),
    val bills: List<MonthlyBill> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val adminInvitations: List<AdminInvitation> = emptyList()
) {
    val activeCustomers: List<CustomerProfile>
        get() = customers.filter { it.customerStatus.name == "ACTIVE" }

    val expectedTiffinsToday: Int
        get() {
            val activeCount = activeCustomers.size
            val slotsCount = mess.mealSlots.size.coerceAtLeast(1)
            return activeCount * slotsCount
        }

    val deliveredTodayCount: Int
        get() = dailyTiffins.count { it.status == TiffinStatus.DELIVERED }

    val notDeliveredTodayCount: Int
        get() = dailyTiffins.count { it.status == TiffinStatus.NOT_DELIVERED }

    val cancelledTodayCount: Int
        get() = dailyTiffins.count { it.status == TiffinStatus.CANCELLED }

    val pendingTodayCount: Int
        get() = (expectedTiffinsToday - dailyTiffins.size).coerceAtLeast(0)

    val completionRatePercent: Int
        get() = if (expectedTiffinsToday == 0) 0 else ((deliveredTodayCount * 100.0) / expectedTiffinsToday).toInt().coerceIn(0, 100)

    val monthBills: List<MonthlyBill>
        get() {
            val monthIso = TiffinBusinessLogic.monthStartIso(selectedMonth)
            return bills.filter { it.billingMonth == monthIso }
        }

    val monthlyExpectedRevenue: Double
        get() = monthBills.sumOf { it.totalAmount }

    val monthlyCollected: Double
        get() = monthBills.sumOf { it.amountPaid }

    val monthlyPendingBalance: Double
        get() = monthBills.filter { it.status != BillStatus.OVERDUE }.sumOf { it.balanceDue }

    val monthlyOverdueBalance: Double
        get() = monthBills.filter { it.status == BillStatus.OVERDUE }.sumOf { it.balanceDue }
}

class AdminViewModel(
    private val repository: TiffinRepository,
    initialProfile: Profile,
    initialMess: Mess,
    initialIsOwner: Boolean
) : ViewModel() {

    private val initialToday = TiffinBusinessLogic.messToday(initialMess.timezone)
    private val initialMonth = TiffinBusinessLogic.messYearMonth(initialMess.timezone)

    private val _uiState = MutableStateFlow(
        AdminUiState(
            profile = initialProfile,
            mess = initialMess,
            isOwner = initialIsOwner,
            selectedDate = initialToday,
            selectedMonth = initialMonth
        )
    )
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
        observeRealtime()
    }

    private fun observeRealtime() {
        viewModelScope.launch {
            repository.observeMessRealtime(
                messId = _uiState.value.mess.id,
                tables = listOf("registration_requests", "tiffin_records", "daily_menus", "monthly_bills")
            ).collect {
                refreshAll(silent = true)
            }
        }
    }

    fun refreshAll(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val state = _uiState.value
            val messId = state.mess.id
            val dateIso = state.selectedDate.toString()
            val monthStart = TiffinBusinessLogic.monthStartIso(state.selectedMonth)
            val monthEnd = TiffinBusinessLogic.monthEndIso(state.selectedMonth)

            val customersRes = repository.getMessCustomers(messId)
            val requestsRes = repository.getPendingRegistrationRequests(messId)
            val plansRes = repository.getMessPlans(messId, activeOnly = false)
            val subsRes = repository.getMessSubscriptions(messId)
            val renewalsRes = repository.getMessRenewalRequests(messId)
            val dailyTiffinsRes = repository.getMessTiffinsForDate(messId, dateIso)
            val monthTiffinsRes = repository.getMessTiffinsForRange(messId, monthStart, monthEnd)
            val menusRes = repository.getAllMessMenus(messId)
            val billsRes = repository.getMessBills(messId)
            val paymentsRes = repository.getMessPayments(messId)
            val invitesRes = if (state.isOwner) repository.getAdminInvitations(messId) else Result.success(emptyList())

            val firstErr = listOf(customersRes, dailyTiffinsRes, menusRes, billsRes)
                .firstOrNull { it.isFailure }?.exceptionOrNull()?.message

            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = firstErr,
                    customers = customersRes.getOrDefault(state.customers),
                    pendingRequests = requestsRes.getOrDefault(state.pendingRequests),
                    plans = plansRes.getOrDefault(state.plans),
                    subscriptions = subsRes.getOrDefault(state.subscriptions),
                    renewalRequests = renewalsRes.getOrDefault(state.renewalRequests),
                    dailyTiffins = dailyTiffinsRes.getOrDefault(state.dailyTiffins),
                    monthTiffins = monthTiffinsRes.getOrDefault(state.monthTiffins),
                    menus = menusRes.getOrDefault(state.menus),
                    bills = billsRes.getOrDefault(state.bills),
                    payments = paymentsRes.getOrDefault(state.payments),
                    adminInvitations = invitesRes.getOrDefault(state.adminInvitations)
                )
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        refreshAll()
    }

    fun selectMonth(month: YearMonth) {
        _uiState.update { it.copy(selectedMonth = month) }
        refreshAll()
    }

    fun clearBanners() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun reviewRegistrationRequest(requestId: String, approve: Boolean) {
        runAction(if (approve) "Customer approved." else "Registration rejected.") {
            repository.reviewRegistrationRequest(requestId, approve)
        }
    }

    fun createCustomer(fullName: String, phone: String, email: String?, addressOrRoom: String) {
        if (fullName.isBlank() || phone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name and mobile number are required.") }
            return
        }
        runAction("Customer added.") {
            repository.adminCreateCustomer(_uiState.value.mess.id, fullName, phone, email, addressOrRoom)
        }
    }

    fun updateCustomerDetails(customerId: String, fullName: String, phone: String, addressOrRoom: String) {
        runAction("Customer details updated.") {
            repository.adminUpdateCustomerDetails(customerId, fullName, phone, addressOrRoom)
        }
    }

    fun updateCustomerStatus(customerId: String, newStatus: String) {
        runAction("Customer status updated to $newStatus.") {
            repository.adminUpdateCustomerStatus(customerId, newStatus)
        }
    }

    fun markTiffin(
        customerId: String,
        dateIso: String,
        mealSlot: String,
        status: TiffinStatus,
        note: String? = null
    ) {
        runAction("Tiffin marked as ${status.name.lowercase().replace('_', ' ')}.") {
            repository.upsertTiffinRecord(customerId, dateIso, mealSlot, status, note)
        }
    }

    fun bulkMarkDelivered(customerIds: List<String>, dateIso: String, mealSlot: String) {
        if (customerIds.isEmpty()) return
        runAction("Marked ${customerIds.size} tiffins as delivered.") {
            repository.bulkMarkTiffinsDelivered(_uiState.value.mess.id, customerIds, dateIso, mealSlot)
        }
    }

    fun saveMenu(
        dateIso: String,
        breakfast: String?,
        lunch: String?,
        dinner: String?,
        specialItems: String?,
        notes: String?,
        publish: Boolean
    ) {
        runAction(if (publish) "Menu published for $dateIso." else "Draft menu saved.") {
            repository.saveMenu(
                messId = _uiState.value.mess.id,
                menuDateIso = dateIso,
                breakfast = breakfast,
                lunch = lunch,
                dinner = dinner,
                specialItems = specialItems,
                notes = notes,
                publish = publish,
                createdBy = _uiState.value.profile.id
            )
        }
    }

    fun createPlan(
        name: String,
        durationDays: Int,
        price: Double,
        includedTiffins: Int,
        mealSlots: List<String>,
        extraTiffinPrice: Double
    ) {
        runAction("Subscription plan created.") {
            repository.createSubscriptionPlan(
                messId = _uiState.value.mess.id,
                name = name,
                durationDays = durationDays,
                price = price,
                includedTiffins = includedTiffins,
                mealSlots = mealSlots,
                extraTiffinPrice = extraTiffinPrice
            )
        }
    }

    fun assignSubscription(
        customerId: String,
        plan: SubscriptionPlan?,
        customName: String,
        startDateIso: String,
        endDateIso: String,
        includedTiffins: Int,
        extraTiffinPrice: Double,
        mealSlots: List<String>,
        agreedPrice: Double
    ) {
        runAction("Subscription assigned.") {
            repository.assignSubscription(
                messId = _uiState.value.mess.id,
                customerId = customerId,
                planId = plan?.id,
                planName = plan?.name ?: customName,
                startDateIso = startDateIso,
                endDateIso = endDateIso,
                includedTiffins = includedTiffins,
                extraTiffinPrice = extraTiffinPrice,
                mealSlots = mealSlots,
                agreedPrice = agreedPrice,
                createdBy = _uiState.value.profile.id
            )
        }
    }

    fun updateSubscriptionStatus(subscriptionId: String, status: SubscriptionStatus) {
        runAction("Subscription updated.") {
            repository.updateSubscriptionStatus(subscriptionId, status)
        }
    }

    fun reviewRenewalRequest(requestId: String, approve: Boolean) {
        runAction(if (approve) "Renewal request approved." else "Renewal request declined.") {
            repository.reviewRenewalRequest(requestId, approve, _uiState.value.profile.id)
        }
    }

    fun generateMonthlyBill(
        customerId: String,
        billingMonthIso: String,
        discountAmount: Double,
        previousBalance: Double
    ) {
        runAction("Monthly bill generated.") {
            repository.generateMonthlyBill(customerId, billingMonthIso, discountAmount, previousBalance)
        }
    }

    fun recordPayment(billId: String, amount: Double, method: PaymentMethod, referenceNote: String?) {
        runAction("Payment recorded.") {
            repository.recordPayment(billId, amount, method, referenceNote)
        }
    }

    fun reversePayment(paymentId: String, reason: String) {
        runAction("Payment reversed and bill balance recalculated.") {
            repository.reversePayment(paymentId, reason)
        }
    }

    fun updateMessSettings(
        name: String,
        phone: String,
        address: String,
        currency: String,
        defaultDueDay: Int,
        mealSlots: List<String>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            repository.updateMessSettings(_uiState.value.mess.id, name, phone, address, currency, defaultDueDay, mealSlots)
                .fold(
                    onSuccess = { updatedMess ->
                        _uiState.update {
                            it.copy(isActionBusy = false, mess = updatedMess, successMessage = "Mess settings saved.")
                        }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(isActionBusy = false, errorMessage = err.message ?: "Update failed.") }
                    }
                )
        }
    }

    fun rotateRegistrationCode(newCode: String) {
        runAction("Mess registration code updated.") {
            repository.ownerUpdateRegistrationCode(_uiState.value.mess.id, newCode)
        }
    }

    fun inviteAdmin(email: String) {
        runAction("Admin invitation created for $email.") {
            repository.ownerInviteAdmin(_uiState.value.mess.id, email)
        }
    }

    private fun <T> runAction(successMsg: String, block: suspend () -> Result<T>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            block().fold(
                onSuccess = {
                    _uiState.update { it.copy(isActionBusy = false, successMessage = successMsg) }
                    refreshAll(silent = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isActionBusy = false, errorMessage = err.message ?: "Operation failed.")
                    }
                }
            )
        }
    }

    class Factory(
        private val repository: TiffinRepository,
        private val profile: Profile,
        private val mess: Mess,
        private val isOwner: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminViewModel(repository, profile, mess, isOwner) as T
        }
    }
}
