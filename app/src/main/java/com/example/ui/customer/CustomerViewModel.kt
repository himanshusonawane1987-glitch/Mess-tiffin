package com.example.ui.customer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ImageUploadHelper
import com.example.data.LocationHelper
import com.example.data.TiffinRepository
import com.example.ui.theme.AppStrings
import com.example.domain.CustomerDailyMark
import com.example.domain.CustomerProfile
import com.example.domain.DailyMarkChoice
import com.example.domain.DailyMenu
import com.example.domain.DiscoverableMess
import com.example.domain.LeaveStatus
import com.example.domain.Mess
import com.example.domain.MessReview
import com.example.domain.MonthlyBill
import com.example.domain.Payment
import com.example.domain.PaymentProof
import com.example.domain.Profile
import com.example.domain.RenewalRequest
import com.example.domain.Subscription
import com.example.domain.SubscriptionPlan
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinLeave
import com.example.domain.TiffinMonthSummary
import com.example.domain.TiffinRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val todayMark: CustomerDailyMark? = null,
    val monthMarks: List<CustomerDailyMark> = emptyList(),
    val leaves: List<TiffinLeave> = emptyList(),
    val monthSummary: TiffinMonthSummary = TiffinMonthSummary(0, 0, 0, 0, null, 0),
    val publishedMenus: List<DailyMenu> = emptyList(),
    val todayMenu: DailyMenu? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val activeSubscription: Subscription? = null,
    val availablePlans: List<SubscriptionPlan> = emptyList(),
    val renewalRequests: List<RenewalRequest> = emptyList(),
    val bills: List<MonthlyBill> = emptyList(),
    val currentMonthBill: MonthlyBill? = null,
    val payments: List<Payment> = emptyList(),
    val paymentProofs: List<PaymentProof> = emptyList(),
    val upiQrSignedUrl: String? = null,
    val proofSignedUrls: Map<String, String> = emptyMap(),
    val locationBannerDismissed: Boolean = false,
    val discoveryMesses: List<DiscoverableMess> = emptyList(),
    val discoveryAreaFilter: String = "",
    val discoveryCityFilter: String = "",
    val discoveryLoading: Boolean = false,
    val isLocating: Boolean = false,
    val discoveryInfoMessage: String? = null,
    val selectedDiscoveryMess: DiscoverableMess? = null,
    val selectedMessReviews: List<MessReview> = emptyList(),
    val reviewsLoading: Boolean = false
)

