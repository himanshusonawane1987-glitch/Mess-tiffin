package com.example.domain

import androidx.annotation.StringRes
import com.example.R
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class TiffinMonthSummary(
    val deliveredCount: Int,
    val notDeliveredCount: Int,
    val cancelledCount: Int,
    val scheduledOrTotalRecords: Int,
    val remainingTiffins: Int?,
    val extraTiffins: Int
)

data class BillComputation(
    val totalPayable: Double,
    val amountPaid: Double,
    val balanceDue: Double,
    val status: BillStatus
)

enum class OnboardingStep {
    ROLE_CHOICE,
    CUSTOMER_REGISTRATION,
    SELLER_REGISTRATION,
    SELLER_SUCCESS,
    DISCOVERY,
    SELLER_DETAIL
}

enum class OnboardingRoleChoice {
    CUSTOMER,
    SELLER,
    BROWSE_NEARBY
}

sealed interface AuthRouteState {
    data object Loading : AuthRouteState
    data object SupabaseConfigMissing : AuthRouteState
    data object SignedOut : AuthRouteState
    data class NeedsCustomerRegistration(val profile: Profile) : AuthRouteState
    data class AwaitingApproval(val profile: Profile, val messName: String?, val request: RegistrationRequest?) : AuthRouteState
    data class Rejected(val profile: Profile, val messName: String?) : AuthRouteState
    data class Suspended(val profile: Profile) : AuthRouteState
    data class CustomerActive(
        val profile: Profile,
        val mess: Mess,
        val customer: CustomerProfile
    ) : AuthRouteState
    data class AdminActive(
        val profile: Profile,
        val mess: Mess,
        val isOwner: Boolean
    ) : AuthRouteState
}

object TiffinBusinessLogic {

    fun safeZoneId(timezone: String?): ZoneId {
        return try {
            ZoneId.of(timezone?.takeIf { it.isNotBlank() } ?: "Asia/Kolkata")
        } catch (_: Exception) {
            ZoneId.of("Asia/Kolkata")
        }
    }

    fun messToday(timezone: String?): LocalDate {
        return LocalDate.now(safeZoneId(timezone))
    }

    fun messYearMonth(timezone: String?, offsetMonths: Long = 0): YearMonth {
        return YearMonth.from(messToday(timezone)).plusMonths(offsetMonths)
    }

    fun monthStartIso(yearMonth: YearMonth): String = yearMonth.atDay(1).toString()

    fun monthEndIso(yearMonth: YearMonth): String = yearMonth.atEndOfMonth().toString()

