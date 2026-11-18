package com.example.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    @SerialName("customer") CUSTOMER,
    @SerialName("admin") ADMIN,
    @SerialName("owner") OWNER
}

@Serializable
enum class AccountStatus {
    @SerialName("pending") PENDING,
    @SerialName("active") ACTIVE,
    @SerialName("suspended") SUSPENDED,
    @SerialName("rejected") REJECTED
}

@Serializable
enum class TiffinStatus {
    @SerialName("delivered") DELIVERED,
    @SerialName("not_delivered") NOT_DELIVERED,
    @SerialName("cancelled") CANCELLED
}

@Serializable
enum class MealSlot(val dbValue: String, val label: String) {
    @SerialName("breakfast") BREAKFAST("breakfast", "Breakfast"),
    @SerialName("lunch") LUNCH("lunch", "Lunch"),
    @SerialName("dinner") DINNER("dinner", "Dinner"),
    @SerialName("daily") DAILY("daily", "Daily Tiffin")
}

@Serializable
enum class SubscriptionStatus {
    @SerialName("active") ACTIVE,
    @SerialName("expiring_soon") EXPIRING_SOON,
    @SerialName("expired") EXPIRED,
    @SerialName("paused") PAUSED,
    @SerialName("cancelled") CANCELLED
}

@Serializable
enum class BillStatus {
    @SerialName("paid") PAID,
    @SerialName("partially_paid") PARTIALLY_PAID,
    @SerialName("pending") PENDING,
    @SerialName("overdue") OVERDUE
}

@Serializable
enum class PaymentMethod(val dbValue: String, val label: String) {
    @SerialName("cash") CASH("cash", "Cash"),
    @SerialName("upi") UPI("upi", "UPI"),
    @SerialName("bank_transfer") BANK_TRANSFER("bank_transfer", "Bank Transfer"),
    @SerialName("other") OTHER("other", "Other")
}

@Serializable
enum class PaymentStatus {
    @SerialName("confirmed") CONFIRMED,
    @SerialName("reversed") REVERSED
}

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val email: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val phone: String = "",
    val role: UserRole = UserRole.CUSTOMER,
    @SerialName("account_status") val accountStatus: AccountStatus = AccountStatus.PENDING,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class Mess(
    val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String,
    val phone: String = "",
    val address: String = "",
    val timezone: String = "Asia/Kolkata",
    val currency: String = "INR",
    @SerialName("meal_slots") val mealSlots: List<String> = listOf("lunch", "dinner"),
    @SerialName("default_due_day") val defaultDueDay: Int = 5
)

@Serializable
data class MessMember(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("member_role") val memberRole: UserRole,
    @SerialName("membership_status") val membershipStatus: AccountStatus,
    @SerialName("joined_at") val joinedAt: String? = null
)

@Serializable
data class CustomerProfile(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("full_name") val fullName: String,
    val phone: String = "",
    val email: String? = null,
    @SerialName("address_or_room") val addressOrRoom: String = "",
    @SerialName("customer_status") val customerStatus: AccountStatus = AccountStatus.ACTIVE,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RegistrationRequest(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("requested_name") val requestedName: String,
    val phone: String,
    @SerialName("address_or_room") val addressOrRoom: String = "",
    val status: String = "pending",
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SubscriptionPlan(
    val id: String,
    @SerialName("mess_id") val messId: String,
    val name: String,
    @SerialName("duration_days") val durationDays: Int,
    val price: Double,
    @SerialName("included_tiffins") val includedTiffins: Int,
    @SerialName("meal_slots") val mealSlots: List<String> = listOf("lunch", "dinner"),
    @SerialName("extra_tiffin_price") val extraTiffinPrice: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class Subscription(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("plan_id") val planId: String? = null,
    @SerialName("plan_name_snapshot") val planNameSnapshot: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("included_tiffins") val includedTiffins: Int,
    @SerialName("extra_tiffin_price_snapshot") val extraTiffinPriceSnapshot: Double = 0.0,
    @SerialName("meal_slots_snapshot") val mealSlotsSnapshot: List<String> = listOf("lunch", "dinner"),
    @SerialName("agreed_price") val agreedPrice: Double,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TiffinRecord(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("subscription_id") val subscriptionId: String? = null,
    @SerialName("service_date") val serviceDate: String,
    @SerialName("meal_slot") val mealSlot: String,
    val status: TiffinStatus,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("marked_by") val markedBy: String? = null,
    val note: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class DailyMenu(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("menu_date") val menuDate: String,
    val breakfast: String? = null,
    val lunch: String? = null,
    val dinner: String? = null,
    @SerialName("special_items") val specialItems: String? = null,
    val notes: String? = null,
    val status: String = "draft",
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class MonthlyBill(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("subscription_id") val subscriptionId: String? = null,
    @SerialName("billing_month") val billingMonth: String,
    @SerialName("subscription_charge") val subscriptionCharge: Double = 0.0,
    @SerialName("extra_tiffin_count") val extraTiffinCount: Int = 0,
    @SerialName("extra_tiffin_charge") val extraTiffinCharge: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("previous_balance") val previousBalance: Double = 0.0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("amount_paid") val amountPaid: Double = 0.0,
    @SerialName("balance_due") val balanceDue: Double = 0.0,
    @SerialName("due_date") val dueDate: String,
    val status: BillStatus = BillStatus.PENDING,
    @SerialName("generated_at") val generatedAt: String? = null
)

@Serializable
data class Payment(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("bill_id") val billId: String,
    val amount: Double,
    @SerialName("payment_method") val paymentMethod: PaymentMethod,
    @SerialName("payment_date") val paymentDate: String,
    @SerialName("reference_note") val referenceNote: String? = null,
    @SerialName("recorded_by") val recordedBy: String,
    val status: PaymentStatus = PaymentStatus.CONFIRMED
)

@Serializable
data class RenewalRequest(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("requested_plan_id") val requestedPlanId: String? = null,
    val note: String? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AdminInvitation(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("invited_email") val invitedEmail: String,
    @SerialName("invited_role") val invitedRole: String = "admin",
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AuthContextPayload(
    val profile: Profile? = null,
    val membership: MessMember? = null,
    val mess: Mess? = null,
    @SerialName("customer_profile") val customerProfile: CustomerProfile? = null,
    @SerialName("registration_request") val registrationRequest: RegistrationRequest? = null
)
