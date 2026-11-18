package com.example.domain

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
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

    fun formatDateDisplay(isoDate: String): String {
        return try {
            LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH))
        } catch (_: Exception) {
            isoDate
        }
    }

    fun formatMonthDisplay(isoMonthDate: String): String {
        return try {
            val date = LocalDate.parse(isoMonthDate)
            date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        } catch (_: Exception) {
            isoMonthDate
        }
    }

    fun formatTimeInMessZone(isoTimestamp: String?, timezone: String?): String? {
        if (isoTimestamp.isNullOrBlank()) return null
        return try {
            val zdt = Instant.parse(isoTimestamp).atZone(safeZoneId(timezone))
            zdt.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
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
     * Cancelled and Not Delivered records never count as delivered.
     * Remaining tiffins is floored at 0 (never negative).
     */
    fun calculateMonthlyTiffinSummary(
        records: List<TiffinRecord>,
        activeSubscription: Subscription?
    ): TiffinMonthSummary {
        val delivered = records.count { it.status == TiffinStatus.DELIVERED }
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
}