    fun greetingForMessTime(timezone: String?): String {
        val hour = ZonedDateTime.now(safeZoneId(timezone)).hour
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun formatDateDisplay(isoDate: String, locale: Locale = Locale.ENGLISH): String {
        return try {
            LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", locale))
        } catch (_: Exception) {
            isoDate
        }
    }

    fun formatMonthDisplay(isoMonthDate: String, locale: Locale = Locale.ENGLISH): String {
        return try {
            val date = LocalDate.parse(isoMonthDate)
            date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        } catch (_: Exception) {
            isoMonthDate
        }
    }

    fun formatTimeInMessZone(isoTimestamp: String?, timezone: String?, locale: Locale = Locale.ENGLISH): String? {
        if (isoTimestamp.isNullOrBlank()) return null
        return try {
            val zdt = Instant.parse(isoTimestamp).atZone(safeZoneId(timezone))
            zdt.format(DateTimeFormatter.ofPattern("hh:mm a", locale))
        } catch (_: Exception) {
            null
        }
    }

    fun formatCurrency(amount: Double, currencyCode: String = "INR"): String {
        return try {
            val locale = if (currencyCode.equals("INR", ignoreCase = true)) {
                Locale("en", "IN")
            } else {
                Locale.US
            }
            val fmt = NumberFormat.getCurrencyInstance(locale)
            fmt.currency = Currency.getInstance(currencyCode.uppercase(Locale.ENGLISH))
            fmt.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            fmt.format(amount)
        } catch (_: Exception) {
            "₹${amount.roundToInt()}"
        }
    }

    /**
     * Centralized tiffin count & remaining allowance logic.
     * Only DELIVERED records count toward delivered/used tiffins.
     * Cancelled, Not Delivered, "Nahi Chahiye" (not_wanted), and active Leave days never count.
     * Remaining tiffins is floored at 0 (never negative).
     */
    fun calculateMonthlyTiffinSummary(
        records: List<TiffinRecord>,
        activeSubscription: Subscription?,
        dailyMarks: List<CustomerDailyMark> = emptyList(),
        leaves: List<TiffinLeave> = emptyList()
    ): TiffinMonthSummary {
        val notWantedDates = dailyMarks
            .filter { it.choice == DailyMarkChoice.NOT_WANTED }
            .map { it.serviceDate }
            .toSet()
        val activeLeaves = leaves.filter { it.status == LeaveStatus.ACTIVE }

        val countableRecords = records.filter { rec ->
            rec.serviceDate !in notWantedDates &&
                activeLeaves.none { leave ->
                    rec.serviceDate >= leave.startDate && rec.serviceDate <= leave.endDate
                }
        }

        val delivered = countableRecords.count { it.status == TiffinStatus.DELIVERED }
        val notDelivered = records.count { it.status == TiffinStatus.NOT_DELIVERED }
        val cancelled = records.count { it.status == TiffinStatus.CANCELLED }

        val allowance = activeSubscription?.includedTiffins
        val remaining = if (allowance != null && allowance > 0) {
            max(0, allowance - delivered)
        } else {
            null
        }
        val extra = if (allowance != null && allowance > 0) {
            max(0, delivered - allowance)
        } else {
            0
        }

        return TiffinMonthSummary(
            deliveredCount = delivered,
            notDeliveredCount = notDelivered,
            cancelledCount = cancelled,
            scheduledOrTotalRecords = records.size,
            remainingTiffins = remaining,
            extraTiffins = extra
        )
    }

    /**
     * Centralized monthly billing computation.
     * Reversed payments are excluded from confirmed payments.
     */
    fun calculateMonthlyBillTotals(
        subscriptionCharge: Double,
        extraTiffinCharge: Double,
        previousBalance: Double,
        discountAmount: Double,
        payments: List<Payment>,
        dueDateIso: String,
        todayInMessZone: LocalDate
    ): BillComputation {
        val totalPayable = max(0.0, subscriptionCharge + extraTiffinCharge + previousBalance - discountAmount)
        val confirmedPaid = payments
            .filter { it.status == PaymentStatus.CONFIRMED }
            .sumOf { it.amount }
        val balanceDue = max(0.0, totalPayable - confirmedPaid)
        val dueDate = try {
            LocalDate.parse(dueDateIso)
        } catch (_: Exception) {
            todayInMessZone
        }
        val status = when {
            balanceDue <= 0.009 -> BillStatus.PAID
            todayInMessZone.isAfter(dueDate) -> BillStatus.OVERDUE
            confirmedPaid > 0.0 -> BillStatus.PARTIALLY_PAID
            else -> BillStatus.PENDING
        }
        return BillComputation(
            totalPayable = totalPayable,
            amountPaid = confirmedPaid,
            balanceDue = balanceDue,
            status = status
        )
    }

    /**
     * Server-verified role and membership state resolution.
     * Never trusts client-supplied roles.
     */
    fun resolveAuthRouteState(context: AuthContextPayload?): AuthRouteState {
        val profile = context?.profile ?: return AuthRouteState.SignedOut
        val membership = context.membership
        val mess = context.mess

        if (profile.accountStatus == AccountStatus.SUSPENDED ||
            membership?.membershipStatus == AccountStatus.SUSPENDED
        ) {
            return AuthRouteState.Suspended(profile)
        }

        if (profile.accountStatus == AccountStatus.REJECTED ||
            membership?.membershipStatus == AccountStatus.REJECTED ||
            context.registrationRequest?.status == "rejected"
        ) {
            return AuthRouteState.Rejected(profile, mess?.name)
        }

        val isAdminRole = (profile.role == UserRole.ADMIN || profile.role == UserRole.OWNER) &&
            (membership?.memberRole == UserRole.ADMIN || membership?.memberRole == UserRole.OWNER)

        if (isAdminRole &&
            profile.accountStatus == AccountStatus.ACTIVE &&
            membership?.membershipStatus == AccountStatus.ACTIVE &&
            mess != null
        ) {
            return AuthRouteState.AdminActive(
                profile = profile,
                mess = mess,
                isOwner = membership.memberRole == UserRole.OWNER
            )
        }

        if (profile.accountStatus == AccountStatus.ACTIVE &&
            membership?.membershipStatus == AccountStatus.ACTIVE &&
            mess != null &&
            context.customerProfile != null
        ) {
            return AuthRouteState.CustomerActive(
                profile = profile,
                mess = mess,
                customer = context.customerProfile
            )
        }

        if (context.registrationRequest?.status == "pending" ||
            membership?.membershipStatus == AccountStatus.PENDING
        ) {
            return AuthRouteState.AwaitingApproval(
                profile = profile,
                messName = mess?.name,
                request = context.registrationRequest
            )
        }

        return AuthRouteState.NeedsCustomerRegistration(profile)
    }

    fun resolveOnboardingStepForChoice(choice: OnboardingRoleChoice): OnboardingStep {
        return when (choice) {
            OnboardingRoleChoice.CUSTOMER -> OnboardingStep.CUSTOMER_REGISTRATION
            OnboardingRoleChoice.SELLER -> OnboardingStep.SELLER_REGISTRATION
            OnboardingRoleChoice.BROWSE_NEARBY -> OnboardingStep.DISCOVERY
        }
    }

    fun resolveOnboardingBackStep(currentStep: OnboardingStep): OnboardingStep {
        return when (currentStep) {
            OnboardingStep.SELLER_DETAIL -> OnboardingStep.DISCOVERY
            OnboardingStep.DISCOVERY,
            OnboardingStep.CUSTOMER_REGISTRATION,
            OnboardingStep.SELLER_REGISTRATION -> OnboardingStep.ROLE_CHOICE
            OnboardingStep.SELLER_SUCCESS,
            OnboardingStep.ROLE_CHOICE -> OnboardingStep.ROLE_CHOICE
        }
    }

    fun validateSellerRegistration(
        messName: String,
        area: String,
        city: String,
        phone: String,
        vegType: VegType?,
        monthlyPriceInput: String
    ): String? {
        if (messName.trim().isBlank()) return "Mess name is required."
        if (area.trim().isBlank()) return "Area / locality is required."
        if (city.trim().isBlank()) return "City is required."
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) return "Enter a valid mobile number (at least 10 digits)."
        if (vegType == null) return "Select Veg, Non-Veg, or Both."
        val price = monthlyPriceInput.trim().toDoubleOrNull()
            ?: return "Enter a valid monthly price in ₹."
        if (price <= 0.0) return "Monthly price must be greater than ₹0."
        return null
    }

