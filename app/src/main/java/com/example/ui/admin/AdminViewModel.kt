package com.example.ui.admin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ImageUploadHelper
import com.example.data.TiffinRepository
import com.example.ui.theme.AppStrings
import com.example.domain.AdminCustomerMarkStatus
import com.example.domain.AdminDailyCustomerSummary
import com.example.domain.AdminDayTotals
import com.example.domain.AdminInvitation
import com.example.domain.BillStatus
import com.example.domain.CustomerDailyMark
import com.example.domain.CustomerProfile
import com.example.domain.DailyMenu
import com.example.domain.Mess
import com.example.domain.MonthlyBill
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.PaymentProof
import com.example.domain.PaymentProofStatus
import com.example.domain.Profile
import com.example.domain.RegistrationRequest
import com.example.domain.RenewalRequest
import com.example.domain.Subscription
import com.example.domain.SubscriptionPlan
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinLeave
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
    val dailyMarks: List<CustomerDailyMark> = emptyList(),
    val monthDailyMarks: List<CustomerDailyMark> = emptyList(),
    val leaves: List<TiffinLeave> = emptyList(),
    val menus: List<DailyMenu> = emptyList(),
    val bills: List<MonthlyBill> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val paymentProofs: List<PaymentProof> = emptyList(),
    val upiQrSignedUrl: String? = null,
    val proofSignedUrls: Map<String, String> = emptyMap(),
    val adminInvitations: List<AdminInvitation> = emptyList()
) {
    val pendingPaymentProofs: List<PaymentProof>
        get() = paymentProofs.filter { it.status == PaymentProofStatus.PENDING }
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

    val dailyCustomerSummaries: Map<String, AdminDailyCustomerSummary>
        get() {
            val dateIso = selectedDate.toString()
            val combinedMarks = (monthDailyMarks + dailyMarks)
                .distinctBy { "${it.customerId}_${it.serviceDate}" }
            return TiffinBusinessLogic.buildAdminDailyCustomerSummaries(
                customers = customers,
                dateIso = dateIso,
                dailyMarks = combinedMarks,
                leaves = leaves,
                monthTiffins = monthTiffins,
                subscriptions = subscriptions,
                mess = mess
            )
        }

    val dayTotals: AdminDayTotals
        get() = TiffinBusinessLogic.computeAdminDayTotals(
            activeCustomers = activeCustomers,
            summaries = dailyCustomerSummaries,
            dailyTiffins = dailyTiffins,
            dateIso = selectedDate.toString()
        )
}

