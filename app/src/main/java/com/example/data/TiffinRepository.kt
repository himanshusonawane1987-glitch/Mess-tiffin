package com.example.data

import com.example.domain.AdminInvitation
import com.example.domain.AppLanguage
import com.example.domain.AuthContextPayload
import com.example.domain.CustomerDailyMark
import com.example.domain.CustomerProfile
import com.example.domain.DailyMarkChoice
import com.example.domain.DailyMenu
import com.example.domain.Mess
import com.example.domain.MessReview
import com.example.domain.MessUpiDetails
import com.example.domain.MonthlyBill
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.PaymentProof
import com.example.domain.Profile
import com.example.domain.RegistrationRequest
import com.example.domain.RenewalRequest
import com.example.domain.SellerRegistrationResult
import com.example.domain.Subscription
import com.example.domain.SubscriptionPlan
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinLeave
import com.example.domain.TiffinRecord
import com.example.domain.TiffinStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class TiffinRepository(
    private val rest: SupabaseRestClient,
    private val sessionStore: SessionStore,
    private val realtime: SupabaseRealtimeClient,
    private val storage: SupabaseStorageClient
) {
    private val json = rest.json

    fun observeMessRealtime(messId: String, tables: List<String>): Flow<RealtimeChangeEvent> {
        return realtime.observeMessChanges(messId, tables)
    }

    suspend fun getCachedAuthContext(): AuthContextPayload? {
        val raw = sessionStore.getCachedAuthContext() ?: return null
        return runCatching { json.decodeFromString(AuthContextPayload.serializer(), raw) }.getOrNull()
    }

    suspend fun fetchAuthContext(): Result<AuthContextPayload> {
        val result = rest.rpc("get_my_auth_context", "{}", AuthContextPayload.serializer())
        result.onSuccess { payload ->
            runCatching {
                sessionStore.saveCachedAuthContext(json.encodeToString(AuthContextPayload.serializer(), payload))
            }
        }
        return result
    }

    fun observeLocalLanguage(): Flow<AppLanguage> = sessionStore.observeLanguage()

    fun observeHasChosenLanguage(): Flow<Boolean> = sessionStore.observeHasChosenLanguage()

    suspend fun getLocalLanguage(): AppLanguage = sessionStore.getLanguage()

    suspend fun hasChosenLanguage(): Boolean = sessionStore.hasChosenLanguage()

    suspend fun saveLocalLanguage(language: AppLanguage, markChosen: Boolean = true) {
        sessionStore.saveLanguage(language, markChosen)
    }

    suspend fun updateMyLanguage(language: AppLanguage): Result<Profile> {
        sessionStore.saveLanguage(language, markChosen = true)
        val params = buildJsonObject {
            put("p_language", language.dbValue)
        }.toString()
        val res = rest.rpc("set_my_language", params, Profile.serializer())
        res.onSuccess { updatedProfile ->
            runCatching {
                val cached = getCachedAuthContext()
                if (cached != null) {
                    val updatedCtx = cached.copy(profile = updatedProfile)
                    sessionStore.saveCachedAuthContext(json.encodeToString(AuthContextPayload.serializer(), updatedCtx))
                }
            }
        }
        return res
    }

    suspend fun registerMyMess(
        messName: String,
        area: String,
        city: String,
        phone: String,
        vegTypeDb: String,
        monthlyPrice: Double,
        lunchTime: String,
        dinnerTime: String,
        deliveryAreas: String,
        description: String
    ): Result<SellerRegistrationResult> {
        val params = buildJsonObject {
            put("p_mess_name", messName.trim())
            put("p_area", area.trim())
            put("p_city", city.trim())
            put("p_phone", phone.trim())
            put("p_veg_type", vegTypeDb)
            put("p_monthly_price", monthlyPrice)
            put("p_lunch_time", lunchTime.trim())
            put("p_dinner_time", dinnerTime.trim())
            put("p_delivery_areas", deliveryAreas.trim())
            put("p_description", description.trim())
        }.toString()
        return rest.rpc("register_my_mess", params, SellerRegistrationResult.serializer())
    }

    suspend fun submitCustomerRegistration(
        registrationCode: String,
        fullName: String,
        phone: String,
        addressOrRoom: String,
        area: String,
        city: String
    ): Result<Unit> {
        val params = buildJsonObject {
            put("p_registration_code", registrationCode.trim())
            put("p_full_name", fullName.trim())
            put("p_phone", phone.trim())
            put("p_address_or_room", addressOrRoom.trim())
            put("p_area", area.trim())
            put("p_city", city.trim())
        }.toString()
        return rest.rpc("submit_customer_registration", params, JsonObject.serializer())
            .map { }
    }

    suspend fun submitCustomerRegistrationByMess(
        messId: String,
        fullName: String,
        phone: String,
        addressOrRoom: String,
        area: String,
        city: String
    ): Result<Unit> {
        val params = buildJsonObject {
            put("p_mess_id", messId)
            put("p_full_name", fullName.trim())
            put("p_phone", phone.trim())
            put("p_address_or_room", addressOrRoom.trim())
            put("p_area", area.trim())
            put("p_city", city.trim())
        }.toString()
        return rest.rpc("submit_customer_registration_by_mess", params, JsonObject.serializer())
            .map { }
    }

    private val safeMessColumns =
        "id,name,owner_id,phone,phone_public,address,area,city,veg_type,monthly_price," +
            "lunch_time,dinner_time,delivery_areas,description,is_active,avg_rating,rating_count," +
            "daily_mark_cutoff_time,timezone,currency,meal_slots,default_due_day"

    suspend fun getDiscoverableActiveMesses(
        todayIso: String
    ): Result<Pair<List<Mess>, Map<String, DailyMenu>>> {
        val messesRes = rest.select(
            table = "messes",
            queryParams = listOf(
                "select" to safeMessColumns,
                "is_active" to "eq.true",
                "order" to "avg_rating.desc,name.asc"
            ),
            deserializer = ListSerializer(Mess.serializer())
        )
        if (messesRes.isFailure) return Result.failure(messesRes.exceptionOrNull()!!)

        val menusRes = rest.select(
            table = "daily_menus",
            queryParams = listOf(
                "menu_date" to "eq.$todayIso",
                "status" to "eq.published"
            ),
            deserializer = ListSerializer(DailyMenu.serializer())
        )
        val menusByMess = menusRes.getOrDefault(emptyList()).associateBy { it.messId }
        return Result.success(messesRes.getOrThrow() to menusByMess)
    }

    suspend fun getMessReviews(messId: String): Result<List<MessReview>> {
        val params = buildJsonObject {
            put("p_mess_id", messId)
        }.toString()
        return rest.rpc("get_mess_reviews", params, ListSerializer(MessReview.serializer()))
    }

    suspend fun upsertMessRating(
        messId: String,
        customerUserId: String,
        stars: Int,
        comment: String
    ): Result<Unit> {
        val body = buildJsonObject {
            put("mess_id", messId)
            put("customer_id", customerUserId)
            put("stars", stars.coerceIn(1, 5))
            put("comment", comment.trim())
        }.toString()
        return rest.insert(
            table = "mess_ratings",
            bodyJson = body,
            deserializer = ListSerializer(JsonObject.serializer()),
            upsertOnConflict = "mess_id,customer_id"
        ).map { }
    }

    // ==================== CUSTOMER DAILY MARK & LEAVES ====================

    suspend fun customerMarkTodayTiffin(choice: DailyMarkChoice): Result<CustomerDailyMark> {
        val params = buildJsonObject {
            put("p_choice", choice.dbValue)
        }.toString()
        return rest.rpc("customer_mark_today_tiffin", params, CustomerDailyMark.serializer())
    }

    suspend fun getCustomerDailyMarksForRange(
        customerId: String,
        startDateIso: String,
        endDateIso: String
    ): Result<List<CustomerDailyMark>> {
        return rest.select(
            table = "customer_daily_marks",
            queryParams = listOf(
                "customer_id" to "eq.$customerId",
                "service_date" to "gte.$startDateIso",
                "service_date" to "lte.$endDateIso",
                "order" to "service_date.desc"
            ),
            deserializer = ListSerializer(CustomerDailyMark.serializer())
        )
    }

    suspend fun getMessDailyMarksForDate(
        messId: String,
        dateIso: String
    ): Result<List<CustomerDailyMark>> {
        return rest.select(
            table = "customer_daily_marks",
            queryParams = listOf(
                "mess_id" to "eq.$messId",
                "service_date" to "eq.$dateIso"
            ),
            deserializer = ListSerializer(CustomerDailyMark.serializer())
        )
    }

    suspend fun getMessDailyMarksForRange(
        messId: String,
        startDateIso: String,
        endDateIso: String
    ): Result<List<CustomerDailyMark>> {
        return rest.select(
            table = "customer_daily_marks",
            queryParams = listOf(
                "mess_id" to "eq.$messId",
                "service_date" to "gte.$startDateIso",
                "service_date" to "lte.$endDateIso"
            ),
            deserializer = ListSerializer(CustomerDailyMark.serializer())
        )
    }

    suspend fun getCustomerLeaves(customerId: String): Result<List<TiffinLeave>> {
        return rest.select(
            table = "tiffin_leaves",
            queryParams = listOf(
                "customer_id" to "eq.$customerId",
                "order" to "start_date.desc"
            ),
            deserializer = ListSerializer(TiffinLeave.serializer())
        )
    }

    suspend fun getMessLeaves(messId: String): Result<List<TiffinLeave>> {
        return rest.select(
            table = "tiffin_leaves",
            queryParams = listOf(
                "mess_id" to "eq.$messId",
                "status" to "eq.active",
                "order" to "start_date.desc"
            ),
            deserializer = ListSerializer(TiffinLeave.serializer())
        )
    }

    suspend fun createTiffinLeave(
        messId: String,
        customerId: String,
        startDateIso: String,
        endDateIso: String,
        reason: String
    ): Result<TiffinLeave> {
        val body = buildJsonObject {
            put("mess_id", messId)
            put("customer_id", customerId)
            put("start_date", startDateIso)
            put("end_date", endDateIso)
            put("reason", reason.trim())
            put("status", "active")
        }.toString()
        return rest.insert("tiffin_leaves", body, ListSerializer(TiffinLeave.serializer()))
    }

    suspend fun cancelTiffinLeave(leaveId: String): Result<TiffinLeave> {
        val body = buildJsonObject {
            put("status", "cancelled")
        }.toString()
        return rest.update(
            table = "tiffin_leaves",
            filters = listOf("id" to "eq.$leaveId"),
            bodyJson = body,
            deserializer = ListSerializer(TiffinLeave.serializer())
        ).map { it.first() }
    }

    // ==================== CUSTOMER QUERIES & ACTIONS ====================

    suspend fun getCustomerTiffinsForRange(
        customerId: String,
        startDateIso: String,
        endDateIso: String
    ): Result<List<TiffinRecord>> {
        return rest.select(
            table = "tiffin_records",
            queryParams = listOf(
                "customer_id" to "eq.$customerId",
                "service_date" to "gte.$startDateIso",
                "service_date" to "lte.$endDateIso",
                "order" to "service_date.desc,meal_slot.asc"
            ),
            deserializer = ListSerializer(TiffinRecord.serializer())
        )
    }

    suspend fun getPublishedMenus(messId: String, limit: Int = 14): Result<List<DailyMenu>> {
        val res = rest.select(
            table = "daily_menus",
            queryParams = listOf(
                "mess_id" to "eq.$messId",
                "status" to "eq.published",
                "order" to "menu_date.desc",
                "limit" to limit.toString()
            ),
            deserializer = ListSerializer(DailyMenu.serializer())
        )
        res.onSuccess { list ->
            runCatching {
                sessionStore.saveCachedMenus(json.encodeToString(ListSerializer(DailyMenu.serializer()), list))
            }
        }
        return res
    }

    suspend fun getCachedMenus(): List<DailyMenu> {
        val raw = sessionStore.getCachedMenus() ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(DailyMenu.serializer()), raw) }.getOrDefault(emptyList())
    }

    suspend fun getCustomerSubscriptions(customerId: String): Result<List<Subscription>> {
        return rest.select(
            table = "subscriptions",
            queryParams = listOf(
                "customer_id" to "eq.$customerId",
                "order" to "created_at.desc"
            ),
            deserializer = ListSerializer(Subscription.serializer())
        )
    }

    suspend fun getMessPlans(messId: String, activeOnly: Boolean = true): Result<List<SubscriptionPlan>> {
        val params = mutableListOf(
            "mess_id" to "eq.$messId",
            "order" to "price.asc"
        )
        if (activeOnly) params.add("is_active" to "eq.true")
        return rest.select("subscription_plans", params, ListSerializer(SubscriptionPlan.serializer()))
    }

    suspend fun submitRenewalRequest(
        messId: String,
        customerId: String,
        planId: String?,
        note: String?
    ): Result<RenewalRequest> {
        val body = buildJsonObject {
            put("mess_id", messId)
            put("customer_id", customerId)
            if (!planId.isNullOrBlank()) put("requested_plan_id", planId)
            if (!note.isNullOrBlank()) put("note", note.trim())
        }.toString()
        return rest.insert("subscription_renewal_requests", body, ListSerializer(RenewalRequest.serializer()))
    }

    suspend fun getCustomerRenewalRequests(customerId: String): Result<List<RenewalRequest>> {
        return rest.select(
            table = "subscription_renewal_requests",
            queryParams = listOf("customer_id" to "eq.$customerId", "order" to "created_at.desc"),
            deserializer = ListSerializer(RenewalRequest.serializer())
        )
    }

    suspend fun getCustomerBills(customerId: String): Result<List<MonthlyBill>> {
        return rest.select(
            table = "monthly_bills",
            queryParams = listOf("customer_id" to "eq.$customerId", "order" to "billing_month.desc"),
            deserializer = ListSerializer(MonthlyBill.serializer())
        )
    }

    suspend fun getCustomerPayments(customerId: String): Result<List<Payment>> {
        return rest.select(
            table = "payments",
            queryParams = listOf("customer_id" to "eq.$customerId", "order" to "payment_date.desc"),
            deserializer = ListSerializer(Payment.serializer())
        )
    }

    suspend fun updateOwnCustomerProfile(
        userId: String,
        customerId: String,
        fullName: String,
        phone: String,
        addressOrRoom: String,
        area: String,
        city: String
    ): Result<Unit> {
        val profileBody = buildJsonObject {
            put("full_name", fullName.trim())
            put("phone", phone.trim())
            put("area", area.trim())
            put("city", city.trim())
        }.toString()
        val pRes = rest.update("profiles", listOf("id" to "eq.$userId"), profileBody, ListSerializer(Profile.serializer()))
        if (pRes.isFailure) return Result.failure(pRes.exceptionOrNull()!!)

        val custBody = buildJsonObject {
            put("full_name", fullName.trim())
            put("phone", phone.trim())
            put("address_or_room", addressOrRoom.trim())
        }.toString()
        return rest.update(
            "customer_profiles",
            listOf("id" to "eq.$customerId"),
            custBody,
            ListSerializer(CustomerProfile.serializer())
        ).map { }
    }

    // ==================== ADMIN QUERIES & ACTIONS ====================

    suspend fun getMessCustomers(messId: String): Result<List<CustomerProfile>> {
        return rest.select(
            table = "customer_profiles",
            queryParams = listOf("mess_id" to "eq.$messId", "order" to "full_name.asc"),
            deserializer = ListSerializer(CustomerProfile.serializer())
        )
    }

    suspend fun getPendingRegistrationRequests(messId: String): Result<List<RegistrationRequest>> {
        return rest.select(
            table = "registration_requests",
            queryParams = listOf(
                "mess_id" to "eq.$messId",
                "status" to "eq.pending",
                "order" to "created_at.desc"
            ),
            deserializer = ListSerializer(RegistrationRequest.serializer())
        )
    }

    suspend fun reviewRegistrationRequest(requestId: String, approve: Boolean): Result<Unit> {
        val params = buildJsonObject {
            put("p_request_id", requestId)
            put("p_decision", if (approve) "approved" else "rejected")
        }.toString()
        return rest.rpc("review_registration_request", params, kotlinx.serialization.json.JsonObject.serializer()).map { }
    }

    suspend fun adminCreateCustomer(
        messId: String,
        fullName: String,
        phone: String,
        email: String?,
        addressOrRoom: String
    ): Result<CustomerProfile> {
        val params = buildJsonObject {
            put("p_mess_id", messId)
            put("p_full_name", fullName.trim())
            put("p_phone", phone.trim())
            put("p_email", email?.trim()?.ifBlank { null })
            put("p_address_or_room", addressOrRoom.trim())
        }.toString()
        return rest.rpc("admin_create_customer", params, CustomerProfile.serializer())
    }

    suspend fun adminUpdateCustomerStatus(customerId: String, newStatus: String): Result<CustomerProfile> {
        val body = buildJsonObject { put("customer_status", newStatus) }.toString()
        return rest.update(
            "customer_profiles",
            listOf("id" to "eq.$customerId"),
            body,
            ListSerializer(CustomerProfile.serializer())
        ).map { it.first() }
    }

    suspend fun adminUpdateCustomerDetails(
        customerId: String,
        fullName: String,
        phone: String,
        addressOrRoom: String
    ): Result<CustomerProfile> {
        val body = buildJsonObject {
            put("full_name", fullName.trim())
            put("phone", phone.trim())
            put("address_or_room", addressOrRoom.trim())
        }.toString()
        return rest.update(
            "customer_profiles",
            listOf("id" to "eq.$customerId"),
            body,
            ListSerializer(CustomerProfile.serializer())
        ).map { it.first() }
    }

    suspend fun getMessTiffinsForDate(messId: String, serviceDateIso: String): Result<List<TiffinRecord>> {
        return rest.select(
            table = "tiffin_records",
            queryParams = listOf(
                "mess_id" to "eq.$messId",
                "service_date" to "eq.$serviceDateIso",
                "order" to "meal_slot.asc"
            ),
            deserializer = ListSerializer(TiffinRecord.serializer())
        )
    }

    suspend fun getMessTiffinsForRange(
        messId: String,
        startDateIso: String,
        endDateIso: String
    ): Result<List<TiffinRecord>> {
        return rest.select(
            table = "tiffin_records",
            queryParams = listOf(
                "mess_id" to "eq.$messId",
                "service_date" to "gte.$startDateIso",
                "service_date" to "lte.$endDateIso",
                "order" to "service_date.desc"
            ),
            deserializer = ListSerializer(TiffinRecord.serializer())
        )
    }

    suspend fun upsertTiffinRecord(
        customerId: String,
        serviceDateIso: String,
        mealSlot: String,
        status: TiffinStatus,
        note: String?
    ): Result<TiffinRecord> {
        val params = buildJsonObject {
            put("p_customer_id", customerId)
            put("p_service_date", serviceDateIso)
            put("p_meal_slot", mealSlot)
            put(
                "p_status",
                when (status) {
                    TiffinStatus.DELIVERED -> "delivered"
                    TiffinStatus.NOT_DELIVERED -> "not_delivered"
                    TiffinStatus.CANCELLED -> "cancelled"
                }
            )
            put("p_note", note?.trim()?.ifBlank { null })
        }.toString()
        return rest.rpc("upsert_tiffin_record", params, TiffinRecord.serializer())
    }

    suspend fun bulkMarkTiffinsDelivered(
        messId: String,
        customerIds: List<String>,
        serviceDateIso: String,
        mealSlot: String,
        note: String? = null
    ): Result<Int> {
        val params = buildJsonObject {
            put("p_mess_id", messId)
            put("p_customer_ids", JsonArray(customerIds.map { JsonPrimitive(it) }))
            put("p_service_date", serviceDateIso)
            put("p_meal_slot", mealSlot)
            put("p_note", note?.trim()?.ifBlank { null })
        }.toString()
        return rest.rpc("bulk_mark_tiffin_delivered", params, kotlinx.serialization.json.JsonPrimitive.serializer())
            .map { it.content.toIntOrNull() ?: 0 }
    }

    suspend fun getAllMessMenus(messId: String): Result<List<DailyMenu>> {
        return rest.select(
            table = "daily_menus",
            queryParams = listOf("mess_id" to "eq.$messId", "order" to "menu_date.desc", "limit" to "30"),
            deserializer = ListSerializer(DailyMenu.serializer())
        )
    }

    suspend fun saveMenu(
        messId: String,
        menuDateIso: String,
        breakfast: String?,
        lunch: String?,
        dinner: String?,
        specialItems: String?,
        notes: String?,
        publish: Boolean,
        createdBy: String
    ): Result<DailyMenu> {
        val body = buildJsonObject {
            put("mess_id", messId)
            put("menu_date", menuDateIso)
            put("breakfast", breakfast?.trim()?.ifBlank { null })
            put("lunch", lunch?.trim()?.ifBlank { null })
            put("dinner", dinner?.trim()?.ifBlank { null })
            put("special_items", specialItems?.trim()?.ifBlank { null })
            put("notes", notes?.trim()?.ifBlank { null })
            put("status", if (publish) "published" else "draft")
            if (publish) put("published_at", java.time.Instant.now().toString())
            put("created_by", createdBy)
        }.toString()
        return rest.insert(
            table = "daily_menus",
            bodyJson = body,
            deserializer = ListSerializer(DailyMenu.serializer()),
            upsertOnConflict = "mess_id,menu_date"
        )
    }

    suspend fun getMessSubscriptions(messId: String): Result<List<Subscription>> {
        return rest.select(
            table = "subscriptions",
            queryParams = listOf("mess_id" to "eq.$messId", "order" to "created_at.desc"),
            deserializer = ListSerializer(Subscription.serializer())
        )
    }

    suspend fun createSubscriptionPlan(
        messId: String,
        name: String,
        durationDays: Int,
        price: Double,
        includedTiffins: Int,
        mealSlots: List<String>,
        extraTiffinPrice: Double
    ): Result<SubscriptionPlan> {
        val body = buildJsonObject {
            put("mess_id", messId)
            put("name", name.trim())
            put("duration_days", durationDays)
            put("price", price)
            put("included_tiffins", includedTiffins)
            put("meal_slots", JsonArray(mealSlots.map { JsonPrimitive(it) }))
            put("extra_tiffin_price", extraTiffinPrice)
            put("is_active", true)
        }.toString()
        return rest.insert("subscription_plans", body, ListSerializer(SubscriptionPlan.serializer()))
    }

    suspend fun assignSubscription(
        messId: String,
        customerId: String,
        planId: String?,
        planName: String,
        startDateIso: String,
        endDateIso: String,
        includedTiffins: Int,
        extraTiffinPrice: Double,
        mealSlots: List<String>,
        agreedPrice: Double,
        createdBy: String
    ): Result<Subscription> {
        val body = buildJsonObject {
            put("mess_id", messId)
            put("customer_id", customerId)
            if (!planId.isNullOrBlank()) put("plan_id", planId)
            put("plan_name_snapshot", planName.trim())
            put("start_date", startDateIso)
            put("end_date", endDateIso)
            put("included_tiffins", includedTiffins)
            put("extra_tiffin_price_snapshot", extraTiffinPrice)
            put("meal_slots_snapshot", JsonArray(mealSlots.map { JsonPrimitive(it) }))
            put("agreed_price", agreedPrice)
            put("status", "active")
            put("created_by", createdBy)
        }.toString()
        return rest.insert("subscriptions", body, ListSerializer(Subscription.serializer()))
    }

    suspend fun updateSubscriptionStatus(subscriptionId: String, status: SubscriptionStatus): Result<Subscription> {
        val statusStr = when (status) {
            SubscriptionStatus.ACTIVE -> "active"
            SubscriptionStatus.EXPIRING_SOON -> "expiring_soon"
            SubscriptionStatus.EXPIRED -> "expired"
            SubscriptionStatus.PAUSED -> "paused"
            SubscriptionStatus.CANCELLED -> "cancelled"
        }
        val body = buildJsonObject { put("status", statusStr) }.toString()
        return rest.update(
            "subscriptions",
            listOf("id" to "eq.$subscriptionId"),
            body,
            ListSerializer(Subscription.serializer())
        ).map { it.first() }
    }

    suspend fun getMessRenewalRequests(messId: String): Result<List<RenewalRequest>> {
        return rest.select(
            table = "subscription_renewal_requests",
            queryParams = listOf("mess_id" to "eq.$messId", "status" to "eq.pending", "order" to "created_at.desc"),
            deserializer = ListSerializer(RenewalRequest.serializer())
        )
    }

    suspend fun reviewRenewalRequest(requestId: String, approve: Boolean, adminId: String): Result<Unit> {
        val body = buildJsonObject {
            put("status", if (approve) "approved" else "rejected")
            put("reviewed_by", adminId)
            put("reviewed_at", java.time.Instant.now().toString())
        }.toString()
        return rest.update(
            "subscription_renewal_requests",
            listOf("id" to "eq.$requestId"),
            body,
            ListSerializer(RenewalRequest.serializer())
        ).map { }
    }

    suspend fun getMessBills(messId: String, billingMonthIso: String? = null): Result<List<MonthlyBill>> {
        val params = mutableListOf(
            "mess_id" to "eq.$messId",
            "order" to "billing_month.desc,generated_at.desc"
        )
        if (!billingMonthIso.isNullOrBlank()) {
            params.add("billing_month" to "eq.$billingMonthIso")
        }
        return rest.select("monthly_bills", params, ListSerializer(MonthlyBill.serializer()))
    }

    suspend fun getMessPayments(messId: String): Result<List<Payment>> {
        return rest.select(
            table = "payments",
            queryParams = listOf("mess_id" to "eq.$messId", "order" to "payment_date.desc", "limit" to "100"),
            deserializer = ListSerializer(Payment.serializer())
        )
    }

    suspend fun generateMonthlyBill(
        customerId: String,
        billingMonthIso: String,
        discountAmount: Double,
        previousBalance: Double
    ): Result<MonthlyBill> {
        val params = buildJsonObject {
            put("p_customer_id", customerId)
            put("p_billing_month", billingMonthIso)
            put("p_discount_amount", discountAmount)
            put("p_previous_balance", previousBalance)
        }.toString()
        return rest.rpc("generate_monthly_bill", params, MonthlyBill.serializer())
    }

    suspend fun recordPayment(
        billId: String,
        amount: Double,
        method: PaymentMethod,
        referenceNote: String?
    ): Result<Payment> {
        val params = buildJsonObject {
            put("p_bill_id", billId)
            put("p_amount", amount)
            put("p_payment_method", method.dbValue)
            put("p_reference_note", referenceNote?.trim()?.ifBlank { null })
        }.toString()
        return rest.rpc("record_customer_payment", params, Payment.serializer())
    }

    suspend fun reversePayment(paymentId: String, reason: String): Result<Payment> {
        val params = buildJsonObject {
            put("p_payment_id", paymentId)
            put("p_reason", reason.trim().ifBlank { "Admin reversal" })
        }.toString()
        return rest.rpc("reverse_payment", params, Payment.serializer())
    }

    suspend fun updateMessSettings(
        messId: String,
        name: String,
        phone: String,
        address: String,
        currency: String,
        defaultDueDay: Int,
        mealSlots: List<String>,
        dailyMarkCutoffTime: String = "10:00"
    ): Result<Mess> {
        val body = buildJsonObject {
            put("name", name.trim())
            put("phone", phone.trim())
            put("address", address.trim())
            put("currency", currency.trim().uppercase())
            put("default_due_day", defaultDueDay.coerceIn(1, 28))
            put("meal_slots", JsonArray(mealSlots.map { JsonPrimitive(it) }))
            put("daily_mark_cutoff_time", dailyMarkCutoffTime.trim())
        }.toString()
        return rest.update(
            "messes",
            listOf("id" to "eq.$messId", "select" to safeMessColumns),
            body,
            ListSerializer(Mess.serializer())
        ).map { it.first() }
    }

    suspend fun ownerUpdateRegistrationCode(messId: String, newCode: String): Result<Boolean> {
        val params = buildJsonObject {
            put("p_mess_id", messId)
            put("p_new_code", newCode.trim())
        }.toString()
        return rest.rpc("owner_update_registration_code", params, JsonPrimitive.serializer())
            .map { it.content.toBooleanStrictOrNull() ?: true }
    }

    suspend fun ownerInviteAdmin(messId: String, email: String): Result<AdminInvitation> {
        val params = buildJsonObject {
            put("p_mess_id", messId)
            put("p_email", email.trim())
        }.toString()
        return rest.rpc("owner_invite_admin", params, AdminInvitation.serializer())
    }

    suspend fun getAdminInvitations(messId: String): Result<List<AdminInvitation>> {
        return rest.select(
            table = "admin_invitations",
            queryParams = listOf("mess_id" to "eq.$messId", "order" to "created_at.desc"),
            deserializer = ListSerializer(AdminInvitation.serializer())
        )
    }

    // ==================== SELLER UPI & PAYMENT SCREENSHOT PROOFS ====================

    suspend fun uploadSellerQrImage(messId: String, imageBytes: ByteArray): Result<String> {
        val objectPath = TiffinBusinessLogic.storagePathForSellerQr(messId, UUID.randomUUID().toString())
        return storage.uploadImage(
            bucket = SupabaseStorageClient.PAYMENT_PROOFS_BUCKET,
            objectPath = objectPath,
            bytes = imageBytes
        )
    }

    suspend fun updateSellerUpiSettings(
        messId: String,
        upiId: String?,
        upiQrPath: String?
    ): Result<MessUpiDetails> {
        val params = buildJsonObject {
            put("p_mess_id", messId)
            put("p_upi_id", upiId?.trim()?.ifBlank { null })
            put("p_upi_qr_path", upiQrPath?.trim()?.ifBlank { null })
        }.toString()
        return rest.rpc("seller_update_upi_settings", params, MessUpiDetails.serializer())
    }

    suspend fun getMyMessUpiDetails(messId: String): Result<MessUpiDetails> {
        val params = buildJsonObject {
            put("p_mess_id", messId)
        }.toString()
        return rest.rpc("get_my_mess_upi_details", params, MessUpiDetails.serializer())
    }

    suspend fun getSignedStorageUrl(objectPath: String): Result<String> {
        return storage.createSignedUrl(
            bucket = SupabaseStorageClient.PAYMENT_PROOFS_BUCKET,
            objectPath = objectPath
        )
    }

    suspend fun uploadCustomerPaymentScreenshot(
        messId: String,
        customerId: String,
        imageBytes: ByteArray
    ): Result<String> {
        val objectPath = TiffinBusinessLogic.storagePathForCustomerProof(
            messId = messId,
            customerId = customerId,
            fileId = UUID.randomUUID().toString()
        )
        return storage.uploadImage(
            bucket = SupabaseStorageClient.PAYMENT_PROOFS_BUCKET,
            objectPath = objectPath,
            bytes = imageBytes
        )
    }

    suspend fun submitPaymentProof(
        billId: String?,
        amount: Double,
        screenshotPath: String
    ): Result<PaymentProof> {
        val params = buildJsonObject {
            put("p_bill_id", billId)
            put("p_amount", amount)
            put("p_screenshot_path", screenshotPath.trim())
        }.toString()
        return rest.rpc("submit_payment_proof", params, PaymentProof.serializer())
    }

    suspend fun getCustomerPaymentProofs(customerId: String): Result<List<PaymentProof>> {
        return rest.select(
            table = "payment_proofs",
            queryParams = listOf(
                "customer_id" to "eq.$customerId",
                "order" to "created_at.desc"
            ),
            deserializer = ListSerializer(PaymentProof.serializer())
        )
    }

    suspend fun getMessPaymentProofs(messId: String): Result<List<PaymentProof>> {
        return rest.select(
            table = "payment_proofs",
            queryParams = listOf(
                "mess_id" to "eq.$messId",
                "order" to "created_at.desc"
            ),
            deserializer = ListSerializer(PaymentProof.serializer())
        )
    }

    suspend fun reviewPaymentProof(
        proofId: String,
        confirm: Boolean,
        rejectReason: String? = null
    ): Result<PaymentProof> {
        val params = buildJsonObject {
            put("p_proof_id", proofId)
            put("p_decision", if (confirm) "confirmed" else "rejected")
            put("p_reject_reason", rejectReason?.trim()?.ifBlank { null })
        }.toString()
        return rest.rpc("review_payment_proof", params, PaymentProof.serializer())
    }
}