    @StringRes
    fun validateSellerRegistrationRes(
        messName: String,
        area: String,
        city: String,
        phone: String,
        vegType: VegType?,
        monthlyPriceInput: String
    ): Int? {
        if (messName.trim().isBlank()) return R.string.err_seller_name_req
        if (area.trim().isBlank()) return R.string.err_area_req
        if (city.trim().isBlank()) return R.string.err_city_req
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) return R.string.err_mobile_10_digits
        if (vegType == null) return R.string.err_veg_type_req
        val price = monthlyPriceInput.trim().toDoubleOrNull()
            ?: return R.string.err_monthly_price_valid
        if (price <= 0.0) return R.string.err_monthly_price_gt_zero
        return null
    }

    fun validateCustomerRegistrationInput(
        hasSelectedMess: Boolean,
        registrationCode: String,
        fullName: String,
        phone: String,
        area: String,
        city: String
    ): String? {
        if (!hasSelectedMess && registrationCode.trim().isBlank()) {
            return "Enter your mess registration code or browse nearby messes."
        }
        if (fullName.trim().isBlank()) return "Full name is required."
        if (phone.filter { it.isDigit() }.length < 10) return "Enter a valid mobile number."
        if (area.trim().isBlank()) return "Area / locality is required."
        if (city.trim().isBlank()) return "City is required."
        return null
    }

    @StringRes
    fun validateCustomerRegistrationInputRes(
        hasSelectedMess: Boolean,
        registrationCode: String,
        fullName: String,
        phone: String,
        area: String,
        city: String
    ): Int? {
        if (!hasSelectedMess && registrationCode.trim().isBlank()) {
            return R.string.err_enter_code_or_browse
        }
        if (fullName.trim().isBlank()) return R.string.err_full_name_req
        if (phone.filter { it.isDigit() }.length < 10) return R.string.err_mobile_valid
        if (area.trim().isBlank()) return R.string.err_area_req
        if (city.trim().isBlank()) return R.string.err_city_req
        return null
    }

    fun sortMessesByProximity(
        messes: List<Mess>,
        todayMenusByMessId: Map<String, DailyMenu>,
        customerArea: String,
        customerCity: String
    ): List<DiscoverableMess> {
        val normArea = customerArea.trim().lowercase(Locale.ENGLISH)
        val normCity = customerCity.trim().lowercase(Locale.ENGLISH)

        return messes
            .filter { it.isActive }
            .map { mess ->
                val mArea = mess.area.trim().lowercase(Locale.ENGLISH)
                val mCity = mess.city.trim().lowercase(Locale.ENGLISH)
                val areaMatches = normArea.isNotBlank() && (
                    mArea == normArea ||
                        mArea.contains(normArea) ||
                        normArea.contains(mArea) && mArea.isNotBlank() ||
                        mess.deliveryAreas.lowercase(Locale.ENGLISH).contains(normArea)
                    )
                val cityMatches = normCity.isNotBlank() && mCity == normCity

                val tier = when {
                    areaMatches && (cityMatches || normCity.isBlank()) -> ProximityTier.SAME_AREA_AND_CITY
                    cityMatches -> ProximityTier.SAME_CITY
                    else -> ProximityTier.OTHER
                }
                DiscoverableMess(
                    mess = mess,
                    todayMenu = todayMenusByMessId[mess.id],
                    proximityTier = tier
                )
            }
            .sortedWith(
                compareBy<DiscoverableMess> { it.proximityTier.rank }
                    .thenByDescending { it.mess.avgRating }
                    .thenByDescending { it.mess.ratingCount }
                    .thenBy { it.mess.name.lowercase(Locale.ENGLISH) }
            )
    }

    fun computeRatingSummary(starsList: List<Int>): Pair<Double, Int> {
        val valid = starsList.filter { it in 1..5 }
        if (valid.isEmpty()) return 0.0 to 0
        val avg = ((valid.sum().toDouble() / valid.size) * 100.0).roundToInt() / 100.0
        return avg to valid.size
    }

    fun buildDiscoveryJoinPrefill(mess: Mess): DiscoveryJoinPrefill {
        return DiscoveryJoinPrefill(
            messId = mess.id,
            messName = mess.name,
            area = mess.area,
            city = mess.city
        )
    }

    fun menuPreviewItems(menu: DailyMenu?, maxItems: Int = 3): List<String> {
        if (menu == null) return emptyList()
        val items = mutableListOf<String>()
        if (!menu.lunch.isNullOrBlank()) items.add("Lunch: ${menu.lunch.trim()}")
        if (!menu.dinner.isNullOrBlank()) items.add("Dinner: ${menu.dinner.trim()}")
        if (!menu.breakfast.isNullOrBlank()) items.add("Breakfast: ${menu.breakfast.trim()}")
        if (!menu.specialItems.isNullOrBlank()) items.add("Special: ${menu.specialItems.trim()}")
        return items.take(maxItems)
    }

    // ==================== DAILY MARK, LEAVE & TOTALS LOGIC ====================

    private val cutoffRegex = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

    fun isValidCutoffTime(cutoffHHmm: String): Boolean {
        return cutoffRegex.matches(cutoffHHmm.trim())
    }

    fun parseCutoffTime(cutoffHHmm: String?): LocalTime {
        val cleaned = cutoffHHmm?.trim().orEmpty()
        return if (cutoffRegex.matches(cleaned)) {
            LocalTime.parse(cleaned)
        } else {
            LocalTime.of(10, 0)
        }
    }

    fun formatCutoffDisplay(cutoffHHmm: String?): String {
        val time = parseCutoffTime(cutoffHHmm)
        return time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    }

    fun isPastDailyCutoff(
        cutoffHHmm: String?,
        timezone: String?,
        currentLocalTime: LocalTime? = null
    ): Boolean {
        val nowTime = currentLocalTime ?: ZonedDateTime.now(safeZoneId(timezone)).toLocalTime()
        val cutoff = parseCutoffTime(cutoffHHmm)
        return nowTime.isAfter(cutoff)
    }

    fun findActiveLeaveForDate(
        dateIso: String,
        leaves: List<TiffinLeave>,
        customerId: String? = null
    ): TiffinLeave? {
        return leaves.firstOrNull { leave ->
            leave.status == LeaveStatus.ACTIVE &&
                (customerId == null || leave.customerId == customerId) &&
                dateIso >= leave.startDate &&
                dateIso <= leave.endDate
        }
    }

    fun canCustomerCancelLeave(leave: TiffinLeave, todayIso: String): Boolean {
        return leave.status == LeaveStatus.ACTIVE && leave.startDate > todayIso
    }

    fun computePerTiffinRate(subscription: Subscription?, mess: Mess): Double {
        if (subscription != null) {
            if (subscription.includedTiffins > 0 && subscription.agreedPrice > 0.0) {
                return subscription.agreedPrice / subscription.includedTiffins.toDouble()
            }
            if (subscription.extraTiffinPriceSnapshot > 0.0) {
                return subscription.extraTiffinPriceSnapshot
            }
        }
        val monthly = mess.monthlyPrice
        if (monthly != null && monthly > 0.0) {
            return monthly / 30.0
        }
        return 0.0
    }

    fun buildAdminDailyCustomerSummaries(
        customers: List<CustomerProfile>,
        dateIso: String,
        dailyMarks: List<CustomerDailyMark>,
        leaves: List<TiffinLeave>,
        monthTiffins: List<TiffinRecord>,
        subscriptions: List<Subscription>,
        mess: Mess
    ): Map<String, AdminDailyCustomerSummary> {
        val marksByCust = dailyMarks
            .filter { it.serviceDate == dateIso }
            .associateBy { it.customerId }
        val monthTiffinsByCust = monthTiffins.groupBy { it.customerId }
        val subsByCust = subscriptions.groupBy { it.customerId }
        val marksAllByCust = dailyMarks.groupBy { it.customerId }
        val leavesByCust = leaves.groupBy { it.customerId }

        return customers.associate { cust ->
            val custLeaves = leavesByCust[cust.id].orEmpty()
            val activeLeave = findActiveLeaveForDate(dateIso, custLeaves, cust.id)
            val mark = marksByCust[cust.id]

            val markStatus = when {
                activeLeave != null -> AdminCustomerMarkStatus.ON_LEAVE
                mark?.choice == DailyMarkChoice.WANTED -> AdminCustomerMarkStatus.WANTED
                mark?.choice == DailyMarkChoice.NOT_WANTED -> AdminCustomerMarkStatus.NOT_MARKED.let { AdminCustomerMarkStatus.NOT_WANTED }
                else -> AdminCustomerMarkStatus.NOT_MARKED
            }

            val custSubs = subsByCust[cust.id].orEmpty()
            val activeSub = custSubs.firstOrNull {
                it.status == SubscriptionStatus.ACTIVE || it.status == SubscriptionStatus.EXPIRING_SOON
            } ?: custSubs.firstOrNull()

            val monthSummary = calculateMonthlyTiffinSummary(
                records = monthTiffinsByCust[cust.id].orEmpty(),
                activeSubscription = activeSub,
                dailyMarks = marksAllByCust[cust.id].orEmpty(),
                leaves = custLeaves
            )
            val rate = computePerTiffinRate(activeSub, mess)
            val monthAmt = monthSummary.deliveredCount * rate

            cust.id to AdminDailyCustomerSummary(
                customerId = cust.id,
                markStatus = markStatus,
                activeLeave = activeLeave,
                monthDeliveredCount = monthSummary.deliveredCount,
                perTiffinRate = rate,
                monthAmount = monthAmt
            )
        }
    }

    fun computeAdminDayTotals(
        activeCustomers: List<CustomerProfile>,
        summaries: Map<String, AdminDailyCustomerSummary>,
        dailyTiffins: List<TiffinRecord>,
        dateIso: String
    ): AdminDayTotals {
        var wanted = 0
        var notWanted = 0
        var onLeave = 0
        var notMarked = 0
        var deliveredCount = 0
        var deliveredAmount = 0.0
        var wantedAmount = 0.0

        activeCustomers.forEach { cust ->
            val sum = summaries[cust.id]
            val rate = sum?.perTiffinRate ?: 0.0
            when (sum?.markStatus ?: AdminCustomerMarkStatus.NOT_MARKED) {
                AdminCustomerMarkStatus.WANTED -> {
                    wanted++
                    wantedAmount += rate
                }
                AdminCustomerMarkStatus.NOT_WANTED -> notWanted++
                AdminCustomerMarkStatus.ON_LEAVE -> onLeave++
                AdminCustomerMarkStatus.NOT_MARKED -> notMarked++
            }
            val custDeliveredToday = dailyTiffins.count {
                it.customerId == cust.id &&
                    it.serviceDate == dateIso &&
                    it.status == TiffinStatus.DELIVERED &&
                    sum?.markStatus != AdminCustomerMarkStatus.ON_LEAVE &&
                    sum?.markStatus != AdminCustomerMarkStatus.NOT_WANTED
            }
            deliveredCount += custDeliveredToday
            deliveredAmount += custDeliveredToday * rate
        }

        return AdminDayTotals(
            wantedCount = wanted,
            notWantedCount = notWanted,
            onLeaveCount = onLeave,
            notMarkedCount = notMarked,
            deliveredTodayCount = deliveredCount,
            deliveredTodayAmount = deliveredAmount,
            wantedTodayAmount = wantedAmount
        )
    }

    // ==================== SELLER UPI & PAYMENT PROOF LOGIC ====================

    private val upiIdRegex = Regex("^[a-zA-Z0-9._-]{2,}@[a-zA-Z0-9]{2,}$")

    fun isValidUpiId(upiId: String): Boolean {
        return upiIdRegex.matches(upiId.trim())
    }

    fun storagePathForSellerQr(messId: String, fileId: String): String {
        return "$messId/qr/${fileId.trim()}.jpg"
    }

    fun storagePathForCustomerProof(messId: String, customerId: String, fileId: String): String {
        return "$messId/proofs/$customerId/${fileId.trim()}.jpg"
    }

    fun latestProofForBill(billId: String, proofs: List<PaymentProof>): PaymentProof? {
        return proofs
            .filter { it.billId == billId }
            .maxByOrNull { it.createdAt.orEmpty() }
    }

    fun buildPaymentProofDraft(
        customer: CustomerProfile,
        activeMessId: String,
        bill: MonthlyBill,
        amount: Double,
        screenshotPath: String,
        proofId: String = "pending"
    ): Result<PaymentProof> {
        if (customer.customerStatus != AccountStatus.ACTIVE) {
            return Result.failure(IllegalStateException("Active customer membership required."))
        }
        if (customer.messId != activeMessId) {
            return Result.failure(IllegalStateException("Customer does not belong to this mess."))
        }
        if (bill.messId != customer.messId || bill.customerId != customer.id) {
            return Result.failure(IllegalStateException("Unauthorized: cannot submit payment proof for another mess or customer."))
        }
        if (bill.balanceDue <= 0.009) {
            return Result.failure(IllegalStateException("This bill is already paid."))
        }
        if (amount <= 0.0 || amount > bill.balanceDue + 0.009) {
            return Result.failure(IllegalStateException("Payment proof amount must be between ₹1 and remaining due."))
        }
        val cleanedPath = screenshotPath.trim()
        if (cleanedPath.isBlank() || !cleanedPath.startsWith("${customer.messId}/proofs/${customer.id}/")) {
            return Result.failure(IllegalStateException("Invalid screenshot storage path."))
        }
        return Result.success(
            PaymentProof(
                id = proofId,
                messId = bill.messId,
                customerId = customer.id,
                billId = bill.id,
                amount = amount,
                screenshotPath = cleanedPath,
                status = PaymentProofStatus.PENDING
            )
        )
    }

    fun applyPaymentProofReview(
        proof: PaymentProof,
        bill: MonthlyBill,
        existingBillPayments: List<Payment>,
        decision: PaymentProofStatus,
        rejectReason: String?,
        reviewerAdminId: String,
        reviewerMessId: String,
        todayInMessZone: LocalDate
    ): Result<Pair<PaymentProof, MonthlyBill>> {
        if (reviewerMessId != proof.messId || bill.messId != proof.messId) {
            return Result.failure(IllegalStateException("Unauthorized: proof belongs to a different mess."))
        }
        if (proof.status != PaymentProofStatus.PENDING) {
            return Result.failure(IllegalStateException("Payment proof has already been reviewed."))
        }
        if (decision == PaymentProofStatus.PENDING) {
            return Result.failure(IllegalArgumentException("Review decision must be confirmed or rejected."))
        }

        if (decision == PaymentProofStatus.REJECTED) {
            val updatedProof = proof.copy(
                status = PaymentProofStatus.REJECTED,
                rejectReason = rejectReason?.trim()?.ifBlank { null },
                reviewedBy = reviewerAdminId
            )
            return Result.success(updatedProof to bill)
        }

        // CONFIRMED: record UPI payment & recompute bill totals without altering unpaid bills before confirmation
        val confirmedPayment = Payment(
            id = "pay_${proof.id}",
            messId = bill.messId,
            customerId = bill.customerId,
            billId = bill.id,
            amount = proof.amount,
            paymentMethod = PaymentMethod.UPI,
            paymentDate = todayInMessZone.toString(),
            referenceNote = "UPI Proof #${proof.id.take(8)}",
            recordedBy = reviewerAdminId,
            status = PaymentStatus.CONFIRMED
        )
        val computation = calculateMonthlyBillTotals(
            subscriptionCharge = bill.subscriptionCharge,
            extraTiffinCharge = bill.extraTiffinCharge,
            previousBalance = bill.previousBalance,
            discountAmount = bill.discountAmount,
            payments = existingBillPayments + confirmedPayment,
            dueDateIso = bill.dueDate,
            todayInMessZone = todayInMessZone
        )
        val updatedBill = bill.copy(
            amountPaid = computation.amountPaid,
            balanceDue = computation.balanceDue,
            status = computation.status
        )
        val updatedProof = proof.copy(
            status = PaymentProofStatus.CONFIRMED,
            rejectReason = null,
            reviewedBy = reviewerAdminId
        )
        return Result.success(updatedProof to updatedBill)
    }
}