class AdminViewModel(
    private val repository: TiffinRepository,
    initialProfile: Profile,
    initialMess: Mess,
    initialIsOwner: Boolean,
    private val strings: AppStrings? = null
) : ViewModel() {

    private fun str(resId: Int, vararg args: Any): String = strings?.get(resId, *args) ?: ""

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
                tables = listOf(
                    "registration_requests",
                    "tiffin_records",
                    "daily_menus",
                    "monthly_bills",
                    "customer_daily_marks",
                    "tiffin_leaves",
                    "payment_proofs",
                    "payments"
                )
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
            val dailyMarksRes = repository.getMessDailyMarksForDate(messId, dateIso)
            val monthMarksRes = repository.getMessDailyMarksForRange(messId, monthStart, monthEnd)
            val leavesRes = repository.getMessLeaves(messId)
            val menusRes = repository.getAllMessMenus(messId)
            val billsRes = repository.getMessBills(messId)
            val paymentsRes = repository.getMessPayments(messId)
            val proofsRes = repository.getMessPaymentProofs(messId)
            val upiRes = repository.getMyMessUpiDetails(messId)
            val invitesRes = if (state.isOwner) repository.getAdminInvitations(messId) else Result.success(emptyList())

            val firstErr = listOf(customersRes, dailyTiffinsRes, menusRes, billsRes)
                .firstOrNull { it.isFailure }?.exceptionOrNull()?.message

            val upiDetails = upiRes.getOrNull()
            val refreshedMess = if (upiDetails != null) {
                state.mess.copy(upiId = upiDetails.upiId, upiQrPath = upiDetails.upiQrPath)
            } else {
                state.mess
            }
            val qrSignedUrl = if (!refreshedMess.upiQrPath.isNullOrBlank()) {
                repository.getSignedStorageUrl(refreshedMess.upiQrPath).getOrNull() ?: state.upiQrSignedUrl
            } else {
                null
            }

            val proofs = proofsRes.getOrDefault(state.paymentProofs)
            val updatedProofUrls = state.proofSignedUrls.toMutableMap()
            proofs.take(20).forEach { proof ->
                if (proof.screenshotPath.isNotBlank() && !updatedProofUrls.containsKey(proof.screenshotPath)) {
                    repository.getSignedStorageUrl(proof.screenshotPath).onSuccess { url ->
                        updatedProofUrls[proof.screenshotPath] = url
                    }
                }
            }

            _uiState.update {
                it.copy(
                    mess = refreshedMess,
                    isLoading = false,
                    errorMessage = firstErr,
                    customers = customersRes.getOrDefault(state.customers),
                    pendingRequests = requestsRes.getOrDefault(state.pendingRequests),
                    plans = plansRes.getOrDefault(state.plans),
                    subscriptions = subsRes.getOrDefault(state.subscriptions),
                    renewalRequests = renewalsRes.getOrDefault(state.renewalRequests),
                    dailyTiffins = dailyTiffinsRes.getOrDefault(state.dailyTiffins),
                    monthTiffins = monthTiffinsRes.getOrDefault(state.monthTiffins),
                    dailyMarks = dailyMarksRes.getOrDefault(state.dailyMarks),
                    monthDailyMarks = monthMarksRes.getOrDefault(state.monthDailyMarks),
                    leaves = leavesRes.getOrDefault(state.leaves),
                    menus = menusRes.getOrDefault(state.menus),
                    bills = billsRes.getOrDefault(state.bills),
                    payments = paymentsRes.getOrDefault(state.payments),
                    paymentProofs = proofs,
                    upiQrSignedUrl = qrSignedUrl,
                    proofSignedUrls = updatedProofUrls,
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
        val msg = if (approve) {
            str(R.string.msg_customer_approved).ifBlank { "Customer approved." }
        } else {
            str(R.string.msg_registration_rejected).ifBlank { "Registration rejected." }
        }
        runAction(msg) {
            repository.reviewRegistrationRequest(requestId, approve)
        }
    }

    fun createCustomer(fullName: String, phone: String, email: String?, addressOrRoom: String) {
        if (fullName.isBlank() || phone.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = str(R.string.err_name_mobile_req).ifBlank { "Name and mobile number are required." })
            }
            return
        }
        runAction(str(R.string.msg_customer_added).ifBlank { "Customer added." }) {
            repository.adminCreateCustomer(_uiState.value.mess.id, fullName, phone, email, addressOrRoom)
        }
    }

    fun updateCustomerDetails(customerId: String, fullName: String, phone: String, addressOrRoom: String) {
        runAction(str(R.string.msg_customer_details_updated).ifBlank { "Customer details updated." }) {
            repository.adminUpdateCustomerDetails(customerId, fullName, phone, addressOrRoom)
        }
    }

    fun updateCustomerStatus(customerId: String, newStatus: String) {
        runAction(str(R.string.msg_customer_status_updated).ifBlank { "Customer status updated." }) {
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
        runAction(str(R.string.msg_tiffin_marked).ifBlank { "Tiffin record updated." }) {
            repository.upsertTiffinRecord(customerId, dateIso, mealSlot, status, note)
        }
    }

    fun bulkMarkDelivered(customerIds: List<String>, dateIso: String, mealSlot: String) {
        if (customerIds.isEmpty()) return
        runAction(str(R.string.msg_bulk_delivered).ifBlank { "Marked eligible tiffins as delivered." }) {
            repository.bulkMarkTiffinsDelivered(_uiState.value.mess.id, customerIds, dateIso, mealSlot)
        }
    }

    fun createLeaveForCustomer(
        customerId: String,
        startDateIso: String,
        endDateIso: String,
        reason: String
    ) {
        val parsed = runCatching {
            LocalDate.parse(startDateIso.trim()) to LocalDate.parse(endDateIso.trim())
        }
        if (parsed.isFailure) {
            _uiState.update {
                it.copy(errorMessage = str(R.string.err_valid_dates).ifBlank { "Enter valid dates in YYYY-MM-DD format." })
            }
            return
        }
        val (start, end) = parsed.getOrThrow()
        if (end.isBefore(start)) {
            _uiState.update {
                it.copy(errorMessage = str(R.string.err_end_before_start).ifBlank { "End date must be on or after start date." })
            }
            return
        }
        runAction(str(R.string.msg_leave_added_customer).ifBlank { "Leave added for customer." }) {
            repository.createTiffinLeave(
                messId = _uiState.value.mess.id,
                customerId = customerId,
                startDateIso = start.toString(),
                endDateIso = end.toString(),
                reason = reason
            )
        }
    }

    fun cancelCustomerLeave(leaveId: String) {
        runAction(str(R.string.msg_leave_cancelled).ifBlank { "Leave cancelled." }) {
            repository.cancelTiffinLeave(leaveId)
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
        val msg = if (publish) {
            str(R.string.msg_menu_published, dateIso).ifBlank { "Menu published for $dateIso." }
        } else {
            str(R.string.msg_menu_draft_saved).ifBlank { "Draft menu saved." }
        }
        runAction(msg) {
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
        runAction(str(R.string.msg_plan_created).ifBlank { "Subscription plan created." }) {
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
        runAction(str(R.string.msg_sub_assigned).ifBlank { "Subscription assigned." }) {
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
        runAction(str(R.string.msg_sub_updated).ifBlank { "Subscription updated." }) {
            repository.updateSubscriptionStatus(subscriptionId, status)
        }
    }

    fun reviewRenewalRequest(requestId: String, approve: Boolean) {
        val msg = if (approve) {
            str(R.string.msg_renewal_approved).ifBlank { "Renewal request approved." }
        } else {
            str(R.string.msg_renewal_declined).ifBlank { "Renewal request declined." }
        }
        runAction(msg) {
            repository.reviewRenewalRequest(requestId, approve, _uiState.value.profile.id)
        }
    }

    fun generateMonthlyBill(
        customerId: String,
        billingMonthIso: String,
        discountAmount: Double,
        previousBalance: Double
    ) {
        runAction(str(R.string.msg_bill_generated).ifBlank { "Monthly bill generated." }) {
            repository.generateMonthlyBill(customerId, billingMonthIso, discountAmount, previousBalance)
        }
    }

    fun recordPayment(billId: String, amount: Double, method: PaymentMethod, referenceNote: String?) {
        runAction(str(R.string.msg_payment_recorded).ifBlank { "Payment recorded." }) {
            repository.recordPayment(billId, amount, method, referenceNote)
        }
    }

    fun reversePayment(paymentId: String, reason: String) {
        runAction(str(R.string.msg_payment_reversed).ifBlank { "Payment reversed and bill balance recalculated." }) {
            repository.reversePayment(paymentId, reason)
        }
    }

    fun updateMessSettings(
        name: String,
        phone: String,
        address: String,
        currency: String,
        defaultDueDay: Int,
        mealSlots: List<String>,
        dailyMarkCutoffTime: String = _uiState.value.mess.dailyMarkCutoffTime
    ) {
        val trimmedCutoff = dailyMarkCutoffTime.trim()
        if (!TiffinBusinessLogic.isValidCutoffTime(trimmedCutoff)) {
            _uiState.update {
                it.copy(
                    errorMessage = str(R.string.err_cutoff_format)
                        .ifBlank { "Cutoff time must be in 24h HH:mm format (e.g. 10:00)." }
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            repository.updateMessSettings(
                messId = _uiState.value.mess.id,
                name = name,
                phone = phone,
                address = address,
                currency = currency,
                defaultDueDay = defaultDueDay,
                mealSlots = mealSlots,
                dailyMarkCutoffTime = trimmedCutoff
            ).fold(
                onSuccess = { updatedMess ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            mess = updatedMess,
                            successMessage = str(R.string.msg_mess_settings_saved).ifBlank { "Mess settings saved." }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_update_failed).ifBlank { "Update failed." }
                        )
                    }
                }
            )
        }
    }

    fun rotateRegistrationCode(newCode: String) {
        runAction(str(R.string.msg_code_updated).ifBlank { "Mess registration code updated." }) {
            repository.ownerUpdateRegistrationCode(_uiState.value.mess.id, newCode)
        }
    }

    fun inviteAdmin(email: String) {
        runAction(str(R.string.msg_admin_invited, email).ifBlank { "Admin invitation created for $email." }) {
            repository.ownerInviteAdmin(_uiState.value.mess.id, email)
        }
    }

    fun saveSellerUpiSettings(context: Context, upiIdInput: String, newQrUri: Uri?) {
        val trimmedUpi = upiIdInput.trim()
        if (trimmedUpi.isNotEmpty() && !TiffinBusinessLogic.isValidUpiId(trimmedUpi)) {
            _uiState.update {
                it.copy(
                    errorMessage = str(R.string.invalid_upi_id_err)
                        .ifBlank { "Enter a valid UPI ID (e.g. name@okhdfcbank)." }
                )
            }
            return
        }
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }

            val uploadedQrPath = if (newQrUri != null) {
                val bytes = ImageUploadHelper.readCompressedJpeg(context, newQrUri).getOrElse {
                    _uiState.update { s ->
                        s.copy(
                            isActionBusy = false,
                            errorMessage = str(R.string.err_pick_screenshot_failed)
                                .ifBlank { "Could not read selected image. Please try another image." }
                        )
                    }
                    return@launch
                }
                repository.uploadSellerQrImage(state.mess.id, bytes).getOrElse { err ->
                    _uiState.update { s ->
                        s.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_update_failed).ifBlank { "Update failed." }
                        )
                    }
                    return@launch
                }
            } else {
                state.mess.upiQrPath
            }

            repository.updateSellerUpiSettings(
                messId = state.mess.id,
                upiId = trimmedUpi.ifBlank { null },
                upiQrPath = uploadedQrPath
            ).fold(
                onSuccess = { details ->
                    val signedUrl = details.upiQrPath?.let { path ->
                        repository.getSignedStorageUrl(path).getOrNull()
                    }
                    _uiState.update { s ->
                        s.copy(
                            isActionBusy = false,
                            mess = s.mess.copy(upiId = details.upiId, upiQrPath = details.upiQrPath),
                            upiQrSignedUrl = signedUrl ?: s.upiQrSignedUrl,
                            successMessage = str(R.string.msg_payment_settings_saved)
                                .ifBlank { "UPI payment settings saved." }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_update_failed).ifBlank { "Update failed." }
                        )
                    }
                }
            )
        }
    }

    fun confirmPaymentProof(proofId: String) {
        runAction(str(R.string.msg_proof_confirmed).ifBlank { "Payment confirmed and bill updated." }) {
            repository.reviewPaymentProof(proofId = proofId, confirm = true, rejectReason = null)
        }
    }

    fun rejectPaymentProof(proofId: String, reason: String?) {
        runAction(str(R.string.msg_proof_rejected).ifBlank { "Payment proof rejected." }) {
            repository.reviewPaymentProof(proofId = proofId, confirm = false, rejectReason = reason)
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
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_operation_failed).ifBlank { "Operation failed." }
                        )
                    }
                }
            )
        }
    }

    class Factory(
        private val repository: TiffinRepository,
        private val profile: Profile,
        private val mess: Mess,
        private val isOwner: Boolean,
        private val strings: AppStrings? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminViewModel(repository, profile, mess, isOwner, strings) as T
        }
    }
}
