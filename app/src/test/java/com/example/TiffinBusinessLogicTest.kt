package com.example

import com.example.domain.AccountStatus
import com.example.domain.AdminCustomerMarkStatus
import com.example.domain.AuthContextPayload
import com.example.domain.AuthRouteState
import com.example.domain.BillStatus
import com.example.domain.CustomerDailyMark
import com.example.domain.CustomerProfile
import com.example.domain.DailyMarkChoice
import com.example.domain.DailyMenu
import com.example.domain.LeaveStatus
import com.example.domain.Mess
import com.example.domain.MessMember
import com.example.domain.MonthlyBill
import com.example.domain.OnboardingRoleChoice
import com.example.domain.OnboardingStep
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.PaymentProof
import com.example.domain.PaymentProofStatus
import com.example.domain.PaymentStatus
import com.example.domain.Profile
import com.example.domain.ProximityTier
import com.example.domain.RegistrationRequest
import com.example.domain.Subscription
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinLeave
import com.example.domain.TiffinRecord
import com.example.domain.TiffinStatus
import com.example.domain.UserRole
import com.example.domain.VegType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TiffinBusinessLogicTest {

    @Test
    fun `delivered count excludes cancelled and not delivered and floors remaining at zero`() {
        val sub = Subscription(
            id = "s1",
            messId = "m1",
            customerId = "c1",
            planNameSnapshot = "26 Tiffins",
            startDate = "2026-11-01",
            endDate = "2026-11-30",
            includedTiffins = 2,
            agreedPrice = 2000.0
        )
        val records = listOf(
            TiffinRecord("1", "m1", "c1", "s1", "2026-11-01", "lunch", TiffinStatus.DELIVERED),
            TiffinRecord("2", "m1", "c1", "s1", "2026-11-02", "lunch", TiffinStatus.DELIVERED),
            TiffinRecord("3", "m1", "c1", "s1", "2026-11-03", "lunch", TiffinStatus.DELIVERED),
            TiffinRecord("4", "m1", "c1", "s1", "2026-11-04", "lunch", TiffinStatus.CANCELLED),
            TiffinRecord("5", "m1", "c1", "s1", "2026-11-05", "lunch", TiffinStatus.NOT_DELIVERED)
        )

        val summary = TiffinBusinessLogic.calculateMonthlyTiffinSummary(records, sub)
        assertEquals(3, summary.deliveredCount)
        assertEquals(1, summary.cancelledCount)
        assertEquals(1, summary.notDeliveredCount)
        assertEquals(0, summary.remainingTiffins)
        assertEquals(1, summary.extraTiffins)
    }

    @Test
    fun `billing ignores reversed payments and computes overdue and partially paid accurately`() {
        val payments = listOf(
            Payment("p1", "m1", "c1", "b1", 1000.0, PaymentMethod.UPI, "2026-11-02T10:00:00Z", null, "admin1", PaymentStatus.CONFIRMED),
            Payment("p2", "m1", "c1", "b1", 500.0, PaymentMethod.CASH, "2026-11-03T10:00:00Z", null, "admin1", PaymentStatus.REVERSED)
        )

        val partial = TiffinBusinessLogic.calculateMonthlyBillTotals(
            subscriptionCharge = 2000.0,
            extraTiffinCharge = 200.0,
            previousBalance = 300.0,
            discountAmount = 100.0,
            payments = payments,
            dueDateIso = "2026-11-10",
            todayInMessZone = LocalDate.of(2026, 11, 8)
        )
        assertEquals(2400.0, partial.totalPayable, 0.01)
        assertEquals(1000.0, partial.amountPaid, 0.01)
        assertEquals(1400.0, partial.balanceDue, 0.01)
        assertEquals(BillStatus.PARTIALLY_PAID, partial.status)

        val overdue = TiffinBusinessLogic.calculateMonthlyBillTotals(
            subscriptionCharge = 2000.0,
            extraTiffinCharge = 200.0,
            previousBalance = 300.0,
            discountAmount = 100.0,
            payments = payments,
            dueDateIso = "2026-11-10",
            todayInMessZone = LocalDate.of(2026, 11, 15)
        )
        assertEquals(BillStatus.OVERDUE, overdue.status)
    }

    @Test
    fun `role routing never grants admin access unless both profile and membership verify admin`() {
        val mess = Mess("m1", "Annapurna", "owner1")
        val customer = CustomerProfile("c1", "m1", "u1", "Rohan", "9999999999")
        val spoofedProfile = Profile("u1", "Rohan", "rohan@test.com", role = UserRole.ADMIN, accountStatus = AccountStatus.ACTIVE)
        val customerMembership = MessMember("mm1", "m1", "u1", UserRole.CUSTOMER, AccountStatus.ACTIVE)

        val resolved = TiffinBusinessLogic.resolveAuthRouteState(
            AuthContextPayload(
                profile = spoofedProfile,
                membership = customerMembership,
                mess = mess,
                customerProfile = customer
            )
        )
        assertTrue(resolved is AuthRouteState.CustomerActive)
    }

    @Test
    fun `pending registration request routes to awaiting approval`() {
        val profile = Profile("u2", "Neha", "neha@test.com", role = UserRole.CUSTOMER, accountStatus = AccountStatus.PENDING)
        val req = RegistrationRequest("r1", "m1", "u2", "Neha", "9898989898", status = "pending")
        val resolved = TiffinBusinessLogic.resolveAuthRouteState(
            AuthContextPayload(profile = profile, registrationRequest = req)
        )
        assertTrue(resolved is AuthRouteState.AwaitingApproval)
    }

    @Test
    fun `role choice routing maps customer seller and browse nearby steps accurately`() {
        val newProfile = Profile("u3", "Priya", "priya@test.com", role = UserRole.CUSTOMER, accountStatus = AccountStatus.PENDING)
        val authRoute = TiffinBusinessLogic.resolveAuthRouteState(AuthContextPayload(profile = newProfile))
        assertTrue(authRoute is AuthRouteState.NeedsCustomerRegistration)

        assertEquals(
            OnboardingStep.CUSTOMER_REGISTRATION,
            TiffinBusinessLogic.resolveOnboardingStepForChoice(OnboardingRoleChoice.CUSTOMER)
        )
        assertEquals(
            OnboardingStep.SELLER_REGISTRATION,
            TiffinBusinessLogic.resolveOnboardingStepForChoice(OnboardingRoleChoice.SELLER)
        )
        assertEquals(
            OnboardingStep.DISCOVERY,
            TiffinBusinessLogic.resolveOnboardingStepForChoice(OnboardingRoleChoice.BROWSE_NEARBY)
        )
        assertEquals(
            OnboardingStep.DISCOVERY,
            TiffinBusinessLogic.resolveOnboardingBackStep(OnboardingStep.SELLER_DETAIL)
        )
        assertEquals(
            OnboardingStep.ROLE_CHOICE,
            TiffinBusinessLogic.resolveOnboardingBackStep(OnboardingStep.SELLER_REGISTRATION)
        )
    }

    @Test
    fun `register_my_mess validation rejects invalid fields and accepts valid seller form`() {
        assertNotNull(
            TiffinBusinessLogic.validateSellerRegistration(
                messName = "",
                area = "Dharmaji Colony",
                city = "Nashik",
                phone = "9876543210",
                vegType = VegType.VEG,
                monthlyPriceInput = "2500"
            )
        )
        assertNotNull(
            TiffinBusinessLogic.validateSellerRegistration(
                messName = "Maa Kitchen",
                area = "",
                city = "Nashik",
                phone = "9876543210",
                vegType = VegType.VEG,
                monthlyPriceInput = "2500"
            )
        )
        assertNotNull(
            TiffinBusinessLogic.validateSellerRegistration(
                messName = "Maa Kitchen",
                area = "Dharmaji Colony",
                city = "Nashik",
                phone = "12345",
                vegType = VegType.VEG,
                monthlyPriceInput = "2500"
            )
        )
        assertNotNull(
            TiffinBusinessLogic.validateSellerRegistration(
                messName = "Maa Kitchen",
                area = "Dharmaji Colony",
                city = "Nashik",
                phone = "9876543210",
                vegType = VegType.BOTH,
                monthlyPriceInput = "0"
            )
        )
        assertNull(
            TiffinBusinessLogic.validateSellerRegistration(
                messName = "Maa Kitchen",
                area = "Dharmaji Colony",
                city = "Nashik",
                phone = "+91 9876543210",
                vegType = VegType.VEG,
                monthlyPriceInput = "2500"
            )
        )
    }

    @Test
    fun `discovery sorting places same area first then same city then others and excludes inactive`() {
        val mOtherCity = Mess(
            id = "m1",
            name = "Pune Spice",
            ownerId = "o1",
            area = "Kothrud",
            city = "Pune",
            isActive = true,
            avgRating = 4.9,
            ratingCount = 20
        )
        val mSameCity = Mess(
            id = "m2",
            name = "College Road Mess",
            ownerId = "o2",
            area = "College Road",
            city = "Nashik",
            isActive = true,
            avgRating = 4.2,
            ratingCount = 10
        )
        val mSameArea = Mess(
            id = "m3",
            name = "Dharmaji Homemade",
            ownerId = "o3",
            area = "Dharmaji Colony",
            city = "Nashik",
            isActive = true,
            avgRating = 0.0,
            ratingCount = 0
        )
        val mInactive = Mess(
            id = "m4",
            name = "Closed Mess",
            ownerId = "o4",
            area = "Dharmaji Colony",
            city = "Nashik",
            isActive = false
        )
        val todayMenu = DailyMenu(
            id = "dm1",
            messId = "m3",
            menuDate = "2026-11-17",
            lunch = "Dal Tadka, Paneer Bhurji, 4 Chapati",
            dinner = "Aloo Gobi, Khichdi",
            status = "published"
        )

        val sorted = TiffinBusinessLogic.sortMessesByProximity(
            messes = listOf(mOtherCity, mSameCity, mSameArea, mInactive),
            todayMenusByMessId = mapOf("m3" to todayMenu),
            customerArea = "dharmaji colony",
            customerCity = "nashik"
        )

        assertEquals(3, sorted.size)
        assertEquals("m3", sorted[0].mess.id)
        assertEquals(ProximityTier.SAME_AREA_AND_CITY, sorted[0].proximityTier)
        assertNotNull(sorted[0].todayMenu)
        assertEquals("m2", sorted[1].mess.id)
        assertEquals(ProximityTier.SAME_CITY, sorted[1].proximityTier)
        assertEquals("m1", sorted[2].mess.id)
        assertEquals(ProximityTier.OTHER, sorted[2].proximityTier)
    }

    @Test
    fun `rating average computation returns zero for empty and rounds valid ratings`() {
        val emptySummary = TiffinBusinessLogic.computeRatingSummary(emptyList())
        assertEquals(0.0, emptySummary.first, 0.001)
        assertEquals(0, emptySummary.second)

        val summary = TiffinBusinessLogic.computeRatingSummary(listOf(5, 4, 5, 99, 0))
        assertEquals(4.67, summary.first, 0.01)
        assertEquals(3, summary.second)
    }

    @Test
    fun `join from discovery auto fills code slot and passes validation without manual code`() {
        val mess = Mess(
            id = "m10",
            name = "Annapurna Ghar Ka Khana",
            ownerId = "o10",
            area = "Dharmaji Colony",
            city = "Nashik"
        )
        val prefill = TiffinBusinessLogic.buildDiscoveryJoinPrefill(mess)
        assertEquals("m10", prefill.messId)
        assertEquals("Annapurna Ghar Ka Khana", prefill.messName)
        assertTrue(prefill.autoFilledCodeSlot.isNotBlank())

        val error = TiffinBusinessLogic.validateCustomerRegistrationInput(
            hasSelectedMess = true,
            registrationCode = "",
            fullName = "Rohan Patil",
            phone = "9876543210",
            area = prefill.area,
            city = prefill.city
        )
        assertNull(error)
    }

    @Test
    fun `customer mark cutoff enforces exact before and after cutoff times`() {
        assertTrue(TiffinBusinessLogic.isValidCutoffTime("10:00"))
        assertTrue(TiffinBusinessLogic.isValidCutoffTime("09:30"))
        assertFalse(TiffinBusinessLogic.isValidCutoffTime("25:00"))
        assertFalse(TiffinBusinessLogic.isValidCutoffTime("10:00 AM"))

        // Before and exactly at 10:00 AM are allowed; 10:01 AM is past cutoff
        assertFalse(
            TiffinBusinessLogic.isPastDailyCutoff("10:00", "Asia/Kolkata", LocalTime.of(9, 59))
        )
        assertFalse(
            TiffinBusinessLogic.isPastDailyCutoff("10:00", "Asia/Kolkata", LocalTime.of(10, 0))
        )
        assertTrue(
            TiffinBusinessLogic.isPastDailyCutoff("10:00", "Asia/Kolkata", LocalTime.of(10, 1))
        )
        assertTrue(
            TiffinBusinessLogic.isPastDailyCutoff("09:30", "Asia/Kolkata", LocalTime.of(9, 31))
        )
    }

    @Test
    fun `leave days and nahi chahiye marks are excluded from monthly counts and billing`() {
        val sub = Subscription(
            id = "s1",
            messId = "m1",
            customerId = "c1",
            planNameSnapshot = "Monthly 30",
            startDate = "2026-11-01",
            endDate = "2026-11-30",
            includedTiffins = 30,
            agreedPrice = 3000.0
        )
        val records = listOf(
            TiffinRecord("1", "m1", "c1", "s1", "2026-11-01", "lunch", TiffinStatus.DELIVERED),
            TiffinRecord("2", "m1", "c1", "s1", "2026-11-02", "lunch", TiffinStatus.DELIVERED), // Nahi Chahiye
            TiffinRecord("3", "m1", "c1", "s1", "2026-11-03", "lunch", TiffinStatus.DELIVERED), // Active leave
            TiffinRecord("4", "m1", "c1", "s1", "2026-11-04", "lunch", TiffinStatus.DELIVERED), // Active leave
            TiffinRecord("5", "m1", "c1", "s1", "2026-11-05", "lunch", TiffinStatus.DELIVERED)  // Cancelled leave -> counts
        )
        val marks = listOf(
            CustomerDailyMark("dm1", "m1", "c1", "2026-11-01", DailyMarkChoice.WANTED),
            CustomerDailyMark("dm2", "m1", "c1", "2026-11-02", DailyMarkChoice.NOT_WANTED)
        )
        val leaves = listOf(
            TiffinLeave("l1", "m1", "c1", "2026-11-03", "2026-11-04", "Ghar ja raha hu", LeaveStatus.ACTIVE),
            TiffinLeave("l2", "m1", "c1", "2026-11-05", "2026-11-06", "Cancelled plan", LeaveStatus.CANCELLED)
        )

        val summary = TiffinBusinessLogic.calculateMonthlyTiffinSummary(
            records = records,
            activeSubscription = sub,
            dailyMarks = marks,
            leaves = leaves
        )
        // Only Nov 1 and Nov 5 count
        assertEquals(2, summary.deliveredCount)
        assertEquals(28, summary.remainingTiffins)
    }

    @Test
    fun `customer can cancel leave only before its start date`() {
        val futureLeave = TiffinLeave("l1", "m1", "c1", "2026-11-20", "2026-11-25", "Home", LeaveStatus.ACTIVE)
        val startingTodayLeave = TiffinLeave("l2", "m1", "c1", "2026-11-18", "2026-11-22", "Home", LeaveStatus.ACTIVE)
        val cancelledLeave = TiffinLeave("l3", "m1", "c1", "2026-11-21", "2026-11-22", "Home", LeaveStatus.CANCELLED)

        assertTrue(TiffinBusinessLogic.canCustomerCancelLeave(futureLeave, "2026-11-18"))
        assertFalse(TiffinBusinessLogic.canCustomerCancelLeave(startingTodayLeave, "2026-11-18"))
        assertFalse(TiffinBusinessLogic.canCustomerCancelLeave(cancelledLeave, "2026-11-18"))
    }

    @Test
    fun `admin daily list summaries show marks leave status month totals and day totals accurately`() {
        val mess = Mess(id = "m1", name = "Annapurna", ownerId = "o1", monthlyPrice = 3000.0)
        val c1 = CustomerProfile("c1", "m1", "u1", "Rohan", "9999999991")
        val c2 = CustomerProfile("c2", "m1", "u2", "Neha", "9999999992")
        val c3 = CustomerProfile("c3", "m1", "u3", "Vikram", "9999999993")
        val c4 = CustomerProfile("c4", "m1", "u4", "Priya", "9999999994")

        val subs = listOf(
            Subscription(id = "s1", messId = "m1", customerId = "c1", planNameSnapshot = "Plan 30", startDate = "2026-11-01", endDate = "2026-11-30", includedTiffins = 30, agreedPrice = 3000.0),
            Subscription(id = "s2", messId = "m1", customerId = "c2", planNameSnapshot = "Plan 30", startDate = "2026-11-01", endDate = "2026-11-30", includedTiffins = 30, agreedPrice = 3000.0),
            Subscription(id = "s3", messId = "m1", customerId = "c3", planNameSnapshot = "Plan 30", startDate = "2026-11-01", endDate = "2026-11-30", includedTiffins = 30, agreedPrice = 3000.0),
            Subscription(id = "s4", messId = "m1", customerId = "c4", planNameSnapshot = "Plan 30", startDate = "2026-11-01", endDate = "2026-11-30", includedTiffins = 30, agreedPrice = 3000.0)
        )
        val marks = listOf(
            CustomerDailyMark("m1_1", "m1", "c1", "2026-11-18", DailyMarkChoice.WANTED),
            CustomerDailyMark("m1_2", "m1", "c2", "2026-11-18", DailyMarkChoice.NOT_WANTED)
        )
        val leaves = listOf(
            TiffinLeave("lv1", "m1", "c3", "2026-11-17", "2026-11-20", "Ghar ja raha hu", LeaveStatus.ACTIVE)
        )
        val monthTiffins = listOf(
            TiffinRecord("t1", "m1", "c1", "s1", "2026-11-10", "lunch", TiffinStatus.DELIVERED),
            TiffinRecord("t2", "m1", "c1", "s1", "2026-11-18", "lunch", TiffinStatus.DELIVERED),
            TiffinRecord("t3", "m1", "c3", "s3", "2026-11-10", "lunch", TiffinStatus.DELIVERED),
            // Even if accidentally recorded on leave date, excluded from month count
            TiffinRecord("t4", "m1", "c3", "s3", "2026-11-18", "lunch", TiffinStatus.DELIVERED)
        )
        val dailyTiffins = monthTiffins.filter { it.serviceDate == "2026-11-18" }

        val summaries = TiffinBusinessLogic.buildAdminDailyCustomerSummaries(
            customers = listOf(c1, c2, c3, c4),
            dateIso = "2026-11-18",
            dailyMarks = marks,
            leaves = leaves,
            monthTiffins = monthTiffins,
            subscriptions = subs,
            mess = mess
        )

        assertEquals(AdminCustomerMarkStatus.WANTED, summaries["c1"]?.markStatus)
        assertEquals(2, summaries["c1"]?.monthDeliveredCount)
        assertEquals(200.0, summaries["c1"]!!.monthAmount, 0.01)

        assertEquals(AdminCustomerMarkStatus.NOT_WANTED, summaries["c2"]?.markStatus)
        assertEquals(AdminCustomerMarkStatus.ON_LEAVE, summaries["c3"]?.markStatus)
        assertEquals(1, summaries["c3"]?.monthDeliveredCount) // t4 on leave date excluded
        assertEquals(100.0, summaries["c3"]!!.monthAmount, 0.01)
        assertEquals(AdminCustomerMarkStatus.NOT_MARKED, summaries["c4"]?.markStatus)

        val dayTotals = TiffinBusinessLogic.computeAdminDayTotals(
            activeCustomers = listOf(c1, c2, c3, c4),
            summaries = summaries,
            dailyTiffins = dailyTiffins,
            dateIso = "2026-11-18"
        )
        assertEquals(1, dayTotals.wantedCount)
        assertEquals(1, dayTotals.notWantedCount)
        assertEquals(1, dayTotals.onLeaveCount)
        assertEquals(1, dayTotals.notMarkedCount)
        assertEquals(1, dayTotals.deliveredTodayCount)
        assertEquals(100.0, dayTotals.deliveredTodayAmount, 0.01)
        assertEquals(100.0, dayTotals.wantedTodayAmount, 0.01)
    }

    @Test
    fun `app language defaults to hinglish and maps db values accurately`() {
        assertEquals(com.example.domain.AppLanguage.HINGLISH, com.example.domain.AppLanguage.DEFAULT)
        assertEquals(com.example.domain.AppLanguage.ENGLISH, com.example.domain.AppLanguage.fromDb("en"))
        assertEquals(com.example.domain.AppLanguage.ENGLISH, com.example.domain.AppLanguage.fromDb("English"))
        assertEquals(com.example.domain.AppLanguage.HINDI, com.example.domain.AppLanguage.fromDb("hi"))
        assertEquals(com.example.domain.AppLanguage.HINDI, com.example.domain.AppLanguage.fromDb("hindi"))
        assertEquals(com.example.domain.AppLanguage.HINGLISH, com.example.domain.AppLanguage.fromDb("hinglish"))
        assertEquals(com.example.domain.AppLanguage.HINGLISH, com.example.domain.AppLanguage.fromDb(null))
        assertEquals(com.example.domain.AppLanguage.HINGLISH, com.example.domain.AppLanguage.fromDb(""))
    }

    @Test
    fun `payment proof submission links right bill and amount without marking bill paid`() {
        val customer = CustomerProfile(
            id = "c1",
            messId = "m1",
            userId = "u1",
            fullName = "Rohan Patil",
            phone = "9876543210",
            customerStatus = AccountStatus.ACTIVE
        )
        val unpaidBill = MonthlyBill(
            id = "b1",
            messId = "m1",
            customerId = "c1",
            billingMonth = "2026-11-01",
            subscriptionCharge = 2500.0,
            totalAmount = 2500.0,
            amountPaid = 0.0,
            balanceDue = 2500.0,
            dueDate = "2026-11-10",
            status = BillStatus.PENDING
        )
        val path = TiffinBusinessLogic.storagePathForCustomerProof("m1", "c1", "proof001")
        val draft = TiffinBusinessLogic.buildPaymentProofDraft(
            customer = customer,
            activeMessId = "m1",
            bill = unpaidBill,
            amount = 2500.0,
            screenshotPath = path,
            proofId = "pr1"
        )
        assertTrue(draft.isSuccess)
        val proof = draft.getOrThrow()
        assertEquals("m1", proof.messId)
        assertEquals("c1", proof.customerId)
        assertEquals("b1", proof.billId)
        assertEquals(2500.0, proof.amount, 0.01)
        assertEquals(PaymentProofStatus.PENDING, proof.status)
        // Money rule: proof upload must NOT auto-mark bill paid
        assertEquals(BillStatus.PENDING, unpaidBill.status)
        assertEquals(2500.0, unpaidBill.balanceDue, 0.01)
    }

    @Test
    fun `seller confirm payment proof records UPI payment and marks bill paid`() {
        val unpaidBill = MonthlyBill(
            id = "b1",
            messId = "m1",
            customerId = "c1",
            billingMonth = "2026-11-01",
            subscriptionCharge = 2500.0,
            totalAmount = 2500.0,
            amountPaid = 0.0,
            balanceDue = 2500.0,
            dueDate = "2026-11-10",
            status = BillStatus.PENDING
        )
        val proof = PaymentProof(
            id = "pr1",
            messId = "m1",
            customerId = "c1",
            billId = "b1",
            amount = 2500.0,
            screenshotPath = "m1/proofs/c1/proof001.jpg",
            status = PaymentProofStatus.PENDING
        )

        val reviewed = TiffinBusinessLogic.applyPaymentProofReview(
            proof = proof,
            bill = unpaidBill,
            existingBillPayments = emptyList(),
            decision = PaymentProofStatus.CONFIRMED,
            rejectReason = null,
            reviewerAdminId = "owner1",
            reviewerMessId = "m1",
            todayInMessZone = LocalDate.of(2026, 11, 8)
        )
        assertTrue(reviewed.isSuccess)
        val (updatedProof, updatedBill) = reviewed.getOrThrow()
        assertEquals(PaymentProofStatus.CONFIRMED, updatedProof.status)
        assertNull(updatedProof.rejectReason)
        assertEquals(2500.0, updatedBill.amountPaid, 0.01)
        assertEquals(0.0, updatedBill.balanceDue, 0.01)
        assertEquals(BillStatus.PAID, updatedBill.status)
    }

    @Test
    fun `seller reject payment proof keeps bill unpaid and stores reject reason`() {
        val unpaidBill = MonthlyBill(
            id = "b1",
            messId = "m1",
            customerId = "c1",
            billingMonth = "2026-11-01",
            subscriptionCharge = 2500.0,
            totalAmount = 2500.0,
            amountPaid = 0.0,
            balanceDue = 2500.0,
            dueDate = "2026-11-10",
            status = BillStatus.PENDING
        )
        val proof = PaymentProof(
            id = "pr2",
            messId = "m1",
            customerId = "c1",
            billId = "b1",
            amount = 2500.0,
            screenshotPath = "m1/proofs/c1/proof002.jpg",
            status = PaymentProofStatus.PENDING
        )

        val reviewed = TiffinBusinessLogic.applyPaymentProofReview(
            proof = proof,
            bill = unpaidBill,
            existingBillPayments = emptyList(),
            decision = PaymentProofStatus.REJECTED,
            rejectReason = "UTR not visible in screenshot",
            reviewerAdminId = "owner1",
            reviewerMessId = "m1",
            todayInMessZone = LocalDate.of(2026, 11, 8)
        )
        assertTrue(reviewed.isSuccess)
        val (updatedProof, updatedBill) = reviewed.getOrThrow()
        assertEquals(PaymentProofStatus.REJECTED, updatedProof.status)
        assertEquals("UTR not visible in screenshot", updatedProof.rejectReason)
        assertEquals(0.0, updatedBill.amountPaid, 0.01)
        assertEquals(2500.0, updatedBill.balanceDue, 0.01)
        assertEquals(BillStatus.PENDING, updatedBill.status)
    }

    @Test
    fun `customer cannot submit payment proof for another mess or cross mess review`() {
        val customerMess1 = CustomerProfile(
            id = "c1",
            messId = "m1",
            userId = "u1",
            fullName = "Rohan Patil",
            phone = "9876543210",
            customerStatus = AccountStatus.ACTIVE
        )
        val billInMess2 = MonthlyBill(
            id = "b_other",
            messId = "m2",
            customerId = "c_other",
            billingMonth = "2026-11-01",
            subscriptionCharge = 3000.0,
            totalAmount = 3000.0,
            amountPaid = 0.0,
            balanceDue = 3000.0,
            dueDate = "2026-11-10",
            status = BillStatus.PENDING
        )

        val crossMessResult = TiffinBusinessLogic.buildPaymentProofDraft(
            customer = customerMess1,
            activeMessId = "m1",
            bill = billInMess2,
            amount = 3000.0,
            screenshotPath = "m1/proofs/c1/x.jpg"
        )
        assertTrue(crossMessResult.isFailure)

        val mismatchedStoragePath = TiffinBusinessLogic.buildPaymentProofDraft(
            customer = customerMess1,
            activeMessId = "m1",
            bill = billInMess2.copy(messId = "m1", customerId = "c1"),
            amount = 3000.0,
            screenshotPath = "m2/proofs/c_other/x.jpg"
        )
        assertTrue(mismatchedStoragePath.isFailure)

        val crossMessAdminReview = TiffinBusinessLogic.applyPaymentProofReview(
            proof = PaymentProof("pr9", "m2", "c_other", "b_other", 3000.0, "m2/proofs/c_other/x.jpg"),
            bill = billInMess2,
            existingBillPayments = emptyList(),
            decision = PaymentProofStatus.CONFIRMED,
            rejectReason = null,
            reviewerAdminId = "owner_m1",
            reviewerMessId = "m1",
            todayInMessZone = LocalDate.of(2026, 11, 8)
        )
        assertTrue(crossMessAdminReview.isFailure)
    }

    @Test
    fun `upi id validation accepts valid handles and rejects invalid strings`() {
        assertTrue(TiffinBusinessLogic.isValidUpiId("annapurnamess@okhdfcbank"))
        assertTrue(TiffinBusinessLogic.isValidUpiId("9876543210@ybl"))
        assertFalse(TiffinBusinessLogic.isValidUpiId("invalid_without_at"))
        assertFalse(TiffinBusinessLogic.isValidUpiId("@okicici"))
        assertFalse(TiffinBusinessLogic.isValidUpiId("name@"))
    }
}