class CustomerViewModel(
    private val repository: TiffinRepository,
    initialProfile: Profile,
    initialMess: Mess,
    initialCustomer: CustomerProfile,
    private val strings: AppStrings? = null
) : ViewModel() {

    private fun str(resId: Int, vararg args: Any): String = strings?.get(resId, *args) ?: ""

    private val initialMonth = TiffinBusinessLogic.messYearMonth(initialMess.timezone)
    private var rawDiscoveryMesses: List<Mess> = emptyList()
    private var rawTodayMenus: Map<String, DailyMenu> = emptyMap()

    private val _uiState = MutableStateFlow(
        CustomerUiState(
            profile = initialProfile,
            mess = initialMess,
            customer = initialCustomer,
            selectedMonth = initialMonth,
            discoveryAreaFilter = initialProfile.area,
            discoveryCityFilter = initialProfile.city
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
                tables = listOf(
                    "daily_menus",
                    "tiffin_records",
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
            val marksRes = repository.getCustomerDailyMarksForRange(state.customer.id, monthStart, monthEnd)
            val todayMarkRes = if (monthStart <= todayIso && todayIso <= monthEnd) {
                marksRes.map { list -> list.firstOrNull { it.serviceDate == todayIso } }
            } else {
                repository.getCustomerDailyMarksForRange(state.customer.id, todayIso, todayIso)
                    .map { it.firstOrNull() }
            }
            val leavesRes = repository.getCustomerLeaves(state.customer.id)
            val menusRes = repository.getPublishedMenus(state.mess.id)
            val subsRes = repository.getCustomerSubscriptions(state.customer.id)
            val plansRes = repository.getMessPlans(state.mess.id)
            val renewalsRes = repository.getCustomerRenewalRequests(state.customer.id)
            val billsRes = repository.getCustomerBills(state.customer.id)
            val paymentsRes = repository.getCustomerPayments(state.customer.id)
            val proofsRes = repository.getCustomerPaymentProofs(state.customer.id)
            val upiRes = repository.getMyMessUpiDetails(state.mess.id)

            val firstError = listOf(tiffinsRes, menusRes, subsRes, billsRes, paymentsRes)
                .firstOrNull { it.isFailure }?.exceptionOrNull()?.message

            val subs = subsRes.getOrDefault(state.subscriptions)
            val activeSub = subs.firstOrNull {
                it.status.name == "ACTIVE" || it.status.name == "EXPIRING_SOON"
            } ?: subs.firstOrNull()

            val monthRecords = tiffinsRes.getOrDefault(state.monthTiffins)
            val marks = marksRes.getOrDefault(state.monthMarks)
            val leaves = leavesRes.getOrDefault(state.leaves)
            val summary = TiffinBusinessLogic.calculateMonthlyTiffinSummary(
                records = monthRecords,
                activeSubscription = activeSub,
                dailyMarks = marks,
                leaves = leaves
            )
            val menus = menusRes.getOrDefault(state.publishedMenus)
            val bills = billsRes.getOrDefault(state.bills)
            val proofs = proofsRes.getOrDefault(state.paymentProofs)
            val upiDetails = upiRes.getOrNull()
            val refreshedMess = if (upiDetails != null) {
                state.mess.copy(upiId = upiDetails.upiId, upiQrPath = upiDetails.upiQrPath)
            } else {
                state.mess
            }

            val qrPath = refreshedMess.upiQrPath
            val qrSignedUrl = if (!qrPath.isNullOrBlank()) {
                repository.getSignedStorageUrl(qrPath).getOrNull() ?: state.upiQrSignedUrl
            } else {
                null
            }

            val updatedProofUrls = state.proofSignedUrls.toMutableMap()
            proofs.take(10).forEach { proof ->
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
                    errorMessage = firstError,
                    todayTiffins = todayRes.getOrDefault(state.todayTiffins),
                    monthTiffins = monthRecords,
                    todayMark = todayMarkRes.getOrDefault(state.todayMark),
                    monthMarks = marks,
                    leaves = leaves,
                    monthSummary = summary,
                    publishedMenus = menus,
                    todayMenu = menus.firstOrNull { m -> m.menuDate == todayIso },
                    subscriptions = subs,
                    activeSubscription = activeSub,
                    availablePlans = plansRes.getOrDefault(state.availablePlans),
                    renewalRequests = renewalsRes.getOrDefault(state.renewalRequests),
                    bills = bills,
                    currentMonthBill = bills.firstOrNull { b -> b.billingMonth == currentMonthStart } ?: bills.firstOrNull(),
                    payments = paymentsRes.getOrDefault(state.payments),
                    paymentProofs = proofs,
                    upiQrSignedUrl = qrSignedUrl,
                    proofSignedUrls = updatedProofUrls
                )
            }
        }
    }

    fun submitPaymentProof(
        context: Context,
        bill: MonthlyBill,
        amountInput: String,
        screenshotUri: Uri
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val amount = amountInput.trim().toDoubleOrNull()
            if (amount == null || amount <= 0.0 || amount > bill.balanceDue + 0.009) {
                _uiState.update {
                    it.copy(
                        errorMessage = str(R.string.err_invalid_proof_amount)
                            .ifBlank { "Enter a valid amount between ₹1 and remaining balance." }
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            val bytes = ImageUploadHelper.readCompressedJpeg(context, screenshotUri).getOrElse {
                _uiState.update { s ->
                    s.copy(
                        isActionBusy = false,
                        errorMessage = str(R.string.err_pick_screenshot_failed)
                            .ifBlank { "Could not read selected image. Please try another screenshot." }
                    )
                }
                return@launch
            }
            val path = repository.uploadCustomerPaymentScreenshot(
                messId = state.mess.id,
                customerId = state.customer.id,
                imageBytes = bytes
            ).getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isActionBusy = false,
                        errorMessage = err.message ?: str(R.string.err_operation_failed).ifBlank { "Operation failed." }
                    )
                }
                return@launch
            }
            val draftCheck = TiffinBusinessLogic.buildPaymentProofDraft(
                customer = state.customer,
                activeMessId = state.mess.id,
                bill = bill,
                amount = amount,
                screenshotPath = path
            )
            if (draftCheck.isFailure) {
                _uiState.update {
                    it.copy(
                        isActionBusy = false,
                        errorMessage = draftCheck.exceptionOrNull()?.message
                    )
                }
                return@launch
            }
            repository.submitPaymentProof(
                billId = bill.id,
                amount = amount,
                screenshotPath = path
            ).fold(
                onSuccess = { createdProof ->
                    val signedUrl = repository.getSignedStorageUrl(createdProof.screenshotPath).getOrNull()
                    _uiState.update { s ->
                        val urls = if (signedUrl != null) {
                            s.proofSignedUrls + (createdProof.screenshotPath to signedUrl)
                        } else {
                            s.proofSignedUrls
                        }
                        s.copy(
                            isActionBusy = false,
                            paymentProofs = listOf(createdProof) + s.paymentProofs.filterNot { it.id == createdProof.id },
                            proofSignedUrls = urls,
                            successMessage = str(R.string.msg_payment_proof_sent)
                                .ifBlank { "Payment proof sent! Your bill will update once the seller confirms." }
                        )
                    }
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

    fun markTodayTiffin(choice: DailyMarkChoice) {
        viewModelScope.launch {
            val state = _uiState.value
            val todayIso = TiffinBusinessLogic.messToday(state.mess.timezone).toString()
            if (TiffinBusinessLogic.isPastDailyCutoff(state.mess.dailyMarkCutoffTime, state.mess.timezone)) {
                val cutoff = TiffinBusinessLogic.formatCutoffDisplay(state.mess.dailyMarkCutoffTime)
                _uiState.update {
                    it.copy(
                        errorMessage = str(R.string.err_cutoff_passed, cutoff)
                            .ifBlank { "Daily cutoff ($cutoff) has passed." }
                    )
                }
                return@launch
            }
            if (choice == DailyMarkChoice.WANTED &&
                TiffinBusinessLogic.findActiveLeaveForDate(todayIso, state.leaves, state.customer.id) != null
            ) {
                _uiState.update {
                    it.copy(
                        errorMessage = str(R.string.err_leave_active_today)
                            .ifBlank { "You have 'No Tiffin' leave active today." }
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            repository.customerMarkTodayTiffin(choice).fold(
                onSuccess = { saved ->
                    val updatedMarks = state.monthMarks.filterNot { it.serviceDate == saved.serviceDate } + saved
                    val updatedSummary = TiffinBusinessLogic.calculateMonthlyTiffinSummary(
                        records = state.monthTiffins,
                        activeSubscription = state.activeSubscription,
                        dailyMarks = updatedMarks,
                        leaves = state.leaves
                    )
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            todayMark = saved,
                            monthMarks = updatedMarks,
                            monthSummary = updatedSummary,
                            successMessage = if (choice == DailyMarkChoice.WANTED) {
                                str(R.string.msg_marked_wanted).ifBlank { "Marked: Want Tiffin for today!" }
                            } else {
                                str(R.string.msg_marked_not_wanted).ifBlank { "Marked: No Tiffin for today." }
                            }
                        )
                    }
                    refreshAll(silent = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_save_choice_failed)
                                .ifBlank { "Failed to save today's tiffin choice." }
                        )
                    }
                }
            )
        }
    }

    fun createLeave(startDateIso: String, endDateIso: String, reason: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val today = TiffinBusinessLogic.messToday(state.mess.timezone)
            val parsedDates = runCatching {
                LocalDate.parse(startDateIso.trim()) to LocalDate.parse(endDateIso.trim())
            }
            if (parsedDates.isFailure) {
                _uiState.update {
                    it.copy(errorMessage = str(R.string.err_valid_dates).ifBlank { "Enter valid dates in YYYY-MM-DD format." })
                }
                return@launch
            }
            val (startDate, endDate) = parsedDates.getOrThrow()
            if (endDate.isBefore(startDate)) {
                _uiState.update {
                    it.copy(errorMessage = str(R.string.err_end_before_start).ifBlank { "End date must be on or after start date." })
                }
                return@launch
            }
            if (endDate.isBefore(today)) {
                _uiState.update {
                    it.copy(errorMessage = str(R.string.err_dates_in_past).ifBlank { "Leave dates cannot be in the past." })
                }
                return@launch
            }
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            repository.createTiffinLeave(
                messId = state.mess.id,
                customerId = state.customer.id,
                startDateIso = startDate.toString(),
                endDateIso = endDate.toString(),
                reason = reason
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            successMessage = str(R.string.msg_leave_set, startDate.toString(), endDate.toString())
                                .ifBlank { "No-Tiffin leave set ($startDate to $endDate)." }
                        )
                    }
                    refreshAll(silent = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_set_leave_failed).ifBlank { "Failed to set leave." }
                        )
                    }
                }
            )
        }
    }

    fun cancelLeave(leave: TiffinLeave) {
        viewModelScope.launch {
            val state = _uiState.value
            val todayIso = TiffinBusinessLogic.messToday(state.mess.timezone).toString()
            if (!TiffinBusinessLogic.canCustomerCancelLeave(leave, todayIso)) {
                _uiState.update {
                    it.copy(
                        errorMessage = str(R.string.err_cancel_leave_before_start)
                            .ifBlank { "Leave can only be cancelled before its start date." }
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            repository.cancelTiffinLeave(leave.id).fold(
                onSuccess = {
                    val updatedLeaves = state.leaves.map {
                        if (it.id == leave.id) it.copy(status = LeaveStatus.CANCELLED) else it
                    }
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            leaves = updatedLeaves,
                            successMessage = str(R.string.msg_leave_cancelled).ifBlank { "Leave cancelled." }
                        )
                    }
                    refreshAll(silent = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_cancel_leave_failed).ifBlank { "Failed to cancel leave." }
                        )
                    }
                }
            )
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
                            successMessage = str(R.string.msg_renewal_sent, state.mess.name)
                                .ifBlank { "Renewal request sent to ${state.mess.name} admin." }
                        )
                    }
                    refreshAll(silent = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_renewal_failed)
                                .ifBlank { "Failed to submit renewal request." }
                        )
                    }
                }
            )
        }
    }

    fun updateProfile(
        fullName: String,
        phone: String,
        addressOrRoom: String,
        area: String,
        city: String
    ) {
        viewModelScope.launch {
            if (fullName.isBlank() || phone.isBlank() || area.isBlank() || city.isBlank()) {
                _uiState.update {
                    it.copy(
                        errorMessage = str(R.string.err_profile_fields_empty)
                            .ifBlank { "Name, mobile number, area, and city cannot be empty." }
                    )
                }
                return@launch
            }
            val state = _uiState.value
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            repository.updateOwnCustomerProfile(
                userId = state.profile.id,
                customerId = state.customer.id,
                fullName = fullName,
                phone = phone,
                addressOrRoom = addressOrRoom,
                area = area,
                city = city
            ).fold(
                onSuccess = {
                    val trimmedArea = area.trim()
                    val trimmedCity = city.trim()
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            profile = it.profile.copy(
                                fullName = fullName.trim(),
                                phone = phone.trim(),
                                area = trimmedArea,
                                city = trimmedCity
                            ),
                            customer = it.customer.copy(
                                fullName = fullName.trim(),
                                phone = phone.trim(),
                                addressOrRoom = addressOrRoom.trim()
                            ),
                            discoveryAreaFilter = trimmedArea,
                            discoveryCityFilter = trimmedCity,
                            successMessage = str(R.string.msg_profile_updated).ifBlank { "Profile updated." }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_profile_update_failed)
                                .ifBlank { "Unable to update profile." }
                        )
                    }
                }
            )
        }
    }

    fun dismissLocationBanner() {
        _uiState.update { it.copy(locationBannerDismissed = true) }
    }

    fun loadDiscoveryMesses() {
        viewModelScope.launch {
            _uiState.update { it.copy(discoveryLoading = true, errorMessage = null) }
            val state = _uiState.value
            val todayIso = TiffinBusinessLogic.messToday(state.mess.timezone).toString()
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
                    val refreshedCurrentMess = messes.firstOrNull { it.id == state.mess.id } ?: state.mess
                    _uiState.update {
                        it.copy(
                            discoveryLoading = false,
                            discoveryMesses = sorted,
                            mess = refreshedCurrentMess
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
            _uiState.update { it.copy(isLocating = true, errorMessage = null, discoveryInfoMessage = null) }
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
                            discoveryInfoMessage = str(R.string.info_location_detected, detected.area, detected.city)
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
                errorMessage = null,
                successMessage = null
            )
        }
        fetchReviewsForMess(item.mess.id)
    }

    fun openOwnMessForReview() {
        val state = _uiState.value
        val item = state.discoveryMesses.firstOrNull { it.mess.id == state.mess.id }
            ?: DiscoverableMess(mess = state.mess, todayMenu = state.todayMenu)
        openSellerDetail(item)
    }

    private fun fetchReviewsForMess(messId: String) {
        viewModelScope.launch {
            repository.getMessReviews(messId).fold(
                onSuccess = { reviews ->
                    val (avg, count) = TiffinBusinessLogic.computeRatingSummary(reviews.map { it.stars })
                    _uiState.update { state ->
                        val updatedSelected = state.selectedDiscoveryMess?.let { sel ->
                            if (sel.mess.id == messId) {
                                sel.copy(mess = sel.mess.copy(avgRating = avg, ratingCount = count))
                            } else sel
                        }
                        state.copy(
                            selectedMessReviews = reviews,
                            reviewsLoading = false,
                            selectedDiscoveryMess = updatedSelected
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(reviewsLoading = false) }
                }
            )
        }
    }

    fun submitMessReview(messId: String, stars: Int, comment: String) {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null, successMessage = null) }
            repository.upsertMessRating(
                messId = messId,
                customerUserId = state.profile.id,
                stars = stars,
                comment = comment
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            successMessage = str(R.string.msg_rating_saved).ifBlank { "Your rating has been saved!" }
                        )
                    }
                    fetchReviewsForMess(messId)
                    loadDiscoveryMesses()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            errorMessage = err.message ?: str(R.string.err_review_failed).ifBlank { "Unable to submit review." }
                        )
                    }
                }
            )
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, discoveryInfoMessage = null) }
    }

    class Factory(
        private val repository: TiffinRepository,
        private val profile: Profile,
        private val mess: Mess,
        private val customer: CustomerProfile,
        private val strings: AppStrings? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CustomerViewModel(repository, profile, mess, customer, strings) as T
        }
    }
}
